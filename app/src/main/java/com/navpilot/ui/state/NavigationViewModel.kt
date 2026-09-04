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

    private var userInteractionJob: Job? = null

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
        val currentPos = _state.value.position ?: GeoPosition(12.9716, 77.5946)

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

                    val orientation = vehicleAlignmentEngine.update(imuFrame)

                    if (_state.value.gnssStatus == GnssAvailability.LOST) {
                        val deadReckoningEstimate = deadReckoningEngine.update(
                            frame = imuFrame,
                            gnssAvailability = GnssAvailability.LOST,
                            latestKnownPosition = _state.value.position,
                            latestKnownVelocity = Velocity(_state.value.speedMetersPerSecond, _state.value.headingDegrees),
                            orientation = orientation
                        )

                        deadReckoningEstimate.position?.let { pos ->
                            updateVehiclePositionOnRoute(
                                position = pos,
                                speed = deadReckoningEstimate.velocity.speedMetersPerSecond,
                                heading = deadReckoningEstimate.headingDegrees ?: 0f,
                                confidence = deadReckoningEstimate.confidence
                            )
                        }
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
        val newPos = GeoPosition(
            latitude = sample.latitude,
            longitude = sample.longitude,
            altitudeMeters = sample.altitudeMeters,
            horizontalAccuracyMeters = sample.accuracyMeters,
            timestampMillis = sample.timestampMillis
        )
        updateVehiclePositionOnRoute(newPos, sample.speedMetersPerSecond, sample.bearingDegrees ?: _state.value.headingDegrees ?: 0f, 0.9f)
    }

    private fun updateVehiclePositionOnRoute(
        position: GeoPosition,
        speed: Float,
        heading: Float,
        confidence: Float
    ) {
        _state.update { currentState ->
            val route = currentState.currentRoute
            if (currentState.isNavigating && route != null && route.segments.isNotEmpty()) {
                var segIdx = currentState.currentSegmentIndex
                var seg = route.segments[segIdx]
                var distToSegEnd = calculateDistanceMeters(position, seg.coordinates.lastOrNull() ?: route.destinationPosition)

                if (distToSegEnd < 30.0 && segIdx < route.segments.lastIndex) {
                    segIdx += 1
                    seg = route.segments[segIdx]
                    distToSegEnd = calculateDistanceMeters(position, seg.coordinates.lastOrNull() ?: route.destinationPosition)
                }

                val remainingDist = calculateDistanceMeters(position, route.destinationPosition).toFloat()
                val arrived = remainingDist < 25f
                val etaMin = (remainingDist / 500.0).toInt().coerceAtLeast(1)

                currentState.copy(
                    position = position,
                    speedMetersPerSecond = speed,
                    headingDegrees = heading,
                    currentSegmentIndex = segIdx,
                    currentTurnType = seg.turnType,
                    upcomingTurn = seg.instruction,
                    turnDistanceMeters = distToSegEnd.toFloat(),
                    distanceRemainingMeters = remainingDist,
                    etaMinutes = etaMin,
                    confidence = confidence,
                    isNavigating = !arrived,
                    isArrived = arrived || currentState.isArrived,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            } else {
                currentState.copy(
                    position = position,
                    speedMetersPerSecond = speed,
                    headingDegrees = heading,
                    lastUpdatedMillis = System.currentTimeMillis()
                )
            }
        }
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
