package com.wherepaid.validator.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wherepaid.validator.app
import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.data.PaymentRecord
import com.wherepaid.validator.data.Settings
import com.wherepaid.validator.export.CsvBuilder
import com.wherepaid.validator.export.CsvShare
import com.wherepaid.validator.parse.PaymentKind
import com.wherepaid.validator.util.Permissions
import com.wherepaid.validator.util.TimeFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    onOpen: (Long) -> Unit,
    onPermissions: () -> Unit,
    onSettings: () -> Unit,
    onTest: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember { ctx.app.db.recordDao().observeAll() }.collectAsStateWithLifecycle(emptyList())
    val settings by remember { ctx.app.settings.flow }.collectAsStateWithLifecycle(Settings())
    var filter by rememberSaveable { mutableStateOf(Filter.REAL) }
    var exporting by remember { mutableStateOf(false) }

    var missingPermission by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        missingPermission = !Permissions.hasNotificationAccess(ctx) || !Permissions.hasBackground(ctx)
    }

    // 실결제 = 대상 앱의 결제/취소 알림만 (포인트 적립, 그룹 요약 등은 '전체'에서만 보인다)
    val real = all.filter { !it.isTest && settings.isTarget(it.packageName) && it.parsedKind.isPayment() }
    val tests = all.filter { it.isTest }
    val shown = when (filter) {
        Filter.REAL -> real
        Filter.TEST -> tests
        Filter.ALL -> all
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기록") },
                actions = {
                    TextButton(onClick = onTest) { Text("테스트") }
                    TextButton(onClick = onPermissions) { Text("권한") }
                    TextButton(onClick = onSettings) { Text("설정") }
                    TextButton(
                        enabled = !exporting,
                        onClick = {
                            exporting = true
                            scope.launch {
                                try {
                                    ctx.startActivity(CsvShare.buildShareIntent(ctx))
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "내보내기 실패: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                    ) { Text("CSV") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (missingPermission) {
                Card(
                    onClick = onPermissions,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    Text(
                        "알림 접근 또는 백그라운드 위치 권한이 없습니다. 눌러서 권한 화면으로 이동",
                        Modifier.padding(12.dp),
                    )
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == Filter.REAL, onClick = { filter = Filter.REAL }, label = { Text("실결제 (${real.size})") })
                FilterChip(selected = filter == Filter.TEST, onClick = { filter = Filter.TEST }, label = { Text("테스트 (${tests.size})") })
                FilterChip(selected = filter == Filter.ALL, onClick = { filter = Filter.ALL }, label = { Text("전체 (${all.size})") })
            }
            if (filter != Filter.ALL) Summary(shown)
            if (shown.isEmpty()) {
                Text(
                    if (filter == Filter.TEST) {
                        "테스트 기록이 없습니다.\n상단 '테스트'에서 테스트 알림을 예약해 보세요."
                    } else {
                        "아직 기록이 없습니다.\n권한을 모두 허용한 뒤 토스로 결제해 보세요."
                    },
                    Modifier.padding(24.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { r ->
                    RecordRow(r, isTarget = r.isTest || settings.isTarget(r.packageName), onClick = { onOpen(r.id) })
                }
            }
        }
    }
}

private enum class Filter { REAL, TEST, ALL }

private fun PaymentKind?.isPayment() = this == PaymentKind.PAYMENT || this == PaymentKind.CANCEL

/** 팀 목표(40건) 진행 상황을 한눈에. */
@Composable
private fun Summary(targets: List<PaymentRecord>) {
    val success = targets.count { it.locStatus == LocStatus.SUCCESS }
    val withRef = targets.count { CsvBuilder.distanceRaw(it) != null }
    val within = targets.count { (CsvBuilder.distanceRaw(it) ?: Double.MAX_VALUE) <= CsvBuilder.WITHIN_METERS }
    val manual = targets.count { it.actualPaidAt != null }
    Text(
        "${targets.size}건 · 위치 성공 $success · 실제시각 입력 $manual · 거리 계산 $withRef · 100m 이내 $within",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun RecordRow(r: PaymentRecord, isTarget: Boolean, onClick: () -> Unit) {
    val distance = CsvBuilder.distanceRaw(r)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(TimeFormat.formatShort(r.receivedAt), style = MaterialTheme.typography.bodySmall)
            if (!isTarget) Text(r.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (r.parsedKind == PaymentKind.FAILED) Text("[실패]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (r.parsedKind == PaymentKind.POINT) Text("[적립]", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (r.parsedKind == PaymentKind.OTHER) Text("[기타]", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (r.isTest) Text("[테스트]", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (r.actualPaidAt != null) Text("✔입력됨", style = MaterialTheme.typography.bodySmall)
        }
        if (isTarget) {
            val cancel = if (r.parsedIsCancel == true) " [취소]" else ""
            Text("${amountText(r.parsedAmount)} · ${r.actualStoreName ?: r.parsedMerchant ?: r.subText ?: r.text ?: "?"}$cancel")
            r.parsedCardName?.let {
                Text(it + (r.parsedPayType?.let { t -> " · $t" } ?: ""), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        } else {
            Text("${r.title ?: ""} · ${r.text ?: ""}", maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                locSummary(r),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.locStatus == LocStatus.FAILED) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            if (distance != null) {
                Text(
                    "거리 ${fmtMeters(distance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (distance <= CsvBuilder.WITHIN_METERS) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
