package com.navpilot.domain.model

enum class NavigationMode(val label: String) {
    INITIALIZING("Initializing"),
    GNSS("GNSS"),
    GNSS_INS("GNSS + INS"),
    DEAD_RECKONING("Dead Reckoning")
}

enum class GnssAvailability {
    AVAILABLE,
    DEGRADED,
    LOST
}

enum class MotionState(val label: String) {
    STATIONARY("Stationary"),
    NORMAL_DRIVING("Normal driving"),
    ACCELERATION("Acceleration"),
    BRAKING("Braking"),
    TURNING("Turning"),
    HIGH_FREQUENCY_VIBRATION("Vibration")
}

data class ImuSample(
    val timestampNanos: Long = System.nanoTime(),
    val accelerometerX: Float = 0f,
    val accelerometerY: Float = 0f,
    val accelerometerZ: Float = 0f,
    val gyroscopeX: Float = 0f,
    val gyroscopeY: Float = 0f,
    val gyroscopeZ: Float = 0f,
    val magnetometerX: Float = 0f,
    val magnetometerY: Float = 0f,
    val magnetometerZ: Float = 0f,
    val accelerometerAccuracy: Int = 0,
    val gyroscopeAccuracy: Int = 0,
    val magnetometerAccuracy: Int = 0
)

data class GnssSample(
    val timestampMillis: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Float = 0f,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val provider: String = ""
)

data class GeoPosition(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val horizontalAccuracyMeters: Float? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class Velocity(
    val speedMetersPerSecond: Float,
    val bearingDegrees: Float? = null
)

data class GnssSatelliteInfo(
    val visibleSatellites: Int = 0,
    val usedInFix: Int = 0
)

data class SensorSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long
)

data class ImuFrame(
    val accelerometer: SensorSample? = null,
    val gyroscope: SensorSample? = null,
    val magnetometer: SensorSample? = null,
    val timestampNanos: Long = 0L
)

data class SensorStatus(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasMagnetometer: Boolean = false,
    val lastUpdateMillis: Long? = null
) {
    val isImuReady: Boolean
        get() = hasAccelerometer && hasGyroscope && hasMagnetometer
}

data class OrientationState(
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val yawDegrees: Float = 0f,
    val vehicleAccelerationX: Float = 0f,
    val vehicleAccelerationY: Float = 0f,
    val vehicleAccelerationZ: Float = 0f,
    val confidence: Float = 0f
)

data class NavigationEstimate(
    val position: GeoPosition?,
    val velocity: Velocity = Velocity(0f),
    val headingDegrees: Float? = null,
    val confidence: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class RoadCandidate(
    val id: String,
    val start: GeoPosition,
    val end: GeoPosition,
    val roadClass: String? = null
)

data class MapMatchedPosition(
    val position: GeoPosition,
    val headingDegrees: Float?,
    val confidence: Float,
    val matchedRoadSegment: RoadCandidate? = null
)

data class NavigationState(
    val position: GeoPosition? = null,
    val speedMetersPerSecond: Float = 0f,
    val headingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val gnssStatus: GnssAvailability = GnssAvailability.LOST,
    val satelliteInfo: GnssSatelliteInfo = GnssSatelliteInfo(),
    val navigationMode: NavigationMode = NavigationMode.INITIALIZING,
    val sensorStatus: SensorStatus = SensorStatus(),
    val motionState: MotionState = MotionState.STATIONARY,
    val confidence: Float = 0f,
    val mapMatchConfidence: Float = 0f,
    val isFollowingVehicle: Boolean = true,
    val permissionGranted: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    // Consumer navigation fields
    val isNavigating: Boolean = false,
    val currentRoute: Route? = null,
    val currentSegmentIndex: Int = 0,
    val currentTurnType: TurnType = TurnType.START,
    val destinationName: String? = null,
    val destinationPosition: GeoPosition? = null,
    val routePoints: List<GeoPosition> = emptyList(),
    val etaMinutes: Int = 0,
    val distanceRemainingMeters: Float = 0f,
    val upcomingTurn: String = "Head north",
    val turnDistanceMeters: Float = 0f,
    val isArrived: Boolean = false,
    val showDeveloperDiagnostics: Boolean = false
)

data class SavedPlace(val label: String, val detail: String, val time: String)
data class Trip(val destination: String, val date: String, val time: String, val distance: String, val duration: String)
