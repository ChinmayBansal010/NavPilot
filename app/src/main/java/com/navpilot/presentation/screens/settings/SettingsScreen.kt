package com.navpilot.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize().background(Bg)) {
        Text("Settings", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 18.dp))
        Card(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { IconCircle(Icons.Default.Person, Brand, 56, Brand.copy(.1f)); Column(Modifier.weight(1f).padding(start = 14.dp)) { Text("Aarav Mehta", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink); Text("aarav.mehta@email.com", fontSize = 13.sp, color = Ink2) }; Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), Ink3) }
        }
        SettingsSection("Navigation") {
            SettingRow(Icons.Default.Notifications, "Voice guidance", right = { Toggle(voice) { voice = !voice } })
            SettingRow(Icons.Default.SignalCellularAlt, "Weak-signal alerts", right = { Toggle(alerts) { alerts = !alerts } })
            SettingRow(Icons.Default.Straighten, "Units", right = {
                Row(Modifier.background(Bg, RoundedCornerShape(50))) {
                    Text("km", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (metric) Ink else Ink3, modifier = Modifier.background(if (metric) Surface else Bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp).clickable { metric = true })
                    Text("mi", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (!metric) Ink else Ink3, modifier = Modifier.background(if (!metric) Surface else Bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp).clickable { metric = false })
                }
            })
            SettingRow(Icons.Default.Download, "Map source", "Offline · 280 MB cached", onClick = onOffline, chevron = true)
        }
        SettingsSection("General") {
            SettingRow(Icons.Default.Shield, "Privacy", "Location kept on device", chevron = true)
            SettingRow(Icons.Default.Info, "About NavPilot", "v1.0", chevron = true)
        }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 10.dp)); Card(content = content) }
}

@Composable private fun SettingRow(icon: ImageVector, label: String, detail: String? = null, right: (@Composable () -> Unit)? = null, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, Ink2, 36)
        Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); if (detail != null) Text(detail, fontSize = 13.sp, color = Ink2) }
        right?.invoke()
        if (chevron) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), Ink3)
    }
}
