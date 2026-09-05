package com.navpilot.ml

import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.Route

data class AIMotionHistorySample(
    val imuFrame: ImuFrame,
    val estimate: NavigationEstimate,
    val angularVelocityZ: Float,
    val accelerationMetersPerSecondSquared: Float,
    val timestampMillis: Long
)

data class AIPositionCorrectionInput(
    val motionHistory: List<AIMotionHistorySample>,
    val previousPosition: GeoPosition?,
    val previousUncertaintyMeters: Float,
    val secondsSinceLastGnss: Float,
    val distanceSinceLastGnssMeters: Float,
    val mapMatchedPosition: GeoPosition?,
    val mapMatchingConfidence: Float,
    val route: Route?
)

data class AIPositionCorrectionOutput(
    val deltaEastMeters: Float,
    val deltaNorthMeters: Float,
    val velocityCorrectionMetersPerSecond: Float,
    val headingCorrectionDegrees: Float,
    val estimatedUncertaintyMeters: Float,
    val confidence: Float
)

interface AIPositionCorrectionModel {
    val isAvailable: Boolean

    fun correct(input: AIPositionCorrectionInput): AIPositionCorrectionOutput?
}

class NoOpAIPositionCorrectionModel : AIPositionCorrectionModel {
    override val isAvailable: Boolean = false

    override fun correct(input: AIPositionCorrectionInput): AIPositionCorrectionOutput? = null
}

class LocalDeterministicAIPositionCorrectionModel : AIPositionCorrectionModel {
    override val isAvailable: Boolean = true

    override fun correct(input: AIPositionCorrectionInput): AIPositionCorrectionOutput? {
        val previous = input.previousPosition ?: return null
        val matched = input.mapMatchedPosition ?: return null
        if (input.mapMatchingConfidence < 0.45f || input.secondsSinceLastGnss < 2f) return null

        val eastMeters = metersEast(previous, matched)
        val northMeters = metersNorth(previous, matched)
        val correctionLimit = when {
            input.secondsSinceLastGnss > 20f -> 7.5f
            input.secondsSinceLastGnss > 8f -> 5.0f
            else -> 2.5f
        }
        val limitedEast = eastMeters.coerceIn(-correctionLimit, correctionLimit)
        val limitedNorth = northMeters.coerceIn(-correctionLimit, correctionLimit)
        val confidence = (input.mapMatchingConfidence * 0.72f)
            .coerceIn(0.25f, 0.82f)

        return AIPositionCorrectionOutput(
            deltaEastMeters = limitedEast,
            deltaNorthMeters = limitedNorth,
            velocityCorrectionMetersPerSecond = 0f,
            headingCorrectionDegrees = 0f,
            estimatedUncertaintyMeters = (input.previousUncertaintyMeters * (1f - confidence * 0.35f))
                .coerceIn(4f, 80f),
            confidence = confidence
        )
    }

    private fun metersEast(from: GeoPosition, to: GeoPosition): Float {
        val metersPerDegreeLon = 111_320.0 * kotlin.math.cos(Math.toRadians(from.latitude))
        return ((to.longitude - from.longitude) * metersPerDegreeLon).toFloat()
    }

    private fun metersNorth(from: GeoPosition, to: GeoPosition): Float =
        ((to.latitude - from.latitude) * 111_320.0).toFloat()
}
