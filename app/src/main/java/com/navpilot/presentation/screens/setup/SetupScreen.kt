package com.navpilot.presentation.screens.setup

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        "Calibrating IMU sensors",
        "Loading AI navigation models",
        "Warming up map matcher"
    )
    
    LaunchedEffect(step) {
        if (step < steps.size) {
            delay(1200)
            step++
        } else {
            delay(1000)
            onDone()
        }
    }
    
    val isDone = step >= steps.size

    val infiniteTransition = rememberInfiniteTransition(label = "setup")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(
            targetState = isDone,
            transitionSpec = {
                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
            },
            label = "iconTransition"
        ) { done ->
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(if (done) Success.copy(alpha = 0.1f) else Brand.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (done) Icons.Default.Check else Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .then(if (done) Modifier else Modifier.rotate(rotation)),
                    tint = if (done) Success else Brand
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = if (isDone) "You're all set" else "Optimizing NavPilot",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink
        )

        Text(
            text = if (isDone) "The road ahead is clear." else "Configuring your offline experience.",
            color = Ink2,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            steps.forEachIndexed { i, label ->
                val complete = step > i
                val active = step == i
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.alpha(if (active || complete) 1f else 0.4f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (complete) Success.copy(alpha = 0.12f)
                                else if (active) Brand.copy(alpha = 0.12f)
                                else Bg
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (complete) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                Modifier.size(18.dp),
                                Success
                            )
                        } else {
                            if (active) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Brand
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Ink3.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active || complete) Ink else Ink3
                    )
                }
            }
        }
    }
}
