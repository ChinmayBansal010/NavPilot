package com.navpilot.ui.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.navpilot.data.map.MapProvider
import com.navpilot.data.map.OsmMapProvider
import com.navpilot.domain.model.GeoPosition
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@SuppressLint("ClickableViewAccessibility")
@Composable
fun NavPilotMap(
    modifier: Modifier = Modifier,
    position: GeoPosition?,
    headingDegrees: Float?,
    isFollowingVehicle: Boolean,
    routePoints: List<GeoPosition> = emptyList(),
    destinationPosition: GeoPosition? = null,
    mapProvider: MapProvider? = null,
    onUserMapInteraction: () -> Unit = {},
    onMapReady: (MapView) -> Unit = {}
) {
    val defaultProvider = remember { OsmMapProvider() }
    val provider = mapProvider ?: defaultProvider

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                provider.createMapView(ctx).apply {
                    setMultiTouchControls(true)

                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                            onUserMapInteraction()
                        }
                        false
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            onUserMapInteraction()
                            return false
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onUserMapInteraction()
                            return false
                        }
                    })

                    onMapReady(this)
                }
            },
            update = { mapView ->
                runCatching {
                    // Vehicle Marker
                    var vehicleMarker = mapView.overlays.filterIsInstance<Marker>()
                        .firstOrNull { it.title == "Current vehicle position" }
                    if (vehicleMarker == null) {
                        vehicleMarker = Marker(mapView).apply {
                            title = "Current vehicle position"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            mapView.overlays.add(this)
                        }
                    }

                    if (position != null) {
                        val point = GeoPoint(position.latitude, position.longitude)
                        vehicleMarker.position = point
                        vehicleMarker.setRotation(headingDegrees ?: 0f)
                        vehicleMarker.setEnabled(true)
                        if (isFollowingVehicle) {
                            mapView.controller.animateTo(point)
                        }
                    } else {
                        vehicleMarker.setEnabled(false)
                    }

                    // Destination Marker
                    var destMarker = mapView.overlays.filterIsInstance<Marker>()
                        .firstOrNull { it.title == "Destination" }
                    if (destMarker == null) {
                        destMarker = Marker(mapView).apply {
                            title = "Destination"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            mapView.overlays.add(this)
                        }
                    }

                    if (destinationPosition != null) {
                        destMarker.position = GeoPoint(destinationPosition.latitude, destinationPosition.longitude)
                        destMarker.setEnabled(true)
                    } else {
                        destMarker.setEnabled(false)
                    }

                    // Route Polyline
                    var polyline = mapView.overlays.filterIsInstance<Polyline>().firstOrNull()
                    if (polyline == null) {
                        polyline = Polyline(mapView).apply {
                            outlinePaint.color = android.graphics.Color.parseColor("#3B82F6")
                            outlinePaint.strokeWidth = 14f
                            mapView.overlays.add(this)
                        }
                    }

                    if (routePoints.isNotEmpty()) {
                        polyline.setPoints(routePoints.map { GeoPoint(it.latitude, it.longitude) })
                        polyline.setEnabled(true)
                    } else {
                        polyline.setEnabled(false)
                    }

                    mapView.invalidate()
                }
            },
            onRelease = { mapView ->
                runCatching {
                    mapView.onDetach()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
