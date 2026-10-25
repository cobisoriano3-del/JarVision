package com.jarvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvision.app.ui.navigation.JarVisionApp
import com.jarvision.app.ui.theme.JarVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JarVisionTheme {
                JarVisionApp()
            }
        }
    }
}
