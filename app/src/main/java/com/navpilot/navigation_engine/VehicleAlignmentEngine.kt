package com.navpilot.navigation_engine

import android.hardware.SensorManager
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.OrientationState
import kotlin.math.PI

class VehicleAlignmentEngine {
    private val gravity = FloatArray(3)
    private val magnetic = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun update(frame: ImuFrame): OrientationState {
        frame.accelerometer?.let {
            gravity[0] = it.x
            gravity[1] = it.y
            gravity[2] = it.z
        }

        frame.magnetometer?.let {
            magnetic[0] = it.x
            magnetic[1] = it.y
            magnetic[2] = it.z
        }

        val hasOrientation = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            gravity,
            magnetic
        )

        if (!hasOrientation) {
            return OrientationState(confidence = 0f)
        }

        SensorManager.getOrientation(rotationMatrix, orientation)

        val acceleration = frame.accelerometer
        val vehicleAcceleration = acceleration?.let {
            transformToVehicleFrame(floatArrayOf(it.x, it.y, it.z))
        } ?: FloatArray(3)

        return OrientationState(
            pitchDegrees = orientation[1].toDegrees(),
            rollDegrees = orientation[2].toDegrees(),
            yawDegrees = normalizeDegrees(orientation[0].toDegrees()),
            vehicleAccelerationX = vehicleAcceleration[0],
            vehicleAccelerationY = vehicleAcceleration[1],
            vehicleAccelerationZ = vehicleAcceleration[2],
            confidence = 0.75f
        )
    }

    private fun transformToVehicleFrame(values: FloatArray): FloatArray =
        floatArrayOf(
            rotationMatrix[0] * values[0] + rotationMatrix[1] * values[1] + rotationMatrix[2] * values[2],
            rotationMatrix[3] * values[0] + rotationMatrix[4] * values[1] + rotationMatrix[5] * values[2],
            rotationMatrix[6] * values[0] + rotationMatrix[7] * values[1] + rotationMatrix[8] * values[2]
        )

    private fun Float.toDegrees(): Float = (this * 180f / PI.toFloat())

    private fun normalizeDegrees(value: Float): Float =
        ((value + 360f) % 360f)
}
