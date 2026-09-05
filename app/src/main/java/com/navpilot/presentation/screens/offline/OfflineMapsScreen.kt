package com.navpilot.presentation.screens.offline

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navpilot.core.ui.theme.*
import com.navpilot.domain.model.OfflineMapRegion
import com.navpilot.domain.model.OfflineMapStatus
import com.navpilot.domain.model.SearchResultType
import com.navpilot.presentation.components.BackButton
import com.navpilot.presentation.components.Card
import com.navpilot.presentation.components.SectionTitle
import com.navpilot.ui.state.OfflineMapViewModel
import com.navpilot.ui.state.OfflineMapViewModelFactory

@Composable
fun OfflineMapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: OfflineMapViewModel = viewModel(
        factory = OfflineMapViewModelFactory(
            context.applicationContext as Application
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Text(
                text = "Offline maps",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            item {
                Card(Modifier.padding(bottom = 28.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(
                                text = "Local Storage",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            val downloadedCount = state.availableRegions.count { it.status == OfflineMapStatus.DOWNLOADED }
                            Text(
                                text = "$downloadedCount regions saved",
                                fontSize = 12.sp,
                                color = Ink2
                            )
                        }
                        Text(
                            text = "${state.availableRegions.count { it.status == OfflineMapStatus.DOWNLOADED } * 125} MB",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Brand
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (state.availableRegions.isEmpty()) 0f 
                            else (state.availableRegions.count { it.status == OfflineMapStatus.DOWNLOADED }.toFloat() / state.availableRegions.size).coerceAtMost(1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Brand,
                        trackColor = Line.copy(alpha = 0.4f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    placeholder = { Text("Search states or UTs", fontSize = 15.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Brand) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Brand,
                        unfocusedBorderColor = Line,
                        focusedContainerColor = Surface,
                        unfocusedContainerColor = Surface
                    )
                )
            }

            val country = state.availableRegions.filter { it.type == SearchResultType.COUNTRY }
            if (country.isNotEmpty()) {
                item { SectionTitle("Country") }
                items(country) { region ->
                    RegionItem(
                        region = region,
                        onDownload = { viewModel.downloadRegion(region) },
                        onDelete = { viewModel.deleteRegion(region.id) }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            val states = state.availableRegions.filter { it.type == SearchResultType.STATE }
            if (states.isNotEmpty()) {
                item { SectionTitle("States") }
                items(states) { region ->
                    RegionItem(
                        region = region,
                        onDownload = { viewModel.downloadRegion(region) },
                        onDelete = { viewModel.deleteRegion(region.id) }
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }

            val uts = state.availableRegions.filter { it.type == SearchResultType.MAP_REGION }
            if (uts.isNotEmpty()) {
                item { SectionTitle("Union Territories") }
                items(uts) { region ->
                    RegionItem(
                        region = region,
                        onDownload = { viewModel.downloadRegion(region) },
                        onDelete = { viewModel.deleteRegion(region.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionItem(
    region: OfflineMapRegion,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Bg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Ink2
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = region.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text(
                        text = region.description,
                        fontSize = 12.sp,
                        color = Ink2
                    )
                    if (region.status == OfflineMapStatus.DOWNLOADED) {
                        Text(
                            text = "Size: 125 MB · Saved",
                            fontSize = 11.sp,
                            color = Success,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                when (region.status) {
                    OfflineMapStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Brand
                        )
                    }
                    OfflineMapStatus.DOWNLOADED -> {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Delete",
                                tint = Danger.copy(alpha = 0.8f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    OfflineMapStatus.AVAILABLE -> {
                        Surface(
                            onClick = onDownload,
                            shape = RoundedCornerShape(12.dp),
                            color = Brand.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Brand
                                )
                                Text(
                                    text = "Get",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Brand,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (region.status == OfflineMapStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { region.downloadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 60.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Brand,
                    trackColor = Line.copy(alpha = 0.3f)
                )
            }
        }
    }
}
