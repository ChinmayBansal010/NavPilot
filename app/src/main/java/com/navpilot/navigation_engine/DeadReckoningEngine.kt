package com.navpilot.navigation_engine

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.OrientationState
import com.navpilot.domain.model.PositionEstimateSource
import com.navpilot.domain.model.Velocity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class DeadReckoningEngine {
    private var lastEstimate: NavigationEstimate? = null
    private var lastTimestampNanos: Long? = null
    private var longitudinalAccelerationBias = 0f
    private var yawRateBias = 0f

    fun update(
        frame: ImuFrame,
        gnssAvailability: GnssAvailability,
        latestKnownPosition: GeoPosition?,
        latestKnownVelocity: Velocity,
        orientation: OrientationState
    ): NavigationEstimate {
        if (latestKnownPosition == null) {
            return NavigationEstimate(
                position = null,
                velocity = latestKnownVelocity,
                headingDegrees = latestKnownVelocity.bearingDegrees ?: orientation.yawDegrees,
                confidence = 0f
            )
        }

        if (gnssAvailability != GnssAvailability.LOST) {
            updateBias(latestKnownVelocity, orientation, frame)
            val assisted = NavigationEstimate(
                position = latestKnownPosition,
                velocity = latestKnownVelocity,
                headingDegrees = latestKnownVelocity.bearingDegrees ?: orientation.yawDegrees,
                confidence = if (gnssAvailability == GnssAvailability.AVAILABLE) 0.95f else 0.7f,
                timestampMillis = latestKnownPosition.timestampMillis,
                uncertaintyMeters = latestKnownPosition.horizontalAccuracyMeters ?: 18f
            )
            lastEstimate = assisted
            lastTimestampNanos = frame.timestampNanos.takeIf { it > 0L }
            return assisted
        }

        val previous = lastEstimate ?: NavigationEstimate(
            position = latestKnownPosition,
            velocity = latestKnownVelocity,
            headingDegrees = latestKnownVelocity.bearingDegrees ?: orientation.yawDegrees,
            confidence = 0.65f,
            timestampMillis = latestKnownPosition.timestampMillis
        )

        val deltaSeconds = calculateDeltaSeconds(frame.timestampNanos)
        val accelerationMetersPerSecond = (orientation.vehicleAccelerationY - longitudinalAccelerationBias)
            .coerceIn(-4f, 4f)
        val previousSpeed = previous.velocity.speedMetersPerSecond
        val stationary = previousSpeed < 0.45f && abs(accelerationMetersPerSecond) < 0.18f
        val speed = if (stationary) {
            0f
        } else {
            max(0f, previousSpeed + accelerationMetersPerSecond * deltaSeconds)
        }
        val yawDelta = ((frame.gyroscope?.z ?: 0f) - yawRateBias) * deltaSeconds * 180f / PI.toFloat()
        val heading = latestKnownVelocity.bearingDegrees
            ?: previous.headingDegrees?.let { normalizeDegrees(it + yawDelta) }
            ?: orientation.yawDegrees
        val distanceMeters = ((previousSpeed + speed) / 2f) * deltaSeconds
        val moved = previous.position?.move(distanceMeters, heading)

        val estimate = NavigationEstimate(
            position = moved,
            velocity = Velocity(speedMetersPerSecond = speed, bearingDegrees = heading),
            headingDegrees = heading,
            confidence = (previous.confidence - deltaSeconds * 0.015f).coerceIn(0.15f, 0.75f),
            timestampMillis = System.currentTimeMillis(),
            uncertaintyMeters = ((previous.uncertaintyMeters + distanceMeters * 0.08f + deltaSeconds * 0.6f)
                .coerceIn(4f, 90f)),
            source = PositionEstimateSource.IMU
        )
        lastEstimate = estimate
        return estimate
    }

    private fun updateBias(velocity: Velocity, orientation: OrientationState, frame: ImuFrame) {
        if (velocity.speedMetersPerSecond < 0.5f) {
            longitudinalAccelerationBias = longitudinalAccelerationBias * 0.985f +
                orientation.vehicleAccelerationY * 0.015f
            frame.gyroscope?.let {
                yawRateBias = yawRateBias * 0.985f + it.z * 0.015f
            }
        }
    }

    private fun calculateDeltaSeconds(timestampNanos: Long): Float {
        val previous = lastTimestampNanos
        lastTimestampNanos = timestampNanos.takeIf { it > 0L } ?: lastTimestampNanos
        if (previous == null || timestampNanos <= 0L) {
            return 0.02f
        }
        return ((timestampNanos - previous) / 1_000_000_000f).coerceIn(0.001f, 1f)
    }
}

private fun normalizeDegrees(value: Float): Float =
    ((value % 360f) + 360f) % 360f

private fun GeoPosition.move(distanceMeters: Float, bearingDegrees: Float): GeoPosition {
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS
    val bearing = bearingDegrees.toRadians()
    val lat1 = latitude.toRadians()
    val lon1 = longitude.toRadians()

    val lat2 = asin(
        sin(lat1) * cos(angularDistance) +
            cos(lat1) * sin(angularDistance) * cos(bearing)
    )
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angularDistance) * cos(lat1),
        cos(angularDistance) - sin(lat1) * sin(lat2)
    )

    return copy(
        latitude = lat2.toDegrees(),
        longitude = lon2.toDegrees(),
        horizontalAccuracyMeters = horizontalAccuracyMeters?.plus(distanceMeters * 0.08f),
        timestampMillis = System.currentTimeMillis()
    )
}

private fun Float.toRadians(): Double = this.toDouble() * PI / 180.0
private fun Double.toRadians(): Double = this * PI / 180.0
private fun Double.toDegrees(): Double = this * 180.0 / PI

private const val EARTH_RADIUS_METERS = 6_371_000.0
