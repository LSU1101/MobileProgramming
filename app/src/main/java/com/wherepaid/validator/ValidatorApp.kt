package com.wherepaid.validator

import android.app.Application
import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.wherepaid.validator.capture.LocationCapturer
import com.wherepaid.validator.data.AppDatabase
import com.wherepaid.validator.data.SettingsStore
import com.wherepaid.validator.parse.TossNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ValidatorApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val locationCapturer: LocationCapturer by lazy { LocationCapturer(this) }

    /** 기록마다 남기는 앱 버전. 예: "0.1.8(8)" */
    val appVersion: String by lazy {
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            "${info.versionName}(${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("unknown")
    }

    override fun onCreate() {
        super.onCreate()
        // 새 프로세스에서는 진행 중인 위치 요청이 있을 수 없다 → 남은 PENDING은 중단된 것.
        val startedAt = System.currentTimeMillis()
        appScope.launch { db.recordDao().markPendingInterrupted(startedAt) }
        appScope.launch { reparseOutdated() }
    }

    /** 파서 규칙이 바뀌면(VERSION 증가) 이전 기록을 원본에서 다시 파싱한다. 원본 알림은 건드리지 않는다. */
    private suspend fun reparseOutdated() {
        val dao = db.recordDao()
        for (r in dao.getParsedBefore(TossNotificationParser.VERSION)) {
            val p = runCatching { TossNotificationParser.parse(r.title, r.text, r.subText, r.bigText) }.getOrNull()
            dao.updateParsed(
                id = r.id, kind = p?.kind, amount = p?.amount, cardName = p?.cardName, merchant = p?.merchant,
                payType = p?.payType, isCancel = p?.isCancel, version = TossNotificationParser.VERSION,
            )
        }
    }
}

val Context.app: ValidatorApp get() = applicationContext as ValidatorApp
