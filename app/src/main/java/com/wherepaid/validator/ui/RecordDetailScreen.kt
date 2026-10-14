@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.wherepaid.validator.ui

import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wherepaid.validator.app
import com.wherepaid.validator.data.Channel
import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.data.PaymentRecord
import com.wherepaid.validator.export.CsvBuilder
import com.wherepaid.validator.util.Geo
import com.wherepaid.validator.util.Permissions
import com.wherepaid.validator.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun RecordDetailScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val record by remember(id) { ctx.app.db.recordDao().observe(id) }.collectAsStateWithLifecycle(null)

    Scaffold(topBar = { BackTopBar("기록 #$id", onBack) }) { padding ->
        val r = record
        if (r == null) {
            Text("불러오는 중…", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ManualInputSection(r)
            HorizontalDivider(Modifier.padding(top = 16.dp))
            LocationSection(r)
            HorizontalDivider(Modifier.padding(top = 16.dp))
            RawSection(r)
            HorizontalDivider(Modifier.padding(top = 16.dp))
            DeleteSection(r, onDeleted = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ManualInputSection(r: PaymentRecord) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = ctx.app.db.recordDao()

    // 레코드 id 기준으로 한 번만 초기화 (위치 업데이트 등으로 r이 바뀌어도 입력 중인 값 유지)
    var paidAtText by rememberSaveable(r.id) {
        mutableStateOf(TimeFormat.format(r.actualPaidAt ?: r.receivedAt))
    }
    var store by rememberSaveable(r.id) { mutableStateOf(r.actualStoreName ?: r.parsedMerchant ?: "") }
    var channel by rememberSaveable(r.id) { mutableStateOf(r.channel) }
    var memo by rememberSaveable(r.id) { mutableStateOf(r.memo ?: "") }
    var capturingRef by remember { mutableStateOf(false) }

    val paidAtParsed = TimeFormat.parse(paidAtText)

    SectionTitle("수동 입력 (검증용)")
    if (r.actualPaidAt == null) {
        Text(
            "※ 실제 결제 시각은 아직 저장 안 됨. 아래 기본값은 알림 수신 시각이니 토스 거래내역을 보고 고쳐서 저장하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = paidAtText,
        onValueChange = { paidAtText = it },
        label = { Text("실제 결제 시각 (yyyy-MM-dd HH:mm[:ss])") },
        isError = paidAtText.isNotBlank() && paidAtParsed == null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    paidAtParsed?.let {
        Text(
            "알림 지연: " + String.format(Locale.US, "%.1f초", (r.receivedAt - it) / 1000.0),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    OutlinedTextField(
        value = store, onValueChange = { store = it },
        label = { Text("실제 매장명") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    val channels = listOf(Channel.OFFLINE to "오프라인", Channel.ONLINE to "온라인", Channel.UNKNOWN to "미정")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        channels.forEachIndexed { i, (c, label) ->
            SegmentedButton(
                selected = channel == c,
                onClick = { channel = c },
                shape = SegmentedButtonDefaults.itemShape(i, channels.size),
            ) { Text(label) }
        }
    }
    OutlinedTextField(
        value = memo, onValueChange = { memo = it },
        label = { Text("메모") }, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        enabled = paidAtText.isBlank() || paidAtParsed != null,
        onClick = {
            scope.launch {
                dao.updateManual(
                    r.id,
                    actualPaidAt = if (paidAtText.isBlank()) null else paidAtParsed,
                    storeName = store.trim().ifEmpty { null },
                    channel = channel,
                    memo = memo.trim().ifEmpty { null },
                )
                Toast.makeText(ctx, "저장됨", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("수동 입력 저장") }

    SectionTitle("매장 앞 기준 좌표")
    Text(
        "결제한 매장 앞에 서서 누르세요. 알림 수신 좌표와의 거리를 계산합니다.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (r.refLat != null) {
        KeyValue("기준 좌표", "${r.refLat}, ${r.refLng}")
        KeyValue("기준 정확도", r.refAccuracyM?.let { String.format(Locale.US, "±%.0fm", it) })
        KeyValue("찍은 시각", TimeFormat.format(r.refCapturedAt))
    }
    KeyValue("거리(알림↔기준)", fmtMeters(CsvBuilder.distanceRaw(r)))
    KeyValue(
        "거리(lastKnown↔기준)",
        fmtMeters(Geo.distanceMetersOrNull(r.lastKnownLat, r.lastKnownLng, r.refLat, r.refLng)),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = !capturingRef,
            onClick = {
                if (!Permissions.hasFine(ctx) && !Permissions.hasCoarse(ctx)) {
                    Toast.makeText(ctx, "위치 권한이 없습니다. 권한 화면에서 허용하세요.", Toast.LENGTH_LONG).show()
                } else {
                    capturingRef = true
                    scope.launch {
                        val res = ctx.app.locationCapturer.captureReference()
                        if (res.status == LocStatus.SUCCESS) {
                            dao.updateReference(r.id, res.lat, res.lng, res.accuracyM, System.currentTimeMillis())
                            Toast.makeText(ctx, "기준 좌표 저장 (±${res.accuracyM?.toInt()}m)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "위치 실패: ${res.failReason} ${res.errorMessage ?: ""}", Toast.LENGTH_LONG).show()
                        }
                        capturingRef = false
                    }
                }
            },
        ) { Text(if (capturingRef) "위치 측정 중…" else "지금 매장 앞에서 위치 찍기") }
        if (r.refLat != null) {
            OutlinedButton(onClick = {
                scope.launch { dao.updateReference(r.id, null, null, null, null) }
            }) { Text("기준 삭제") }
        }
    }
}

@Composable
private fun LocationSection(r: PaymentRecord) {
    SectionTitle("알림 수신 시점 위치")
    KeyValue("상태", r.locStatus)
    KeyValue("실패 원인", r.locFailReason)
    KeyValue("오류 메시지", r.locErrorMessage)
    KeyValue("좌표", r.lat?.let { "$it, ${r.lng}" })
    KeyValue("정확도", r.accuracyM?.let { String.format(Locale.US, "±%.1fm", it) })
    KeyValue("획득 소요", r.locElapsedMs?.let { "${it}ms" })
    KeyValue("측정 시각", TimeFormat.format(r.locFixTime))
    KeyValue("측정 후 경과", r.locFixAgeMs?.let { "${it}ms" })
    KeyValue("provider", r.locProvider)
    KeyValue("lastKnown 좌표", r.lastKnownLat?.let { "$it, ${r.lastKnownLng}" })
    KeyValue("lastKnown 정확도", r.lastKnownAccuracyM?.let { String.format(Locale.US, "±%.1fm", it) })
    KeyValue("lastKnown 경과", r.lastKnownAgeMs?.let { "${it / 1000}s" })

    SectionTitle("수신 시점 기기 상태")
    KeyValue("정확한 위치", r.permFine)
    KeyValue("대략적 위치", r.permCoarse)
    KeyValue("백그라운드 위치", r.permBackground)
    KeyValue("위치 서비스", r.locationEnabled)
    KeyValue("배터리 최적화 제외", r.batteryOptIgnored)
    KeyValue("절전 모드", r.powerSaveMode)
    KeyValue("Doze", r.deviceIdleMode)
    KeyValue("앱 상태", "${r.appImportanceLabel} (${r.appImportance})")
}

@Composable
private fun RawSection(r: PaymentRecord) {
    SectionTitle("원본 알림")
    if (r.isTest) {
        KeyValue("테스트 알림", "예 (예약 ${TimeFormat.format(r.testScheduledAt)})")
        KeyValue("알람 지연", r.testScheduledAt?.let { "${(r.receivedAt - it) / 1000.0}초" })
    }
    KeyValue("패키지", r.packageName)
    KeyBlock("key", r.notificationKey)
    KeyValue("postTime", TimeFormat.format(r.postTime) + " (${r.postTime})")
    KeyValue("수신 시각", TimeFormat.format(r.receivedAt) + " (${r.receivedAt})")
    KeyValue("수신−post", "${r.receivedAt - r.postTime}ms")
    KeyBlock("title", r.title)
    KeyBlock("text", r.text)
    KeyBlock("subText", r.subText)
    KeyBlock("bigText", r.bigText)
    KeyBlock("textLines", r.textLines)
    KeyValue("category", r.category)
    KeyValue("groupSummary", r.isGroupSummary)
    KeyValue("ongoing", r.isOngoing)

    SectionTitle("파싱 결과 (v${r.parserVersion ?: "?"})")
    KeyValue("금액", r.parsedAmount)
    KeyValue("카드", r.parsedCardName)
    KeyValue("가맹점", r.parsedMerchant)
    KeyValue("결제방식", r.parsedPayType)
    KeyValue("취소", r.parsedIsCancel)

    SectionTitle("extras 전체")
    KeyBlock("", r.extrasDump)
}

@Composable
private fun DeleteSection(r: PaymentRecord, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = { confirm = true }) { Text("이 기록 삭제") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("기록 삭제") },
            text = { Text("이 기록을 삭제할까요? 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    scope.launch {
                        ctx.app.db.recordDao().delete(r.id)
                        onDeleted()
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
}
