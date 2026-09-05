package com.navpilot.navigation_engine

import com.navpilot.data.routing.BundledDelhiNcrRoadNetwork
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.MapMatchedPosition
import com.navpilot.domain.model.RoadCandidate
import com.navpilot.domain.model.RoadEdge
import com.navpilot.domain.model.RoadNetwork
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

interface MapMatcher {
    fun update(
        position: GeoPosition,
        headingDegrees: Float?,
        speedMetersPerSecond: Float
    ): MapMatchResult?

    fun reset()
}

data class CandidateRoad(
    val edge: RoadEdge,
    val projectedPosition: GeoPosition,
    val distanceMeters: Double,
    val roadBearingDegrees: Float,
    val fractionAlongEdge: Double
)

data class MapMatchCandidate(
    val road: CandidateRoad,
    val emissionLogProbability: Double,
    val transitionLogProbability: Double = 0.0,
    val totalLogProbability: Double = Double.NEGATIVE_INFINITY,
    val previousEdgeId: Long? = null
)

data class MapMatchResult(
    val position: GeoPosition,
    val headingDegrees: Float?,
    val confidence: Float,
    val matchedRoad: CandidateRoad,
    val sequence: List<CandidateRoad>
)

class EmissionProbability(
    private val sigmaMeters: Double = 12.0
) {
    fun logProbability(candidate: CandidateRoad, headingDegrees: Float?): Double {
        val distanceScore = -0.5 * (candidate.distanceMeters / sigmaMeters) * (candidate.distanceMeters / sigmaMeters)
        val headingScore = headingDegrees?.let {
            -angularDifference(it, candidate.roadBearingDegrees) / 90.0
        } ?: 0.0
        return distanceScore + headingScore
    }
}

class TransitionProbability(
    private val betaMeters: Double = 35.0
) {
    fun logProbability(previous: CandidateRoad, current: CandidateRoad, observedDistanceMeters: Double): Double {
        val networkDistance = if (previous.edge.id == current.edge.id) {
            abs(current.fractionAlongEdge - previous.fractionAlongEdge) * current.edge.distanceMeters
        } else {
            calculateDistanceMeters(previous.projectedPosition, current.projectedPosition)
        }
        val distanceDelta = abs(networkDistance - observedDistanceMeters)
        val headingDelta = angularDifference(previous.roadBearingDegrees, current.roadBearingDegrees)
        return -distanceDelta / betaMeters - headingDelta / 180.0
    }
}

class HmmViterbiMapMatcher(
    private val roadNetwork: RoadNetwork = BundledDelhiNcrRoadNetwork.create(),
    private val emissionProbability: EmissionProbability = EmissionProbability(),
    private val transitionProbability: TransitionProbability = TransitionProbability()
) : MapMatcher {
    private var previousObservation: GeoPosition? = null
    private var previousCandidates: List<MapMatchCandidate> = emptyList()
    private var bestSequence: List<CandidateRoad> = emptyList()

    override fun update(
        position: GeoPosition,
        headingDegrees: Float?,
        speedMetersPerSecond: Float
    ): MapMatchResult? {
        val candidates = selectCandidates(position, headingDegrees, speedMetersPerSecond)
        if (candidates.isEmpty()) return null

        val observedDistance = previousObservation?.let { calculateDistanceMeters(it, position) } ?: 0.0
        val scored = if (previousCandidates.isEmpty()) {
            candidates.map { candidate ->
                val emission = emissionProbability.logProbability(candidate, headingDegrees)
                MapMatchCandidate(
                    road = candidate,
                    emissionLogProbability = emission,
                    totalLogProbability = emission
                )
            }
        } else {
            candidates.map { current ->
                val bestPrevious = previousCandidates.maxBy { previous ->
                    previous.totalLogProbability +
                        transitionProbability.logProbability(previous.road, current, observedDistance)
                }
                val transition = transitionProbability.logProbability(bestPrevious.road, current, observedDistance)
                val emission = emissionProbability.logProbability(current, headingDegrees)
                MapMatchCandidate(
                    road = current,
                    emissionLogProbability = emission,
                    transitionLogProbability = transition,
                    totalLogProbability = bestPrevious.totalLogProbability + transition + emission,
                    previousEdgeId = bestPrevious.road.edge.id
                )
            }
        }

        val best = scored.maxBy { it.totalLogProbability }
        previousObservation = position
        previousCandidates = scored
        bestSequence = (bestSequence + best.road).takeLast(MAX_SEQUENCE_LENGTH)

        return MapMatchResult(
            position = best.road.projectedPosition,
            headingDegrees = best.road.roadBearingDegrees,
            confidence = confidenceFromLogProbability(best.totalLogProbability),
            matchedRoad = best.road,
            sequence = bestSequence
        )
    }

    override fun reset() {
        previousObservation = null
        previousCandidates = emptyList()
        bestSequence = emptyList()
    }

    private fun selectCandidates(
        position: GeoPosition,
        headingDegrees: Float?,
        speedMetersPerSecond: Float
    ): List<CandidateRoad> {
        val radiusMeters = when {
            speedMetersPerSecond > 15f -> 45.0
            speedMetersPerSecond > 3f -> 30.0
            else -> 20.0
        }

        return roadNetwork.edges.values
            .asSequence()
            .map { edge -> edge.project(position) }
            .filter { candidate ->
                candidate.distanceMeters <= radiusMeters &&
                    headingDegrees?.let { angularDifference(it, candidate.roadBearingDegrees) <= 80.0 } ?: true
            }
            .sortedBy { it.distanceMeters }
            .take(6)
            .toList()
    }

    private fun RoadEdge.project(position: GeoPosition): CandidateRoad {
        val geometryPoints = geometry.ifEmpty {
            listOf(
                roadNetwork.nodes.getValue(fromNodeId).position,
                roadNetwork.nodes.getValue(toNodeId).position
            )
        }

        var bestProjection: Projection? = null
        var cumulativeDistance = 0.0
        val totalDistance = geometryPoints.windowed(2).sumOf { calculateDistanceMeters(it[0], it[1]) }

        geometryPoints.windowed(2).forEach { pair ->
            val segmentDistance = calculateDistanceMeters(pair[0], pair[1])
            val projection = projectToSegment(position, pair[0], pair[1])
            val along = cumulativeDistance + projection.segmentFraction * segmentDistance
            val fraction = if (totalDistance == 0.0) 0.0 else along / totalDistance
            val candidateProjection = projection.copy(fractionAlongEdge = fraction)
            if (bestProjection == null || candidateProjection.distanceMeters < bestProjection!!.distanceMeters) {
                bestProjection = candidateProjection
            }
            cumulativeDistance += segmentDistance
        }

        val projection = bestProjection ?: Projection(
            position = geometryPoints.first(),
            distanceMeters = calculateDistanceMeters(position, geometryPoints.first()),
            segmentFraction = 0.0,
            fractionAlongEdge = 0.0
        )

        return CandidateRoad(
            edge = this,
            projectedPosition = projection.position,
            distanceMeters = projection.distanceMeters,
            roadBearingDegrees = bearingDegrees(geometryPoints.first(), geometryPoints.last()).toFloat(),
            fractionAlongEdge = projection.fractionAlongEdge
        )
    }

    private companion object {
        const val MAX_SEQUENCE_LENGTH = 20
    }
}

class MapMatchingEngine(
    private val matcher: MapMatcher = HmmViterbiMapMatcher()
) {
    fun match(
        estimatedPosition: GeoPosition?,
        headingDegrees: Float?,
        speedMetersPerSecond: Float,
        roadCandidates: List<RoadCandidate>
    ): MapMatchedPosition? {
        if (estimatedPosition == null) return null

        val result = matcher.update(
            position = estimatedPosition,
            headingDegrees = headingDegrees,
            speedMetersPerSecond = speedMetersPerSecond
        ) ?: return null

        val compatibilityRoad = roadCandidates.firstOrNull {
            it.id == result.matchedRoad.edge.id.toString()
        } ?: RoadCandidate(
            id = result.matchedRoad.edge.id.toString(),
            start = result.matchedRoad.edge.geometry.firstOrNull() ?: result.position,
            end = result.matchedRoad.edge.geometry.lastOrNull() ?: result.position,
            roadClass = result.matchedRoad.edge.roadName
        )

        return MapMatchedPosition(
            position = result.position,
            headingDegrees = result.headingDegrees,
            confidence = result.confidence,
            matchedRoadSegment = compatibilityRoad
        )
    }

    fun reset() {
        matcher.reset()
    }
}

private data class Projection(
    val position: GeoPosition,
    val distanceMeters: Double,
    val segmentFraction: Double,
    val fractionAlongEdge: Double
)

private fun projectToSegment(
    position: GeoPosition,
    start: GeoPosition,
    end: GeoPosition
): Projection {
    val originLat = position.latitude.toRadians()
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLon = 111_320.0 * cos(originLat).coerceAtLeast(0.0001)
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
    return Projection(
        position = GeoPosition(
            latitude = position.latitude + py / metersPerDegreeLat,
            longitude = position.longitude + px / metersPerDegreeLon,
            timestampMillis = position.timestampMillis
        ),
        distanceMeters = hypot(px, py),
        segmentFraction = t,
        fractionAlongEdge = t
    )
}

private fun confidenceFromLogProbability(logProbability: Double): Float {
    val normalized = exp((logProbability / 8.0).coerceAtLeast(-12.0))
    return (normalized / (1.0 + normalized)).toFloat().coerceIn(0.05f, 0.98f)
}

private fun angularDifference(a: Float, b: Float): Double =
    angularDifference(a.toDouble(), b.toDouble())

private fun angularDifference(a: Double, b: Double): Double {
    val diff = abs((a - b + 540.0) % 360.0 - 180.0)
    return diff.coerceIn(0.0, 180.0)
}

private fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
    val lat1 = from.latitude.toRadians()
    val lat2 = to.latitude.toRadians()
    val dLon = (to.longitude - from.longitude).toRadians()
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
}

private fun calculateDistanceMeters(p1: GeoPosition, p2: GeoPosition): Double {
    val lat1 = p1.latitude.toRadians()
    val lat2 = p2.latitude.toRadians()
    val dLat = (p2.latitude - p1.latitude).toRadians()
    val dLon = (p2.longitude - p1.longitude).toRadians()
    val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(lat1) * cos(lat2) *
        sin(dLon / 2.0) * sin(dLon / 2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return 6_371_000.0 * c
}

private fun Double.toRadians(): Double = this * PI / 180.0
