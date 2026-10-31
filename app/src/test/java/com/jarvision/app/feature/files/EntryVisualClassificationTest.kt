package com.jarvision.app.feature.files

import com.jarvision.app.data.storage.StorageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryVisualClassificationTest {
    @Test
    fun mapsCommonFileExtensionsToDistinctKinds() {
        assertEquals(EntryVisualKind.APK, classify("app-debug.apk"))
        assertEquals(EntryVisualKind.PDF, classify("paper.pdf"))
        assertEquals(EntryVisualKind.TEXT, classify("notes.txt"))
        assertEquals(EntryVisualKind.IMAGE, classify("photo.jpg"))
        assertEquals(EntryVisualKind.VIDEO, classify("clip.mp4"))
        assertEquals(EntryVisualKind.AUDIO, classify("sound.mp3"))
        assertEquals(EntryVisualKind.ZIP_UNKNOWN, classify("bundle.zip"))
        assertEquals(EntryVisualKind.ARCHIVE, classify("bundle.mcpack"))
        assertEquals(EntryVisualKind.GENERIC, classify("unknown.blob"))
    }

    @Test
    fun identifiesPackageDirectoryOnlyWhenItsParentIsAndroidData() {
        val state = FilesUiState(
            accessMode = StorageAccessMode.SHARED_STORAGE,
            currentPath = listOf(
                FolderLocation("android", "Android", "/storage/emulated/0/Android"),
                FolderLocation("data", "data", "/storage/emulated/0/Android/data")
            )
        )
        val packageFolder = StorageEntry(
            documentId = "/storage/emulated/0/Android/data/com.discord",
            displayName = "com.discord",
            mimeType = null,
            isDirectory = true,
            sizeBytes = null,
            lastModifiedMillis = null,
            absolutePath = "/storage/emulated/0/Android/data/com.discord"
        )
        assertTrue(isAndroidDataPackageFolder(packageFolder, state))
        assertEquals(EntryVisualKind.APP_FOLDER, defaultEntryVisualKind(packageFolder, state))
    }

    @Test
    fun regularPackageNamedFolderDoesNotLookLikeAnAppFolder() {
        val state = FilesUiState(
            accessMode = StorageAccessMode.SHARED_STORAGE,
            currentPath = listOf(FolderLocation("download", "Download", "/storage/emulated/0/Download"))
        )
        val folder = StorageEntry(
            documentId = "/storage/emulated/0/Download/com.discord",
            displayName = "com.discord",
            mimeType = null,
            isDirectory = true,
            sizeBytes = null,
            lastModifiedMillis = null,
            absolutePath = "/storage/emulated/0/Download/com.discord"
        )
        assertFalse(isAndroidDataPackageFolder(folder, state))
        assertEquals(EntryVisualKind.FOLDER, defaultEntryVisualKind(folder, state))
    }

    private fun classify(name: String): EntryVisualKind = defaultEntryVisualKind(
        StorageEntry(
            documentId = name,
            displayName = name,
            mimeType = null,
            isDirectory = false,
            sizeBytes = 100,
            lastModifiedMillis = 1
        ),
        FilesUiState()
    )

    @Test
    fun apkInstallCandidateIsExclusiveToApkFiles() {
        fun entry(name: String, directory: Boolean = false) = StorageEntry(
            documentId = name,
            displayName = name,
            mimeType = null,
            isDirectory = directory,
            sizeBytes = null,
            lastModifiedMillis = null
        )

        assertTrue(isApkInstallCandidate(entry("installer.apk")))
        assertTrue(isApkInstallCandidate(entry("INSTALLER.APK")))
        assertFalse(isApkInstallCandidate(entry("installer.apk", directory = true)))
        assertFalse(isApkInstallCandidate(entry("installer.apks")))
        assertFalse(isApkInstallCandidate(entry("installer.zip")))
        assertFalse(isApkInstallCandidate(entry("installer.txt")))
    }
}
