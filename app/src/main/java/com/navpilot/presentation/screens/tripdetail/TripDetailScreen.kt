package com.navpilot.presentation.screens.tripdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.navpilot.core.ui.theme.*
import com.navpilot.presentation.components.BackButton
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.OfflineMap
import com.navpilot.presentation.components.SectionTitle

@Composable
fun TripDetailScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            OfflineMap()
            BackButton(onBack)
        }
        LazyColumn(Modifier.fillMaxSize().offset(y = (-22).dp).background(Bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)) {
            item { Text("Kempegowda Airport", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text("Today · 9:14 AM – 10:06 AM", fontSize = 14.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)) }
            item {
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("38 km" to "Distance", "52 min" to "Duration", "44 km/h" to "Avg speed").forEach { stat -> Card(Modifier.weight(1f)) { Text(stat.first, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text(stat.second, fontSize = 12.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)) } }
                }
            }
            item {
                Card(Modifier.padding(top = 14.dp)) { SectionTitle("Route quality"); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(Success.copy(.12f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Shield, null, Modifier.size(21.dp), Success) }; Column(Modifier.padding(start = 12.dp)) { Text("Smooth navigation", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text("Signal briefly dropped in the tunnel — position stayed accurate.", fontSize = 13.sp, color = Ink2) } } }
            }
            item {
                Card(Modifier.padding(top = 14.dp)) {
                    listOf("Start" to "Indiranagar, 12th Main", "Tunnel section" to "1.2 km on motion tracking", "Arrived" to "Terminal 2, Departures").forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (i == 1) Icons.Default.SignalCellularAlt else Icons.Default.LocationOn, null, Modifier.size(20.dp), Ink3); Column(Modifier.padding(start = 13.dp)) { Text(row.first, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text(row.second, fontSize = 13.sp, color = Ink2) } }
                        if (i < 2) androidx.compose.material3.HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }
}
