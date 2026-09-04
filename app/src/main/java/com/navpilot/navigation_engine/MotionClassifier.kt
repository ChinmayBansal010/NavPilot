package com.navpilot.navigation_engine

import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.MotionState
import com.navpilot.domain.model.OrientationState
import kotlin.math.abs
import kotlin.math.sqrt

class MotionClassifier {
    fun classify(
        frame: ImuFrame,
        orientation: OrientationState,
        speedMetersPerSecond: Float
    ): MotionState {
        val accelerometer = frame.accelerometer ?: return MotionState.STATIONARY
        val gyroscope = frame.gyroscope

        val magnitude = sqrt(
            accelerometer.x * accelerometer.x +
                accelerometer.y * accelerometer.y +
                accelerometer.z * accelerometer.z
        )
        val verticalImpulse = abs(magnitude - STANDARD_GRAVITY)
        val yawRate = abs(gyroscope?.z ?: 0f)
        val longitudinal = orientation.vehicleAccelerationY

        return when {
            verticalImpulse > 4.0f -> MotionState.HIGH_FREQUENCY_VIBRATION
            speedMetersPerSecond < 0.8f && abs(longitudinal) < 0.35f -> MotionState.STATIONARY
            yawRate > 0.45f -> MotionState.TURNING
            longitudinal > 1.0f -> MotionState.ACCELERATION
            longitudinal < -1.0f -> MotionState.BRAKING
            else -> MotionState.NORMAL_DRIVING
        }
    }

    private companion object {
        const val STANDARD_GRAVITY = 9.80665f
    }
}
