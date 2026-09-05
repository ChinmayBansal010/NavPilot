package com.navpilot.data.routing

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.Maneuver
import com.navpilot.domain.model.RoadClass
import com.navpilot.domain.model.Route
import com.navpilot.domain.model.RouteDataSource
import com.navpilot.domain.model.RouteSegment
import com.navpilot.domain.model.RouteStep
import com.navpilot.domain.model.RoutingProfile
import com.navpilot.domain.model.TurnType
import com.navpilot.domain.routing.RoutingEngine
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class OnlineOsmRoutingEngine(
    private val baseUrl: String = "https://router.project-osrm.org"
) : RoutingEngine {
    override suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route {
        val url = buildRouteUrl(origin, destination)
        val response = request(url)
        val root = JSONObject(response)
        require(root.optString("code") == "Ok") {
            root.optString("message", "OSM route request failed")
        }

        val routeObject = root.getJSONArray("routes").getJSONObject(0)
        val geometry = routeObject
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
            .toGeoPositions()

        val steps = routeObject
            .getJSONArray("legs")
            .getJSONObject(0)
            .getJSONArray("steps")
            .toRouteSteps(destinationName)

        val segments = steps
            .filter { it.distanceMeters > 0.0 }
            .mapIndexed { index, step ->
                RouteSegment(
                    streetName = step.roadName.ifBlank { "Unnamed road" },
                    instruction = step.instruction,
                    turnType = step.maneuver.type,
                    distanceMeters = step.distanceMeters,
                    coordinates = stepGeometryForIndex(routeObject, index).ifEmpty { listOf(step.position) },
                    durationSeconds = step.durationSeconds,
                    roadClass = RoadClass.PRIMARY
                )
            }

        return Route(
            destinationName = destinationName,
            destinationPosition = destination,
            totalDistanceMeters = routeObject.optDouble("distance", geometryDistance(geometry)),
            estimatedTravelTimeSeconds = routeObject.optDouble("duration", 60.0).toLong().coerceAtLeast(1L),
            segments = segments.ifEmpty {
                listOf(
                    RouteSegment(
                        streetName = "Route",
                        instruction = "Continue to $destinationName",
                        turnType = TurnType.ARRIVE,
                        distanceMeters = routeObject.optDouble("distance", geometryDistance(geometry)),
                        coordinates = geometry,
                        durationSeconds = routeObject.optDouble("duration", 60.0).toLong(),
                        roadClass = RoadClass.PRIMARY
                    )
                )
            },
            orderedCoordinates = geometry,
            steps = steps,
            profile = RoutingProfile(),
            dataSource = RouteDataSource.ONLINE_OSM
        )
    }

    private fun buildRouteUrl(origin: GeoPosition, destination: GeoPosition): String {
        val coordinates = String.format(
            Locale.US,
            "%.6f,%.6f;%.6f,%.6f",
            origin.longitude,
            origin.latitude,
            destination.longitude,
            destination.latitude
        )
        return "$baseUrl/route/v1/driving/$coordinates?overview=full&geometries=geojson&steps=true&annotations=false"
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "NavPilot-Android")
        }

        return try {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        val body = stream.bufferedReader().use { it.readText() }
            require(connection.responseCode in 200..299) {
                "OSM route request failed with HTTP ${connection.responseCode}"
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun stepGeometryForIndex(routeObject: JSONObject, stepIndex: Int): List<GeoPosition> =
        runCatching {
            routeObject
                .getJSONArray("legs")
                .getJSONObject(0)
                .getJSONArray("steps")
                .getJSONObject(stepIndex)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")
                .toGeoPositions()
        }.getOrDefault(emptyList())
}

private fun JSONArray.toGeoPositions(): List<GeoPosition> =
    (0 until length()).map { index ->
        val coordinate = getJSONArray(index)
        GeoPosition(
            latitude = coordinate.getDouble(1),
            longitude = coordinate.getDouble(0)
        )
    }

private fun JSONArray.toRouteSteps(destinationName: String): List<RouteStep> {
    val routeSteps = (0 until length()).map { index ->
        val step = getJSONObject(index)
        val maneuverObject = step.getJSONObject("maneuver")
        val maneuverType = maneuverObject.toTurnType(isFinal = index == length() - 1)
        val location = maneuverObject.getJSONArray("location")
        val roadName = step.optString("name").ifBlank { "Unnamed road" }
        val modifier = maneuverObject.optString("modifier")
        val instruction = instructionFor(maneuverType, modifier, roadName, destinationName)
        val bearingBefore = maneuverObject.optDoubleOrNull("bearing_before")?.toFloat()
        val bearingAfter = maneuverObject.optDoubleOrNull("bearing_after")?.toFloat()

        RouteStep(
            instruction = instruction,
            maneuver = Maneuver(
                type = maneuverType,
                instruction = instruction,
                bearingBeforeDegrees = bearingBefore,
                bearingAfterDegrees = bearingAfter
            ),
            distanceMeters = step.optDouble("distance", 0.0),
            durationSeconds = step.optDouble("duration", 0.0).toLong(),
            roadName = roadName,
            position = GeoPosition(
                latitude = location.getDouble(1),
                longitude = location.getDouble(0)
            ),
            headingDegrees = bearingAfter,
            nextManeuver = null
        )
    }

    return routeSteps.mapIndexed { index, step ->
        step.copy(nextManeuver = routeSteps.getOrNull(index + 1)?.maneuver?.type)
    }
}

private fun JSONObject.toTurnType(isFinal: Boolean): TurnType {
    if (isFinal || optString("type") == "arrive") return TurnType.DESTINATION_REACHED

    return when (optString("type")) {
        "depart" -> TurnType.START
        "roundabout", "rotary" -> TurnType.ROUNDABOUT_ENTER
        "exit roundabout", "exit rotary" -> TurnType.ROUNDABOUT_EXIT
        "turn", "end of road" -> when (optString("modifier")) {
            "left" -> TurnType.TURN_LEFT
            "right" -> TurnType.TURN_RIGHT
            "sharp left" -> TurnType.SHARP_LEFT
            "sharp right" -> TurnType.SHARP_RIGHT
            "slight left" -> TurnType.SLIGHT_LEFT
            "slight right" -> TurnType.SLIGHT_RIGHT
            "uturn" -> TurnType.U_TURN
            else -> TurnType.STRAIGHT
        }
        "new name", "continue", "notification" -> TurnType.STRAIGHT
        "merge", "on ramp", "off ramp", "fork" -> when (optString("modifier")) {
            "left", "slight left" -> TurnType.SLIGHT_LEFT
            "right", "slight right" -> TurnType.SLIGHT_RIGHT
            else -> TurnType.STRAIGHT
        }
        else -> TurnType.STRAIGHT
    }
}

private fun instructionFor(
    maneuverType: TurnType,
    modifier: String,
    roadName: String,
    destinationName: String
): String =
    when (maneuverType) {
        TurnType.START -> "Start on $roadName"
        TurnType.STRAIGHT -> "Continue on $roadName"
        TurnType.SLIGHT_LEFT -> "Slight left onto $roadName"
        TurnType.SLIGHT_RIGHT -> "Slight right onto $roadName"
        TurnType.TURN_LEFT -> "Turn left onto $roadName"
        TurnType.TURN_RIGHT -> "Turn right onto $roadName"
        TurnType.SHARP_LEFT -> "Sharp left onto $roadName"
        TurnType.SHARP_RIGHT -> "Sharp right onto $roadName"
        TurnType.U_TURN -> "Make a U-turn"
        TurnType.ROUNDABOUT_ENTER, TurnType.ROUNDABOUT -> "Enter the roundabout toward $roadName"
        TurnType.ROUNDABOUT_EXIT -> "Exit the roundabout onto $roadName"
        TurnType.ARRIVE, TurnType.DESTINATION_REACHED -> "Arrive at $destinationName"
    }.replace("Unnamed road", if (modifier.isBlank()) "the road" else "the $modifier road")

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun geometryDistance(points: List<GeoPosition>): Double =
    points.windowed(2).sumOf { pair -> haversineMeters(pair[0], pair[1]) }

private fun haversineMeters(a: GeoPosition, b: GeoPosition): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = kotlin.math.sin(dLat / 2.0) * kotlin.math.sin(dLat / 2.0) +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(dLon / 2.0) * kotlin.math.sin(dLon / 2.0)
    return earthRadiusMeters * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1.0 - h))
}
