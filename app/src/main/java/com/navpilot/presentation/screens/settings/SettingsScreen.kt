package com.navpilot.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.IconCircle
import com.navpilot.presentation.components.Toggle

@Composable
fun SettingsScreen(onOffline: () -> Unit) {
    var voice by remember { mutableStateOf(true) }
    var alerts by remember { mutableStateOf(true) }
    var metric by remember { mutableStateOf(true) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 20.dp)
            )
        }
        
        item {
            Card(Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.Person, Brand, 56, Brand.copy(0.1f))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = "Chinmay Bansal",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            text = "chinmay8521@email.com",
                            fontSize = 13.sp,
                            color = Ink2
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), Ink3)
                }
            }
        }
        
        item {
            SettingsSection("Navigation") {
                SettingRow(
                    icon = Icons.Default.Notifications,
                    label = "Voice guidance",
                    right = { Toggle(voice) { voice = !voice } }
                )
                SettingRow(
                    icon = Icons.Default.SignalCellularAlt,
                    label = "Weak-signal alerts",
                    right = { Toggle(alerts) { alerts = !alerts } }
                )
                SettingRow(
                    icon = Icons.Default.Straighten,
                    label = "Units",
                    right = {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Bg)
                                .padding(2.dp)
                        ) {
                            UnitToggle("km", metric) { metric = true }
                            UnitToggle("mi", !metric) { metric = false }
                        }
                    }
                )
                SettingRow(
                    icon = Icons.Default.Download,
                    label = "Offline maps",
                    detail = "Haryana · 125 MB cached",
                    onClick = onOffline,
                    chevron = true
                )
            }
        }
        
        item {
            SettingsSection("General") {
                SettingRow(
                    icon = Icons.Default.Shield,
                    label = "Privacy",
                    detail = "Location kept on device",
                    chevron = true
                )
                SettingRow(
                    icon = Icons.Default.Info,
                    label = "About NavPilot",
                    detail = "v1.0.2",
                    chevron = true
                )
            }
        }
    }
}

@Composable
private fun UnitToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        color = if (active) Surface else Color.Transparent
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Ink else Ink3,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Brand,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        Card(content = content)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    right: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, Ink2, 38)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
            if (detail != null) {
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    color = Ink2
                )
            }
        }
        right?.invoke()
        if (chevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Ink3
            )
        }
    }
}
