package com.jarvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvision.app.data.storage.ShizukuStorageAdapter
import com.jarvision.app.ui.navigation.JarVisionApp
import com.jarvision.app.ui.theme.JarVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ShizukuStorageAdapter.get(applicationContext)

        setContent {
            JarVisionTheme {
                JarVisionApp()
            }
        }
    }
}
