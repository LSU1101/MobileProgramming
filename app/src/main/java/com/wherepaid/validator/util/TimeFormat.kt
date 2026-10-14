package com.wherepaid.validator.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeFormat {
    private val FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val NO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val SHORT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun format(epochMs: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        epochMs?.let { FULL.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: ""

    fun formatShort(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        SHORT.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    /**
     * "yyyy-MM-dd HH:mm:ss" 또는 "yyyy-MM-dd HH:mm"(초 생략 시 0초)를 epoch ms로.
     * 형식이 틀리면 null.
     */
    fun parse(s: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val t = s.trim()
        for (f in listOf(FULL, NO_SECONDS)) {
            try {
                return LocalDateTime.parse(t, f).atZone(zone).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }
}
