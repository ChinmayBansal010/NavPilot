package com.navpilot.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navpilot.data.local.GnssDeniedDemoSource
import com.navpilot.data.remote.NominatimPlaceSearchDataSource
import com.navpilot.data.repository.NavigationRepository
import com.navpilot.data.repository.OsmOfflineMapRepository
import com.navpilot.domain.model.*
import com.navpilot.domain.repository.OfflineMapRepository
import com.navpilot.location.GnssLocationProvider
import com.navpilot.location.LocationRepository
import com.navpilot.navigation_engine.DeadReckoningEngine
import com.navpilot.navigation_engine.ExtendedKalmanPositionFusionEngine
import com.navpilot.navigation_engine.MapMatchingEngine
import com.navpilot.navigation_engine.VehicleAlignmentEngine
import com.navpilot.sensors.SensorManagerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.*

class NavigationViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val locationRepository = LocationRepository(
        GnssLocationProvider(application.applicationContext)
    )
    private val sensorRepository = SensorManagerRepository(application.applicationContext)
    private val navigationRepository = NavigationRepository(
        locationRepository = locationRepository,
        sensorRepository = sensorRepository
    )

    private val deadReckoningEngine = DeadReckoningEngine()
    private val vehicleAlignmentEngine = VehicleAlignmentEngine()
    private val positionFusionEngine = ExtendedKalmanPositionFusionEngine(deadReckoningEngine)
    private val mapMatchingEngine = MapMatchingEngine()
    private val demoSource = GnssDeniedDemoSource()
    private val searchDataSource = NominatimPlaceSearchDataSource()
    private val offlineMapRepository: OfflineMapRepository = OsmOfflineMapRepository(application.applicationContext)

    private var userInteractionJob: Job? = null
    private var demoJob: Job? = null
    private var rerouteJob: Job? = null
    private var searchJob: Job? = null
    private var lastRerouteStartedMillis = 0L
    private var latestImuFrame = ImuFrame()

    private val _state = MutableStateFlow(
        NavigationState(
            sensorStatus = navigationRepository.sensorStatus,
            permissionGranted = navigationRepository.hasLocationPermission()
        )
    )
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    init {
        observeRepository()
        observeOfflineMaps()
        if (navigationRepository.hasLocationPermission()) {
            navigationRepository.start()
        }
    }

    private fun observeOfflineMaps() {
        viewModelScope.launch {
            offlineMapRepository.getDownloadedRegions().collect { regions ->
                _state.update { it.copy(offlineMapRegions = regions) }
            }
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) {
            navigationRepository.start()
        }
    }

    fun setFollowingVehicle(following: Boolean) {
        _state.update { it.copy(isFollowingVehicle = following) }
        if (following) {
            userInteractionJob?.cancel()
        }
    }

    fun onUserMapInteraction() {
        _state.update { it.copy(isFollowingVehicle = false) }
        userInteractionJob?.cancel()
        userInteractionJob = viewModelScope.launch {
            delay(8000L)
            _state.update { it.copy(isFollowingVehicle = true) }
        }
    }

    fun recenterMap() {
        userInteractionJob?.cancel()
        _state.update { it.copy(isFollowingVehicle = true) }
    }

    fun startNavigation(destinationName: String, destinationPosition: GeoPosition) {
        val currentPos = _state.value.position ?: GeoPosition(28.6315, 77.2167)

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRouteLoading = true,
                    isRerouting = false,
                    routeErrorMessage = null
                )
            }
            val route = runCatching {
                withContext(Dispatchers.IO) {
                    navigationRepository.calculateRoute(currentPos, destinationPosition, destinationName)
                }
            }.getOrElse { error ->
                _state.update { currentState ->
                    currentState.copy(
                        isRouteLoading = false,
                        isRerouting = false,
                        routeErrorMessage = routeErrorMessage(error)
                    )
                }
                return@launch
            }
            val initialSegment = route.segments.firstOrNull()

            _state.update {
                it.copy(
                    isNavigating = true,
                    currentRoute = route,
                    currentSegmentIndex = 0,
                    currentTurnType = initialSegment?.turnType ?: TurnType.START,
                    destinationName = destinationName,
                    destinationPosition = destinationPosition,
                    routePoints = route.orderedCoordinates,
                    etaMinutes = (route.estimatedTravelTimeSeconds / 60L).toInt().coerceAtLeast(1),
                    distanceRemainingMeters = route.totalDistanceMeters.toFloat(),
                    upcomingTurn = initialSegment?.instruction ?: "Head out towards $destinationName",
                    turnDistanceMeters = initialSegment?.distanceMeters?.toFloat() ?: 200f,
                    isArrived = false,
                    isFollowingVehicle = true,
                    isRerouting = false,
                    isRouteLoading = false,
                    routeErrorMessage = null,
                    currentRoadName = initialSegment?.streetName,
                    routeDataSource = route.dataSource
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300L) // Debounce
            val results = searchDataSource.search(query)
            _state.update { currentState ->
                val enriched = results.map { res ->
                    val isOffline = currentState.offlineMapRegions.any { it.boundingBox.contains(res.position) }
                    res.copy(isDownloaded = isOffline)
                }
                currentState.copy(searchResults = enriched)
            }
        }
    }

    fun selectSearchResult(result: DestinationSearchResult) {
        _state.update {
            it.copy(
                selectedDestination = result,
                searchQuery = result.title,
                routeErrorMessage = null
            )
        }
    }

    fun startNavigationToSelected() {
        val selected = _state.value.selectedDestination ?: return
        startNavigation(selected.title, selected.position)
    }

    fun stopNavigation() {
        stopGnssDeniedDemo()
        _state.update {
            it.copy(
                isNavigating = false,
                currentRoute = null,
                currentSegmentIndex = 0,
                currentTurnType = TurnType.START,
                destinationName = null,
                destinationPosition = null,
                routePoints = emptyList(),
                isArrived = false,
                isRouteLoading = false,
                routeErrorMessage = null,
                routeDataSource = null
            )
        }
    }

    fun dismissArrival() {
        stopGnssDeniedDemo()
        _state.update {
            it.copy(
                isArrived = false,
                isNavigating = false,
                currentRoute = null,
                currentSegmentIndex = 0,
                currentTurnType = TurnType.START,
                destinationName = null,
                destinationPosition = null,
                routePoints = emptyList(),
                isRouteLoading = false,
                routeErrorMessage = null,
                routeDataSource = null
            )
        }
    }

    fun toggleDeveloperDiagnostics(show: Boolean) {
        _state.update { it.copy(showDeveloperDiagnostics = show) }
    }

    fun startGnssDeniedDemo() {
        if (_state.value.isDemoRunning) {
            stopGnssDeniedDemo()
            return
        }

        val route = _state.value.currentRoute
        if (route == null || route.orderedCoordinates.size < 2) {
            _state.update { it.copy(routeErrorMessage = "Start navigation before running the GNSS-denied demo.") }
            return
        }
        
        demoJob?.cancel()
        _state.update { it.copy(isDemoRunning = true, routeErrorMessage = null) }
        
        demoJob = viewModelScope.launch(Dispatchers.Default) {
            demoSource.frames(route.orderedCoordinates).forEach { frame ->
                val orientation = vehicleAlignmentEngine.update(frame.imuFrame)
                _state.update {
                    it.copy(
                        gnssStatus = frame.gnssAvailability,
                        navigationMode = if (frame.gnssAvailability == GnssAvailability.LOST) {
                            NavigationMode.DEAD_RECKONING
                        } else {
                            NavigationMode.GNSS_INS
                        }
                    )
                }

                if (frame.gnssSample != null) {
                    updateLocationState(frame.gnssSample)
                } else {
                    val estimate = positionFusionEngine.update(
                        gnssPosition = null,
                        gnssVelocity = Velocity(_state.value.speedMetersPerSecond, _state.value.headingDegrees),
                        gnssAvailability = GnssAvailability.LOST,
                        imuFrame = frame.imuFrame,
                        orientation = orientation
                    )
                    applyNavigationPosition(
                        position = estimate.position,
                        speed = estimate.velocity.speedMetersPerSecond,
                        heading = estimate.headingDegrees ?: _state.value.headingDegrees ?: 0f,
                        confidence = estimate.confidence
                    )
                }
                applyDemoReference(frame.referencePosition)
                delay(250L)
            }
            _state.update { it.copy(isDemoRunning = false) }
        }
    }

    fun stopGnssDeniedDemo() {
        demoJob?.cancel()
        demoJob = null
        _state.update { it.copy(isDemoRunning = false) }
    }

    private fun observeRepository() {
        viewModelScope.launch {
            navigationRepository.gnssSample.collect { sample ->
                if (!_state.value.isDemoRunning) {
                    sample?.let { updateLocationState(it) }
                }
            }
        }

        viewModelScope.launch {
            navigationRepository.gnssAvailability.collect { availability ->
                if (!_state.value.isDemoRunning) {
                    _state.update { currentState ->
                        val mode = when (availability) {
                            GnssAvailability.AVAILABLE -> NavigationMode.GNSS
                            GnssAvailability.DEGRADED -> NavigationMode.GNSS_INS
                            GnssAvailability.LOST -> NavigationMode.DEAD_RECKONING
                        }
                        currentState.copy(
                            gnssStatus = availability,
                            navigationMode = mode
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            navigationRepository.satelliteInfo.collect { info ->
                _state.update { it.copy(satelliteInfo = info) }
            }
        }

        viewModelScope.launch {
            navigationRepository.imuSample.collect { sample ->
                if (sample != null) {
                    val imuFrame = ImuFrame(
                        accelerometer = SensorSample(sample.accelerometerX, sample.accelerometerY, sample.accelerometerZ, sample.timestampNanos),
                        gyroscope = SensorSample(sample.gyroscopeX, sample.gyroscopeY, sample.gyroscopeZ, sample.timestampNanos),
                        magnetometer = SensorSample(sample.magnetometerX, sample.magnetometerY, sample.magnetometerZ, sample.timestampNanos),
                        timestampNanos = sample.timestampNanos
                    )
                    latestImuFrame = imuFrame

                    if (!_state.value.isDemoRunning) {
                        val orientation = vehicleAlignmentEngine.update(imuFrame)

                        if (_state.value.gnssStatus == GnssAvailability.LOST) {
                            val fusedEstimate = positionFusionEngine.update(
                                gnssPosition = null,
                                gnssVelocity = Velocity(_state.value.speedMetersPerSecond, _state.value.headingDegrees),
                                gnssAvailability = GnssAvailability.LOST,
                                imuFrame = imuFrame,
                                orientation = orientation
                            )

                            applyNavigationPosition(
                                position = fusedEstimate.position,
                                speed = fusedEstimate.velocity.speedMetersPerSecond,
                                heading = fusedEstimate.headingDegrees ?: _state.value.headingDegrees ?: 0f,
                                confidence = fusedEstimate.confidence
                            )
                        } else {
                            _state.update {
                                it.copy(
                                    sensorStatus = navigationRepository.sensorStatus,
                                    lastUpdatedMillis = System.currentTimeMillis()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateLocationState(sample: GnssSample) {
        val gnssPosition = GeoPosition(
            latitude = sample.latitude,
            longitude = sample.longitude,
            altitudeMeters = sample.altitudeMeters,
            horizontalAccuracyMeters = sample.accuracyMeters,
            timestampMillis = sample.timestampMillis
        )
        val orientation = vehicleAlignmentEngine.update(latestImuFrame)
        val fusedEstimate = positionFusionEngine.update(
            gnssPosition = gnssPosition,
            gnssVelocity = Velocity(sample.speedMetersPerSecond, sample.bearingDegrees),
            gnssAvailability = _state.value.gnssStatus,
            imuFrame = latestImuFrame,
            orientation = orientation
        )

        applyNavigationPosition(
            position = fusedEstimate.position ?: gnssPosition,
            speed = fusedEstimate.velocity.speedMetersPerSecond,
            heading = fusedEstimate.headingDegrees ?: sample.bearingDegrees ?: _state.value.headingDegrees ?: 0f,
            confidence = fusedEstimate.confidence.coerceAtLeast(0.65f)
        )
    }

    private fun applyNavigationPosition(
        position: GeoPosition?,
        speed: Float,
        heading: Float,
        confidence: Float
    ) {
        if (position == null) return
        val currentState = _state.value
        val activeRoute = currentState.currentRoute

        if (currentState.isNavigating && activeRoute != null && activeRoute.dataSource == RouteDataSource.ONLINE_OSM) {
            val projection = projectOntoRoute(position, activeRoute.orderedCoordinates)
            val correctedEstimate = projection
                ?.takeIf { it.distanceMeters <= ACTIVE_ROUTE_SNAP_LIMIT_METERS }
                ?.let {
                    positionFusionEngine.applyAiAndMapCorrection(
                        mapMatchedPosition = MapMatchedPosition(
                            position = it.position,
                            headingDegrees = it.headingDegrees,
                            confidence = routeProjectionConfidence(it.distanceMeters),
                            matchedRoadSegment = null
                        ),
                        route = activeRoute,
                        gnssAvailability = currentState.gnssStatus
                    )
                }
            val navigationPosition = if (projection != null && projection.distanceMeters <= ACTIVE_ROUTE_SNAP_LIMIT_METERS) {
                correctedEstimate?.position ?: projection.position
            } else {
                position
            }
            updateVehiclePositionOnRoute(
                position = navigationPosition,
                speed = speed,
                heading = correctedEstimate?.headingDegrees ?: projection?.headingDegrees ?: heading,
                confidence = correctedEstimate?.confidence ?: if (projection != null) confidence.coerceAtLeast(0.8f) else confidence,
                matchedRoadName = currentState.currentRoadName
            )
            return
        }

        val matched = mapMatchingEngine.match(
            estimatedPosition = position,
            headingDegrees = heading,
            speedMetersPerSecond = speed,
            roadCandidates = emptyList()
        )
        val correctedEstimate = positionFusionEngine.applyAiAndMapCorrection(
            mapMatchedPosition = matched,
            route = _state.value.currentRoute,
            gnssAvailability = _state.value.gnssStatus
        )
        val navigationPosition = correctedEstimate?.position ?: matched?.position ?: position
        updateVehiclePositionOnRoute(
            position = navigationPosition,
            speed = speed,
            heading = correctedEstimate?.headingDegrees ?: matched?.headingDegrees ?: heading,
            confidence = correctedEstimate?.confidence ?: matched?.confidence ?: confidence,
            matchedRoadName = matched?.matchedRoadSegment?.roadClass ?: _state.value.currentRoadName
        )
    }

    private fun updateVehiclePositionOnRoute(
        position: GeoPosition,
        speed: Float,
        heading: Float,
        confidence: Float,
        matchedRoadName: String? = null
    ) {
        _state.update { currentState ->
            val route = currentState.currentRoute
            if (currentState.isNavigating && route != null && route.segments.isNotEmpty()) {
                var segIdx = currentState.currentSegmentIndex.coerceIn(0, route.segments.lastIndex)
                var seg = route.segments[segIdx]
                val distanceToRoute = distanceToRouteMeters(position, route.orderedCoordinates)
                val shouldReroute = distanceToRoute > 90.0 && canStartReroute(currentState.isRerouting)
                if (shouldReroute && !currentState.isDemoRunning) {
                    requestReroute(position, currentState.destinationName, currentState.destinationPosition)
                }

                var distToSegEnd = distanceAlongRouteFromPosition(position, seg.coordinates).toFloat()

                if (distToSegEnd < 30.0 && segIdx < route.segments.lastIndex) {
                    segIdx += 1
                    seg = route.segments[segIdx]
                    distToSegEnd = distanceAlongRouteFromPosition(position, seg.coordinates).toFloat()
                }

                val remainingDist = route.segments
                    .drop(segIdx)
                    .sumOf { segment -> segment.distanceMeters }
                    .toFloat()
                    .coerceAtMost(calculateDistanceMeters(position, route.destinationPosition).toFloat() * 1.8f)
                val arrived = remainingDist < 25f
                val etaMin = (remainingDist / (speed.coerceAtLeast(8f) * 60f)).toInt().coerceAtLeast(1)
                val progress = if (route.totalDistanceMeters > 0.0) {
                    (1f - remainingDist / route.totalDistanceMeters.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                currentState.copy(
                    position = position,
                    speedMetersPerSecond = speed,
                    headingDegrees = heading,
                    accuracyMeters = position.horizontalAccuracyMeters,
                    currentSegmentIndex = segIdx,
                    currentTurnType = seg.turnType,
                    upcomingTurn = seg.instruction,
                    turnDistanceMeters = distToSegEnd.toFloat(),
                    distanceRemainingMeters = remainingDist,
                    etaMinutes = minOf(etaMin, 180),
                    confidence = confidence,
                    mapMatchConfidence = confidence,
                    currentRoadName = matchedRoadName ?: seg.streetName,
                    routeProgress = progress,
                    isNavigating = !arrived,
                    isArrived = arrived || currentState.isArrived,
                    positioningDiagnostics = positionFusionEngine.diagnostics(),
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            } else {
                currentState.copy(
                    position = position,
                    speedMetersPerSecond = speed,
                    headingDegrees = heading,
                    accuracyMeters = position.horizontalAccuracyMeters,
                    confidence = confidence,
                    currentRoadName = matchedRoadName,
                    positioningDiagnostics = positionFusionEngine.diagnostics(),
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
        }
    }

    private fun requestReroute(
        position: GeoPosition,
        destinationName: String?,
        destinationPosition: GeoPosition?
    ) {
        if (destinationName == null || destinationPosition == null) return
        if (rerouteJob?.isActive == true) return
        lastRerouteStartedMillis = System.currentTimeMillis()
        _state.update { it.copy(isRerouting = true, routeErrorMessage = null) }
        rerouteJob = viewModelScope.launch {
            val route = runCatching {
                withContext(Dispatchers.IO) {
                    navigationRepository.calculateRoute(position, destinationPosition, destinationName)
                }
            }.getOrElse { error ->
                _state.update { currentState ->
                    currentState.copy(
                        isRerouting = false,
                        routeErrorMessage = routeErrorMessage(error)
                    )
                }
                return@launch
            }
            
            // Wait for minimum display time to prevent flickering
            val elapsed = System.currentTimeMillis() - lastRerouteStartedMillis
            if (elapsed < REROUTE_MIN_DISPLAY_MILLIS) {
                delay(REROUTE_MIN_DISPLAY_MILLIS - elapsed)
            }

            val initialSegment = route.segments.firstOrNull()
            _state.update {
                it.copy(
                    currentRoute = route,
                    currentSegmentIndex = 0,
                    currentTurnType = initialSegment?.turnType ?: TurnType.START,
                    routePoints = route.orderedCoordinates,
                    upcomingTurn = initialSegment?.instruction ?: "Continue toward $destinationName",
                    turnDistanceMeters = initialSegment?.distanceMeters?.toFloat() ?: 0f,
                    distanceRemainingMeters = route.totalDistanceMeters.toFloat(),
                    etaMinutes = (route.estimatedTravelTimeSeconds / 60L).toInt().coerceAtLeast(1),
                    isRerouting = false,
                    routeProgress = 0f,
                    routeErrorMessage = null,
                    routeDataSource = route.dataSource
                )
            }
        }
    }

    private fun projectOntoRoute(position: GeoPosition, routePoints: List<GeoPosition>): ActiveRouteProjection? {
        if (routePoints.size < 2) return null

        var bestProjection: ActiveRouteProjection? = null
        routePoints.windowed(2).forEach { pair ->
            val projection = projectOntoSegment(position, pair[0], pair[1])
            if (bestProjection == null || projection.distanceMeters < bestProjection!!.distanceMeters) {
                bestProjection = projection
            }
        }

        return bestProjection
    }

    private fun projectOntoSegment(
        position: GeoPosition,
        start: GeoPosition,
        end: GeoPosition
    ): ActiveRouteProjection {
        val originLat = Math.toRadians(position.latitude)
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = (111_320.0 * cos(originLat)).coerceAtLeast(0.0001)
        val ax = (start.longitude - position.longitude) * metersPerDegreeLon
        val ay = (start.latitude - position.latitude) * metersPerDegreeLat
        val bx = (end.longitude - position.longitude) * metersPerDegreeLon
        val by = (end.latitude - position.latitude) * metersPerDegreeLat
        val abx = bx - ax
        val aby = by - ay
        val lengthSquared = abx * abx + aby * aby
        val t = if (lengthSquared == 0.0) {
            0.0
        } else {
            (-(ax * abx + ay * aby) / lengthSquared).coerceIn(0.0, 1.0)
        }
        val px = ax + abx * t
        val py = ay + aby * t

        return ActiveRouteProjection(
            position = GeoPosition(
                latitude = position.latitude + py / metersPerDegreeLat,
                longitude = position.longitude + px / metersPerDegreeLon,
                altitudeMeters = position.altitudeMeters,
                horizontalAccuracyMeters = position.horizontalAccuracyMeters,
                timestampMillis = position.timestampMillis
            ),
            headingDegrees = bearingDegrees(start, end).toFloat(),
            distanceMeters = hypot(px, py)
        )
    }

    private fun bearingDegrees(from: GeoPosition, to: GeoPosition): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun routeErrorMessage(error: Throwable): String {
        val detail = error.message?.takeIf { it.isNotBlank() } ?: "Unable to calculate an on-road route"
        return "Could not get an OSM road route. Check internet access and try again. $detail"
    }

    private fun routeProjectionConfidence(distanceMeters: Double): Float =
        (1f - (distanceMeters / ACTIVE_ROUTE_SNAP_LIMIT_METERS).toFloat()).coerceIn(0.35f, 0.9f)

    private fun canStartReroute(isRerouting: Boolean): Boolean {
        if (isRerouting || rerouteJob?.isActive == true) return false
        val sensitivity = if (_state.value.gnssStatus == GnssAvailability.LOST) 0.5f else 1.0f
        return System.currentTimeMillis() - lastRerouteStartedMillis > (REROUTE_COOLDOWN_MILLIS * sensitivity).toLong()
    }

    private fun applyDemoReference(referencePosition: GeoPosition) {
        _state.update { currentState ->
            val estimate = currentState.position
            val error = estimate?.let { calculateDistanceMeters(referencePosition, it).toFloat() }
            currentState.copy(
                positioningDiagnostics = currentState.positioningDiagnostics.copy(
                    referencePosition = referencePosition,
                    estimatedPosition = estimate,
                    positionErrorMeters = error,
                    driftDistanceMeters = error ?: currentState.positioningDiagnostics.driftDistanceMeters,
                    isWithinDemoTarget = (error ?: 0f) <= 10f
                )
            )
        }
    }

    private fun distanceAlongRouteFromPosition(position: GeoPosition, routePoints: List<GeoPosition>): Double {
        val nearestIndex = nearestRoutePointIndex(position, routePoints)
        if (nearestIndex == -1) return 0.0
        return routePoints
            .drop(nearestIndex)
            .windowed(2)
            .sumOf { calculateDistanceMeters(it[0], it[1]) }
    }

    private fun distanceToRouteMeters(position: GeoPosition, routePoints: List<GeoPosition>): Double {
        if (routePoints.isEmpty()) return Double.POSITIVE_INFINITY
        return routePoints.minOf { calculateDistanceMeters(position, it) }
    }

    private fun nearestRoutePointIndex(position: GeoPosition, routePoints: List<GeoPosition>): Int {
        if (routePoints.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        routePoints.forEachIndexed { index, routePoint ->
            val distance = calculateDistanceMeters(position, routePoint)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        return bestIndex
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

    private data class ActiveRouteProjection(
        val position: GeoPosition,
        val headingDegrees: Float,
        val distanceMeters: Double
    )

    private companion object {
        const val ACTIVE_ROUTE_SNAP_LIMIT_METERS = 45.0
        const val REROUTE_COOLDOWN_MILLIS = 15_000L
        const val REROUTE_MIN_DISPLAY_MILLIS = 1_500L
    }
}

class NavigationViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NavigationViewModel::class.java)) {
            return NavigationViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
