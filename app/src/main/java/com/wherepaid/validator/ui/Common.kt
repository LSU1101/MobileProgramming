package com.wherepaid.validator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.data.PaymentRecord
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { TextButton(onClick = onBack) { Text("← 뒤로") } },
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** 라벨: 값 한 줄. 값은 길게 눌러 복사 가능. */
@Composable
fun KeyValue(key: String, value: Any?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
        SelectionContainer {
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun KeyBlock(key: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(key, style = MaterialTheme.typography.bodySmall)
        SelectionContainer { Text(value ?: "—", style = MaterialTheme.typography.bodyMedium) }
    }
}

fun fmtMeters(m: Double?): String = m?.let { String.format(Locale.US, "%.0fm", it) } ?: "—"

fun locSummary(r: PaymentRecord): String = when (r.locStatus) {
    LocStatus.SUCCESS -> "±" + (r.accuracyM?.let { String.format(Locale.US, "%.0fm", it) } ?: "?") +
        (r.locElapsedMs?.let { " (${it}ms)" } ?: "")
    LocStatus.FAILED -> "실패: ${r.locFailReason ?: "?"}"
    LocStatus.PENDING -> "위치 요청 중…"
    LocStatus.SKIPPED -> "위치 안 함(대상 앱 아님)"
}

fun amountText(amount: Long?): String =
    amount?.let { String.format(Locale.KOREA, "%,d원", it) } ?: "금액?"
