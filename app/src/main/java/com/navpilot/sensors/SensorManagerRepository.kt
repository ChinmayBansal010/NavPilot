package com.navpilot.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.ImuSample
import com.navpilot.domain.model.SensorSample
import com.navpilot.domain.model.SensorStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorManagerRepository(
    context: Context
) {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val status: SensorStatus
        get() = SensorStatus(
            hasAccelerometer = accelerometer != null,
            hasGyroscope = gyroscope != null,
            hasMagnetometer = magnetometer != null
        )

    fun imuSamples(): Flow<ImuSample> = callbackFlow {
        var accelX = 0f; var accelY = 0f; var accelZ = 0f; var accelAcc = 0
        var gyroX = 0f; var gyroY = 0f; var gyroZ = 0f; var gyroAcc = 0
        var magX = 0f; var magY = 0f; var magZ = 0f; var magAcc = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val vals = event.values
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelX = vals[0]; accelY = vals[1]; accelZ = vals[2]
                        accelAcc = event.accuracy
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        gyroX = vals[0]; gyroY = vals[1]; gyroZ = vals[2]
                        gyroAcc = event.accuracy
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        magX = vals[0]; magY = vals[1]; magZ = vals[2]
                        magAcc = event.accuracy
                    }
                }

                trySend(
                    ImuSample(
                        timestampNanos = event.timestamp,
                        accelerometerX = accelX,
                        accelerometerY = accelY,
                        accelerometerZ = accelZ,
                        gyroscopeX = gyroX,
                        gyroscopeY = gyroY,
                        gyroscopeZ = gyroZ,
                        magnetometerX = magX,
                        magnetometerY = magY,
                        magnetometerZ = magZ,
                        accelerometerAccuracy = accelAcc,
                        gyroscopeAccuracy = gyroAcc,
                        magnetometerAccuracy = magAcc
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor == null) return
                when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelAcc = accuracy
                    Sensor.TYPE_GYROSCOPE -> gyroAcc = accuracy
                    Sensor.TYPE_MAGNETIC_FIELD -> magAcc = accuracy
                }
            }
        }

        val activeSensors = listOfNotNull(accelerometer, gyroscope, magnetometer)
        activeSensors.forEach { sensor ->
            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    fun imuFrames(): Flow<ImuFrame> = callbackFlow {
        var lastAccelerometer: SensorSample? = null
        var lastGyroscope: SensorSample? = null
        var lastMagnetometer: SensorSample? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sample = SensorSample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestampNanos = event.timestamp
                )

                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> lastAccelerometer = sample
                    Sensor.TYPE_GYROSCOPE -> lastGyroscope = sample
                    Sensor.TYPE_MAGNETIC_FIELD -> lastMagnetometer = sample
                }

                trySend(
                    ImuFrame(
                        accelerometer = lastAccelerometer,
                        gyroscope = lastGyroscope,
                        magnetometer = lastMagnetometer,
                        timestampNanos = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        listOfNotNull(accelerometer, gyroscope, magnetometer).forEach { sensor ->
            sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
