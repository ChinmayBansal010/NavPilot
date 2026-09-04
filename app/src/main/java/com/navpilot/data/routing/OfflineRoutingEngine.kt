package com.navpilot.data.routing

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.Maneuver
import com.navpilot.domain.model.RoadClass
import com.navpilot.domain.model.RoadEdge
import com.navpilot.domain.model.RoadNetwork
import com.navpilot.domain.model.RoadNode
import com.navpilot.domain.model.Route
import com.navpilot.domain.model.RouteCostMode
import com.navpilot.domain.model.RouteSegment
import com.navpilot.domain.model.RouteStep
import com.navpilot.domain.model.RoutingProfile
import com.navpilot.domain.model.TurnType
import com.navpilot.domain.routing.RoutingEngine
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineRoutingEngine(
    private val roadNetwork: RoadNetwork = DemoDelhiRoadNetwork.create(),
    private val router: HierarchicalRoutePlanner = ContractionHierarchyRoutePlanner(roadNetwork)
) : RoutingEngine {

    override suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route =
        calculateRoute(
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            profile = RoutingProfile(costMode = RouteCostMode.FASTEST)
        )

    suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String,
        profile: RoutingProfile
    ): Route {
        val originNode = roadNetwork.nearestNode(origin)
        val destinationNode = roadNetwork.nearestNode(destination)
        val path = router.findRoute(originNode.id, destinationNode.id, profile)
        val routeEdges = path.flatMap { expandShortcut(it, roadNetwork) }

        val edgeSegments = buildRouteSegments(
            origin = origin,
            destination = destination,
            destinationName = destinationName,
            routeEdges = routeEdges,
            profile = profile
        )

        val orderedCoordinates = edgeSegments
            .flatMap { it.coordinates }
            .removeNearDuplicates()

        val steps = buildRouteSteps(edgeSegments, destinationName)
        val totalDistanceMeters = edgeSegments.sumOf { it.distanceMeters }
        val totalDurationSeconds = edgeSegments.sumOf { it.durationSeconds }.coerceAtLeast(60L)

        return Route(
            destinationName = destinationName,
            destinationPosition = destination,
            totalDistanceMeters = totalDistanceMeters,
            estimatedTravelTimeSeconds = totalDurationSeconds,
            segments = edgeSegments,
            orderedCoordinates = orderedCoordinates,
            steps = steps,
            profile = profile
        )
    }

    private fun buildRouteSegments(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String,
        routeEdges: List<RoadEdge>,
        profile: RoutingProfile
    ): List<RouteSegment> {
        if (routeEdges.isEmpty()) {
            val distance = calculateDistanceMeters(origin, destination)
            return listOf(
                RouteSegment(
                    streetName = "Local access road",
                    instruction = "Continue to $destinationName",
                    turnType = TurnType.ARRIVE,
                    distanceMeters = distance,
                    coordinates = densify(listOf(origin, destination), steps = 10),
                    durationSeconds = estimateDurationSeconds(distance, RoadClass.RESIDENTIAL),
                    roadClass = RoadClass.RESIDENTIAL
                )
            )
        }

        val segments = mutableListOf<RouteSegment>()
        var currentRoad = routeEdges.first().roadName
        var currentClass = routeEdges.first().roadClass
        var currentEdges = mutableListOf<RoadEdge>()
        var currentPoints = mutableListOf(origin)
        var previousBearing: Double? = null
        var currentTurn = TurnType.START

        routeEdges.forEachIndexed { index, edge ->
            val edgeGeometry = edge.geometry.ifEmpty {
                val from = roadNetwork.nodes.getValue(edge.fromNodeId).position
                val to = roadNetwork.nodes.getValue(edge.toNodeId).position
                listOf(from, to)
            }

            val bearing = bearingDegrees(edgeGeometry.first(), edgeGeometry.last())
            val turn = previousBearing?.let { classifyTurn(it, bearing) } ?: TurnType.START
            val shouldStartNewSegment = index > 0 &&
                (edge.roadName != currentRoad || turn !in setOf(TurnType.START, TurnType.STRAIGHT))

            if (shouldStartNewSegment && currentEdges.isNotEmpty()) {
                segments += createRouteSegment(
                    edges = currentEdges,
                    roadName = currentRoad,
                    roadClass = currentClass,
                    points = currentPoints,
                    turnType = currentTurn,
                    isFinal = false,
                    destinationName = destinationName,
                    profile = profile
                )
                currentEdges = mutableListOf()
                currentPoints = mutableListOf(currentPoints.last())
                currentRoad = edge.roadName
                currentClass = edge.roadClass
                currentTurn = turn
            }

            currentEdges += edge
            val pointsToAdd = if (currentPoints.last().isNear(edgeGeometry.first())) {
                edgeGeometry.drop(1)
            } else {
                edgeGeometry
            }
            currentPoints.addAll(pointsToAdd)
            previousBearing = bearing
        }

        if (!currentPoints.last().isNear(destination)) {
            currentPoints += destination
        }

        if (currentEdges.isNotEmpty()) {
            segments += createRouteSegment(
                edges = currentEdges,
                roadName = currentRoad,
                roadClass = currentClass,
                points = currentPoints,
                turnType = if (segments.isEmpty()) TurnType.START else currentTurn,
                isFinal = true,
                destinationName = destinationName,
                profile = profile
            )
        }

        return segments
    }

    private fun createRouteSegment(
        edges: List<RoadEdge>,
        roadName: String,
        roadClass: RoadClass,
        points: List<GeoPosition>,
        turnType: TurnType,
        isFinal: Boolean,
        destinationName: String,
        profile: RoutingProfile
    ): RouteSegment {
        val distance = points.windowed(2).sumOf { calculateDistanceMeters(it[0], it[1]) }
        val duration = estimateDurationSeconds(distance, roadClass)
        val maneuver = if (isFinal) TurnType.ARRIVE else turnType
        return RouteSegment(
            streetName = roadName,
            instruction = instructionFor(maneuver, roadName, destinationName, isFinal),
            turnType = maneuver,
            distanceMeters = distance,
            coordinates = densify(points),
            durationSeconds = duration,
            roadClass = roadClass
        )
    }

    private fun buildRouteSteps(
        segments: List<RouteSegment>,
        destinationName: String
    ): List<RouteStep> =
        segments.mapIndexed { index, segment ->
            val before = segments.getOrNull(index - 1)?.coordinates?.bearing()
            val after = segment.coordinates.bearing()
            val next = segments.getOrNull(index + 1)?.turnType
            RouteStep(
                instruction = segment.instruction,
                maneuver = Maneuver(
                    type = segment.turnType,
                    instruction = segment.instruction,
                    bearingBeforeDegrees = before?.toFloat(),
                    bearingAfterDegrees = after?.toFloat()
                ),
                distanceMeters = segment.distanceMeters,
                durationSeconds = segment.durationSeconds,
                roadName = segment.streetName,
                position = segment.coordinates.firstOrNull() ?: GeoPosition(0.0, 0.0),
                headingDegrees = after?.toFloat(),
                nextManeuver = next ?: if (index == segments.lastIndex) TurnType.DESTINATION_REACHED else null
            )
        } + listOfNotNull(
            segments.lastOrNull()?.coordinates?.lastOrNull()?.let { destination ->
                RouteStep(
                    instruction = "Arrive at $destinationName",
                    maneuver = Maneuver(
                        type = TurnType.DESTINATION_REACHED,
                        instruction = "Arrive at $destinationName",
                        bearingBeforeDegrees = segments.lastOrNull()?.coordinates?.bearing()?.toFloat(),
                        bearingAfterDegrees = null
                    ),
                    distanceMeters = 0.0,
                    durationSeconds = 0L,
                    roadName = destinationName,
                    position = destination,
                    headingDegrees = null,
                    nextManeuver = null
                )
            }
        )

    private fun instructionFor(
        turnType: TurnType,
        roadName: String,
        destinationName: String,
        isFinal: Boolean
    ): String {
        if (isFinal) return "Continue on $roadName toward $destinationName"
        return when (turnType) {
            TurnType.START -> "Start on $roadName"
            TurnType.STRAIGHT -> "Continue straight on $roadName"
            TurnType.SLIGHT_LEFT -> "Slight left onto $roadName"
            TurnType.SLIGHT_RIGHT -> "Slight right onto $roadName"
            TurnType.TURN_LEFT -> "Turn left onto $roadName"
            TurnType.TURN_RIGHT -> "Turn right onto $roadName"
            TurnType.SHARP_LEFT -> "Sharp left onto $roadName"
            TurnType.SHARP_RIGHT -> "Sharp right onto $roadName"
            TurnType.U_TURN -> "Make a U-turn onto $roadName"
            TurnType.ROUNDABOUT_ENTER -> "Enter the roundabout for $roadName"
            TurnType.ROUNDABOUT_EXIT, TurnType.ROUNDABOUT -> "Exit the roundabout onto $roadName"
            TurnType.ARRIVE, TurnType.DESTINATION_REACHED -> "Arrive at $destinationName"
        }
    }
}

interface HierarchicalRoutePlanner {
    fun findRoute(
        originNodeId: Long,
        destinationNodeId: Long,
        profile: RoutingProfile
    ): List<RoadEdge>
}

class ContractionHierarchyRoutePlanner(
    private val network: RoadNetwork
) : HierarchicalRoutePlanner {
    private val contractedNetwork = ContractedRoadNetwork.from(network)

    override fun findRoute(
        originNodeId: Long,
        destinationNodeId: Long,
        profile: RoutingProfile
    ): List<RoadEdge> {
        if (originNodeId == destinationNodeId) return emptyList()

        val forwardQueue = PriorityQueue(compareBy<SearchLabel> { it.cost })
        val backwardQueue = PriorityQueue(compareBy<SearchLabel> { it.cost })
        val forwardCost = mutableMapOf(originNodeId to 0.0)
        val backwardCost = mutableMapOf(destinationNodeId to 0.0)
        val forwardParent = mutableMapOf<Long, RoadEdge>()
        val backwardParent = mutableMapOf<Long, RoadEdge>()
        val settledForward = mutableSetOf<Long>()
        val settledBackward = mutableSetOf<Long>()

        forwardQueue += SearchLabel(originNodeId, 0.0)
        backwardQueue += SearchLabel(destinationNodeId, 0.0)

        var bestMeetingNode: Long? = null
        var bestCost = Double.POSITIVE_INFINITY

        while (forwardQueue.isNotEmpty() || backwardQueue.isNotEmpty()) {
            if (forwardQueue.isNotEmpty()) {
                val current = forwardQueue.poll()
                if (settledForward.add(current.nodeId)) {
                    val combined = current.cost + backwardCost.getOrDefault(current.nodeId, Double.POSITIVE_INFINITY)
                    if (combined < bestCost) {
                        bestCost = combined
                        bestMeetingNode = current.nodeId
                    }
                    contractedNetwork.forwardEdges(current.nodeId).forEach { edge ->
                        relax(edge, current.nodeId, forwardCost, forwardParent, forwardQueue, profile)
                    }
                }
            }

            if (backwardQueue.isNotEmpty()) {
                val current = backwardQueue.poll()
                if (settledBackward.add(current.nodeId)) {
                    val combined = current.cost + forwardCost.getOrDefault(current.nodeId, Double.POSITIVE_INFINITY)
                    if (combined < bestCost) {
                        bestCost = combined
                        bestMeetingNode = current.nodeId
                    }
                    contractedNetwork.backwardEdges(current.nodeId).forEach { edge ->
                        relaxReverse(edge, current.nodeId, backwardCost, backwardParent, backwardQueue, profile)
                    }
                }
            }

            val forwardMin = forwardQueue.peek()?.cost ?: Double.POSITIVE_INFINITY
            val backwardMin = backwardQueue.peek()?.cost ?: Double.POSITIVE_INFINITY
            if (bestMeetingNode != null && forwardMin + backwardMin >= bestCost) {
                break
            }
        }

        return reconstructPath(
            meetingNode = bestMeetingNode ?: return emptyList(),
            originNodeId = originNodeId,
            destinationNodeId = destinationNodeId,
            forwardParent = forwardParent,
            backwardParent = backwardParent
        )
    }

    private fun relax(
        edge: RoadEdge,
        fromNodeId: Long,
        cost: MutableMap<Long, Double>,
        parent: MutableMap<Long, RoadEdge>,
        queue: PriorityQueue<SearchLabel>,
        profile: RoutingProfile
    ) {
        val to = edge.toNodeId
        val newCost = cost.getValue(fromNodeId) + edge.cost(profile)
        if (newCost < cost.getOrDefault(to, Double.POSITIVE_INFINITY)) {
            cost[to] = newCost
            parent[to] = edge
            queue += SearchLabel(to, newCost)
        }
    }

    private fun relaxReverse(
        edge: RoadEdge,
        toNodeId: Long,
        cost: MutableMap<Long, Double>,
        parent: MutableMap<Long, RoadEdge>,
        queue: PriorityQueue<SearchLabel>,
        profile: RoutingProfile
    ) {
        val from = edge.fromNodeId
        val newCost = cost.getValue(toNodeId) + edge.cost(profile)
        if (newCost < cost.getOrDefault(from, Double.POSITIVE_INFINITY)) {
            cost[from] = newCost
            parent[from] = edge
            queue += SearchLabel(from, newCost)
        }
    }

    private fun reconstructPath(
        meetingNode: Long,
        originNodeId: Long,
        destinationNodeId: Long,
        forwardParent: Map<Long, RoadEdge>,
        backwardParent: Map<Long, RoadEdge>
    ): List<RoadEdge> {
        val forward = mutableListOf<RoadEdge>()
        var current = meetingNode
        while (current != originNodeId) {
            val edge = forwardParent[current] ?: break
            forward += edge
            current = edge.fromNodeId
        }

        val backward = mutableListOf<RoadEdge>()
        current = meetingNode
        while (current != destinationNodeId) {
            val edge = backwardParent[current] ?: break
            backward += edge
            current = edge.toNodeId
        }

        return forward.asReversed() + backward
    }
}

private data class SearchLabel(
    val nodeId: Long,
    val cost: Double
)

private class ContractedRoadNetwork(
    private val network: RoadNetwork,
    private val upwardEdges: Map<Long, List<RoadEdge>>,
    private val downwardEdges: Map<Long, List<RoadEdge>>
) {
    fun forwardEdges(nodeId: Long): List<RoadEdge> =
        upwardEdges[nodeId].orEmpty().ifEmpty { network.adjacency[nodeId].orEmpty() }

    fun backwardEdges(nodeId: Long): List<RoadEdge> =
        downwardEdges[nodeId].orEmpty().ifEmpty { network.reverseAdjacency[nodeId].orEmpty() }

    companion object {
        fun from(network: RoadNetwork): ContractedRoadNetwork {
            val rankedEdges = network.edges.values
            val upward = rankedEdges
                .filter { edge ->
                    val fromRank = network.nodes.getValue(edge.fromNodeId).contractionRank
                    val toRank = network.nodes.getValue(edge.toNodeId).contractionRank
                    toRank >= fromRank
                }
                .groupBy { it.fromNodeId }

            val downward = rankedEdges
                .filter { edge ->
                    val fromRank = network.nodes.getValue(edge.fromNodeId).contractionRank
                    val toRank = network.nodes.getValue(edge.toNodeId).contractionRank
                    fromRank >= toRank
                }
                .groupBy { it.toNodeId }

            return ContractedRoadNetwork(network, upward, downward)
        }
    }
}

object DemoDelhiRoadNetwork {
    fun create(): RoadNetwork {
        val nodes = listOf(
            node(1, "Connaught Place", 28.6315, 77.2167, 9),
            node(2, "India Gate", 28.6129, 77.2295, 10),
            node(3, "ITO", 28.6283, 77.2473, 8),
            node(4, "Mandi House", 28.6256, 77.2342, 7),
            node(5, "Lajpat Nagar", 28.5677, 77.2433, 6),
            node(6, "AIIMS", 28.5672, 77.2100, 7),
            node(7, "Hauz Khas", 28.5494, 77.2001, 5),
            node(8, "Saket", 28.5245, 77.2066, 4),
            node(9, "Nehru Place", 28.5483, 77.2513, 5),
            node(10, "Akshardham", 28.6127, 77.2773, 6),
            node(11, "Mayur Vihar", 28.6085, 77.2956, 5),
            node(12, "Dwarka Mor", 28.6193, 77.0333, 4),
            node(13, "Janakpuri", 28.6219, 77.0878, 5),
            node(14, "Rajouri Garden", 28.6425, 77.1209, 6),
            node(15, "Karol Bagh", 28.6510, 77.1907, 7),
            node(16, "Kashmere Gate", 28.6676, 77.2280, 8),
            node(17, "Noida Sector 18", 28.5708, 77.3261, 4),
            node(18, "Ashram", 28.5726, 77.2606, 7),
            node(19, "Dhaula Kuan", 28.5919, 77.1616, 8),
            node(20, "Aerocity", 28.5488, 77.1207, 6)
        ).associateBy { it.id }

        val edgeSpecs = listOf(
            spec(1, 4, "Barakhamba Road", RoadClass.PRIMARY, 50),
            spec(4, 2, "Tilak Marg", RoadClass.PRIMARY, 45),
            spec(4, 3, "Vikas Marg", RoadClass.PRIMARY, 45),
            spec(3, 10, "Delhi Meerut Expressway Link", RoadClass.TRUNK, 65),
            spec(10, 11, "Noida Link Road", RoadClass.PRIMARY, 55),
            spec(11, 17, "Noida Sector Road", RoadClass.SECONDARY, 40),
            spec(2, 18, "Mathura Road", RoadClass.PRIMARY, 50),
            spec(18, 5, "Lala Lajpat Rai Marg", RoadClass.PRIMARY, 45),
            spec(5, 9, "Outer Ring Road", RoadClass.TRUNK, 60),
            spec(9, 8, "Press Enclave Road", RoadClass.SECONDARY, 35),
            spec(8, 7, "Aurobindo Marg", RoadClass.SECONDARY, 35),
            spec(7, 6, "Sri Aurobindo Marg", RoadClass.PRIMARY, 45),
            spec(6, 19, "Ring Road", RoadClass.TRUNK, 60),
            spec(19, 20, "NH 48", RoadClass.TRUNK, 65),
            spec(19, 14, "Ring Road", RoadClass.TRUNK, 60),
            spec(14, 13, "Najafgarh Road", RoadClass.PRIMARY, 45),
            spec(13, 12, "Dwarka Road", RoadClass.PRIMARY, 45),
            spec(14, 15, "Pusa Road", RoadClass.PRIMARY, 45),
            spec(15, 1, "Panchkuian Road", RoadClass.PRIMARY, 45),
            spec(1, 16, "Bhavbhuti Marg", RoadClass.PRIMARY, 45),
            spec(16, 3, "Mahatma Gandhi Marg", RoadClass.TRUNK, 55),
            spec(6, 5, "Ring Road", RoadClass.TRUNK, 55),
            spec(5, 18, "Ring Road", RoadClass.TRUNK, 55),
            spec(2, 6, "Safdarjung Road", RoadClass.PRIMARY, 45),
            spec(3, 18, "Ring Road", RoadClass.TRUNK, 55)
        )

        var edgeId = 1L
        val directedEdges = edgeSpecs.flatMap { spec ->
            val forward = edge(edgeId++, spec.from, spec.to, spec.roadName, spec.roadClass, spec.speedKph, nodes)
            val reverse = edge(edgeId++, spec.to, spec.from, spec.roadName, spec.roadClass, spec.speedKph, nodes)
            listOf(forward, reverse)
        }.associateBy { it.id }

        return RoadNetwork(
            regionId = "delhi-ncr-demo",
            regionName = "Delhi NCR Demo",
            nodes = nodes,
            edges = directedEdges
        )
    }

    private fun node(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
        rank: Int
    ): RoadNode =
        RoadNode(
            id = id,
            name = name,
            position = GeoPosition(latitude, longitude),
            contractionRank = rank
        )

    private fun spec(
        from: Long,
        to: Long,
        roadName: String,
        roadClass: RoadClass,
        speedKph: Int
    ): EdgeSpec = EdgeSpec(from, to, roadName, roadClass, speedKph)

    private fun edge(
        id: Long,
        from: Long,
        to: Long,
        roadName: String,
        roadClass: RoadClass,
        speedKph: Int,
        nodes: Map<Long, RoadNode>
    ): RoadEdge {
        val fromPosition = nodes.getValue(from).position
        val toPosition = nodes.getValue(to).position
        return RoadEdge(
            id = id,
            fromNodeId = from,
            toNodeId = to,
            distanceMeters = calculateDistanceMeters(fromPosition, toPosition),
            roadName = roadName,
            roadClass = roadClass,
            speedKph = speedKph,
            geometry = shapedRoadGeometry(fromPosition, toPosition)
        )
    }
}

private data class EdgeSpec(
    val from: Long,
    val to: Long,
    val roadName: String,
    val roadClass: RoadClass,
    val speedKph: Int
)

private fun expandShortcut(edge: RoadEdge, network: RoadNetwork): List<RoadEdge> {
    if (!edge.isShortcut || edge.shortcutEdgeIds.isEmpty()) return listOf(edge)
    return edge.shortcutEdgeIds.mapNotNull { network.edges[it] }
}

private fun RoadNetwork.nearestNode(position: GeoPosition): RoadNode =
    nodes.values.minBy { calculateDistanceMeters(position, it.position) }

private fun List<GeoPosition>.bearing(): Double? =
    takeIf { it.size >= 2 }?.let { bearingDegrees(it.first(), it.last()) }

private fun GeoPosition.isNear(other: GeoPosition): Boolean =
    calculateDistanceMeters(this, other) < 8.0

private fun List<GeoPosition>.removeNearDuplicates(): List<GeoPosition> =
    fold(emptyList()) { acc, point ->
        if (acc.lastOrNull()?.isNear(point) == true) acc else acc + point
    }

private fun densify(points: List<GeoPosition>, steps: Int = 5): List<GeoPosition> =
    points.windowed(2).flatMapIndexed { index, pair ->
        val interpolated = (0..steps).map { step ->
            val t = step.toDouble() / steps.toDouble()
            GeoPosition(
                latitude = pair[0].latitude + (pair[1].latitude - pair[0].latitude) * t,
                longitude = pair[0].longitude + (pair[1].longitude - pair[0].longitude) * t
            )
        }
        if (index == 0) interpolated else interpolated.drop(1)
    }.ifEmpty { points }

private fun shapedRoadGeometry(from: GeoPosition, to: GeoPosition): List<GeoPosition> {
    val midLat = (from.latitude + to.latitude) / 2.0
    val midLon = (from.longitude + to.longitude) / 2.0
    val latOffset = (to.longitude - from.longitude) * 0.08
    val lonOffset = -(to.latitude - from.latitude) * 0.08
    return listOf(
        from,
        GeoPosition(midLat + latOffset, midLon + lonOffset),
        to
    )
}

private fun classifyTurn(previousBearing: Double, currentBearing: Double): TurnType {
    val delta = normalizeBearingDelta(currentBearing - previousBearing)
    return when {
        abs(delta) < 20.0 -> TurnType.STRAIGHT
        delta in 20.0..50.0 -> TurnType.SLIGHT_RIGHT
        delta in 50.0..135.0 -> TurnType.TURN_RIGHT
        delta > 135.0 -> TurnType.SHARP_RIGHT
        delta in -50.0..-20.0 -> TurnType.SLIGHT_LEFT
        delta in -135.0..-50.0 -> TurnType.TURN_LEFT
        delta < -135.0 -> TurnType.SHARP_LEFT
        else -> TurnType.U_TURN
    }
}

private fun normalizeBearingDelta(value: Double): Double {
    var delta = value
    while (delta > 180.0) delta -= 360.0
    while (delta < -180.0) delta += 360.0
    return delta
}

private fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun estimateDurationSeconds(distanceMeters: Double, roadClass: RoadClass): Long {
    val speedMetersPerSecond = roadClass.defaultSpeedKph * 1000.0 / 3600.0
    return (distanceMeters / speedMetersPerSecond).toLong().coerceAtLeast(1L)
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
    return EARTH_RADIUS_METERS * c
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
