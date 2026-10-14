package com.wherepaid.validator.export

import com.wherepaid.validator.data.PaymentRecord
import com.wherepaid.validator.util.Geo
import com.wherepaid.validator.util.TimeFormat
import java.time.ZoneId
import java.util.Locale

/**
 * CSV 생성 (순수 Kotlin, JVM 테스트 가능).
 * 팀원 4명 파일을 그대로 이어붙여 분석할 수 있도록 첫 열에 member를 둔다.
 */
object CsvBuilder {
    const val WITHIN_METERS = 100.0

    private class Col(val name: String, val value: (PaymentRecord) -> Any?)

    private fun columns(member: String, zone: ZoneId): List<Col> = listOf(
        Col("member") { member },
        Col("id") { it.id },
        Col("package") { it.packageName },
        Col("is_test") { it.isTest },
        Col("test_scheduled_at") { TimeFormat.format(it.testScheduledAt, zone) },
        Col("test_alarm_delay_sec") { testAlarmDelaySec(it) },
        Col("notification_key") { it.notificationKey },
        Col("post_time_ms") { it.postTime },
        Col("post_time") { TimeFormat.format(it.postTime, zone) },
        Col("received_at_ms") { it.receivedAt },
        Col("received_at") { TimeFormat.format(it.receivedAt, zone) },
        Col("actual_paid_at_ms") { it.actualPaidAt },
        Col("actual_paid_at") { TimeFormat.format(it.actualPaidAt, zone) },
        Col("notif_delay_sec") { notifDelaySec(it) },
        Col("post_delay_sec") { postDelaySec(it) },
        Col("distance_m") { distanceM(it) },
        Col("within_100m") { distanceRaw(it)?.let { d -> d <= WITHIN_METERS } },
        Col("title") { it.title },
        Col("text") { it.text },
        Col("sub_text") { it.subText },
        Col("big_text") { it.bigText },
        Col("text_lines") { it.textLines },
        Col("category") { it.category },
        Col("is_group_summary") { it.isGroupSummary },
        Col("is_ongoing") { it.isOngoing },
        Col("parsed_amount") { it.parsedAmount },
        Col("parsed_card") { it.parsedCardName },
        Col("parsed_merchant") { it.parsedMerchant },
        Col("parsed_pay_type") { it.parsedPayType },
        Col("parsed_is_cancel") { it.parsedIsCancel },
        Col("parser_version") { it.parserVersion },
        Col("loc_status") { it.locStatus },
        Col("lat") { it.lat },
        Col("lng") { it.lng },
        Col("accuracy_m") { it.accuracyM },
        Col("loc_elapsed_ms") { it.locElapsedMs },
        Col("loc_fix_time") { TimeFormat.format(it.locFixTime, zone) },
        Col("loc_fix_age_ms") { it.locFixAgeMs },
        Col("loc_provider") { it.locProvider },
        Col("loc_fail_reason") { it.locFailReason },
        Col("loc_error") { it.locErrorMessage },
        Col("last_known_lat") { it.lastKnownLat },
        Col("last_known_lng") { it.lastKnownLng },
        Col("last_known_accuracy_m") { it.lastKnownAccuracyM },
        Col("last_known_age_ms") { it.lastKnownAgeMs },
        Col("last_known_distance_m") {
            Geo.distanceMetersOrNull(it.lastKnownLat, it.lastKnownLng, it.refLat, it.refLng)?.let(::fmt1)
        },
        Col("perm_fine") { it.permFine },
        Col("perm_coarse") { it.permCoarse },
        Col("perm_background") { it.permBackground },
        Col("location_enabled") { it.locationEnabled },
        Col("battery_opt_ignored") { it.batteryOptIgnored },
        Col("power_save_mode") { it.powerSaveMode },
        Col("device_idle_mode") { it.deviceIdleMode },
        Col("app_importance") { it.appImportance },
        Col("app_importance_label") { it.appImportanceLabel },
        Col("actual_store_name") { it.actualStoreName },
        Col("channel") { it.channel },
        Col("memo") { it.memo },
        Col("ref_lat") { it.refLat },
        Col("ref_lng") { it.refLng },
        Col("ref_accuracy_m") { it.refAccuracyM },
        Col("ref_captured_at") { TimeFormat.format(it.refCapturedAt, zone) },
        Col("extras_dump") { it.extrasDump },
    )

    /** 알림 지연 = 수신 시각 − 실제 결제 시각 (초). */
    fun notifDelaySec(r: PaymentRecord): String? =
        r.actualPaidAt?.let { fmt3((r.receivedAt - it) / 1000.0) }

    /** postTime − 실제 결제 시각 (초). */
    fun postDelaySec(r: PaymentRecord): String? =
        r.actualPaidAt?.let { fmt3((r.postTime - it) / 1000.0) }

    /** 테스트 알림: 예약 시각 대비 실제 수신이 얼마나 늦었는지 (초). 절전/알람 제한 확인용. */
    fun testAlarmDelaySec(r: PaymentRecord): String? =
        r.testScheduledAt?.let { fmt3((r.receivedAt - it) / 1000.0) }

    /** 알림 수신 좌표 ↔ 매장 앞 기준 좌표 거리. */
    fun distanceRaw(r: PaymentRecord): Double? =
        Geo.distanceMetersOrNull(r.lat, r.lng, r.refLat, r.refLng)

    private fun distanceM(r: PaymentRecord): String? = distanceRaw(r)?.let(::fmt1)

    private fun fmt1(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun fmt3(d: Double) = String.format(Locale.US, "%.3f", d)

    /** UTF-8 BOM 포함 (엑셀에서 한글 깨짐 방지). 줄바꿈은 CRLF(RFC 4180). */
    fun build(records: List<PaymentRecord>, member: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val cols = columns(member, zone)
        val sb = StringBuilder("﻿")
        cols.joinTo(sb, ",") { escape(it.name) }
        sb.append("\r\n")
        for (r in records) {
            cols.joinTo(sb, ",") { escape(it.value(r)?.toString()) }
            sb.append("\r\n")
        }
        return sb.toString()
    }

    fun escape(v: String?): String {
        if (v == null) return ""
        val needsQuote = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
