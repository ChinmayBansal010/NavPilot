package com.navpilot.navigation_engine

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.MapMatchedPosition
import com.navpilot.domain.model.RoadCandidate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

class MapMatchingEngine {
    fun match(
        estimatedPosition: GeoPosition?,
        headingDegrees: Float?,
        speedMetersPerSecond: Float,
        roadCandidates: List<RoadCandidate>
    ): MapMatchedPosition? {
        if (estimatedPosition == null || roadCandidates.isEmpty()) {
            return null
        }

        val match = roadCandidates
            .map { candidate -> candidate to candidate.project(estimatedPosition) }
            .filter { (_, projection) -> projection.distanceMeters <= maxSnapDistance(speedMetersPerSecond) }
            .minByOrNull { (_, projection) -> projection.distanceMeters }
            ?: return null

        val roadHeading = match.first.headingDegrees()
        val headingDelta = headingDegrees?.let { angularDifference(it, roadHeading) } ?: 0f
        if (headingDelta > 45f) {
            return null
        }

        val distanceRatio = (match.second.distanceMeters / maxSnapDistance(speedMetersPerSecond)).toFloat()
        val confidence = (1f - distanceRatio).coerceIn(0f, 1f) *
            (1f - headingDelta / 90f).coerceIn(0.3f, 1f)

        return MapMatchedPosition(
            position = estimatedPosition.copy(
                latitude = match.second.latitude,
                longitude = match.second.longitude
            ),
            headingDegrees = roadHeading,
            confidence = confidence,
            matchedRoadSegment = match.first
        )
    }

    private fun maxSnapDistance(speedMetersPerSecond: Float): Double =
        if (speedMetersPerSecond < 2f) 12.0 else 25.0
}

private data class RoadProjection(
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)

private fun RoadCandidate.project(position: GeoPosition): RoadProjection {
    val originLat = position.latitude.toRadians()
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLon = 111_320.0 * cos(originLat)

    val ax = (start.longitude - position.longitude) * metersPerDegreeLon
    val ay = (start.latitude - position.latitude) * metersPerDegreeLat
    val bx = (end.longitude - position.longitude) * metersPerDegreeLon
    val by = (end.latitude - position.latitude) * metersPerDegreeLat
    val abx = bx - ax
    val aby = by - ay
    val lengthSquared = abx * abx + aby * aby
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * abx + ay * aby) / lengthSquared).coerceIn(0.0, 1.0)
    val px = ax + abx * t
    val py = ay + aby * t

    return RoadProjection(
        latitude = position.latitude + py / metersPerDegreeLat,
        longitude = position.longitude + px / metersPerDegreeLon,
        distanceMeters = hypot(px, py)
    )
}

private fun RoadCandidate.headingDegrees(): Float {
    val y = (end.longitude - start.longitude) * cos(((start.latitude + end.latitude) / 2.0).toRadians())
    val x = end.latitude - start.latitude
    return ((atan2(y, x) * 180.0 / PI + 360.0) % 360.0).toFloat()
}

private fun angularDifference(a: Float, b: Float): Float {
    val diff = abs((a - b + 540f) % 360f - 180f)
    return diff.coerceIn(0f, 180f)
}

private fun Double.toRadians(): Double = this * PI / 180.0
