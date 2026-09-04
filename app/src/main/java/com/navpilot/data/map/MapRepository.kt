package com.navpilot.data.map

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.RoadCandidate

interface MapRepository {
    suspend fun getRoadCandidates(
        position: GeoPosition,
        radiusMeters: Double
    ): List<RoadCandidate>
}

class OfflineFirstMapRepository : MapRepository {
    private val roadCandidates = mutableListOf<RoadCandidate>()

    fun replaceRoadCandidates(candidates: List<RoadCandidate>) {
        roadCandidates.clear()
        roadCandidates.addAll(candidates)
    }

    override suspend fun getRoadCandidates(
        position: GeoPosition,
        radiusMeters: Double
    ): List<RoadCandidate> =
        roadCandidates.filter { candidate ->
            candidate.start.distanceTo(position) <= radiusMeters ||
                candidate.end.distanceTo(position) <= radiusMeters
        }
}

private fun GeoPosition.distanceTo(other: GeoPosition): Double {
    val earthRadiusMeters = 6_371_000.0
    val deltaLatitude = (other.latitude - latitude).toRadians()
    val deltaLongitude = (other.longitude - longitude).toRadians()
    val lat1 = latitude.toRadians()
    val lat2 = other.latitude.toRadians()
    val a = kotlin.math.sin(deltaLatitude / 2.0) * kotlin.math.sin(deltaLatitude / 2.0) +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
        kotlin.math.sin(deltaLongitude / 2.0) * kotlin.math.sin(deltaLongitude / 2.0)
    val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    return earthRadiusMeters * c
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0
