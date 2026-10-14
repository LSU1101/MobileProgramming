package com.wherepaid.validator.capture

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.wherepaid.validator.app
import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.data.PaymentRecord
import com.wherepaid.validator.parse.TossNotificationParser
import kotlinx.coroutines.launch

/**
 * 알림 수집. 대상 패키지(기본 토스) 알림이면 원본 저장 직후 위치를 1회 요청한다.
 * "모든 앱 알림 기록"이 켜져 있으면 다른 앱 알림도 위치 없이(SKIPPED) 저장한다.
 */
class PaymentNotificationListener : NotificationListenerService() {

    // 서비스가 먼저 해제돼도 진행 중인 위치 요청은 끝까지 기록되도록 프로세스 범위 스코프를 쓴다.
    private val scope get() = app.appScope

    override fun onListenerConnected() {
        val now = System.currentTimeMillis()
        scope.launch { app.settings.setListenerConnectedAt(now) }
    }

    override fun onListenerDisconnected() {
        val now = System.currentTimeMillis()
        scope.launch { app.settings.setListenerDisconnectedAt(now) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val receivedAt = System.currentTimeMillis()
        if (sbn.packageName == packageName) return
        // 상태 스냅샷은 지연 없이 수신 시점에 바로 찍는다.
        val state = DeviceStateSnapshot.capture(this)

        scope.launch {
            try {
                handle(sbn, receivedAt, state)
            } catch (e: Exception) {
                Log.e(TAG, "failed to record notification ${sbn.key}", e)
            }
        }
    }

    private suspend fun handle(sbn: StatusBarNotification, receivedAt: Long, state: DeviceStateSnapshot) {
        val settings = app.settings.current()
        val isTarget = settings.isTarget(sbn.packageName)
        if (!isTarget && !settings.logAllApps) return

        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")

        val parsed = runCatching { TossNotificationParser.parse(title, text, subText, bigText) }.getOrNull()

        val dao = app.db.recordDao()
        val id = dao.insert(
            PaymentRecord(
                packageName = sbn.packageName,
                notificationKey = sbn.key,
                postTime = sbn.postTime,
                receivedAt = receivedAt,
                title = title,
                text = text,
                subText = subText,
                bigText = bigText,
                textLines = textLines,
                category = n.category,
                isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                isOngoing = sbn.isOngoing,
                extrasDump = dumpExtras(extras),
                parsedAmount = parsed?.amount,
                parsedCardName = parsed?.cardName,
                parsedMerchant = parsed?.merchant,
                parsedPayType = parsed?.payType,
                parsedIsCancel = parsed?.isCancel,
                parserVersion = TossNotificationParser.VERSION,
                locStatus = if (isTarget) LocStatus.PENDING else LocStatus.SKIPPED,
                permFine = state.permFine,
                permCoarse = state.permCoarse,
                permBackground = state.permBackground,
                locationEnabled = state.locationEnabled,
                batteryOptIgnored = state.batteryOptIgnored,
                powerSaveMode = state.powerSaveMode,
                deviceIdleMode = state.deviceIdleMode,
                appImportance = state.appImportance,
                appImportanceLabel = state.appImportanceLabel,
            )
        )
        if (!isTarget) return

        val r = app.locationCapturer.captureForNotification(state)
        dao.updateLocation(
            id = id, status = r.status,
            lat = r.lat, lng = r.lng, accuracyM = r.accuracyM,
            elapsedMs = r.elapsedMs, fixTime = r.fixTime, fixAgeMs = r.fixAgeMs, provider = r.provider,
            failReason = r.failReason, errorMessage = r.errorMessage,
            lkLat = r.lastKnownLat, lkLng = r.lastKnownLng, lkAccuracyM = r.lastKnownAccuracyM, lkAgeMs = r.lastKnownAgeMs,
        )
    }

    companion object {
        private const val TAG = "PaymentListener"

        /** extras 전체를 key=value 줄로. 비트맵 등은 클래스명만 남는다. */
        @Suppress("DEPRECATION")
        fun dumpExtras(extras: Bundle): String =
            extras.keySet().sorted().joinToString("\n") { key ->
                val v = runCatching { extras.get(key) }.getOrNull()
                val s = when (v) {
                    null -> "null"
                    is CharSequence -> v.toString()
                    is Array<*> -> v.joinToString(" / ", "[", "]")
                    is Number, is Boolean -> v.toString()
                    else -> "<${v::class.java.name}>"
                }
                "$key=$s"
            }
    }
}
