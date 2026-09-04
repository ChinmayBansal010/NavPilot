package com.navpilot.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.navpilot.data.repository.NavigationRepository
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.GnssSample
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.NavigationMode
import com.navpilot.domain.model.NavigationState
import com.navpilot.domain.model.SensorSample
import com.navpilot.domain.model.TurnType
import com.navpilot.domain.model.Velocity
import com.navpilot.location.GnssLocationProvider
import com.navpilot.location.LocationRepository
import com.navpilot.navigation_engine.DeadReckoningEngine
import com.navpilot.navigation_engine.ExtendedKalmanPositionFusionEngine
import com.navpilot.navigation_engine.MapMatchingEngine
import com.navpilot.navigation_engine.VehicleAlignmentEngine
import com.navpilot.sensors.SensorManagerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    private var userInteractionJob: Job? = null
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
        if (navigationRepository.hasLocationPermission()) {
            navigationRepository.start()
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
            val route = navigationRepository.calculateRoute(currentPos, destinationPosition, destinationName)
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
                    isFollowingVehicle = true
                )
            }
        }
    }

    fun stopNavigation() {
        _state.update {
            it.copy(
                isNavigating = false,
                currentRoute = null,
                currentSegmentIndex = 0,
                currentTurnType = TurnType.START,
                destinationName = null,
                destinationPosition = null,
                routePoints = emptyList(),
                isArrived = false
            )
        }
    }

    fun dismissArrival() {
        _state.update {
            it.copy(
                isArrived = false,
                isNavigating = false,
                currentRoute = null,
                currentSegmentIndex = 0,
                currentTurnType = TurnType.START,
                destinationName = null,
                destinationPosition = null,
                routePoints = emptyList()
            )
        }
    }

    fun toggleDeveloperDiagnostics(show: Boolean) {
        _state.update { it.copy(showDeveloperDiagnostics = show) }
    }

    private fun observeRepository() {
        viewModelScope.launch {
            navigationRepository.gnssSample.collect { sample ->
                sample?.let { updateLocationState(it) }
            }
        }

        viewModelScope.launch {
            navigationRepository.gnssAvailability.collect { availability ->
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
        val matched = mapMatchingEngine.match(
            estimatedPosition = position,
            headingDegrees = heading,
            speedMetersPerSecond = speed,
            roadCandidates = emptyList()
        )
        val navigationPosition = matched?.position ?: position
        updateVehiclePositionOnRoute(
            position = navigationPosition,
            speed = speed,
            heading = matched?.headingDegrees ?: heading,
            confidence = matched?.confidence ?: confidence,
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
                val shouldReroute = distanceToRoute > 90.0 && !currentState.isRerouting
                if (shouldReroute) {
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
                    etaMinutes = etaMin,
                    confidence = confidence,
                    mapMatchConfidence = confidence,
                    currentRoadName = matchedRoadName ?: seg.streetName,
                    routeProgress = progress,
                    isRerouting = shouldReroute,
                    isNavigating = !arrived,
                    isArrived = arrived || currentState.isArrived,
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
        viewModelScope.launch {
            val route = navigationRepository.calculateRoute(position, destinationPosition, destinationName)
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
                    routeProgress = 0f
                )
            }
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
