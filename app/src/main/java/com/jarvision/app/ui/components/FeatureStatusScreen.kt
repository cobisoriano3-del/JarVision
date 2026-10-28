package com.jarvision.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun FeatureStatusScreen(
    title: String,
    description: String,
    status: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF020B18), Color(0xFF04172C), Color(0xFF020B18)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(122.dp).background(
                    Brush.radialGradient(listOf(Color(0xFF0D3E70), Color(0xFF062344), Color(0xFF03152A))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(82.dp),
                    shape = RoundedCornerShape(25.dp),
                    color = Color(0xFF082D52),
                    border = BorderStroke(1.dp, Color(0xFF1E5E9A))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color(0xFF79BDFF))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Surface(shape = CircleShape, color = Color(0xFF0A3767), border = BorderStroke(1.dp, Color(0xFF1B5B92))) {
                Text(status, modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFF9BCFFF))
            }
            Spacer(Modifier.height(17.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF061B31)),
                shape = RoundedCornerShape(21.dp),
                border = BorderStroke(1.dp, Color(0xFF123D68))
            ) {
                Column(Modifier.padding(19.dp)) {
                    Text("Built in phases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "This screen is part of the foundation. It will become functional in its own implementation phase; no fake files or unsupported actions are shown.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
