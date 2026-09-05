package com.navpilot.domain.model

import java.util.UUID

enum class TurnType(val label: String) {
    START("Head out"),
    STRAIGHT("Continue straight"),
    TURN_LEFT("Turn left"),
    TURN_RIGHT("Turn right"),
    SLIGHT_LEFT("Slight left"),
    SLIGHT_RIGHT("Slight right"),
    SHARP_LEFT("Sharp left"),
    SHARP_RIGHT("Sharp right"),
    U_TURN("Make a U-turn"),
    ROUNDABOUT_ENTER("Enter roundabout"),
    ROUNDABOUT_EXIT("Exit roundabout"),
    ROUNDABOUT("At roundabout, take exit"),
    ARRIVE("Arrive at destination"),
    DESTINATION_REACHED("Destination reached")
}

enum class RoadClass(val defaultSpeedKph: Int) {
    MOTORWAY(80),
    TRUNK(65),
    PRIMARY(55),
    SECONDARY(45),
    TERTIARY(35),
    RESIDENTIAL(25),
    SERVICE(15)
}

enum class RouteCostMode {
    FASTEST,
    SHORTEST
}

enum class RouteDataSource {
    ONLINE_OSM,
    OFFLINE_IMPORTED,
    OFFLINE_BUNDLED
}

data class RoutingProfile(
    val costMode: RouteCostMode = RouteCostMode.FASTEST,
    val avoidMotorways: Boolean = false,
    val preferMainRoads: Boolean = true
)

data class NavigationPosition(
    val position: GeoPosition,
    val matchedRoadId: String? = null,
    val headingDegrees: Float? = null,
    val speedMetersPerSecond: Float = 0f,
    val accuracyMeters: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class RoadNode(
    val id: Long,
    val position: GeoPosition,
    val name: String? = null,
    val contractionRank: Int = 0
)

data class RoadEdge(
    val id: Long,
    val fromNodeId: Long,
    val toNodeId: Long,
    val distanceMeters: Double,
    val roadName: String,
    val roadClass: RoadClass,
    val speedKph: Int? = null,
    val bidirectional: Boolean = true,
    val geometry: List<GeoPosition> = emptyList(),
    val isShortcut: Boolean = false,
    val shortcutEdgeIds: List<Long> = emptyList()
) {
    fun cost(profile: RoutingProfile): Double {
        val distancePenalty = if (profile.avoidMotorways && roadClass == RoadClass.MOTORWAY) 5.0 else 1.0
        return when (profile.costMode) {
            RouteCostMode.SHORTEST -> distanceMeters * distancePenalty
            RouteCostMode.FASTEST -> {
                val speedMetersPerSecond = ((speedKph ?: roadClass.defaultSpeedKph) * 1000.0) / 3600.0
                val mainRoadBonus = if (profile.preferMainRoads && roadClass in setOf(RoadClass.TRUNK, RoadClass.PRIMARY)) 0.92 else 1.0
                (distanceMeters / speedMetersPerSecond) * distancePenalty * mainRoadBonus
            }
        }
    }
}

data class RoadSegment(
    val id: Long,
    val name: String,
    val roadClass: RoadClass,
    val edgeIds: List<Long>,
    val geometry: List<GeoPosition>
)

data class RoadNetwork(
    val regionId: String,
    val regionName: String,
    val nodes: Map<Long, RoadNode>,
    val edges: Map<Long, RoadEdge>
) {
    val adjacency: Map<Long, List<RoadEdge>> =
        edges.values.groupBy { it.fromNodeId }

    val reverseAdjacency: Map<Long, List<RoadEdge>> =
        edges.values.groupBy { it.toNodeId }
}

data class Maneuver(
    val type: TurnType,
    val instruction: String,
    val bearingBeforeDegrees: Float?,
    val bearingAfterDegrees: Float?
)

data class RouteStep(
    val instruction: String,
    val maneuver: Maneuver,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val roadName: String,
    val position: GeoPosition,
    val headingDegrees: Float?,
    val nextManeuver: TurnType?
)

data class RouteSegment(
    val id: String = UUID.randomUUID().toString(),
    val streetName: String,
    val instruction: String,
    val turnType: TurnType,
    val distanceMeters: Double,
    val coordinates: List<GeoPosition>,
    val durationSeconds: Long = 0L,
    val roadClass: RoadClass = RoadClass.RESIDENTIAL
)

data class Route(
    val id: String = UUID.randomUUID().toString(),
    val destinationName: String,
    val destinationPosition: GeoPosition,
    val totalDistanceMeters: Double,
    val estimatedTravelTimeSeconds: Long,
    val segments: List<RouteSegment>,
    val orderedCoordinates: List<GeoPosition>,
    val steps: List<RouteStep> = emptyList(),
    val profile: RoutingProfile = RoutingProfile(),
    val dataSource: RouteDataSource = RouteDataSource.OFFLINE_BUNDLED
)
