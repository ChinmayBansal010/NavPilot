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
    private val mainHandler = android.os.Handler(Looper.getMainLooper())

    fun hasLocationPermission(): Boolean =
        hasFineLocationPermission() || hasCoarseLocationPermission()

    private fun hasCoarseLocationPermission(): Boolean =
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

        val providers = mutableListOf<String>()
        if (hasFineLocationPermission()) {
            providers.add(LocationManager.GPS_PROVIDER)
            providers.add(LocationManager.NETWORK_PROVIDER)
        } else if (hasCoarseLocationPermission()) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }

        providers.forEach { providerName ->
            try {
                if (locationManager.isProviderEnabled(providerName)) {
                    locationManager.requestLocationUpdates(
                        providerName,
                        1_000L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    val lastKnown = locationManager.getLastKnownLocation(providerName)
                    if (lastKnown != null) {
                        trySend(lastKnown.toGnssSample())
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        awaitClose {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {
            }
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
                try {
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
                } catch (_: Exception) {
                }
            }
        }

        try {
            locationManager.registerGnssStatusCallback(callback, mainHandler)
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        } catch (_: Exception) {
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                locationManager.unregisterGnssStatusCallback(callback)
            } catch (_: Exception) {
            }
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
