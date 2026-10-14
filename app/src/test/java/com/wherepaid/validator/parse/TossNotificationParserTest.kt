package com.wherepaid.validator.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TossNotificationParserTest {

    @Test
    fun `예시 알림 - 하나체크 Adobe 일시불`() {
        val p = TossNotificationParser.parse(
            title = "카드 알림 받기",
            text = "26,400원 결제",
            subText = "하나체크 | Adobe(일시불)",
            bigText = null,
        )
        assertEquals(26_400L, p.amount)
        assertEquals("하나체크", p.cardName)
        assertEquals("Adobe", p.merchant)
        assertEquals("일시불", p.payType)
        assertEquals(false, p.isCancel)
    }

    @Test
    fun `취소 알림`() {
        val p = TossNotificationParser.parse("카드 알림 받기", "26,400원 결제 취소", "하나체크 | Adobe(일시불)", null)
        assertEquals(26_400L, p.amount)
        assertEquals(true, p.isCancel)
    }

    @Test
    fun `할부 개월 표기`() {
        val a = TossNotificationParser.parseCardMerchant("신한카드 | 하이마트(할부 3개월)")!!
        assertEquals("하이마트", a.merchant)
        assertEquals("할부 3개월", a.payType)

        val b = TossNotificationParser.parseCardMerchant("신한카드 | 하이마트(3개월)")!!
        assertEquals("3개월", b.payType)
    }

    @Test
    fun `가맹점명 안의 괄호는 유지하고 끝 괄호만 결제방식으로`() {
        val r = TossNotificationParser.parseCardMerchant("하나체크 | 스타벅스(강남역점)(일시불)")!!
        assertEquals("스타벅스(강남역점)", r.merchant)
        assertEquals("일시불", r.payType)
    }

    @Test
    fun `결제방식 괄호가 없으면 가맹점 전체`() {
        val r = TossNotificationParser.parseCardMerchant("하나체크|GS25 역삼점")!!
        assertEquals("하나체크", r.cardName)
        assertEquals("GS25 역삼점", r.merchant)
        assertNull(r.payType)
    }

    @Test
    fun `구분자가 없으면 카드-가맹점 파싱 안 함`() {
        assertNull(TossNotificationParser.parseCardMerchant("오늘의 혜택을 확인하세요"))
        val p = TossNotificationParser.parse("토스", "오늘의 혜택을 확인하세요", "광고", null)
        assertNull(p.amount)
        assertNull(p.cardName)
        assertNull(p.merchant)
        assertNull(p.isCancel)
    }

    @Test
    fun `큰 금액과 쉼표`() {
        assertEquals(1_234_567L, TossNotificationParser.parseAmount("1,234,567원 결제"))
        assertEquals(500L, TossNotificationParser.parseAmount("500 원"))
        assertNull(TossNotificationParser.parseAmount("원 결제"))
    }

    @Test
    fun `subText가 없으면 bigText 줄에서 찾는다`() {
        val p = TossNotificationParser.parse(
            title = "카드 알림 받기",
            text = null,
            subText = null,
            bigText = "12,000원 결제\n국민카드 | 김밥천국(일시불)",
        )
        assertEquals(12_000L, p.amount)
        assertEquals("국민카드", p.cardName)
        assertEquals("김밥천국", p.merchant)
        assertFalse(p.isCancel!!)
    }

    @Test
    fun `모두 null이어도 예외 없음`() {
        val p = TossNotificationParser.parse(null, null, null, null)
        assertNull(p.amount)
        assertNull(p.merchant)
        assertNull(p.isCancel)
        assertTrue(TossNotificationParser.VERSION >= 1)
    }
}
