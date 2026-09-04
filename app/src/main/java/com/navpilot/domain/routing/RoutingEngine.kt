package com.navpilot.domain.routing

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.Route

interface RoutingEngine {
    suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route
}
