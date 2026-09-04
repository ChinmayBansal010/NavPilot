package com.navpilot.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*
import com.navpilot.domain.model.SavedPlace
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.IconCircle
import com.navpilot.presentation.components.SectionTitle

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val places = listOf(
        SavedPlace("Home", "Indiranagar", "18 min"),
        SavedPlace("Work", "Whitefield Tech Park", "34 min"),
        SavedPlace("Gym", "Cult.fit, Koramangala", "12 min")
    )
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Good afternoon,", fontSize = 14.sp, color = Ink2, fontWeight = FontWeight.Medium)
                    Text("Aarav", fontSize = 25.sp, color = Ink, fontWeight = FontWeight.ExtraBold)
                }
                IconCircle(Icons.Default.Person, Brand, 44, Brand.copy(.1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).background(Surface, RoundedCornerShape(16.dp)).clickable { onNavigate("navigate") }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(21.dp), Ink3)
                Text("Where to?", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink3, modifier = Modifier.padding(start = 12.dp))
            }
        }
        item { Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) { SectionTitle("Saved places", "Edit") } }
        item {
            Card(Modifier.padding(horizontal = 20.dp)) {
                places.forEachIndexed { index, p ->
                    Row(Modifier.fillMaxWidth().clickable { onNavigate("navigate") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(when (p.label) { "Home" -> Icons.Default.Home; "Work" -> Icons.Default.Work; else -> Icons.Default.Star }, Ink2, 40)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(p.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(p.detail, fontSize = 13.sp, color = Ink2)
                        }
                        Text(p.time, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink3)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), Ink3)
                    }
                    if (index != places.lastIndex) androidx.compose.material3.HorizontalDivider(color = Line)
                }
            }
        }
        item { Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) { SectionTitle("This week") } }
        item {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("148" to "km driven", "9" to "trips", "3h 20m" to "on the road").forEach { stat ->
                    Card(Modifier.weight(1f)) { Text(stat.first, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text(stat.second, fontSize = 12.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)) }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(Modifier.clickable { onNavigate("navigate") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Default.Explore, Brand, 44, Brand.copy(.1f))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("IDR Engine", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Success, androidx.compose.foundation.shape.CircleShape)); Text("All systems ready", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Success, modifier = Modifier.padding(start = 6.dp)) }
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), Ink3)
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("IMU Calibration", "AI Speed Filter", "Kalman Fusion", "Map Matching").forEach { label ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Check, null, Modifier.size(14.dp), Success); Text(label, fontSize = 11.sp, color = Ink2, modifier = Modifier.padding(start = 5.dp)) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).clickable { onNavigate("offline") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.Download, Ink2, 36)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Offline maps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text("280 MB saved · 2 regions", fontSize = 12.sp, color = Ink2) }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), Ink3)
                }
            }
        }
    }
}
