package com.navpilot.presentation.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val slides = listOf(
        Triple("Get there with confidence", "Clear, turn-by-turn directions with live traffic and accurate arrival times.", 0),
        Triple("Keeps going without signal", "Drive through tunnels and parking garages. NavPilot keeps tracking your position even when GPS drops.", 1),
        Triple("Private by design", "Your location stays on your phone. Download maps to navigate fully offline, anytime.", 2)
    )
    val last = index == slides.lastIndex
    Column(Modifier.fillMaxSize().background(Surface)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = Ink3, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth().background(Bg, RoundedCornerShape(24.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
                when (index) {
                    0 -> NavigationArt()
                    1 -> OfflineArt()
                    else -> PrivacyArt()
                }
            }
            Spacer(Modifier.height(36.dp))
            Text(slides[index].first, fontSize = 25.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Ink)
            Text(slides[index].second, fontSize = 15.sp, lineHeight = 22.sp, color = Ink2, modifier = Modifier.padding(top = 10.dp))
        }
        Column(Modifier.padding(horizontal = 32.dp, vertical = 34.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slides.indices.forEach { i -> Box(Modifier.height(6.dp).width(if (i == index) 28.dp else 6.dp).background(if (i == index) Brand else Line, RoundedCornerShape(4.dp))) }
            }
            Button(onClick = { if (last) onDone() else index++ }, Modifier.fillMaxWidth().padding(top = 24.dp).height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Brand)) {
                Text(if (last) "Get started" else "Continue", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable private fun NavigationArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Navigation, null, Modifier.size(76.dp), Brand)
        Text("SMART ROUTING", color = Ink3, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable private fun OfflineArt() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(44.dp).background(Success.copy(.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Success, modifier = Modifier.size(25.dp)) }
        Text("GNSS", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Success)
        Text("→", color = Ink3)
        Box(Modifier.size(44.dp).background(Brand.copy(.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Navigation, null, tint = Brand, modifier = Modifier.size(25.dp)) }
        Text("INS", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Brand)
    }
}
@Composable private fun PrivacyArt() {
    Box(Modifier.size(100.dp).background(Color.White, RoundedCornerShape(50.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Success, modifier = Modifier.size(48.dp)) }
}
