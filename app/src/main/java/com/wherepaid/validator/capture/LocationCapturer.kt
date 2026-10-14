package com.wherepaid.validator.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.wherepaid.validator.data.LocFailReason
import com.wherepaid.validator.data.LocStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class LocationResult(
    val status: LocStatus,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Float? = null,
    val elapsedMs: Long? = null,
    val fixTime: Long? = null,
    val fixAgeMs: Long? = null,
    val provider: String? = null,
    val failReason: LocFailReason? = null,
    val errorMessage: String? = null,
    val lastKnownLat: Double? = null,
    val lastKnownLng: Double? = null,
    val lastKnownAccuracyM: Float? = null,
    val lastKnownAgeMs: Long? = null,
)

/**
 * 알림 1건당 1회만 위치를 요청한다. 상시 추적 없음.
 * 권한이 부족해 보여도 일단 요청해 본다 — 실제로 되는지/안 되는지가 검증 대상이기 때문.
 */
class LocationCapturer(context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    companion object {
        const val TIMEOUT_MS = 10_000L
        private const val LAST_KNOWN_TIMEOUT_MS = 2_000L
    }

    /** 알림 수신 직후 호출. 10초 안에 못 얻으면 TIMEOUT_10S. */
    @SuppressLint("MissingPermission")
    suspend fun captureForNotification(state: DeviceStateSnapshot): LocationResult {
        if (!state.permFine && !state.permCoarse) {
            return LocationResult(LocStatus.FAILED, failReason = LocFailReason.NO_LOCATION_PERMISSION)
        }
        if (!state.locationEnabled) {
            return LocationResult(LocStatus.FAILED, failReason = LocFailReason.LOCATION_DISABLED)
        }
        // getLastLocation은 캐시를 즉시 돌려준다. 새 측정보다 먼저 요청해 "수신 직전" 캐시를 잡는다.
        val lastKnownTask = runCatching { fused.lastLocation }.getOrNull()
        val current = requestCurrent(TIMEOUT_MS)
        val lk = lastKnownTask?.let { task ->
            runCatching { withTimeoutOrNull(LAST_KNOWN_TIMEOUT_MS) { task.await() } }.getOrNull()
        }
        return run {
            val withLastKnown = current.copy(
                lastKnownLat = lk?.latitude,
                lastKnownLng = lk?.longitude,
                lastKnownAccuracyM = lk?.takeIf { it.hasAccuracy() }?.accuracy,
                lastKnownAgeMs = lk?.let(::ageMs),
            )
            // 결과가 없었는데 백그라운드 + 백그라운드 권한 없음이면 그쪽이 원인일 가능성이 높다.
            if (withLastKnown.status == LocStatus.FAILED &&
                withLastKnown.failReason == LocFailReason.NULL_RESULT &&
                !state.permBackground && !state.isForeground
            ) {
                withLastKnown.copy(failReason = LocFailReason.NO_BACKGROUND_PERMISSION_WHILE_BACKGROUND)
            } else {
                withLastKnown
            }
        }
    }

    /** "지금 매장 앞에서 위치 찍기" — 앱 화면에서 누르므로 포그라운드. 조금 더 기다려 준다. */
    suspend fun captureReference(): LocationResult = requestCurrent(timeoutMs = 20_000L)

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(timeoutMs: Long): LocationResult {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(timeoutMs)
            .setMaxUpdateAgeMillis(0) // 캐시 말고 새로 측정한 좌표만
            .build()
        val cts = CancellationTokenSource()
        val start = SystemClock.elapsedRealtime()
        return try {
            // durationMillis가 지나면 FLP가 null을 돌려주지만, 혹시 몰라 코루틴 타임아웃도 건다.
            val loc = withTimeoutOrNull(timeoutMs + 500) {
                fused.getCurrentLocation(request, cts.token).await(cts)
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            when {
                loc != null -> LocationResult(
                    status = LocStatus.SUCCESS,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                    elapsedMs = elapsed,
                    fixTime = loc.time,
                    fixAgeMs = ageMs(loc),
                    provider = loc.provider,
                )
                elapsed >= timeoutMs - 500 -> LocationResult(
                    LocStatus.FAILED, elapsedMs = elapsed, failReason = LocFailReason.TIMEOUT_10S,
                )
                else -> LocationResult(
                    LocStatus.FAILED, elapsedMs = elapsed, failReason = LocFailReason.NULL_RESULT,
                )
            }
        } catch (e: SecurityException) {
            LocationResult(
                LocStatus.FAILED, elapsedMs = SystemClock.elapsedRealtime() - start,
                failReason = LocFailReason.SECURITY_EXCEPTION, errorMessage = e.message,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = if (e is ApiException) "${e.statusCode}: ${e.message}" else "${e::class.java.simpleName}: ${e.message}"
            LocationResult(
                LocStatus.FAILED, elapsedMs = SystemClock.elapsedRealtime() - start,
                failReason = LocFailReason.PLAY_SERVICES_ERROR, errorMessage = msg,
            )
        } finally {
            cts.cancel()
        }
    }

    private fun ageMs(loc: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
}
