package com.navpilot.presentation.screens.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*

@Composable
fun SetupScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf("Calibrating IMU sensors", "Loading AI navigation models", "Warming up map matcher")
    LaunchedEffect(step) {
        if (step < steps.size) { kotlinx.coroutines.delay(900); step++ }
        else { kotlinx.coroutines.delay(700); onDone() }
    }
    val done = step >= steps.size
    Column(Modifier.fillMaxSize().background(Surface).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).background(Brand.copy(.1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (done) Icons.Default.Check else Icons.Default.Explore, null, Modifier.size(48.dp), if (done) Success else Brand)
        }
        Text(if (done) "You're all set" else "Setting things up", fontSize = 25.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(top = 32.dp))
        Text(if (done) "NavPilot is ready to guide you." else "This only takes a moment.", color = Ink2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 36.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { i, label ->
                val complete = step > i; val active = step == i
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).background(if (complete) Success.copy(.12f) else if (active) Brand.copy(.12f) else Bg, CircleShape), contentAlignment = Alignment.Center) {
                        if (complete) Icon(Icons.Default.Check, null, Modifier.size(16.dp), Success) else Box(Modifier.size(8.dp).background(if (active) Brand else Ink3.copy(.4f), CircleShape))
                    }
                    Text(label, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = if (complete || active) Ink else Ink3)
                }
            }
        }
    }
}
