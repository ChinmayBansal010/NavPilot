package com.navpilot.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.navpilot.domain.model.SavedPlace
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.IconCircle
import com.navpilot.presentation.components.SectionTitle

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val places = listOf(
        SavedPlace("Home", "Sector 15, Faridabad", "18 min"),
        SavedPlace("Work", "DLF Cyber City, Gurugram", "42 min"),
        SavedPlace("Gym", "Anytime Fitness, NIT", "12 min")
    )
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Good afternoon,",
                        fontSize = 15.sp,
                        color = Ink2,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Chinmay",
                        fontSize = 28.sp,
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }
                Surface(
                    onClick = { /* Profile */ },
                    shape = RoundedCornerShape(16.dp),
                    color = Brand.copy(alpha = 0.1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp),
                        tint = Brand
                    )
                }
            }
        }
        
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigate("navigate") },
                color = Surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Brand
                    )
                    Text(
                        text = "Where to?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink3,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }
        }
        
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp)) {
                SectionTitle("Saved places", "View all")
                Card {
                    places.forEachIndexed { index, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate("navigate") }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconCircle(
                                icon = when (p.label) {
                                    "Home" -> Icons.Default.Home
                                    "Work" -> Icons.Default.Work
                                    else -> Icons.Default.Star
                                },
                                tint = if (p.label == "Home") Brand else if (p.label == "Work") Warning else Success,
                                size = 42,
                                bg = (if (p.label == "Home") Brand else if (p.label == "Work") Warning else Success).copy(alpha = 0.1f)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                Text(
                                    text = p.label,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Ink
                                )
                                Text(
                                    text = p.detail,
                                    fontSize = 13.sp,
                                    color = Ink2
                                )
                            }
                            Text(
                                text = p.time,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Brand
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp),
                                tint = Ink3
                            )
                        }
                        if (index != places.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = Line.copy(alpha = 0.5f),
                                thickness = 0.8.dp
                            )
                        }
                    }
                }
            }
        }
        
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp)) {
                SectionTitle("This week")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(Modifier.weight(1f), "148", "km driven", Brand)
                    StatCard(Modifier.weight(1f), "9", "trips", Success)
                    StatCard(Modifier.weight(1f), "3h 20m", "time", Warning)
                }
            }
        }
        
        item {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.clickable { onNavigate("navigate") }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Default.Explore, Brand, 48, Brand.copy(0.1f))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp)
                        ) {
                            Text(
                                text = "Navigation Engine",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Ink
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Success)
                                )
                                Text(
                                    text = "Positioning optimized",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Success,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), Ink3)
                    }
                    
                    Row(
                        modifier = Modifier.padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EngineTag("IMU", true)
                        EngineTag("AI", true)
                        EngineTag("GPS", true)
                        EngineTag("Map", true)
                    }
                }
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigate("offline") },
                    color = Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconCircle(Icons.Default.Download, Ink, 40, Bg)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                        ) {
                            Text(
                                text = "Offline maps",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Text(
                                text = "Haryana · 125 MB downloaded",
                                fontSize = 12.sp,
                                color = Ink2
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), Ink3)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, color: Color) {
    Card(modifier = modifier) {
        Column {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun EngineTag(label: String, active: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = (if (active) Success else Line).copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (active) Success else Ink3)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Success else Ink3,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    }
}
