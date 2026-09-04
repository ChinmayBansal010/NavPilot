package com.navpilot.domain.model

import java.util.UUID

enum class TurnType(val label: String) {
    START("Head out"),
    STRAIGHT("Continue straight"),
    TURN_LEFT("Turn left"),
    TURN_RIGHT("Turn right"),
    SLIGHT_LEFT("Slight left"),
    SLIGHT_RIGHT("Slight right"),
    ROUNDABOUT("At roundabout, take exit"),
    ARRIVE("Arrive at destination")
}

data class RouteSegment(
    val id: String = UUID.randomUUID().toString(),
    val streetName: String,
    val instruction: String,
    val turnType: TurnType,
    val distanceMeters: Double,
    val coordinates: List<GeoPosition>
)

data class Route(
    val id: String = UUID.randomUUID().toString(),
    val destinationName: String,
    val destinationPosition: GeoPosition,
    val totalDistanceMeters: Double,
    val estimatedTravelTimeSeconds: Long,
    val segments: List<RouteSegment>,
    val orderedCoordinates: List<GeoPosition>
)
