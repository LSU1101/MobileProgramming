@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wherepaid.validator.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.wherepaid.validator.app
import com.wherepaid.validator.capture.TestNotification
import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.util.Permissions
import com.wherepaid.validator.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 결제 없이 위치 검증: 매장 앞에서 기준 위치를 찍고 N초 뒤 테스트 알림을 예약한다.
 * 그 사이 앱을 닫고 폰을 주머니에 넣으면 실제 결제와 같은 "앱 꺼짐" 조건이 된다.
 */
@Composable
fun TestScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notifOk = remember(tick) { TestNotification.notificationsEnabled(ctx) }
    val exactOk = remember(tick) { TestNotification.canScheduleExact(ctx) }
    val listenerOk = remember(tick) { Permissions.hasNotificationAccess(ctx) }
    val locationOk = remember(tick) { Permissions.hasFine(ctx) || Permissions.hasCoarse(ctx) }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    var place by rememberSaveable { mutableStateOf("") }
    var delaySec by rememberSaveable { mutableIntStateOf(30) }
    var busy by remember { mutableStateOf(false) }
    var scheduledAt by rememberSaveable { mutableLongStateOf(0L) }
    var status by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { BackTopBar("테스트 알림 (결제 없이 검증)", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "1. 매장 입구 앞에서 아래 버튼을 누릅니다 (기준 위치를 먼저 찍음).\n" +
                    "2. 예약이 되면 바로 최근 앱 목록에서 이 앱을 지우고 화면을 끈 뒤 폰을 주머니에 넣습니다.\n" +
                    "3. 예약 시각에 '카드 알림 받기' 테스트 알림이 오면 실제 토스 알림처럼 위치를 측정해 기록합니다.\n" +
                    "※ 알림이 올 때까지 그 자리에 있어야 합니다. 기록은 목록의 '테스트' 탭에 쌓입니다.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (!listenerOk || !locationOk || !notifOk || !exactOk) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!listenerOk) Text("❌ 알림 접근 권한이 없습니다 (권한 화면에서 허용).")
                        if (!locationOk) Text("❌ 위치 권한이 없습니다 (권한 화면에서 허용).")
                        if (!notifOk) {
                            Text("❌ 이 앱의 알림 표시가 꺼져 있어 테스트 알림이 뜨지 않습니다.")
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    ctx.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                                    )
                                }
                            }) { Text("알림 표시 허용") }
                        }
                        if (!exactOk) {
                            Text(
                                "⚠️ '알람 및 리마인더' 권한이 없어 알림이 예약 시각보다 몇 분 늦게 올 수 있습니다. " +
                                    "늦어도 기록은 되지만, 허용하면 시각이 정확해집니다.",
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                OutlinedButton(onClick = {
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${ctx.packageName}"),
                                        ),
                                    )
                                }) { Text("알람 및 리마인더 허용") }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text("장소 메모 (예: 강남역 지하상가 B1 올리브영)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("알림까지 대기 시간", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30초", 60 to "1분", 180 to "3분").forEach { (sec, label) ->
                    FilterChip(selected = delaySec == sec, onClick = { delaySec = sec }, label = { Text(label) })
                }
            }

            Button(
                enabled = !busy && listenerOk && locationOk,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    status = "기준 위치 측정 중… (최대 20초)"
                    scope.launch {
                        val ref = ctx.app.locationCapturer.captureReference()
                        val refArg = if (ref.status == LocStatus.SUCCESS && ref.lat != null && ref.lng != null) {
                            TestNotification.Reference(ref.lat, ref.lng, ref.accuracyM, System.currentTimeMillis())
                        } else {
                            null
                        }
                        val fireAt = TestNotification.schedule(ctx, delaySec * 1000L, place.trim(), refArg)
                        scheduledAt = fireAt
                        status = buildString {
                            append(
                                if (refArg != null) {
                                    String.format(Locale.US, "기준 위치 저장 (±%.0fm)\n", refArg.accuracyM ?: 0f)
                                } else {
                                    "⚠️ 기준 위치 실패(${ref.failReason}). 나중에 기록 상세에서 직접 찍으세요.\n"
                                },
                            )
                            append("${TimeFormat.format(fireAt)} 에 테스트 알림 예정.\n")
                            append("지금 이 앱을 최근 앱 목록에서 지우고 화면을 끄세요.")
                        }
                        busy = false
                        Toast.makeText(ctx, "예약됨. 앱을 닫고 기다리세요.", Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text(if (busy) "기준 위치 측정 중…" else "기준 위치 찍고 테스트 알림 예약") }

            if (status.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text(status, Modifier.padding(12.dp))
                }
            }
            if (scheduledAt > 0) {
                Text(
                    "예약을 여러 번 해도 됩니다. 각 예약마다 기록이 1건씩 생깁니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
