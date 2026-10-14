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
        assertEquals(PaymentKind.PAYMENT, p.kind)
    }

    // --- 실제 토스페이 알림 (2026-10-14 팀원 기기에서 수집) ---

    @Test
    fun `토스페이 결제 - 금액은 제목, 가맹점은 가운뎃점 구분`() {
        val p = TossNotificationParser.parse(
            title = "17,000원 결제 완료",
            text = "토스페이머니 · 구글페이먼트코리아 유한회사",
            subText = null,
            bigText = "토스페이머니 · 구글페이먼트코리아 유한회사\n보너스 포인트 적립 가능",
        )
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(17_000L, p.amount)
        assertEquals("토스페이머니", p.cardName)
        assertEquals("구글페이먼트코리아 유한회사", p.merchant)
        assertNull(p.payType)
        assertEquals(false, p.isCancel)
    }

    @Test
    fun `토스페이 결제 - 부가 문구가 본문에 같이 있어도 결제로 판정`() {
        val p = TossNotificationParser.parse(
            "15,000원 결제 완료",
            "토스페이머니 · 구글페이먼트코리아 유한회사\n보너스 포인트 적립 가능",
            null,
            null,
        )
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(15_000L, p.amount)
        assertEquals("구글페이먼트코리아 유한회사", p.merchant)
    }

    @Test
    fun `토스뱅크 체크카드 - 잔액이 아니라 결제 금액을 읽는다`() {
        // 기록 #111 원문 그대로 (text와 bigText가 같고, 둘째 줄에 잔액)
        val body = "토스뱅크 체크카드 | 한성대학교산학협력단 케이키친한\n잔액 740,300원"
        val p = TossNotificationParser.parse(
            title = "10,000원 결제",
            text = body,
            subText = "토스뱅크",
            bigText = body,
        )
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(10_000L, p.amount)
        assertEquals("토스뱅크 체크카드", p.cardName)
        assertEquals("한성대학교산학협력단 케이키친한", p.merchant)
        assertEquals(false, p.isCancel)
    }

    // --- 토스 "카드 알림 받기"로 받은 타 카드사 알림 (2026-10-14 실기기) ---

    @Test
    fun `타 카드사 결제 - 금액은 제목, 카드와 가맹점은 본문`() {
        val p = TossNotificationParser.parse("20,000원 결제", "하나체크 | 네이버페이(일시불)", null, null)
        assertEquals(PaymentKind.PAYMENT, p.kind)
        assertEquals(20_000L, p.amount)
        assertEquals("하나체크", p.cardName)
        assertEquals("네이버페이", p.merchant)
        assertEquals("일시불", p.payType)
        assertEquals(false, p.isCancel)
    }

    @Test
    fun `타 카드사 결제 취소`() {
        val p = TossNotificationParser.parse("20,000원 결제 취소", "하나체크 | 네이버페이(일시불)", null, null)
        assertEquals(PaymentKind.CANCEL, p.kind)
        assertEquals(20_000L, p.amount)
        assertEquals("하나체크", p.cardName)
        assertEquals("네이버페이", p.merchant)
        assertEquals("일시불", p.payType)
        assertEquals(true, p.isCancel)
    }

    @Test
    fun `결제 줄이 없어도 잔액은 금액으로 쓰지 않는다`() {
        val p = TossNotificationParser.parse("토스뱅크", "입출금 알림\n잔액 740,300원", null, null)
        assertNull(p.amount)
    }

    @Test
    fun `토스페이 포인트 적립은 결제가 아님`() {
        val p = TossNotificationParser.parse("포인트 적립", "토스페이 결제 혜택으로 14원 받았어요", null, null)
        assertEquals(PaymentKind.POINT, p.kind)
        assertEquals(14L, p.amount)
        assertNull(p.isCancel)
        assertNull(p.merchant)
    }

    @Test
    fun `만보기처럼 금액 없는 알림은 OTHER`() {
        val p = TossNotificationParser.parse("만보기", "6 걸음", null, null)
        assertEquals(PaymentKind.OTHER, p.kind)
        assertNull(p.amount)
    }

    @Test
    fun `토스페이 결제 취소`() {
        val p = TossNotificationParser.parse("17,000원 결제 취소", "토스페이머니 · 구글페이먼트코리아 유한회사", null, null)
        assertEquals(PaymentKind.CANCEL, p.kind)
        assertEquals(true, p.isCancel)
        assertEquals("구글페이먼트코리아 유한회사", p.merchant)
    }

    @Test
    fun `취소 알림`() {
        val p = TossNotificationParser.parse("카드 알림 받기", "26,400원 결제 취소", "하나체크 | Adobe(일시불)", null)
        assertEquals(26_400L, p.amount)
        assertEquals(true, p.isCancel)
        assertEquals(PaymentKind.CANCEL, p.kind)
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
        assertEquals(PaymentKind.OTHER, p.kind)
        assertTrue(TossNotificationParser.VERSION >= 2)
    }
}
