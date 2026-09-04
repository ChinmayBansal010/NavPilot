package com.navpilot.navigation_engine

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.MapMatchedPosition
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.NavigationMode
import com.navpilot.domain.model.NavigationState
import com.navpilot.domain.model.SensorStatus
import com.navpilot.domain.model.Velocity

class GnssFusionEngine {
    fun fuse(
        gnssAvailability: GnssAvailability,
        gnssPosition: GeoPosition?,
        gnssVelocity: Velocity,
        inertialEstimate: NavigationEstimate,
        mapMatchedPosition: MapMatchedPosition?,
        sensorStatus: SensorStatus,
        permissionGranted: Boolean
    ): NavigationState {
        val selectedPosition = when {
            mapMatchedPosition != null -> mapMatchedPosition.position
            gnssAvailability != GnssAvailability.LOST && gnssPosition != null -> gnssPosition
            else -> inertialEstimate.position
        }

        val selectedVelocity =
            if (gnssAvailability != GnssAvailability.LOST && gnssVelocity.speedMetersPerSecond > 0f) {
                gnssVelocity
            } else {
                inertialEstimate.velocity
            }

        val mode = when {
            !permissionGranted || selectedPosition == null -> NavigationMode.INITIALIZING
            gnssAvailability == GnssAvailability.AVAILABLE && sensorStatus.isImuReady -> NavigationMode.GNSS_INS
            gnssAvailability == GnssAvailability.AVAILABLE -> NavigationMode.GNSS
            gnssAvailability == GnssAvailability.DEGRADED -> NavigationMode.GNSS_INS
            else -> NavigationMode.DEAD_RECKONING
        }

        val confidence = when (mode) {
            NavigationMode.INITIALIZING -> 0f
            NavigationMode.GNSS -> 0.85f
            NavigationMode.GNSS_INS -> inertialEstimate.confidence.coerceAtLeast(0.7f)
            NavigationMode.DEAD_RECKONING -> inertialEstimate.confidence
        }

        return NavigationState(
            position = selectedPosition,
            speedMetersPerSecond = selectedVelocity.speedMetersPerSecond,
            headingDegrees = mapMatchedPosition?.headingDegrees
                ?: selectedVelocity.bearingDegrees
                ?: inertialEstimate.headingDegrees,
            accuracyMeters = selectedPosition?.horizontalAccuracyMeters,
            gnssStatus = gnssAvailability,
            navigationMode = mode,
            sensorStatus = sensorStatus,
            confidence = confidence,
            mapMatchConfidence = mapMatchedPosition?.confidence ?: 0f,
            permissionGranted = permissionGranted,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }
}
