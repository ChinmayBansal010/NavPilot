package com.navpilot.presentation.screens.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.navpilot.core.ui.theme.*
import com.navpilot.presentation.components.BackButton
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.SectionTitle

@Composable
fun OfflineMapsScreen(onBack: () -> Unit) {
    val regions = listOf("Karnataka" to "142 MB", "Tamil Nadu" to "138 MB", "Maharashtra" to "164 MB", "Delhi NCR" to "98 MB")
    var saved by remember { mutableStateOf(setOf("Karnataka", "Tamil Nadu")) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { BackButton(onBack); Text("Offline maps", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(start = 4.dp)) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Card(Modifier.padding(top = 4.dp, bottom = 20.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Storage used", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text("280 MB of 2 GB", fontSize = 14.sp, color = Ink2) }; Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(8.dp).background(Bg, RoundedCornerShape(8.dp))) { Box(Modifier.fillMaxWidth(.14f).fillMaxHeight().background(Brand, RoundedCornerShape(8.dp))) } }
            SectionTitle("Regions")
            Card {
                regions.forEachIndexed { i, region ->
                    val isSaved = region.first in saved
                    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).background(Bg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Layers, null, Modifier.size(20.dp), Ink2) }
                        Column(Modifier.weight(1f).padding(start = 13.dp)) { Text(region.first, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(region.second, fontSize = 13.sp, color = Ink2) }
                        androidx.compose.material3.TextButton(onClick = { saved = if (isSaved) saved - region.first else saved + region.first }) { Icon(if (isSaved) Icons.Default.Check else Icons.Default.Download, null, Modifier.size(17.dp)); Text(if (isSaved) "Saved" else "Get", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp)) }
                    }
                    if (i < regions.lastIndex) androidx.compose.material3.HorizontalDivider(color = Line)
                }
            }
        }
    }
}
