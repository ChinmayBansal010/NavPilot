package com.navpilot.presentation.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.navpilot.core.ui.theme.*
import com.navpilot.domain.model.Trip
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.IconCircle

@Composable
fun TripsScreen(onTrip: () -> Unit) {
    val trips = listOf(
        Trip("Kempegowda Airport", "Today", "9:14 AM", "38 km", "52 min"),
        Trip("Whitefield Tech Park", "Yesterday", "8:40 AM", "22 km", "48 min"),
        Trip("Nandi Hills", "Aug 28", "6:10 AM", "61 km", "1h 24m"),
        Trip("Phoenix Marketcity", "Aug 26", "5:30 PM", "14 km", "36 min"),
        Trip("Indiranagar", "Aug 24", "7:55 PM", "9 km", "24 min")
    )
    LazyColumn(Modifier.fillMaxSize().background(Bg), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) { Text("Your trips", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink); Text("148 km this week", fontSize = 14.sp, color = Ink2, modifier = Modifier.padding(top = 2.dp)) } }
        items(trips.size) { index ->
            val trip = trips[index]
            Card(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).clickable { onTrip() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.DirectionsCar, Brand, 44, Brand.copy(.1f))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) { Text(trip.destination, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink); Text("${trip.date} · ${trip.time}", fontSize = 13.sp, color = Ink2) }
                    Column(horizontalAlignment = Alignment.End) { Text(trip.distance, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink); Text(trip.duration, fontSize = 12.sp, color = Ink3) }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp).padding(start = 2.dp), Ink3)
                }
            }
        }
    }
}
