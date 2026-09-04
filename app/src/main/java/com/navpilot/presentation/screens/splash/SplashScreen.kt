package com.navpilot.presentation.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.Brand

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1900); onDone() }
    val transition = rememberInfiniteTransition(label = "dots")
    val alpha by transition.animateFloat(.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.fillMaxSize().background(Brand), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.scale(.98f)) {
            Box(Modifier.size(80.dp).background(Color.White, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Explore, null, Modifier.size(46.dp), Brand)
            }
            Text("NavPilot", color = Color.White, fontSize = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, modifier = Modifier.padding(top = 20.dp))
            Text("Navigation that never quits", color = Color.White.copy(.7f), fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.size(8.dp).alpha(alpha).background(Color.White.copy(.7f), androidx.compose.foundation.shape.CircleShape)) }
        }
    }
}
