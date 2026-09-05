package com.navpilot.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Line.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            Text(
                text = action,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Brand,
                modifier = Modifier.clickable { onAction?.invoke() }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun IconCircle(icon: ImageVector, tint: Color = Ink2, size: Int = 44, bg: Color = Bg) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size((size * 0.5f).dp),
            tint = tint
        )
    }
}

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    val thumbOffset by animateOffsetAsState(
        targetValue = if (on) Offset(22f, 0f) else Offset(0f, 0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "toggleThumb"
    )
    
    val bgColor by animateColorAsState(
        targetValue = if (on) Brand else Line,
        label = "toggleBg"
    )

    Box(
        modifier = Modifier
            .size(52.dp, 30.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.dp.roundToPx(), 0) }
                .size(22.dp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Surface)
        )
    }
}

@Composable
fun BottomNav(active: Int, onNavigate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        Icons.Default.Home to "Home",
        Icons.Default.Explore to "Navigate",
        Icons.Default.AccessTime to "Trips",
        Icons.Default.Settings to "Settings"
    )
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                spotColor = Color.Black.copy(alpha = 0.15f)
            ),
        color = Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = active == index
                
                val iconColor by animateColorAsState(
                    targetValue = if (selected) Brand else Ink3,
                    label = "navIcon"
                )
                
                val bgColor by animateColorAsState(
                    targetValue = if (selected) Brand.copy(alpha = 0.12f) else Color.Transparent,
                    label = "navBg"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigate(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp, 32.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.first,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = iconColor
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.second,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Brand else Ink3
                    )
                }
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(12.dp)
            .size(44.dp),
        shape = CircleShape,
        color = Surface.copy(alpha = 0.9f),
        shadowElevation = 6.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Back",
                tint = Ink,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
