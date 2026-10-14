package com.wherepaid.validator.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class TimeFormatTest {
    private val kst = ZoneId.of("Asia/Seoul")

    @Test
    fun `초 포함 왕복`() {
        val ms = TimeFormat.parse("2026-10-14 12:34:56", kst)!!
        assertEquals("2026-10-14 12:34:56", TimeFormat.format(ms, kst))
    }

    @Test
    fun `초 생략은 0초`() {
        val ms = TimeFormat.parse(" 2026-10-14 12:34 ", kst)!!
        assertEquals("2026-10-14 12:34:00", TimeFormat.format(ms, kst))
    }

    @Test
    fun `잘못된 형식은 null`() {
        assertNull(TimeFormat.parse("10/14 12:34", kst))
    }
}
