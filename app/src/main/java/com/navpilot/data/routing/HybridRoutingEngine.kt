package com.navpilot.data.routing

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.Route
import com.navpilot.domain.routing.RoutingEngine

class HybridRoutingEngine(
    private val onlineRoutingEngine: RoutingEngine = OnlineOsmRoutingEngine(),
    private val offlineRoutingEngine: RoutingEngine = OfflineRoutingEngine(),
    private val allowBundledOfflineFallback: Boolean = false
) : RoutingEngine {
    override suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route {
        val onlineResult = runCatching {
            onlineRoutingEngine.calculateRoute(origin, destination, destinationName)
        }

        if (onlineResult.isSuccess) {
            return onlineResult.getOrThrow()
        }

        if (allowBundledOfflineFallback) {
            return offlineRoutingEngine.calculateRoute(origin, destination, destinationName)
        }

        throw onlineResult.exceptionOrNull()
            ?: IllegalStateException("Unable to calculate an OSM road route")
    }
}
