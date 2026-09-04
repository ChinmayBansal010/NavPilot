package com.navpilot.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = .05f), spotColor = Color.Black.copy(alpha = .05f))
            .clip(RoundedCornerShape(18.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand,
                modifier = Modifier.clickable { onAction?.invoke() })
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun IconCircle(icon: ImageVector, tint: Color = Ink2, size: Int = 40, bg: Color = Bg) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint)
    }
}

@Composable
fun Toggle(on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (on) Brand else Line).clickable { onClick() },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.padding(2.dp).size(24.dp).clip(CircleShape).background(Surface).shadow(2.dp, CircleShape))
    }
}

@Composable
fun BottomNav(active: Int, onNavigate: (Int) -> Unit) {
    val items = listOf(
        Icons.Default.Home to "Home",
        Icons.Default.Explore to "Navigate",
        Icons.Default.AccessTime to "Trips",
        Icons.Default.Settings to "Settings"
    )
    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        items.forEachIndexed { index, item ->
            val selected = active == index
            Column(Modifier.weight(1f).clickable { onNavigate(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 9.dp).size(56.dp, 28.dp).clip(RoundedCornerShape(20.dp)).background(if (selected) Brand.copy(alpha = .1f) else Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(item.first, null, Modifier.size(22.dp), if (selected) Brand else Ink3)
                }
                Text(item.second, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Brand else Ink3, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Default.ChevronLeft, null, tint = Ink)
    }
}
