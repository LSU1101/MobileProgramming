package com.wherepaid.validator.parse

/**
 * 토스 결제 알림 파서 (참고용).
 *
 * 파싱 규칙은 모두 이 파일에 모아 둔다. 실제 알림 형식이 예상과 다르면 아래 "규칙" 영역만 고치고
 * [VERSION]을 올리면 된다. 앱이 시작될 때 이전 버전으로 파싱된 기록을 다시 파싱한다.
 * 파싱이 실패해도 원본 알림은 항상 DB에 남는다.
 *
 * 확인된 형식:
 *  - 카드 알림 (초기 예시) title="카드 알림 받기"    text="26,400원 결제"
 *                        subText="하나체크 | Adobe(일시불)"
 *  - 타 카드사 (실기기)  title="20,000원 결제" / "20,000원 결제 취소"
 *                        text="하나체크 | 네이버페이(일시불)"
 *  - 토스페이 결제       title="17,000원 결제 완료"
 *                        text="토스페이머니 · 구글페이먼트코리아 유한회사" (+ "보너스 포인트 적립 가능")
 *  - 토스페이 포인트 적립 title="포인트 적립"         text="토스페이 결제 혜택으로 14원 받았어요"
 *  - 결제 실패 (실기기)  title="2,500원 결제 실패"      text="우리체크 | 맘스터치(일시불)" (잔액 부족)
 *  - 토스뱅크 체크카드   title="10,000원 결제"        subText="토스뱅크"
 *                        text="토스뱅크 체크카드 | 한성대학교산학협력단 케이키친한\n잔액 740,300원"
 */
object TossNotificationParser {

    /** 규칙을 바꾸면 올린다. 레코드마다 저장되어 어떤 규칙으로 파싱했는지 추적할 수 있다. */
    const val VERSION = 5

    // ---------------------------------------------------------------------
    // 규칙
    // ---------------------------------------------------------------------

    /** 금액: "26,400원" → 26400. 숫자와 쉼표 뒤에 "원". */
    private val AMOUNT = Regex("""(\d[\d,]*)\s*원""")

    /** 결제 줄: "17,000원 결제 완료", "26,400원 결제", "5,000원 승인". 이 줄이 있어야 결제/취소로 본다. */
    private val PAYMENT_LINE = Regex("""(\d[\d,]*)\s*원\s*(결제|승인|사용)""")

    /** 이 단어가 있는 줄의 금액은 결제 금액이 아니다 ("잔액 740,300원"). */
    private val NON_PAYMENT_AMOUNT_KEYWORDS = listOf("잔액", "누적", "한도")

    /** 이 단어가 있으면 결제가 이뤄지지 않은 알림 (잔액 부족, 한도 초과, 승인 거절 등). 취소보다 먼저 본다. */
    private val FAILED_KEYWORDS = listOf("실패", "거절", "잔액부족", "잔액 부족", "한도초과", "한도 초과", "승인되지 않")

    /** 이 단어가 있으면 취소 알림. */
    private val CANCEL_KEYWORDS = listOf("취소", "환불")

    /** 결제 줄 없이 이 단어가 있으면 포인트/혜택 알림. ("보너스 포인트 적립 가능"은 결제 줄이 같이 있어 결제로 남는다) */
    private val POINT_KEYWORDS = listOf("적립", "캐시백", "받았어요", "혜택으로")

    /**
     * 결제수단(카드)과 가맹점을 나누는 구분자: "하나체크 | Adobe", "토스페이머니 · 구글페이먼트코리아".
     * 가운뎃점은 화면에서 같아 보여도 코드가 여러 가지라 비슷한 문자를 모두 받는다:
     * | ｜ · • ∙ ⋅ ・ ･ ‧ ㆍ
     */
    private val CARD_MERCHANT_SEPARATOR =
        Regex("""\s*[|\uFF5C\u00B7\u2022\u2219\u22C5\u30FB\uFF65\u2027\u318D]\s*""")

    /** 가맹점 끝의 결제방식 괄호: "(일시불)", "(할부 3개월)", "(3개월)". 맨 끝 괄호만 본다. */
    private val PAY_TYPE_SUFFIX = Regex("""\(\s*(일시불|할부[^()]*|\d+\s*개월[^()]*)\s*\)\s*$""")

    // ---------------------------------------------------------------------

    fun parse(title: String?, text: String?, subText: String?, bigText: String?): ParsedPayment {
        // 필드마다 어떤 줄이 들어오는지 앱/OS마다 달라서, 모든 필드의 모든 줄을 한꺼번에 본다.
        val lines = listOfNotNull(title, text, subText, bigText)
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val paymentLine = lines.firstOrNull { PAYMENT_LINE.containsMatchIn(it) }
        val kind = classify(lines, paymentLine != null)
        val amount = paymentLine?.let(::parseAmount)
            ?: lines.filter { l -> NON_PAYMENT_AMOUNT_KEYWORDS.none { it in l } }.firstNotNullOfOrNull(::parseAmount)
        val cardMerchant = if (kind == PaymentKind.PAYMENT || kind == PaymentKind.CANCEL || kind == PaymentKind.FAILED) {
            lines.filter { !AMOUNT.containsMatchIn(it) }.firstNotNullOfOrNull(::parseCardMerchant)
        } else {
            null
        }
        return ParsedPayment(
            kind = kind,
            amount = amount,
            isCancel = when (kind) {
                PaymentKind.CANCEL -> true
                PaymentKind.PAYMENT -> false
                else -> null
            },
            cardMerchant = cardMerchant,
        )
    }

    internal fun classify(lines: List<String>, hasPaymentLine: Boolean): PaymentKind = when {
        lines.any { l -> FAILED_KEYWORDS.any { it in l } } && lines.any { AMOUNT.containsMatchIn(it) } -> PaymentKind.FAILED
        lines.any { l -> CANCEL_KEYWORDS.any { it in l } } && lines.any { AMOUNT.containsMatchIn(it) } -> PaymentKind.CANCEL
        hasPaymentLine -> PaymentKind.PAYMENT
        lines.any { l -> POINT_KEYWORDS.any { it in l } } -> PaymentKind.POINT
        else -> PaymentKind.OTHER
    }

    internal fun parseAmount(s: String): Long? {
        val m = AMOUNT.find(s) ?: return null
        return m.groupValues[1].replace(",", "").toLongOrNull()
    }

    /** "하나체크 | Adobe(일시불)" → (하나체크, Adobe, 일시불). 구분자가 없으면 null. */
    internal fun parseCardMerchant(s: String?): CardMerchant? {
        if (s.isNullOrBlank()) return null
        val parts = s.split(CARD_MERCHANT_SEPARATOR, limit = 2)
        if (parts.size < 2) return null
        val card = parts[0].trim().ifEmpty { null }
        var rest = parts[1].trim()
        var payType: String? = null
        PAY_TYPE_SUFFIX.find(rest)?.let { m ->
            payType = m.groupValues[1].trim()
            rest = rest.substring(0, m.range.first).trim()
        }
        return CardMerchant(cardName = card, merchant = rest.ifEmpty { null }, payType = payType)
    }
}

/**
 * 알림 종류. 분석에서는 PAYMENT/CANCEL만 실결제로 본다.
 * FAILED(결제 실패)는 돈이 나가지 않았지만 매장에서 결제를 시도한 것이라 위치 검증 데이터로는 쓸 수 있다.
 */
enum class PaymentKind { PAYMENT, CANCEL, FAILED, POINT, OTHER }

data class CardMerchant(val cardName: String?, val merchant: String?, val payType: String?)

data class ParsedPayment(
    val kind: PaymentKind,
    val amount: Long?,
    val isCancel: Boolean?,
    private val cardMerchant: CardMerchant?,
) {
    val cardName: String? get() = cardMerchant?.cardName
    val merchant: String? get() = cardMerchant?.merchant
    val payType: String? get() = cardMerchant?.payType
}
