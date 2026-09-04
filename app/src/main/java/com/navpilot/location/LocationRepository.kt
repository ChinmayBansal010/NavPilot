package com.navpilot.location

import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.GnssSample
import com.navpilot.domain.model.GnssSatelliteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class LocationRepository(
    private val provider: GnssLocationProvider,
    private val staleAfterMillis: Long = 5_000L
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _latestGnssSample = MutableStateFlow<GnssSample?>(null)
    private val _satelliteInfo = MutableStateFlow(GnssSatelliteInfo())
    private val _gnssAvailability = MutableStateFlow(GnssAvailability.LOST)
    private var started = false

    val latestGnssSample: StateFlow<GnssSample?> = _latestGnssSample.asStateFlow()
    val satelliteInfo: StateFlow<GnssSatelliteInfo> = _satelliteInfo.asStateFlow()
    val gnssAvailability: StateFlow<GnssAvailability> = _gnssAvailability.asStateFlow()

    fun hasPermission(): Boolean = runCatching { provider.hasLocationPermission() }.getOrDefault(false)

    fun start() {
        if (started) {
            return
        }

        if (!hasPermission()) {
            _gnssAvailability.value = GnssAvailability.LOST
            return
        }

        started = true

        scope.launch {
            try {
                provider.locationUpdates()
                    .catch { _gnssAvailability.value = GnssAvailability.LOST }
                    .collect { sample ->
                        _latestGnssSample.value = sample
                        _gnssAvailability.value = classify(sample, _satelliteInfo.value)
                    }
            } catch (_: Exception) {
                _gnssAvailability.value = GnssAvailability.LOST
            }
        }

        scope.launch {
            try {
                provider.satelliteStatus()
                    .catch { }
                    .collect { info ->
                        _satelliteInfo.value = info
                        _gnssAvailability.value = classify(_latestGnssSample.value, info)
                    }
            } catch (_: Exception) {
            }
        }

        scope.launch {
            while (true) {
                delay(1_000L.milliseconds)
                _gnssAvailability.value = classify(_latestGnssSample.value, _satelliteInfo.value)
            }
        }
    }

    private fun classify(
        sample: GnssSample?,
        satellites: GnssSatelliteInfo
    ): GnssAvailability {
        val currentSample = sample ?: return GnssAvailability.LOST
        val ageMillis = System.currentTimeMillis() - currentSample.timestampMillis

        if (ageMillis > staleAfterMillis) {
            return GnssAvailability.LOST
        }

        val accuracy = currentSample.accuracyMeters ?: Float.MAX_VALUE
        return when {
            accuracy <= 12f && satellites.usedInFix >= 4 -> GnssAvailability.AVAILABLE
            accuracy <= 50f || satellites.visibleSatellites > 0 -> GnssAvailability.DEGRADED
            else -> GnssAvailability.LOST
        }
    }
}
