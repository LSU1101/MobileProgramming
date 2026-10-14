package com.wherepaid.validator.export

import com.wherepaid.validator.data.LocStatus
import com.wherepaid.validator.data.PaymentRecord
import com.wherepaid.validator.util.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvBuilderTest {
    private val kst = ZoneId.of("Asia/Seoul")

    private fun record(
        receivedAt: Long,
        actualPaidAt: Long? = null,
        lat: Double? = null, lng: Double? = null,
        refLat: Double? = null, refLng: Double? = null,
        subText: String? = "하나체크 | Adobe(일시불)",
    ) = PaymentRecord(
        id = 1,
        packageName = "viva.republica.toss",
        notificationKey = "0|viva.republica.toss|1|null|10001",
        postTime = receivedAt - 200,
        receivedAt = receivedAt,
        title = "카드 알림 받기",
        text = "26,400원 결제",
        subText = subText,
        bigText = null,
        textLines = null,
        category = null,
        isGroupSummary = false,
        isOngoing = false,
        extrasDump = null,
        locStatus = LocStatus.SUCCESS,
        lat = lat, lng = lng,
        permFine = true, permCoarse = true, permBackground = true,
        locationEnabled = true, batteryOptIgnored = true,
        powerSaveMode = false, deviceIdleMode = false,
        appImportance = 400, appImportanceLabel = "CACHED",
        actualPaidAt = actualPaidAt,
        refLat = refLat, refLng = refLng,
    )

    private fun parseRow(csv: String, row: Int): Map<String, String> {
        val lines = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }
        val header = lines[0].split(",")
        val values = splitCsvLine(lines[row])
        assertEquals(header.size, values.size)
        return header.zip(values).toMap()
    }

    /** 테스트용 단순 CSV 파서 (따옴표 처리). */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQ && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQ = !inQ
                c == ',' && !inQ -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    @Test
    fun `알림 지연과 거리 컬럼`() {
        val paid = TimeFormat.parse("2026-10-14 12:00:00", kst)!!
        val r = record(
            receivedAt = paid + 3_500,
            actualPaidAt = paid,
            lat = 37.5000, lng = 127.0,
            refLat = 37.5005, refLng = 127.0,
        )
        val row = parseRow(CsvBuilder.build(listOf(r), "철수", kst), 1)
        assertEquals("철수", row["member"])
        assertEquals("3.500", row["notif_delay_sec"])
        assertEquals("3.300", row["post_delay_sec"])
        assertEquals(55.6, row["distance_m"]!!.toDouble(), 0.2)
        assertEquals("true", row["within_100m"])
        assertEquals("2026-10-14 12:00:00", row["actual_paid_at"])
        assertEquals("하나체크 | Adobe(일시불)", row["sub_text"])
    }

    @Test
    fun `테스트 알림 컬럼`() {
        val scheduled = TimeFormat.parse("2026-10-14 15:00:00", kst)!!
        val r = record(receivedAt = scheduled + 2_000).copy(isTest = true, testScheduledAt = scheduled)
        val row = parseRow(CsvBuilder.build(listOf(r), "철수", kst), 1)
        assertEquals("true", row["is_test"])
        assertEquals("2026-10-14 15:00:00", row["test_scheduled_at"])
        assertEquals("2.000", row["test_alarm_delay_sec"])
    }

    @Test
    fun `수동 입력이 없으면 빈 칸`() {
        val row = parseRow(CsvBuilder.build(listOf(record(receivedAt = 1_000_000)), "영희", kst), 1)
        assertEquals("", row["notif_delay_sec"])
        assertEquals("", row["distance_m"])
        assertEquals("", row["within_100m"])
        assertEquals("false", row["is_test"])
        assertEquals("", row["test_alarm_delay_sec"])
    }

    @Test
    fun `쉼표 따옴표 줄바꿈 이스케이프`() {
        assertEquals("\"a,b\"", CsvBuilder.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvBuilder.escape("say \"hi\""))
        assertEquals("\"x\ny\"", CsvBuilder.escape("x\ny"))
        assertEquals("", CsvBuilder.escape(null))
    }

    @Test
    fun `BOM으로 시작`() {
        assertTrue(CsvBuilder.build(emptyList(), "a", kst).startsWith("﻿member,id,"))
    }
}
