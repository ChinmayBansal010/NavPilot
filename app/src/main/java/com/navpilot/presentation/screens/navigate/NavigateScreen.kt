package com.navpilot.presentation.screens.navigate

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navpilot.core.ui.theme.Brand
import com.navpilot.core.ui.theme.Danger
import com.navpilot.core.ui.theme.Ink
import com.navpilot.core.ui.theme.Ink2
import com.navpilot.core.ui.theme.Ink3
import com.navpilot.core.ui.theme.Success
import com.navpilot.core.ui.theme.Warning
import com.navpilot.core.ui.theme.Surface as AppSurface
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.NavigationMode
import com.navpilot.domain.model.NavigationState
import com.navpilot.domain.model.TurnType
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.isNavigating) {
                NavigationInstructionCard(state = state)
            } else {
                DestinationSearchCard(
                    onSelectDestination = { name, pos ->
                        viewModel.startNavigation(name, pos)
                    }
                )
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
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )

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
            modifier = Modifier.align(Alignment.BottomCenter)
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Brand.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Brand,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = state.upcomingTurn,
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "In ${state.turnDistanceMeters.toInt()} m · To ${state.destinationName ?: "Destination"}",
                    color = Ink2,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.speedKmh()} km/h",
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.etaMinutes} min",
                    color = Success,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DestinationSearchCard(onSelectDestination: (String, GeoPosition) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Ink3, modifier = Modifier.size(20.dp))
                Text(
                    text = "Where would you like to go? (Offline)",
                    color = Ink2,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val destinations = listOf(
                Triple("India Gate", GeoPosition(28.6129, 77.2295), Icons.Default.Star),
                Triple("Noida Sector 18", GeoPosition(28.5708, 77.3261), Icons.Default.Work),
                Triple("Saket", GeoPosition(28.5245, 77.2066), Icons.Default.Home)
            )

            destinations.forEachIndexed { index, (name, pos, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDestination(name, pos) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brand.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = Brand, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = name,
                        color = Ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                    Text(
                        text = "Start",
                        color = Brand,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (index < destinations.lastIndex) {
                    HorizontalDivider(color = Color(0xFFF1F3F5), thickness = 0.8.dp)
                }
            }
        }
    }
}

@Composable
private fun GnssLossBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Warning.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = "GPS signal weak/lost — using offline dead reckoning & IMU fusion",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(20.dp)
            )

            Text(
                text = "Location permission is required for live navigation",
                color = Ink2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 9.dp, end = 8.dp)
            )

            OutlinedButton(onClick = onRequestPermission) {
                Text("Allow", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MapControls(
    map: MapView?,
    state: NavigationState,
    onRecenter: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapControlButton(
            icon = Icons.Default.MyLocation,
            contentDescription = "Recenter vehicle",
            tint = if (state.isFollowingVehicle) Brand else Ink2,
            onClick = {
                onRecenter()
                state.position?.let {
                    map?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                }
            }
        )

        MapControlButton(
            icon = Icons.Default.ZoomIn,
            contentDescription = "Zoom in",
            onClick = {
                map?.controller?.setZoom((map.zoomLevelDouble + 1.0).coerceAtMost(20.0))
            }
        )

        MapControlButton(
            icon = Icons.Default.Remove,
            contentDescription = "Zoom out",
            onClick = {
                map?.controller?.setZoom((map.zoomLevelDouble - 1.0).coerceAtLeast(3.0))
            }
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
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = AppSurface,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val distKm = state.distanceRemainingMeters / 1000f
                        Text(
                            text = "${state.etaMinutes} min · ${String.format(Locale.ROOT, "%.1f km", distKm)} · ${state.currentRoadName ?: "Route"}",
                            color = Ink2,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (state.isRerouting) {
                            Text(
                                text = "Finding a better local route...",
                                color = Warning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onStopNavigation,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline Navigation Ready",
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Select a destination above or tap below",
                            color = Ink2,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Button(
                        onClick = {
                            onStartNavigation("India Gate", GeoPosition(28.6129, 77.2295))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Drive Home", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
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
        shape = RoundedCornerShape(20.dp),
        color = AppSurface,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Success.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "You've Arrived!",
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = "You have reached $destinationName.",
                color = Ink2,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun NavigationState.speedKmh(): Int =
    (speedMetersPerSecond * 3.6f).toInt().coerceAtLeast(0)
