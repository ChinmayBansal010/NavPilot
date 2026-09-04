package com.navpilot.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.navpilot.domain.model.GnssSample
import com.navpilot.domain.model.GnssSatelliteInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch

class GnssLocationProvider(
    private val context: Context
) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasLocationPermission(): Boolean =
        hasFineLocationPermission() ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun locationUpdates(): Flow<GnssSample> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { trySend(location.toGnssSample()) }
            }

            @Deprecated("Deprecated in Android SDK")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        runCatching {
            val providers = if (hasFineLocationPermission()) {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            } else {
                listOf(LocationManager.NETWORK_PROVIDER)
            }

            providers
                .filter { locationManager.isProviderEnabled(it) }
                .forEach { provider ->
                    runCatching {
                        locationManager.requestLocationUpdates(
                            provider,
                            1_000L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        locationManager.getLastKnownLocation(provider)?.let { trySend(it.toGnssSample()) }
                    }
                }
        }.onFailure {
            close()
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
        }
    }.catch { }

    @SuppressLint("MissingPermission")
    fun satelliteStatus(): Flow<GnssSatelliteInfo> = callbackFlow {
        if (!hasFineLocationPermission()) {
            close()
            return@callbackFlow
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                runCatching {
                    var usedInFix = 0
                    for (index in 0 until status.satelliteCount) {
                        if (status.usedInFix(index)) {
                            usedInFix += 1
                        }
                    }
                    trySend(
                        GnssSatelliteInfo(
                            visibleSatellites = status.satelliteCount,
                            usedInFix = usedInFix
                        )
                    )
                }
            }
        }

        runCatching {
            locationManager.registerGnssStatusCallback(context.mainExecutor, callback)
        }.onFailure {
            close()
        }

        awaitClose {
            runCatching { locationManager.unregisterGnssStatusCallback(callback) }
        }
    }.catch { }
}

private fun Location.toGnssSample(): GnssSample =
    GnssSample(
        timestampMillis = time.takeIf { it > 0L } ?: System.currentTimeMillis(),
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed else 0f,
        bearingDegrees = if (hasBearing()) bearing else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        provider = provider.orEmpty()
    )
