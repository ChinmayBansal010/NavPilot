package com.navpilot.data.map

import android.content.Context
import org.osmdroid.views.MapView

interface MapProvider {
    fun createMapView(context: Context): MapView
}

class OsmMapProvider : MapProvider {
    override fun createMapView(context: Context): MapView =
        createOsmMapView(context)
}
