package com.navpilot.data.local

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.GnssSample
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.SensorSample
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class GnssDeniedDemoFrame(
    val referencePosition: GeoPosition,
    val gnssSample: GnssSample?,
    val gnssAvailability: GnssAvailability,
    val imuFrame: ImuFrame,
    val timestampMillis: Long
)

class GnssDeniedDemoSource {
    fun frames(routePoints: List<GeoPosition>): List<GnssDeniedDemoFrame> {
        if (routePoints.size < 2) return emptyList()
        val denseRoute = densify(routePoints)
        var accelerometerBias = 0.04f
        var gyroBias = 0.003f

        return denseRoute.mapIndexed { index, position ->
            accelerometerBias += sin(index / 18.0).toFloat() * 0.0006f
            gyroBias += cos(index / 21.0).toFloat() * 0.00005f
            val phase = index.toFloat() / denseRoute.lastIndex.toFloat()
            val gnssAvailability = when {
                phase < 0.24f -> GnssAvailability.AVAILABLE
                phase < 0.78f -> GnssAvailability.LOST
                else -> GnssAvailability.AVAILABLE
            }
            val timestampMillis = System.currentTimeMillis() + index * 250L
            val yawRate = bearingDelta(
                denseRoute.getOrNull(index - 1) ?: position,
                position,
                denseRoute.getOrNull(index + 1) ?: position
            ) / 0.25f
            val longitudinalAcceleration = 0.18f * sin(index / 6.0).toFloat() + accelerometerBias
            GnssDeniedDemoFrame(
                referencePosition = position.copy(timestampMillis = timestampMillis),
                gnssSample = if (gnssAvailability == GnssAvailability.AVAILABLE) {
                    GnssSample(
                        timestampMillis = timestampMillis,
                        latitude = position.latitude,
                        longitude = position.longitude,
                        speedMetersPerSecond = 9.5f,
                        bearingDegrees = bearing(position, denseRoute.getOrNull(index + 1) ?: position),
                        accuracyMeters = 5f,
                        provider = "navpilot-demo"
                    )
                } else {
                    null
                },
                gnssAvailability = gnssAvailability,
                imuFrame = ImuFrame(
                    accelerometer = SensorSample(
                        x = 0.02f * sin(index.toDouble()).toFloat(),
                        y = longitudinalAcceleration,
                        z = 9.80665f + 0.08f * cos(index / 3.0).toFloat(),
                        timestampNanos = timestampMillis * 1_000_000L
                    ),
                    gyroscope = SensorSample(
                        x = 0f,
                        y = 0f,
                        z = yawRate + gyroBias,
                        timestampNanos = timestampMillis * 1_000_000L
                    ),
                    magnetometer = SensorSample(
                        x = 18f,
                        y = 4f,
                        z = -38f,
                        timestampNanos = timestampMillis * 1_000_000L
                    ),
                    timestampNanos = timestampMillis * 1_000_000L
                ),
                timestampMillis = timestampMillis
            )
        }
    }

    private fun densify(points: List<GeoPosition>): List<GeoPosition> =
        points.windowed(2).flatMapIndexed { index, pair ->
            val samples = (0..8).map { step ->
                val t = step / 8.0
                GeoPosition(
                    latitude = pair[0].latitude + (pair[1].latitude - pair[0].latitude) * t,
                    longitude = pair[0].longitude + (pair[1].longitude - pair[0].longitude) * t
                )
            }
            if (index == 0) samples else samples.drop(1)
        }

    private fun bearing(from: GeoPosition, to: GeoPosition): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    private fun bearingDelta(previous: GeoPosition, current: GeoPosition, next: GeoPosition): Float {
        val before = bearing(previous, current)
        val after = bearing(current, next)
        var delta = after - before
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        return delta * PI.toFloat() / 180f
    }
}
