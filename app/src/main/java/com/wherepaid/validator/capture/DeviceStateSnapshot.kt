package com.wherepaid.validator.capture

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import com.wherepaid.validator.util.Permissions

/** 알림 수신 시점의 권한/전원/프로세스 상태. "앱이 꺼진 상태에서도 위치를 얻는가" 분석용. */
data class DeviceStateSnapshot(
    val permFine: Boolean,
    val permCoarse: Boolean,
    val permBackground: Boolean,
    val locationEnabled: Boolean,
    val batteryOptIgnored: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
    val appImportance: Int,
    val appImportanceLabel: String,
) {
    /** 사용자에게 보이는 화면(Activity)이 있는 상태인지. 아니면 백그라운드 위치 제한 대상. */
    val isForeground: Boolean
        get() = appImportance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    companion object {
        fun capture(ctx: Context): DeviceStateSnapshot {
            val pm = ctx.getSystemService(PowerManager::class.java)
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return DeviceStateSnapshot(
                permFine = Permissions.hasFine(ctx),
                permCoarse = Permissions.hasCoarse(ctx),
                permBackground = Permissions.hasBackground(ctx),
                locationEnabled = Permissions.isLocationEnabled(ctx),
                batteryOptIgnored = pm.isIgnoringBatteryOptimizations(ctx.packageName),
                powerSaveMode = pm.isPowerSaveMode,
                deviceIdleMode = pm.isDeviceIdleMode,
                appImportance = info.importance,
                appImportanceLabel = importanceLabel(info.importance),
            )
        }

        @Suppress("DEPRECATION")
        fun importanceLabel(i: Int): String = when (i) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "TOP_SLEEPING"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "PERCEPTIBLE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "CANT_SAVE_STATE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "GONE"
            else -> "OTHER_$i"
        }
    }
}
