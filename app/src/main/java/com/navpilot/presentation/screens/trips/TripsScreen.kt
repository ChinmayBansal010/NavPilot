package com.navpilot.presentation.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text(
                    text = "Your trips",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink
                )
                Text(
                    text = "148 km driven this week",
                    fontSize = 15.sp,
                    color = Ink2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        
        items(trips.size) { index ->
            val trip = trips[index]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTrip() },
                color = Surface,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Line.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconCircle(
                        icon = Icons.Default.Route,
                        tint = Brand,
                        size = 46,
                        bg = Brand.copy(alpha = 0.08f)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = trip.destination,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Text(
                            text = "${trip.date} · ${trip.time}",
                            fontSize = 13.sp,
                            color = Ink2
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = trip.distance,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Ink
                        )
                        Text(
                            text = trip.duration,
                            fontSize = 12.sp,
                            color = Ink3,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(18.dp),
                        tint = Ink3
                    )
                }
            }
        }
    }
}
