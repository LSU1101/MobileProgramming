package com.wherepaid.validator.parse

/**
 * 토스 결제 알림 파서 (참고용).
 *
 * 파싱 규칙은 모두 이 파일에 모아 둔다. 실제 알림 형식이 예상과 다르면 아래 "규칙" 영역만 고치고
 * [VERSION]을 올리면 된다. 파싱이 실패해도 원본 알림은 항상 DB에 남는다.
 *
 * 기준 예시:
 *   title   = "카드 알림 받기"
 *   text    = "26,400원 결제"
 *   subText = "하나체크 | Adobe(일시불)"
 */
object TossNotificationParser {

    /** 규칙을 바꾸면 올린다. 레코드마다 저장되어 어떤 규칙으로 파싱했는지 추적할 수 있다. */
    const val VERSION = 1

    // ---------------------------------------------------------------------
    // 규칙
    // ---------------------------------------------------------------------

    /** 금액: "26,400원" → 26400. 숫자와 쉼표 뒤에 "원". */
    private val AMOUNT = Regex("""(\d[\d,]*)\s*원""")

    /** 이 단어가 제목/본문에 있으면 취소 알림으로 본다. */
    private val CANCEL_KEYWORDS = listOf("취소", "환불")

    /** 이 단어가 있으면 (취소 단어가 없을 때) 일반 결제로 본다. */
    private val PAYMENT_KEYWORDS = listOf("결제", "승인", "사용")

    /** 보조 텍스트에서 카드명과 가맹점을 나누는 구분자. "하나체크 | Adobe(일시불)" */
    private const val CARD_MERCHANT_SEPARATOR = '|'

    /** 가맹점 끝의 결제방식 괄호: "(일시불)", "(할부 3개월)", "(3개월)". 맨 끝 괄호만 본다. */
    private val PAY_TYPE_SUFFIX = Regex("""\(\s*(일시불|할부[^()]*|\d+\s*개월[^()]*)\s*\)\s*$""")

    // ---------------------------------------------------------------------

    fun parse(title: String?, text: String?, subText: String?, bigText: String?): ParsedPayment {
        val bodies = listOfNotNull(text, bigText, title)
        return ParsedPayment(
            amount = bodies.firstNotNullOfOrNull(::parseAmount),
            isCancel = parseIsCancel(bodies),
            cardMerchant = parseCardMerchant(subText)
                ?: bigText?.lineSequence()?.firstNotNullOfOrNull(::parseCardMerchant),
        )
    }

    internal fun parseAmount(s: String): Long? {
        val m = AMOUNT.find(s) ?: return null
        return m.groupValues[1].replace(",", "").toLongOrNull()
    }

    internal fun parseIsCancel(bodies: List<String>): Boolean? {
        if (bodies.any { b -> CANCEL_KEYWORDS.any { it in b } }) return true
        if (bodies.any { b -> PAYMENT_KEYWORDS.any { it in b } }) return false
        return null
    }

    /** "하나체크 | Adobe(일시불)" → (하나체크, Adobe, 일시불). 구분자가 없으면 null. */
    internal fun parseCardMerchant(s: String?): CardMerchant? {
        if (s.isNullOrBlank()) return null
        val idx = s.indexOf(CARD_MERCHANT_SEPARATOR)
        if (idx < 0) return null
        val card = s.substring(0, idx).trim().ifEmpty { null }
        var rest = s.substring(idx + 1).trim()
        var payType: String? = null
        PAY_TYPE_SUFFIX.find(rest)?.let { m ->
            payType = m.groupValues[1].trim()
            rest = rest.substring(0, m.range.first).trim()
        }
        return CardMerchant(cardName = card, merchant = rest.ifEmpty { null }, payType = payType)
    }
}

data class CardMerchant(val cardName: String?, val merchant: String?, val payType: String?)

data class ParsedPayment(
    val amount: Long?,
    val isCancel: Boolean?,
    private val cardMerchant: CardMerchant?,
) {
    val cardName: String? get() = cardMerchant?.cardName
    val merchant: String? get() = cardMerchant?.merchant
    val payType: String? get() = cardMerchant?.payType
}
