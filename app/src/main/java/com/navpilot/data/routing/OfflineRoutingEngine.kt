package com.navpilot.data.routing

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.Route
import com.navpilot.domain.model.RouteSegment
import com.navpilot.domain.model.TurnType
import com.navpilot.domain.routing.RoutingEngine
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineRoutingEngine : RoutingEngine {

    override suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route {
        val segments = buildOfflineSegments(origin, destination, destinationName)
        val allCoordinates = segments.flatMap { it.coordinates }.distinct()
        val totalDist = segments.sumOf { it.distanceMeters }
        val travelTimeSec = (totalDist / 12.5).toLong().coerceAtLeast(60L) // ~45 km/h average speed

        return Route(
            destinationName = destinationName,
            destinationPosition = destination,
            totalDistanceMeters = totalDist,
            estimatedTravelTimeSeconds = travelTimeSec,
            segments = segments,
            orderedCoordinates = allCoordinates
        )
    }

    private fun buildOfflineSegments(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): List<RouteSegment> {
        val dLat = destination.latitude - origin.latitude
        val dLon = destination.longitude - origin.longitude

        val p1 = GeoPosition(origin.latitude + dLat * 0.25, origin.longitude)
        val p2 = GeoPosition(origin.latitude + dLat * 0.25, origin.longitude + dLon * 0.6)
        val p3 = GeoPosition(origin.latitude + dLat * 0.8, origin.longitude + dLon * 0.6)

        val seg1Coords = interpolatePoints(origin, p1, steps = 5)
        val seg2Coords = interpolatePoints(p1, p2, steps = 6)
        val seg3Coords = interpolatePoints(p2, p3, steps = 6)
        val seg4Coords = interpolatePoints(p3, destination, steps = 4)

        val seg1Dist = calculateDistanceMeters(origin, p1)
        val seg2Dist = calculateDistanceMeters(p1, p2)
        val seg3Dist = calculateDistanceMeters(p2, p3)
        val seg4Dist = calculateDistanceMeters(p3, destination)

        return when {
            destinationName.contains("Indiranagar", ignoreCase = true) -> listOf(
                RouteSegment(
                    streetName = "Old Airport Road",
                    instruction = "Head east on Old Airport Road",
                    turnType = TurnType.START,
                    distanceMeters = seg1Dist,
                    coordinates = seg1Coords
                ),
                RouteSegment(
                    streetName = "100 Feet Road",
                    instruction = "In ${seg1Dist.toInt()}m, turn left onto 100 Feet Road",
                    turnType = TurnType.TURN_LEFT,
                    distanceMeters = seg2Dist,
                    coordinates = seg2Coords
                ),
                RouteSegment(
                    streetName = "100 Feet Roundabout",
                    instruction = "At roundabout, take 2nd exit onto 12th Main Road",
                    turnType = TurnType.ROUNDABOUT,
                    distanceMeters = seg3Dist,
                    coordinates = seg3Coords
                ),
                RouteSegment(
                    streetName = "12th Main Road",
                    instruction = "Turn right onto 12th Main Road to destination",
                    turnType = TurnType.TURN_RIGHT,
                    distanceMeters = seg4Dist,
                    coordinates = seg4Coords
                )
            )

            destinationName.contains("Whitefield", ignoreCase = true) -> listOf(
                RouteSegment(
                    streetName = "HAL Old Airport Road",
                    instruction = "Head east towards Marathahalli",
                    turnType = TurnType.START,
                    distanceMeters = seg1Dist,
                    coordinates = seg1Coords
                ),
                RouteSegment(
                    streetName = "Outer Ring Road",
                    instruction = "Slight right onto Outer Ring Road Flyover",
                    turnType = TurnType.SLIGHT_RIGHT,
                    distanceMeters = seg2Dist,
                    coordinates = seg2Coords
                ),
                RouteSegment(
                    streetName = "ITPL Main Road",
                    instruction = "Turn left onto ITPL Main Road",
                    turnType = TurnType.TURN_LEFT,
                    distanceMeters = seg3Dist,
                    coordinates = seg3Coords
                ),
                RouteSegment(
                    streetName = "Whitefield Tech Park Road",
                    instruction = "Arrive at Whitefield Tech Park",
                    turnType = TurnType.ARRIVE,
                    distanceMeters = seg4Dist,
                    coordinates = seg4Coords
                )
            )

            destinationName.contains("Koramangala", ignoreCase = true) -> listOf(
                RouteSegment(
                    streetName = "Inner Ring Road",
                    instruction = "Head south on Inner Ring Road",
                    turnType = TurnType.START,
                    distanceMeters = seg1Dist,
                    coordinates = seg1Coords
                ),
                RouteSegment(
                    streetName = "80 Feet Road",
                    instruction = "Turn right onto Koramangala 80 Feet Road",
                    turnType = TurnType.TURN_RIGHT,
                    distanceMeters = seg2Dist,
                    coordinates = seg2Coords
                ),
                RouteSegment(
                    streetName = "5th Block Avenue",
                    instruction = "Slight left onto 5th Block Avenue",
                    turnType = TurnType.SLIGHT_LEFT,
                    distanceMeters = seg3Dist,
                    coordinates = seg3Coords
                ),
                RouteSegment(
                    streetName = "Koramangala 5th Block",
                    instruction = "Arrive at Koramangala 5th Block",
                    turnType = TurnType.ARRIVE,
                    distanceMeters = seg4Dist,
                    coordinates = seg4Coords
                )
            )

            else -> listOf(
                RouteSegment(
                    streetName = "Main Boulevard",
                    instruction = "Head towards $destinationName",
                    turnType = TurnType.START,
                    distanceMeters = seg1Dist,
                    coordinates = seg1Coords
                ),
                RouteSegment(
                    streetName = "Cross Street",
                    instruction = "In ${seg1Dist.toInt()}m, turn right onto Cross Street",
                    turnType = TurnType.TURN_RIGHT,
                    distanceMeters = seg2Dist,
                    coordinates = seg2Coords
                ),
                RouteSegment(
                    streetName = "City Avenue",
                    instruction = "Slight left onto City Avenue",
                    turnType = TurnType.SLIGHT_LEFT,
                    distanceMeters = seg3Dist,
                    coordinates = seg3Coords
                ),
                RouteSegment(
                    streetName = destinationName,
                    instruction = "Arrive at $destinationName",
                    turnType = TurnType.ARRIVE,
                    distanceMeters = seg4Dist,
                    coordinates = seg4Coords
                )
            )
        }
    }

    private fun interpolatePoints(start: GeoPosition, end: GeoPosition, steps: Int): List<GeoPosition> {
        val list = mutableListOf<GeoPosition>()
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val lat = start.latitude + (end.latitude - start.latitude) * t
            val lon = start.longitude + (end.longitude - start.longitude) * t
            list.add(GeoPosition(lat, lon))
        }
        return list
    }

    private fun calculateDistanceMeters(p1: GeoPosition, p2: GeoPosition): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c
    }
}
