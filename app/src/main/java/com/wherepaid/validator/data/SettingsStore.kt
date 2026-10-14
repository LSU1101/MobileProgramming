package com.wherepaid.validator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** 쉼표로 구분된 대상 패키지. 이 패키지 알림만 위치를 요청한다. */
    val targetPackagesRaw: String = DEFAULT_TARGET,
    /** 켜면 모든 앱 알림을 (위치 없이) 기록 → 실제 토스 패키지명 확인용 */
    val logAllApps: Boolean = false,
    val memberName: String = "",
    val listenerConnectedAt: Long? = null,
    val listenerDisconnectedAt: Long? = null,
) {
    val targetPackages: List<String>
        get() = targetPackagesRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun isTarget(pkg: String) = pkg in targetPackages

    companion object {
        const val DEFAULT_TARGET = "viva.republica.toss"
    }
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val TARGET = stringPreferencesKey("target_packages")
        val LOG_ALL = booleanPreferencesKey("log_all_apps")
        val MEMBER = stringPreferencesKey("member_name")
        val CONNECTED = longPreferencesKey("listener_connected_at")
        val DISCONNECTED = longPreferencesKey("listener_disconnected_at")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            targetPackagesRaw = p[Keys.TARGET] ?: Settings.DEFAULT_TARGET,
            logAllApps = p[Keys.LOG_ALL] ?: false,
            memberName = p[Keys.MEMBER] ?: "",
            listenerConnectedAt = p[Keys.CONNECTED],
            listenerDisconnectedAt = p[Keys.DISCONNECTED],
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun setTargetPackages(v: String) = context.dataStore.edit { it[Keys.TARGET] = v }
    suspend fun setLogAllApps(v: Boolean) = context.dataStore.edit { it[Keys.LOG_ALL] = v }
    suspend fun setMemberName(v: String) = context.dataStore.edit { it[Keys.MEMBER] = v }
    suspend fun setListenerConnectedAt(v: Long) = context.dataStore.edit { it[Keys.CONNECTED] = v }
    suspend fun setListenerDisconnectedAt(v: Long) = context.dataStore.edit { it[Keys.DISCONNECTED] = v }
}
