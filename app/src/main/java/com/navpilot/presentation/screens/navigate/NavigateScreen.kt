package com.navpilot.presentation.screens.navigate

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navpilot.core.ui.theme.*
import com.navpilot.core.ui.theme.Surface as AppSurface
import com.navpilot.domain.model.DestinationSearchResult
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.NavigationMode
import com.navpilot.domain.model.NavigationState
import com.navpilot.domain.model.OfflineMapStatus
import com.navpilot.domain.model.RouteDataSource
import com.navpilot.domain.model.SearchResultType
import com.navpilot.domain.model.TurnType
import com.navpilot.presentation.components.Card
import com.navpilot.ui.map.NavPilotMap
import com.navpilot.ui.state.NavigationViewModel
import com.navpilot.ui.state.NavigationViewModelFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

@Composable
fun NavigateScreen() {
    val context = LocalContext.current
    val viewModel: NavigationViewModel = viewModel(
        factory = NavigationViewModelFactory(
            context.applicationContext as Application
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.setLocationPermissionGranted(
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
    }

    LaunchedEffect(hasLocationPermission) {
        viewModel.setLocationPermissionGranted(hasLocationPermission)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        NavPilotMap(
            modifier = Modifier.fillMaxSize(),
            position = state.position,
            headingDegrees = state.headingDegrees,
            isFollowingVehicle = state.isFollowingVehicle,
            routePoints = state.routePoints,
            destinationPosition = state.destinationPosition,
            onUserMapInteraction = { viewModel.onUserMapInteraction() },
            onMapReady = { mapView = it }
        )

        // Gradient at the top for better status bar visibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = state.isNavigating,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                NavigationInstructionCard(state = state)
            }

            AnimatedVisibility(
                visible = !state.isNavigating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                DestinationSearchCard(
                    state = state,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSelectResult = viewModel::selectSearchResult
                )
            }

            if (state.isRouteLoading) {
                RouteLoadingCard()
            }

            state.routeErrorMessage?.let { message ->
                RouteErrorBanner(message)
            }

            if (!state.permissionGranted) {
                PermissionCard(onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                })
            }

            if (state.gnssStatus == GnssAvailability.LOST || state.navigationMode == NavigationMode.DEAD_RECKONING) {
                GnssLossBanner()
            }
        }

        MapControls(
            map = mapView,
            state = state,
            onRecenter = { viewModel.recenterMap() },
            onToggleDiagnostics = { viewModel.toggleDeveloperDiagnostics(!state.showDeveloperDiagnostics) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )

        if (state.showDeveloperDiagnostics) {
            DeveloperDiagnosticsCard(
                state = state,
                onStartDemo = { viewModel.startGnssDeniedDemo() },
                onClose = { viewModel.toggleDeveloperDiagnostics(false) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp, end = 76.dp)
            )
        }

        if (state.isArrived) {
            ArrivalDialog(
                destinationName = state.destinationName ?: "Destination",
                onDismiss = { viewModel.dismissArrival() }
            )
        }

        NavigationBottomPanel(
            state = state,
            onStartNavigation = { name, pos -> viewModel.startNavigation(name, pos) },
            onStopNavigation = { viewModel.stopNavigation() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun NavigationInstructionCard(state: NavigationState) {
    val icon = when (state.currentTurnType) {
        TurnType.START -> Icons.Default.Navigation
        TurnType.STRAIGHT -> Icons.Default.Straight
        TurnType.TURN_LEFT -> Icons.Default.TurnLeft
        TurnType.TURN_RIGHT -> Icons.Default.TurnRight
        TurnType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
        TurnType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
        TurnType.SHARP_LEFT -> Icons.Default.TurnLeft
        TurnType.SHARP_RIGHT -> Icons.Default.TurnRight
        TurnType.U_TURN -> Icons.Default.RoundaboutRight
        TurnType.ROUNDABOUT_ENTER -> Icons.Default.RoundaboutRight
        TurnType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutRight
        TurnType.ROUNDABOUT -> Icons.Default.RoundaboutRight
        TurnType.ARRIVE -> Icons.Default.Place
        TurnType.DESTINATION_REACHED -> Icons.Default.Place
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = state.upcomingTurn,
                    color = Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 22.sp
                )

                Text(
                    text = "In ${state.turnDistanceMeters.toInt()} m · ${state.destinationName ?: "Destination"}",
                    color = Ink2,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${(state.speedMetersPerSecond * 3.6f).toInt().coerceAtLeast(0)}",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "km/h",
                    color = Ink3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DestinationSearchCard(
    state: NavigationState,
    onQueryChange: (String) -> Unit,
    onSelectResult: (DestinationSearchResult) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(22.dp)
                )
                TextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    placeholder = { Text("Where do you want to go?", fontSize = 15.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Ink3,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (state.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    itemsIndexed(state.searchResults) { index, result ->
                        SearchResultRow(
                            result = result,
                            state = state,
                            onSelectResult = onSelectResult
                        )
                        if (index < state.searchResults.lastIndex) {
                            HorizontalDivider(
                                color = Line.copy(alpha = 0.5f),
                                thickness = 0.8.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: DestinationSearchResult,
    state: NavigationState,
    onSelectResult: (DestinationSearchResult) -> Unit
) {
    val icon = when (result.type) {
        SearchResultType.CITY -> Icons.Default.Home
        SearchResultType.PLACE -> Icons.Default.Star
        SearchResultType.ADDRESS -> Icons.Default.Place
        SearchResultType.MAP_REGION -> Icons.Default.Work
        SearchResultType.COUNTRY -> Icons.Default.Place
        SearchResultType.STATE -> Icons.Default.Place
        SearchResultType.DISTRICT -> Icons.Default.Place
        SearchResultType.TOWN -> Icons.Default.Home
        SearchResultType.VILLAGE -> Icons.Default.Home
        SearchResultType.ROAD -> Icons.Default.Straight
    }
    
    val isSelected = state.selectedDestination?.id == result.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isRouteLoading) { onSelectResult(result) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background((if (isSelected) Brand else Bg).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Brand else Ink2,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.title,
                    color = if (isSelected) Brand else Ink,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.isDownloaded) {
                    Surface(
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Success.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Offline",
                            color = Success,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = result.subtitle,
                color = Ink2,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Success,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RouteLoadingCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = Brand
            )
            Text(
                text = "Optimizing offline road route...",
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun RouteErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Danger.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun GnssLossBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Warning.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "Signal lost — using IMU & Dead Reckoning",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = "Grant location access for navigation",
                color = Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("Allow", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MapControls(
    map: MapView?,
    state: NavigationState,
    onRecenter: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MapControlButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Recenter",
            tint = if (state.isFollowingVehicle) Brand else Ink2,
            onClick = {
                onRecenter()
                state.position?.let {
                    map?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                }
            }
        )

        Column(
            modifier = Modifier
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(AppSurface)
        ) {
            IconButton(
                onClick = { map?.controller?.setZoom((map.zoomLevelDouble + 1.0).coerceAtMost(20.0)) },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Ink2)
            }
            HorizontalDivider(color = Line, thickness = 0.8.dp, modifier = Modifier.width(30.dp).align(Alignment.CenterHorizontally))
            IconButton(
                onClick = { map?.controller?.setZoom((map.zoomLevelDouble - 1.0).coerceAtLeast(3.0)) },
                modifier = Modifier.size(46.dp)
            ) {
                Icon(Icons.Default.Remove, null, tint = Ink2)
            }
        }

        MapControlButton(
            icon = Icons.Default.Build,
            contentDescription = "Diagnostics",
            tint = if (state.showDeveloperDiagnostics) Warning else Ink2,
            onClick = onToggleDiagnostics
        )
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = Ink2,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppSurface,
        shadowElevation = 8.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DeveloperDiagnosticsCard(
    state: NavigationState,
    onStartDemo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val diagnostics = state.positioningDiagnostics
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, Modifier.size(18.dp), Brand)
                    Text("Positioning", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Ink3, modifier = Modifier.size(18.dp))
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            DiagnosticLine("GNSS", state.gnssStatus.name)
            DiagnosticLine("DR Engine", if (diagnostics.deadReckoningActive) "Active" else "Standby")
            DiagnosticLine("AI Correction", if (diagnostics.aiCorrectionActive) "Optimizing" else "Standby")
            DiagnosticLine("Uncertainty", "${String.format(Locale.ROOT, "%.1f", diagnostics.uncertaintyMeters)} m")
            DiagnosticLine("Since GNSS", "${diagnostics.secondsSinceLastGnss.toInt()}s · ${diagnostics.distanceSinceLastGnssMeters.toInt()}m")
            DiagnosticLine("Confidence", "${(diagnostics.deadReckoningConfidence * 100).toInt()}%")
            DiagnosticLine("Error (Est.)", diagnostics.positionErrorMeters?.let { "${String.format(Locale.ROOT, "%.1f", it)} m" } ?: "-")
            
            Button(
                onClick = onStartDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isDemoRunning) Danger else Brand
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (state.isDemoRunning) "Stop GNSS Loss Demo" else "Simulate GNSS Loss",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NavigationBottomPanel(
    state: NavigationState,
    onStartNavigation: (String, GeoPosition) -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = AppSurface,
        shadowElevation = 24.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            if (state.isNavigating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.destinationName ?: "Destination",
                            color = Ink,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val distKm = state.distanceRemainingMeters / 1000f
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                text = "${state.etaMinutes} min",
                                color = Success,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = " · ${String.format(Locale.ROOT, "%.1f km", distKm)}",
                                color = Ink2,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = " · ${state.currentRoadName ?: "Route"}",
                                color = Ink3,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        
                        if (state.isRerouting) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                CircularProgressIndicator(Modifier.size(12.dp), Brand, 2.dp)
                                Text(
                                    text = "Recalculating better route...",
                                    color = Brand,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onStopNavigation,
                        color = Danger.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Stop",
                            color = Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isRouteLoading) "Preparing Route" else if (state.selectedDestination != null) "Start Driving" else "Ready to Navigate",
                            color = Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (state.selectedDestination != null) "To ${state.selectedDestination.title}" else "Select a place to begin",
                            color = Ink2,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val selected = state.selectedDestination
                    Button(
                        enabled = !state.isRouteLoading,
                        onClick = {
                            if (selected != null) {
                                onStartNavigation(selected.title, selected.position)
                            } else {
                                onStartNavigation("India Gate", GeoPosition(28.6129, 77.2295))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (state.isRouteLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Start",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalDialog(destinationName: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        color = AppSurface,
        shadowElevation = 32.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "You've Arrived!",
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "You reached $destinationName.",
                color = Ink2,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
