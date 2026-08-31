package com.dallim.app.onboarding.firstroute

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single-shot current-location fetch for S-06 (첫 코스 제안) — docs/01-feature-spec.md 1.3
 * lists `FusedLocationProviderClient` as S-06's Android component, but the full
 * GPS-tracking pipeline (ForegroundService, Room, WorkManager) belongs to the running module
 * (S-20/S-21) and is out of scope for this round, so this is intentionally a tiny standalone
 * helper rather than something living in a shared `core-location` module.
 *
 * Returns null if location permission hasn't been granted (S-05 lets the user skip/deny) or if
 * a location can't be resolved — callers should treat that as an empty/error state, never crash.
 */
/** 위치 + 정확도(m) — S-20의 "GPS 신호 강도 체크"가 [getCurrentLocationSample]로 이 값을 쓴다. */
data class LocationSample(val lat: Double, val lng: Double, val accuracyM: Float)

@Singleton
class CurrentLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun getCurrentLocation(): Pair<Double, Double>? =
        getCurrentLocationSample()?.let { it.lat to it.lng }

    /** S-20 GPS 신호 강도 체크 — 정확도(m)까지 필요하므로 [getCurrentLocation]과 별도로 노출한다. */
    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() above
    suspend fun getCurrentLocationSample(): LocationSample? {
        if (!hasLocationPermission()) return null
        return runCatching {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build()
            val location = fusedLocationClient.getCurrentLocation(request, null).await()
            location?.let { LocationSample(lat = it.latitude, lng = it.longitude, accuracyM = it.accuracy) }
        }.getOrNull()
    }
}
