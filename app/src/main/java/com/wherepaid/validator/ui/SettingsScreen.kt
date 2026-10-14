package com.wherepaid.validator.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wherepaid.validator.app
import com.wherepaid.validator.data.Settings
import com.wherepaid.validator.util.TimeFormat
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = ctx.app.settings
    val settings by remember { store.flow }.collectAsStateWithLifecycle(null)
    val packages by remember { ctx.app.db.recordDao().observePackageCounts() }.collectAsStateWithLifecycle(emptyList())

    var member by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(settings) {
        val s = settings
        if (s != null && !loaded) {
            member = s.memberName
            targets = s.targetPackagesRaw
            loaded = true
        }
    }
    var confirmCleanup by remember { mutableStateOf(false) }
    var confirmDeleteTests by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("설정", onBack) }) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("팀원 이름")
            Text("CSV의 member 열에 들어갑니다. 4명 데이터를 합칠 때 구분용.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = member, onValueChange = { member = it },
                label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("대상 패키지")
            Text(
                "이 패키지의 알림만 위치를 측정합니다. 여러 개면 쉼표로 구분. 기본값: ${Settings.DEFAULT_TARGET}",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = targets, onValueChange = { targets = it },
                label = { Text("패키지명") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                scope.launch {
                    store.setMemberName(member.trim())
                    store.setTargetPackages(targets.trim().ifEmpty { Settings.DEFAULT_TARGET })
                    Toast.makeText(ctx, "저장됨", Toast.LENGTH_SHORT).show()
                }
            }) { Text("이름·패키지 저장") }

            SectionTitle("모든 앱 알림 기록")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "켜면 다른 앱 알림도 (위치 측정 없이) 기록합니다. 실제 토스 패키지명 확인 후 끄세요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = s.logAllApps,
                    onCheckedChange = { v -> scope.launch { store.setLogAllApps(v) } },
                )
            }

            SectionTitle("수신된 패키지 (기록 기준)")
            if (packages.isEmpty()) Text("아직 없음", style = MaterialTheme.typography.bodySmall)
            packages.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SelectionContainer { Text(p.packageName) }
                        Text(
                            "${p.count}건 · 최근 ${TimeFormat.format(p.lastReceivedAt)}" +
                                if (s.isTarget(p.packageName)) " · 대상" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!s.isTarget(p.packageName) && p.packageName != ctx.packageName) {
                        TextButton(onClick = {
                            val merged = (s.targetPackages + p.packageName).joinToString(",")
                            targets = merged
                            scope.launch { store.setTargetPackages(merged) }
                        }) { Text("대상 추가") }
                    }
                }
            }

            SectionTitle("정리")
            OutlinedButton(onClick = { confirmCleanup = true }) { Text("대상 앱이 아닌 기록 모두 삭제") }
            OutlinedButton(onClick = { confirmDeleteTests = true }) { Text("테스트 기록 모두 삭제") }
        }
    }

    if (confirmCleanup) {
        AlertDialog(
            onDismissRequest = { confirmCleanup = false },
            title = { Text("기록 삭제") },
            text = { Text("대상 패키지가 아닌 앱의 기록을 모두 삭제합니다(테스트 기록은 유지). 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCleanup = false
                    scope.launch {
                        val targetsNow = ctx.app.settings.current().targetPackages
                        val n = ctx.app.db.recordDao().deleteNotIn(targetsNow)
                        Toast.makeText(ctx, "${n}건 삭제", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmCleanup = false }) { Text("취소") } },
        )
    }

    if (confirmDeleteTests) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTests = false },
            title = { Text("테스트 기록 삭제") },
            text = { Text("테스트 알림 기록을 모두 삭제합니다. 필요하면 먼저 CSV를 내보내세요. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteTests = false
                    scope.launch {
                        val n = ctx.app.db.recordDao().deleteTests()
                        Toast.makeText(ctx, "${n}건 삭제", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTests = false }) { Text("취소") } },
        )
    }
}
