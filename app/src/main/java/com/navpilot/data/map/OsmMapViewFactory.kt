package com.navpilot.data.map

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

fun createOsmMapView(context: Context): MapView {
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences(
            "osmdroid",
            Context.MODE_PRIVATE
        )
    )
    Configuration.getInstance().userAgentValue = context.packageName

    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(20.0)
        isTilesScaledToDpi = true
        controller.setZoom(15.0)
        controller.setCenter(GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE))
    }
}

const val DEFAULT_LATITUDE = 28.6315
const val DEFAULT_LONGITUDE = 77.2167
