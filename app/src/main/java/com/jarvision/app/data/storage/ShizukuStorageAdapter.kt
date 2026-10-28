package com.jarvision.app.data.storage

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.jarvision.app.shizuku.IShizukuStorageService
import com.jarvision.app.shizuku.ShizukuStorageUserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ShizukuConnectionStatus(
    val binderAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val isBinding: Boolean = false,
    val isConnected: Boolean = false,
    val serviceUid: Int? = null,
    val message: String = "Shizuku is not connected."
) {
    val identityLabel: String
        get() = when (serviceUid) {
            0 -> "Root identity"
            2000 -> "ADB / shell identity"
            null -> "No active service"
            else -> "UID $serviceUid"
        }
}

/**
 * App-process bridge to the Shizuku UserService. Existing items remain read-only; creation of
 * new folders, files and empty ZIPs is delegated to the service and confined to shared storage.
 */
class ShizukuStorageAdapter private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val mutableStatus = MutableStateFlow(ShizukuConnectionStatus())
    val status: StateFlow<ShizukuConnectionStatus> = mutableStatus.asStateFlow()

    @Volatile
    private var remoteService: IShizukuStorageService? = null

    @Volatile
    private var binding = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                applicationContext.packageName,
                ShizukuStorageUserService::class.java.name
            )
        )
            .tag("jarvision-storage")
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("storage")
            .version(USER_SERVICE_VERSION)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = IShizukuStorageService.Stub.asInterface(binder)
            if (service == null || binder?.isBinderAlive != true) {
                remoteService = null
                binding = false
                mutableStatus.value = mutableStatus.value.copy(
                    isBinding = false,
                    isConnected = false,
                    message = "Shizuku started, but the storage service did not connect."
                )
                return
            }

            try {
                val uid = service.uid
                remoteService = service
                binding = false
                mutableStatus.value = mutableStatus.value.copy(
                    binderAvailable = true,
                    permissionGranted = true,
                    isBinding = false,
                    isConnected = true,
                    serviceUid = uid,
                    message = "Connected. Browse shared storage and create new items where Shizuku permits."
                )
            } catch (error: Exception) {
                remoteService = null
                binding = false
                mutableStatus.value = mutableStatus.value.copy(
                    isBinding = false,
                    isConnected = false,
                    message = "The Shizuku service could not be verified: ${error.message ?: "unknown error"}"
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            binding = false
            mutableStatus.value = mutableStatus.value.copy(
                isBinding = false,
                isConnected = false,
                serviceUid = null,
                message = "Shizuku disconnected. Start its service and reconnect."
            )
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshBinderStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        binding = false
        mutableStatus.value = ShizukuConnectionStatus(
            message = "Shizuku stopped. Start its service to reconnect."
        )
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_PERMISSION_CODE) return@OnRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                mutableStatus.value = mutableStatus.value.copy(
                    permissionGranted = true,
                    message = "Permission granted. Connecting to the storage service…"
                )
                bindUserService()
            } else {
                mutableStatus.value = mutableStatus.value.copy(
                    permissionGranted = false,
                    isBinding = false,
                    message = "Shizuku permission was denied. Approve JarVision in Shizuku to continue."
                )
            }
        }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
            mutableStatus.value = ShizukuConnectionStatus(
                message = "Shizuku is unavailable on this device."
            )
        }
        refreshBinderStatus()
    }

    fun connect() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            mutableStatus.value = ShizukuConnectionStatus(
                message = "Shizuku is not running. Install Shizuku, start its service, then connect."
            )
            return
        }
        if (Shizuku.isPreV11()) {
            mutableStatus.value = mutableStatus.value.copy(
                message = "This Shizuku version is too old. Update Shizuku before connecting."
            )
            return
        }

        val hasPermission = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (error: Throwable) {
            mutableStatus.value = mutableStatus.value.copy(
                binderAvailable = true,
                message = "Couldn't check Shizuku permission: ${error.message ?: "unknown error"}"
            )
            return
        }

        if (hasPermission) {
            bindUserService()
        } else {
            try {
                mutableStatus.value = mutableStatus.value.copy(
                    binderAvailable = true,
                    permissionGranted = false,
                    isBinding = false,
                    message = "Waiting for approval in Shizuku…"
                )
                Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
            } catch (error: Throwable) {
                mutableStatus.value = mutableStatus.value.copy(
                    message = "Couldn't request Shizuku permission: ${error.message ?: "unknown error"}"
                )
            }
        }
    }

    fun disconnect() {
        val wasBinding = binding
        binding = false
        remoteService = null
        try {
            if (wasBinding || mutableStatus.value.isConnected) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        } catch (_: Throwable) {
            // The binder may already be dead; state below still reflects a disconnected adapter.
        }
        mutableStatus.value = mutableStatus.value.copy(
            isBinding = false,
            isConnected = false,
            serviceUid = null,
            message = "Shizuku storage connection disconnected."
        )
    }

    @Suppress("DEPRECATION")
    val sharedStorageRootPath: String
        get() = android.os.Environment.getExternalStorageDirectory().absolutePath

    fun listChildren(path: String): List<StorageEntry> {
        val service = requireService()
        val bundles = try {
            service.listDirectory(path)
        } catch (error: RemoteException) {
            throw IOException(error.message ?: "Shizuku couldn't list this folder.", error)
        }
        return bundles.map { bundle ->
            val absolutePath = bundle.getString(KEY_ABSOLUTE_PATH)
                ?: bundle.getString(KEY_DOCUMENT_ID)
                ?: throw IOException("Shizuku returned an item without a path.")
            StorageEntry(
                documentId = bundle.getString(KEY_DOCUMENT_ID) ?: absolutePath,
                displayName = bundle.getString(KEY_DISPLAY_NAME) ?: "Unnamed item",
                mimeType = bundle.getString(KEY_MIME_TYPE),
                isDirectory = bundle.getBoolean(KEY_IS_DIRECTORY),
                sizeBytes = bundle.getLong(KEY_SIZE_BYTES, -1L).takeIf { it >= 0L },
                lastModifiedMillis = bundle.getLong(KEY_LAST_MODIFIED).takeIf { it > 0L },
                absolutePath = absolutePath
            )
        }.sortedWith(
            compareByDescending<StorageEntry> { it.isDirectory }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
    }

    fun loadFolderThumbnail(path: String): ByteArray? {
        val service = requireService()
        return try {
            service.loadFolderThumbnail(path)
        } catch (error: RemoteException) {
            throw IOException(error.message ?: "Shizuku couldn't load this folder thumbnail.", error)
        }
    }

    /** Creates a new folder in the specified shared-storage directory through Shizuku. */
    fun createDirectory(parentPath: String, name: String) {
        callCreateOperation("folder") { it.createDirectory(parentPath, name) }
    }

    /** Creates a new empty file in the specified shared-storage directory through Shizuku. */
    fun createEmptyFile(parentPath: String, name: String) {
        callCreateOperation("file") { it.createEmptyFile(parentPath, name) }
    }

    /** Creates a valid, empty ZIP archive in the specified shared-storage directory through Shizuku. */
    fun createEmptyZip(parentPath: String, name: String) {
        callCreateOperation("ZIP archive") { it.createEmptyZip(parentPath, name) }
    }

    /** Opens a read-only stream for archive validation or explicit sharing. */
    fun openReadOnlyInputStream(path: String): InputStream {
        val service = requireService()
        val descriptor = try {
            service.openFileReadOnly(path)
        } catch (error: RemoteException) {
            throw IOException(error.message ?: "Shizuku couldn't open this file.", error)
        } ?: throw IOException("Shizuku returned no file descriptor.")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    }

    /**
     * Returns bounded UTF-8 text for an in-app read-only preview. Binary or oversized files are
     * rejected rather than silently truncated or copied somewhere writable.
     */
    fun readTextFile(path: String, maxBytes: Int = MAX_PREVIEW_BYTES): String {
        val service = requireService()
        val descriptor = try {
            service.openFileReadOnly(path)
        } catch (error: RemoteException) {
            throw IOException(error.message ?: "Shizuku couldn't open this file.", error)
        } ?: throw IOException("Shizuku returned no file descriptor.")

        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > maxBytes) {
                    throw IOException("This file is larger than the 1 MiB read-only preview limit.")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.any { it == 0.toByte() }) {
            throw IOException("This looks like a binary file. Read-only preview currently supports text and config files.")
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun callCreateOperation(
        itemDescription: String,
        operation: (IShizukuStorageService) -> Unit
    ) {
        val service = requireService()
        try {
            operation(service)
        } catch (error: RemoteException) {
            throw IOException(error.message ?: "Shizuku couldn't create the $itemDescription.", error)
        }
    }

    private fun requireService(): IShizukuStorageService =
        remoteService?.takeIf { runCatching { it.asBinder().isBinderAlive }.getOrDefault(false) }
            ?: throw IOException("Shizuku is not connected. Connect it from Advanced Access first.")

    private fun bindUserService() {
        if (remoteService != null || binding) return
        if (runCatching { Shizuku.getVersion() }.getOrDefault(0) < MIN_USER_SERVICE_VERSION) {
            mutableStatus.value = mutableStatus.value.copy(
                isBinding = false,
                message = "This Shizuku service is too old for UserService. Update Shizuku and retry."
            )
            return
        }
        binding = true
        mutableStatus.value = mutableStatus.value.copy(
            binderAvailable = true,
            permissionGranted = true,
            isBinding = true,
            message = "Connecting to Shizuku storage…"
        )
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (error: Throwable) {
            binding = false
            mutableStatus.value = mutableStatus.value.copy(
                isBinding = false,
                isConnected = false,
                message = "Shizuku connection failed: ${error.message ?: "unknown error"}"
            )
        }
    }

    private fun refreshBinderStatus() {
        val available = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!available) {
            mutableStatus.value = mutableStatus.value.copy(
                binderAvailable = false,
                isConnected = false,
                isBinding = false,
                serviceUid = null,
                message = "Shizuku is not running. Start its service to enable protected-folder browsing."
            )
            return
        }
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        mutableStatus.value = mutableStatus.value.copy(
            binderAvailable = true,
            permissionGranted = granted,
            message = when {
                remoteService != null -> mutableStatus.value.message
                granted -> "Shizuku is running. Connect the storage service."
                else -> "Shizuku is running. JarVision needs permission to connect."
            }
        )
    }

    companion object {
        const val REQUEST_PERMISSION_CODE = 5721
        const val MIN_USER_SERVICE_VERSION = 10
        const val USER_SERVICE_VERSION = 2
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_MIME_TYPE = "mimeType"
        const val KEY_IS_DIRECTORY = "isDirectory"
        const val KEY_SIZE_BYTES = "sizeBytes"
        const val KEY_LAST_MODIFIED = "lastModifiedMillis"
        const val KEY_ABSOLUTE_PATH = "absolutePath"
        const val MAX_PREVIEW_BYTES = 1024 * 1024

        @Volatile
        private var instance: ShizukuStorageAdapter? = null

        fun get(context: Context): ShizukuStorageAdapter = instance
            ?: synchronized(this) {
                instance ?: ShizukuStorageAdapter(context.applicationContext).also {
                    instance = it
                }
            }
    }
}
