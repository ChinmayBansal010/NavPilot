package com.navpilot.data.repository

import com.navpilot.data.routing.HybridRoutingEngine
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.GnssSample
import com.navpilot.domain.model.GnssSatelliteInfo
import com.navpilot.domain.model.ImuSample
import com.navpilot.domain.model.Route
import com.navpilot.domain.model.SensorStatus
import com.navpilot.domain.routing.RoutingEngine
import com.navpilot.location.LocationRepository
import com.navpilot.sensors.SensorManagerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NavigationRepository(
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorManagerRepository,
    private val routingEngine: RoutingEngine = HybridRoutingEngine()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _imuSample = MutableStateFlow<ImuSample?>(null)
    val imuSample: StateFlow<ImuSample?> = _imuSample.asStateFlow()

    val gnssSample: StateFlow<GnssSample?> = locationRepository.latestGnssSample
    val satelliteInfo: StateFlow<GnssSatelliteInfo> = locationRepository.satelliteInfo
    val gnssAvailability: StateFlow<GnssAvailability> = locationRepository.gnssAvailability
    val sensorStatus: SensorStatus get() = sensorRepository.status

    private var started = false

    fun hasLocationPermission(): Boolean = locationRepository.hasPermission()

    fun start() {
        if (started) return
        started = true

        locationRepository.start()

        scope.launch {
            sensorRepository.imuSamples().collect { sample ->
                _imuSample.value = sample
            }
        }
    }

    suspend fun calculateRoute(
        origin: GeoPosition,
        destination: GeoPosition,
        destinationName: String
    ): Route = routingEngine.calculateRoute(origin, destination, destinationName)
}
