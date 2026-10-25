package com.jarvision.app.feature.access

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jarvision.app.data.storage.ShizukuStorageAdapter

@Composable
fun AdvancedAccessScreen() {
    val context = LocalContext.current
    val adapter = remember(context) { ShizukuStorageAdapter.get(context) }
    val status by adapter.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Advanced Access", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connect optional system-level storage access. Normal file browsing continues to work without Shizuku.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Shizuku storage connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (status.isConnected) {
                    Text(
                        "Connection identity: ${status.identityLabel}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (status.isConnected || status.isBinding) adapter.disconnect()
                            else adapter.connect()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when {
                                status.isConnected || status.isBinding -> "Disconnect"
                                status.binderAvailable && !status.permissionGranted -> "Authorize"
                                else -> "Connect Shizuku"
                            }
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://shizuku.rikka.app/download/")
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                // The status text still explains that Shizuku must be installed.
                            }
                        }
                    ) {
                        Text("Get Shizuku")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Read-only access", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When connected, use the Shizuku button in Files to browse existing shared-storage folders, including Android/data and Android/obb wherever your device permits. Text and config files can be previewed read-only, up to 1 MiB.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Shizuku may run as ADB/shell or root. Shell access is not root access, and Android or the device manufacturer may still deny specific folders. This adapter does not write, rename, replace, or delete files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
