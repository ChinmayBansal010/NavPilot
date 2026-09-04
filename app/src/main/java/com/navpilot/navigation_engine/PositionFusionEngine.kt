package com.navpilot.navigation_engine

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.OrientationState
import com.navpilot.domain.model.Velocity
import kotlin.math.max

interface PositionFusion {
    fun update(
        gnssPosition: GeoPosition?,
        gnssVelocity: Velocity,
        gnssAvailability: GnssAvailability,
        imuFrame: ImuFrame,
        orientation: OrientationState
    ): NavigationEstimate

    fun reset(position: GeoPosition?, velocity: Velocity)
}

class ExtendedKalmanPositionFusionEngine(
    private val deadReckoningEngine: DeadReckoningEngine = DeadReckoningEngine()
) : PositionFusion {
    private var fusedEstimate: NavigationEstimate? = null
    private var covariance = EkfCovariance()

    override fun update(
        gnssPosition: GeoPosition?,
        gnssVelocity: Velocity,
        gnssAvailability: GnssAvailability,
        imuFrame: ImuFrame,
        orientation: OrientationState
    ): NavigationEstimate {
        val prediction = deadReckoningEngine.update(
            frame = imuFrame,
            gnssAvailability = GnssAvailability.LOST,
            latestKnownPosition = fusedEstimate?.position ?: gnssPosition,
            latestKnownVelocity = fusedEstimate?.velocity ?: gnssVelocity,
            orientation = orientation
        )

        covariance = covariance.predict(imuFrame)

        val fused = if (gnssAvailability != GnssAvailability.LOST && gnssPosition != null) {
            covariance = covariance.update(gnssPosition.horizontalAccuracyMeters ?: 25f)
            blendPredictionWithGnss(prediction, gnssPosition, gnssVelocity, gnssAvailability)
        } else {
            prediction.copy(confidence = prediction.confidence.coerceIn(0.15f, 0.75f))
        }

        fusedEstimate = fused
        return fused
    }

    override fun reset(position: GeoPosition?, velocity: Velocity) {
        fusedEstimate = NavigationEstimate(
            position = position,
            velocity = velocity,
            headingDegrees = velocity.bearingDegrees,
            confidence = if (position != null) 0.8f else 0f
        )
        covariance = EkfCovariance()
    }

    private fun blendPredictionWithGnss(
        prediction: NavigationEstimate,
        gnssPosition: GeoPosition,
        gnssVelocity: Velocity,
        gnssAvailability: GnssAvailability
    ): NavigationEstimate {
        val gain = when (gnssAvailability) {
            GnssAvailability.AVAILABLE -> 0.78
            GnssAvailability.DEGRADED -> 0.45
            GnssAvailability.LOST -> 0.0
        }
        val predictedPosition = prediction.position ?: gnssPosition
        val latitude = predictedPosition.latitude * (1.0 - gain) + gnssPosition.latitude * gain
        val longitude = predictedPosition.longitude * (1.0 - gain) + gnssPosition.longitude * gain
        val speed = (prediction.velocity.speedMetersPerSecond * (1.0 - gain) +
            gnssVelocity.speedMetersPerSecond * gain).toFloat()
        val heading = gnssVelocity.bearingDegrees ?: prediction.headingDegrees

        return NavigationEstimate(
            position = gnssPosition.copy(
                latitude = latitude,
                longitude = longitude
            ),
            velocity = Velocity(
                speedMetersPerSecond = max(0f, speed),
                bearingDegrees = heading
            ),
            headingDegrees = heading,
            confidence = covariance.confidence(gnssAvailability),
            timestampMillis = System.currentTimeMillis()
        )
    }
}

private data class EkfCovariance(
    val positionVariance: Double = 30.0,
    val velocityVariance: Double = 8.0,
    val headingVariance: Double = 30.0
) {
    fun predict(frame: ImuFrame): EkfCovariance {
        val deltaScale = if (frame.timestampNanos > 0L) 1.08 else 1.15
        return copy(
            positionVariance = (positionVariance * deltaScale + 0.8).coerceAtMost(2_500.0),
            velocityVariance = (velocityVariance * 1.04 + 0.25).coerceAtMost(400.0),
            headingVariance = (headingVariance * 1.03 + 0.2).coerceAtMost(360.0)
        )
    }

    fun update(horizontalAccuracyMeters: Float): EkfCovariance {
        val measurementVariance = horizontalAccuracyMeters * horizontalAccuracyMeters
        val gain = positionVariance / (positionVariance + measurementVariance.coerceAtLeast(1f))
        return copy(
            positionVariance = positionVariance * (1.0 - gain),
            velocityVariance = velocityVariance * 0.7,
            headingVariance = headingVariance * 0.8
        )
    }

    fun confidence(gnssAvailability: GnssAvailability): Float {
        val covarianceScore = (1.0 / (1.0 + positionVariance / 50.0)).toFloat()
        val gnssScore = when (gnssAvailability) {
            GnssAvailability.AVAILABLE -> 0.95f
            GnssAvailability.DEGRADED -> 0.72f
            GnssAvailability.LOST -> 0.45f
        }
        return (covarianceScore * gnssScore).coerceIn(0.1f, 0.98f)
    }
}
