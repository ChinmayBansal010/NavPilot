package com.navpilot.navigation_engine

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.GnssAvailability
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.MapMatchedPosition
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.OrientationState
import com.navpilot.domain.model.PositionEstimateSource
import com.navpilot.domain.model.PositioningDiagnostics
import com.navpilot.domain.model.Route
import com.navpilot.domain.model.Velocity
import com.navpilot.ml.AIMotionHistorySample
import com.navpilot.ml.AIPositionCorrectionInput
import com.navpilot.ml.AIPositionCorrectionModel
import com.navpilot.ml.LocalDeterministicAIPositionCorrectionModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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
    private val deadReckoningEngine: DeadReckoningEngine = DeadReckoningEngine(),
    private val aiCorrectionModel: AIPositionCorrectionModel = LocalDeterministicAIPositionCorrectionModel()
) : PositionFusion {
    private var fusedEstimate: NavigationEstimate? = null
    private var covariance = EkfCovariance()
    private var lastReliableGnssPosition: GeoPosition? = null
    private var lastReliableGnssTimeMillis: Long? = null
    private var distanceSinceLastGnssMeters = 0f
    private var latestDiagnostics = PositioningDiagnostics()
    private val motionHistory = ArrayDeque<AIMotionHistorySample>()

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

        val deltaDistance = distanceMeters(fusedEstimate?.position, prediction.position).toFloat()
        if (gnssAvailability == GnssAvailability.LOST) {
            distanceSinceLastGnssMeters += deltaDistance.coerceAtLeast(0f)
        }

        covariance = covariance.predict(
            frame = imuFrame,
            gnssAvailability = gnssAvailability,
            secondsSinceLastGnss = secondsSinceLastGnss(),
            vehicleAcceleration = orientation.vehicleAccelerationY
        )
        covariance = maybeApplyZupt(covariance, prediction, orientation)

        val fused = if (gnssAvailability != GnssAvailability.LOST && gnssPosition != null) {
            lastReliableGnssPosition = gnssPosition
            lastReliableGnssTimeMillis = System.currentTimeMillis()
            distanceSinceLastGnssMeters = 0f
            covariance = covariance.updateGnss(gnssPosition.horizontalAccuracyMeters ?: 25f, gnssAvailability)
            blendPredictionWithGnss(prediction, gnssPosition, gnssVelocity, gnssAvailability)
        } else {
            prediction.copy(
                confidence = covariance.confidence(gnssAvailability),
                uncertaintyMeters = covariance.uncertaintyMeters(),
                source = PositionEstimateSource.IMU
            )
        }

        fusedEstimate = fused
        rememberMotion(imuFrame, fused, orientation)
        latestDiagnostics = diagnostics(
            gnssAvailability = gnssAvailability,
            estimate = fused,
            aiConfidence = 0f,
            mapConfidence = 0f
        )
        return fused
    }

    fun applyAiAndMapCorrection(
        mapMatchedPosition: MapMatchedPosition?,
        route: Route?,
        gnssAvailability: GnssAvailability = GnssAvailability.LOST
    ): NavigationEstimate? {
        val estimate = fusedEstimate ?: return null
        if (mapMatchedPosition == null || estimate.position == null) return estimate

        val aiOutput = if (gnssAvailability == GnssAvailability.LOST) aiCorrectionModel.correct(
            AIPositionCorrectionInput(
                motionHistory = motionHistory.toList(),
                previousPosition = estimate.position,
                previousUncertaintyMeters = estimate.uncertaintyMeters,
                secondsSinceLastGnss = secondsSinceLastGnss(),
                distanceSinceLastGnssMeters = distanceSinceLastGnssMeters,
                mapMatchedPosition = mapMatchedPosition.position,
                mapMatchingConfidence = mapMatchedPosition.confidence,
                route = route
            )
        ) else null

        val corrected = if (aiOutput != null && isReasonable(aiOutput.deltaEastMeters, aiOutput.deltaNorthMeters)) {
            covariance = covariance.updateAi(
                aiUncertaintyMeters = aiOutput.estimatedUncertaintyMeters,
                confidence = aiOutput.confidence
            )
            val position = estimate.position.moveMeters(
                eastMeters = aiOutput.deltaEastMeters * aiOutput.confidence,
                northMeters = aiOutput.deltaNorthMeters * aiOutput.confidence
            )
            estimate.copy(
                position = position,
                velocity = estimate.velocity.copy(
                    speedMetersPerSecond = max(
                        0f,
                        estimate.velocity.speedMetersPerSecond + aiOutput.velocityCorrectionMetersPerSecond * aiOutput.confidence
                    )
                ),
                headingDegrees = estimate.headingDegrees?.let {
                    normalizeDegrees(it + aiOutput.headingCorrectionDegrees * aiOutput.confidence)
                },
                confidence = covariance.confidence(GnssAvailability.LOST).coerceAtLeast(aiOutput.confidence),
                uncertaintyMeters = covariance.uncertaintyMeters(),
                source = PositionEstimateSource.AI_CORRECTED
            )
        } else {
            estimate
        }

        val mapGain = mapMatchedPosition.confidence.coerceIn(0f, 0.75f)
        val mapConstrained = corrected.position?.blend(mapMatchedPosition.position, mapGain)?.let { mapPosition ->
            covariance = covariance.updateMap(mapMatchedPosition.confidence)
            corrected.copy(
                position = mapPosition,
                headingDegrees = mapMatchedPosition.headingDegrees ?: corrected.headingDegrees,
                confidence = corrected.confidence.coerceAtLeast(mapMatchedPosition.confidence * 0.75f),
                uncertaintyMeters = covariance.uncertaintyMeters(),
                source = PositionEstimateSource.MAP_CONSTRAINED
            )
        } ?: corrected

        fusedEstimate = mapConstrained
        latestDiagnostics = diagnostics(
            gnssAvailability = gnssAvailability,
            estimate = mapConstrained,
            aiConfidence = aiOutput?.confidence ?: 0f,
            mapConfidence = mapMatchedPosition.confidence
        )
        return mapConstrained
    }

    fun diagnostics(): PositioningDiagnostics = latestDiagnostics

    override fun reset(position: GeoPosition?, velocity: Velocity) {
        fusedEstimate = NavigationEstimate(
            position = position,
            velocity = velocity,
            headingDegrees = velocity.bearingDegrees,
            confidence = if (position != null) 0.8f else 0f,
            uncertaintyMeters = if (position != null) 15f else 80f
        )
        covariance = EkfCovariance()
        lastReliableGnssPosition = position
        lastReliableGnssTimeMillis = System.currentTimeMillis()
        distanceSinceLastGnssMeters = 0f
        motionHistory.clear()
    }

    private fun blendPredictionWithGnss(
        prediction: NavigationEstimate,
        gnssPosition: GeoPosition,
        gnssVelocity: Velocity,
        gnssAvailability: GnssAvailability
    ): NavigationEstimate {
        val gain = when (gnssAvailability) {
            GnssAvailability.AVAILABLE -> covariance.positionGain(gnssPosition.horizontalAccuracyMeters ?: 12f)
            GnssAvailability.DEGRADED -> covariance.positionGain(gnssPosition.horizontalAccuracyMeters ?: 35f) * 0.65
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
            timestampMillis = System.currentTimeMillis(),
            uncertaintyMeters = covariance.uncertaintyMeters(),
            source = PositionEstimateSource.FUSED
        )
    }

    private fun rememberMotion(frame: ImuFrame, estimate: NavigationEstimate, orientation: OrientationState) {
        motionHistory.addLast(
            AIMotionHistorySample(
                imuFrame = frame,
                estimate = estimate,
                angularVelocityZ = frame.gyroscope?.z ?: 0f,
                accelerationMetersPerSecondSquared = orientation.vehicleAccelerationY,
                timestampMillis = estimate.timestampMillis
            )
        )
        while (motionHistory.size > 80) motionHistory.removeFirst()
    }

    private fun maybeApplyZupt(
        current: EkfCovariance,
        estimate: NavigationEstimate,
        orientation: OrientationState
    ): EkfCovariance {
        val stationary = estimate.velocity.speedMetersPerSecond < 0.45f &&
            abs(orientation.vehicleAccelerationY) < 0.25f &&
            abs(orientation.vehicleAccelerationX) < 0.35f
        return if (stationary) current.applyZupt() else current
    }

    private fun secondsSinceLastGnss(): Float {
        val last = lastReliableGnssTimeMillis ?: return 999f
        return ((System.currentTimeMillis() - last) / 1000f).coerceAtLeast(0f)
    }

    private fun diagnostics(
        gnssAvailability: GnssAvailability,
        estimate: NavigationEstimate,
        aiConfidence: Float,
        mapConfidence: Float
    ): PositioningDiagnostics {
        val reference = lastReliableGnssPosition
        val error = distanceMeters(reference, estimate.position).toFloat().takeIf { reference != null && estimate.position != null }
        return PositioningDiagnostics(
            isGnssDenied = gnssAvailability == GnssAvailability.LOST,
            deadReckoningActive = gnssAvailability == GnssAvailability.LOST,
            aiCorrectionActive = aiConfidence > 0f,
            uncertaintyMeters = estimate.uncertaintyMeters,
            driftDistanceMeters = error ?: 0f,
            distanceSinceLastGnssMeters = distanceSinceLastGnssMeters,
            secondsSinceLastGnss = secondsSinceLastGnss(),
            deadReckoningConfidence = estimate.confidence,
            aiCorrectionConfidence = aiConfidence,
            mapMatchingConfidence = mapConfidence,
            referencePosition = reference,
            estimatedPosition = estimate.position,
            positionErrorMeters = error,
            isWithinDemoTarget = (error ?: 0f) <= 10f
        )
    }

    private fun isReasonable(eastMeters: Float, northMeters: Float): Boolean =
        hypot(eastMeters.toDouble(), northMeters.toDouble()) <= 12.0
}

private data class EkfCovariance(
    val positionVariance: Double = 30.0,
    val velocityVariance: Double = 8.0,
    val headingVariance: Double = 30.0
) {
    fun predict(
        frame: ImuFrame,
        gnssAvailability: GnssAvailability,
        secondsSinceLastGnss: Float,
        vehicleAcceleration: Float
    ): EkfCovariance {
        val dynamicScale = 1.0 + abs(vehicleAcceleration) * 0.015
        val gnssDeniedScale = if (gnssAvailability == GnssAvailability.LOST) {
            1.07 + (secondsSinceLastGnss / 120f).coerceAtMost(0.18f)
        } else {
            1.02
        }
        val deltaScale = if (frame.timestampNanos > 0L) gnssDeniedScale * dynamicScale else 1.15
        return copy(
            positionVariance = (positionVariance * deltaScale + 0.8).coerceAtMost(2_500.0),
            velocityVariance = (velocityVariance * 1.04 + 0.25).coerceAtMost(400.0),
            headingVariance = (headingVariance * 1.03 + 0.2).coerceAtMost(360.0)
        )
    }

    fun updateGnss(horizontalAccuracyMeters: Float, gnssAvailability: GnssAvailability): EkfCovariance {
        val measurementVariance = horizontalAccuracyMeters.toDouble() * horizontalAccuracyMeters.toDouble()
        val qualityMultiplier = if (gnssAvailability == GnssAvailability.AVAILABLE) 1.0 else 1.8
        val gain = positionVariance / (positionVariance + measurementVariance.coerceAtLeast(1.0) * qualityMultiplier)
        return copy(
            positionVariance = positionVariance * (1.0 - gain),
            velocityVariance = velocityVariance * 0.7,
            headingVariance = headingVariance * 0.8
        )
    }

    fun updateAi(aiUncertaintyMeters: Float, confidence: Float): EkfCovariance {
        val measurementVariance = (aiUncertaintyMeters.toDouble() * aiUncertaintyMeters.toDouble()) /
            confidence.coerceAtLeast(0.1f).toDouble()
        val gain = positionVariance / (positionVariance + measurementVariance)
        return copy(
            positionVariance = positionVariance * (1.0 - gain * confidence),
            velocityVariance = velocityVariance * (1.0 - 0.18 * confidence),
            headingVariance = headingVariance * (1.0 - 0.12 * confidence)
        )
    }

    fun updateMap(confidence: Float): EkfCovariance {
        val gain = confidence.coerceIn(0f, 0.75f).toDouble()
        return copy(
            positionVariance = positionVariance * (1.0 - gain * 0.45),
            headingVariance = headingVariance * (1.0 - gain * 0.35)
        )
    }

    fun applyZupt(): EkfCovariance =
        copy(
            velocityVariance = velocityVariance * 0.35,
            positionVariance = positionVariance * 0.92
        )

    fun positionGain(horizontalAccuracyMeters: Float): Double {
        val measurementVariance = horizontalAccuracyMeters.toDouble() * horizontalAccuracyMeters.toDouble()
        return (positionVariance / (positionVariance + measurementVariance.coerceAtLeast(1.0))).coerceIn(0.12, 0.82)
    }

    fun uncertaintyMeters(): Float = sqrt(positionVariance).toFloat().coerceIn(3f, 80f)

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

private fun GeoPosition.blend(target: GeoPosition, gain: Float): GeoPosition =
    gain.toDouble().let { weight ->
        copy(
            latitude = latitude * (1.0 - weight) + target.latitude * weight,
            longitude = longitude * (1.0 - weight) + target.longitude * weight,
            horizontalAccuracyMeters = horizontalAccuracyMeters
        )
    }

private fun GeoPosition.moveMeters(eastMeters: Float, northMeters: Float): GeoPosition {
    val dLat = northMeters / 111_320.0
    val dLon = eastMeters / (111_320.0 * cos(Math.toRadians(latitude))).coerceAtLeast(0.0001)
    return copy(
        latitude = latitude + dLat,
        longitude = longitude + dLon,
        timestampMillis = System.currentTimeMillis()
    )
}

private fun normalizeDegrees(value: Float): Float =
    ((value % 360f) + 360f) % 360f

private fun distanceMeters(a: GeoPosition?, b: GeoPosition?): Double {
    if (a == null || b == null) return 0.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2.0) * sin(dLat / 2.0) +
        cos(lat1) * cos(lat2) *
        sin(dLon / 2.0) * sin(dLon / 2.0)
    return 6_371_000.0 * 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
}
