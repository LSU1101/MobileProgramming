package com.wherepaid.validator.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wherepaid.validator.app
import com.wherepaid.validator.data.Settings
import com.wherepaid.validator.util.Permissions
import com.wherepaid.validator.util.TimeFormat

/**
 * 권한 흐름:
 *  ① 알림 접근 → ② 위치(정확) → ③ 백그라운드 위치("항상 허용") → ④ 배터리 최적화 제외
 * ③은 ②가 허용된 뒤에만 요청할 수 있다(Android 정책). 화면으로 돌아올 때마다 상태를 다시 읽는다.
 */
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    // ON_RESUME마다 증가시켜 아래 상태들을 다시 계산
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val settings by remember { ctx.app.settings.flow }.collectAsStateWithLifecycle(Settings())

    val notif = remember(tick) { Permissions.hasNotificationAccess(ctx) }
    val fine = remember(tick) { Permissions.hasFine(ctx) }
    val coarse = remember(tick) { Permissions.hasCoarse(ctx) }
    val background = remember(tick) { Permissions.hasBackground(ctx) }
    val battery = remember(tick) { Permissions.isIgnoringBatteryOptimizations(ctx) }
    val locOn = remember(tick) { Permissions.isLocationEnabled(ctx) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { tick++ }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        tick++
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(ctx, "'항상 허용'이 선택되지 않았습니다. '앱 설정 열기'로 직접 바꿔 주세요.", Toast.LENGTH_LONG).show()
        }
    }
    var showBackgroundGuide by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("권한 상태", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                style = MaterialTheme.typography.bodySmall,
            )

            PermissionCard(
                title = "① 알림 접근",
                ok = notif,
                description = "토스 결제 알림을 읽기 위해 필요합니다. 목록에서 '어디서돈썼지도 검증'을 켜세요.",
            ) {
                Button(onClick = { ctx.safeStart(Permissions.notificationAccessIntent(ctx), Permissions.notificationAccessListIntent()) }) {
                    Text("알림 접근 설정 열기")
                }
                OutlinedButton(onClick = {
                    NotificationListenerService.requestRebind(Permissions.listenerComponent(ctx))
                    Toast.makeText(ctx, "재연결 요청함", Toast.LENGTH_SHORT).show()
                }) { Text("리스너 재연결") }
                Text(
                    "마지막 연결: ${TimeFormat.format(settings.listenerConnectedAt).ifEmpty { "없음" }}\n" +
                        "마지막 해제: ${TimeFormat.format(settings.listenerDisconnectedAt).ifEmpty { "없음" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            PermissionCard(
                title = "② 위치 (정확한 위치)",
                ok = fine,
                description = when {
                    fine -> "정확한 위치 허용됨."
                    coarse -> "대략적 위치만 허용됨 → 100m 검증에 부적합. '정확한 위치 사용'을 켜 주세요."
                    else -> "알림 수신 시 위치를 1회 측정합니다. '앱 사용 중에만 허용' + '정확한 위치'를 선택하세요."
                },
            ) {
                Button(onClick = {
                    foregroundLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    )
                }) { Text("위치 권한 요청") }
                OutlinedButton(onClick = { ctx.safeStart(Permissions.appDetailsIntent(ctx)) }) { Text("앱 설정 열기") }
            }

            PermissionCard(
                title = "③ 백그라운드 위치 (항상 허용)",
                ok = background,
                description = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                        "Android 9 이하는 별도 권한이 없습니다(②에 포함)."
                    !(fine || coarse) -> "②를 먼저 허용해야 요청할 수 있습니다."
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        "앱이 꺼져 있을 때 위치를 얻는 데 필요합니다(핵심 검증 항목). " +
                            "Android 11 이상은 설정 화면에서 '항상 허용'을 직접 선택해야 합니다."
                    else -> "앱이 꺼져 있을 때 위치를 얻는 데 필요합니다. 다이얼로그에서 '항상 허용'을 선택하세요."
                },
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Button(
                        enabled = (fine || coarse) && !background,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                showBackgroundGuide = true
                            } else {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                    ) { Text("'항상 허용' 요청") }
                    OutlinedButton(onClick = { ctx.safeStart(Permissions.appDetailsIntent(ctx)) }) {
                        Text("앱 설정 열기 (권한 → 위치 → 항상 허용)")
                    }
                }
            }

            PermissionCard(
                title = "④ 배터리 최적화 제외",
                ok = battery,
                description = "절전 때문에 알림 리스너나 위치 요청이 지연·중단되는 것을 줄입니다. " +
                    "삼성은 추가로 '설정 → 배터리 → 백그라운드 사용 제한'에서 이 앱을 '사용 안 함 앱'에서 빼 주세요.",
            ) {
                Button(onClick = {
                    ctx.safeStart(Permissions.batteryOptRequestIntent(ctx), Permissions.batteryOptListIntent())
                }) { Text("최적화 제외 요청") }
            }

            PermissionCard(
                title = "(참고) 기기 위치 서비스",
                ok = locOn,
                description = if (locOn) "켜져 있음." else "꺼져 있으면 위치를 얻을 수 없습니다.",
            ) {
                OutlinedButton(onClick = { ctx.safeStart(Permissions.locationSettingsIntent()) }) { Text("위치 설정 열기") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBackgroundGuide) {
        AlertDialog(
            onDismissRequest = { showBackgroundGuide = false },
            title = { Text("'항상 허용'을 선택해 주세요") },
            text = {
                Text(
                    "다음 화면(앱 위치 권한 설정)에서 '항상 허용'을 선택한 뒤 뒤로 돌아오세요.\n" +
                        "앱이 꺼져 있을 때도 결제 알림 시점의 위치를 기록하기 위해서만 사용합니다. " +
                        "상시 위치 추적은 하지 않습니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundGuide = false
                    // API 30+: 시스템이 앱 위치 권한 설정 화면으로 이동시킨다.
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("설정으로 이동") }
            },
            dismissButton = { TextButton(onClick = { showBackgroundGuide = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    ok: Boolean,
    description: String,
    actions: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (ok) "✅ 허용" else "❌ 필요",
                    color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            actions()
        }
    }
}

/** 제조사마다 없는 설정 화면이 있어 실패하면 fallback으로. */
private fun Context.safeStart(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        if (fallback != null) {
            try {
                startActivity(fallback)
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        fallback?.let { startActivity(it) }
    }
}
