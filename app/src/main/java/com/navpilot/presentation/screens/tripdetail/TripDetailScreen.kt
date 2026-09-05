package com.navpilot.presentation.screens.tripdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navpilot.core.ui.theme.*
import com.navpilot.presentation.components.BackButton
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.OfflineMap
import com.navpilot.presentation.components.SectionTitle

@Composable
fun TripDetailScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            OfflineMap()
            BackButton(onBack)
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-28).dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Surface),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)
        ) {
            item {
                Text(
                    text = "Kempegowda Airport",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink
                )
                Text(
                    text = "Today · 9:14 AM – 10:06 AM",
                    fontSize = 15.sp,
                    color = Ink2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            item {
                Row(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TripStat(Modifier.weight(1f), "38 km", "Distance", Brand)
                    TripStat(Modifier.weight(1f), "52 min", "Duration", Success)
                    TripStat(Modifier.weight(1f), "44 km/h", "Avg Speed", Warning)
                }
            }
            
            item {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Navigation insights")
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Success.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Shield, null, Modifier.size(24.dp), Success)
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "High reliability",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                text = "Signal dropped in Hebbal tunnel, but INS tracking kept position accurate.",
                                fontSize = 13.sp,
                                color = Ink2,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Timeline")
                Card {
                    TimelineItem("Start", "Indiranagar, 12th Main", true, false)
                    TimelineItem("Tracking", "Tunnel section (1.2 km on IMU)", false, false)
                    TimelineItem("Arrived", "Terminal 2, Departures", false, true)
                }
            }
        }
    }
}

@Composable
private fun TripStat(modifier: Modifier, value: String, label: String, color: Color) {
    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = Bg.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun TimelineItem(title: String, subtitle: String, isStart: Boolean, isEnd: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isStart) Icons.Default.Circle else if (isEnd) Icons.Default.LocationOn else Icons.Default.Adjust,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isStart || isEnd) Brand else Ink3
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(subtitle, fontSize = 13.sp, color = Ink2)
        }
    }
    if (!isEnd) {
        androidx.compose.material3.HorizontalDivider(color = Line.copy(alpha = 0.5f), thickness = 0.8.dp)
    }
}
