package com.wherepaid.validator.capture

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Locale

/**
 * 결제 없이 검증하기 위한 "테스트 알림".
 * 예약 시각에 이 앱이 스스로 알림을 띄우고, [PaymentNotificationListener]가 그 알림을
 * 토스 알림과 똑같이 처리한다(위치 1회 측정). 기준 좌표는 예약할 때 찍어서 알림 extras로 넘긴다.
 */
object TestNotification {
    private const val CHANNEL_ID = "test_payment"

    const val EXTRA_IS_TEST = "com.wherepaid.validator.IS_TEST"
    const val EXTRA_SCHEDULED_AT = "com.wherepaid.validator.SCHEDULED_AT"
    const val EXTRA_PLACE = "com.wherepaid.validator.PLACE"
    const val EXTRA_REF_LAT = "com.wherepaid.validator.REF_LAT"
    const val EXTRA_REF_LNG = "com.wherepaid.validator.REF_LNG"
    const val EXTRA_REF_ACCURACY = "com.wherepaid.validator.REF_ACCURACY"
    const val EXTRA_REF_AT = "com.wherepaid.validator.REF_AT"

    data class Reference(val lat: Double, val lng: Double, val accuracyM: Float?, val capturedAt: Long)

    /** API 31+는 정확한 알람에 사용자 허용이 필요하다. 없으면 부정확 알람(수 분 지연 가능)으로 예약. */
    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun notificationsEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** @return 알림 예정 시각 (epoch ms) */
    fun schedule(ctx: Context, delayMs: Long, place: String?, ref: Reference?): Long {
        val fireAt = System.currentTimeMillis() + delayMs
        val intent = Intent(ctx, TestAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULED_AT, fireAt)
            place?.let { putExtra(EXTRA_PLACE, it) }
            ref?.let {
                putExtra(EXTRA_REF_LAT, it.lat)
                putExtra(EXTRA_REF_LNG, it.lng)
                it.accuracyM?.let { a -> putExtra(EXTRA_REF_ACCURACY, a) }
                putExtra(EXTRA_REF_AT, it.capturedAt)
            }
        }
        val pi = PendingIntent.getBroadcast(
            ctx, (fireAt % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        // Doze 중에도 울리도록 AllowWhileIdle 사용
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
        return fireAt
    }

    internal fun post(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "테스트 결제 알림", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val place = intent.getStringExtra(EXTRA_PLACE)?.takeIf { it.isNotBlank() } ?: "테스트매장"
        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TEST, true)
            putLong(EXTRA_SCHEDULED_AT, intent.getLongExtra(EXTRA_SCHEDULED_AT, 0L))
            putString(EXTRA_PLACE, intent.getStringExtra(EXTRA_PLACE))
            if (intent.hasExtra(EXTRA_REF_LAT)) {
                putDouble(EXTRA_REF_LAT, intent.getDoubleExtra(EXTRA_REF_LAT, 0.0))
                putDouble(EXTRA_REF_LNG, intent.getDoubleExtra(EXTRA_REF_LNG, 0.0))
                if (intent.hasExtra(EXTRA_REF_ACCURACY)) {
                    putFloat(EXTRA_REF_ACCURACY, intent.getFloatExtra(EXTRA_REF_ACCURACY, 0f))
                }
                putLong(EXTRA_REF_AT, intent.getLongExtra(EXTRA_REF_AT, 0L))
            }
        }
        // 토스 예시 알림과 같은 형태로 만들어 파서까지 같이 검증한다.
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("카드 알림 받기")
            .setContentText(String.format(Locale.KOREA, "%,d원 결제", 1000))
            .setSubText("테스트카드 | $place(일시불)")
            .addExtras(extras)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
        } catch (_: SecurityException) {
            // 알림 권한(POST_NOTIFICATIONS)이 없으면 알림이 뜨지 않는다. 테스트 화면에서 미리 확인한다.
        }
    }
}

class TestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TestNotification.post(context, intent)
    }
}
