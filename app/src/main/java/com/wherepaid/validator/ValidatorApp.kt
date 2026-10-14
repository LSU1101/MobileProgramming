package com.wherepaid.validator

import android.app.Application
import android.content.Context
import com.wherepaid.validator.capture.LocationCapturer
import com.wherepaid.validator.data.AppDatabase
import com.wherepaid.validator.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ValidatorApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase by lazy { AppDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val locationCapturer: LocationCapturer by lazy { LocationCapturer(this) }

    override fun onCreate() {
        super.onCreate()
        // 새 프로세스에서는 진행 중인 위치 요청이 있을 수 없다 → 남은 PENDING은 중단된 것.
        val startedAt = System.currentTimeMillis()
        appScope.launch { db.recordDao().markPendingInterrupted(startedAt) }
    }
}

val Context.app: ValidatorApp get() = applicationContext as ValidatorApp
