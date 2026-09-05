package com.navpilot.presentation.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    val slides = listOf(
        OnboardingSlide(
            title = "Get there with confidence",
            description = "Clear, turn-by-turn directions with road-accurate mapping and arrival times.",
            illustration = { NavigationArt() }
        ),
        OnboardingSlide(
            title = "Keeps going without signal",
            description = "Drive through tunnels and valleys. NavPilot tracks you accurately even when GNSS drops.",
            illustration = { OfflineArt() }
        ),
        OnboardingSlide(
            title = "Private by design",
            description = "Your location stays on your device. Download maps to navigate fully offline, anytime.",
            illustration = { PrivacyArt() }
        )
    )
    val last = index == slides.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (!last) {
                TextButton(onClick = onDone) {
                    Text(
                        text = "Skip",
                        color = Ink3,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }.using(SizeTransform(clip = false))
                },
                label = "slideContent"
            ) { slideIndex ->
                val slide = slides[slideIndex]
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Bg),
                        contentAlignment = Alignment.Center
                    ) {
                        slide.illustration()
                    }

                    Spacer(Modifier.height(48.dp))

                    Text(
                        text = slide.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                        lineHeight = 34.sp
                    )

                    Text(
                        text = slide.description,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        color = Ink2,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 40.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                slides.indices.forEach { i ->
                    val width by animateDpAsState(
                        targetValue = if (i == index) 32.dp else 8.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "indicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == index) Brand else Line)
                    )
                }
            }

            Button(
                onClick = {
                    if (last) onDone() else index++
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                Text(
                    text = if (last) "Get Started" else "Continue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        }
    }
}

@Composable
private fun NavigationArt() {
    val infiniteTransition = rememberInfiniteTransition(label = "navArt")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation),
            tint = Brand
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Brand.copy(alpha = 0.1f)
        ) {
            Text(
                "INTELLIGENT PATHS",
                color = Brand,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun OfflineArt() {
    val infiniteTransition = rememberInfiniteTransition(label = "offlineArt")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Success.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(28.dp).alpha(alpha)
                )
            }
            Text("GNSS", fontWeight = FontWeight.Bold, color = Success, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Icon(
            imageVector = Icons.Default.Navigation,
            contentDescription = null,
            tint = Ink3,
            modifier = Modifier.size(20.dp).rotate(90f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text("INS", fontWeight = FontWeight.Bold, color = Brand, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PrivacyArt() {
    val infiniteTransition = rememberInfiniteTransition(label = "privacyArt")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = Success,
            modifier = Modifier.size(56.dp)
        )
    }
}

data class OnboardingSlide(
    val title: String,
    val description: String,
    val illustration: @Composable () -> Unit
)
