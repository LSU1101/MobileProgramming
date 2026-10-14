package com.wherepaid.validator.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wherepaid.validator.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CsvShare {
    /** 전체 기록을 CSV로 만들어 공유 시트용 Intent를 돌려준다. */
    suspend fun buildShareIntent(ctx: Context): Intent = withContext(Dispatchers.IO) {
        val member = ctx.app.settings.current().memberName.ifBlank { "unknown" }
        val records = ctx.app.db.recordDao().getAll()
        val csv = CsvBuilder.build(records, member)

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 이전 내보내기 정리
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val safeMember = member.replace(Regex("""[^\p{L}\p{N}_-]"""), "_")
        val file = File(dir, "wherepaid_${safeMember}_$stamp.csv")
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri) // 공유 대상 앱에 읽기 권한이 확실히 전달되도록
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "어디서돈썼지도 검증 데이터 (${records.size}건, $member)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "CSV 공유")
    }
}
