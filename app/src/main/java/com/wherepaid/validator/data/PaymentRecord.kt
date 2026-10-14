package com.wherepaid.validator.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 알림 1건 = 행 1개. 같은 notificationKey가 다시 와도 새 행으로 쌓는다(중복 수신 패턴 분석용).
 * 이 클래스는 Android API에 의존하지 않는다(CSV/테스트를 JVM에서 돌릴 수 있게).
 */
@Entity(
    tableName = "payment_record",
    indices = [Index("receivedAt"), Index("packageName"), Index("notificationKey")],
)
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // --- 테스트 알림 여부 (v2) ---
    /** 앱이 스스로 띄운 테스트 알림이면 true. 실제 결제 분석에서는 제외한다. */
    @ColumnInfo(defaultValue = "0") val isTest: Boolean = false,
    /** 테스트 알림 예약 시각. receivedAt과의 차이 = 알람 지연 */
    val testScheduledAt: Long? = null,

    // --- 원본 알림 ---
    val packageName: String,
    val notificationKey: String,
    val postTime: Long,
    /** System.currentTimeMillis() at onNotificationPosted */
    val receivedAt: Long,
    val title: String?,
    val text: String?,
    val subText: String?,
    val bigText: String?,
    /** InboxStyle 줄들, 줄바꿈으로 연결 */
    val textLines: String?,
    val category: String?,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    /** extras 전체 key=value (예상 못한 키 확인용) */
    val extrasDump: String?,

    // --- 파싱 (참고용) ---
    val parsedAmount: Long? = null,
    val parsedCardName: String? = null,
    val parsedMerchant: String? = null,
    val parsedPayType: String? = null,
    val parsedIsCancel: Boolean? = null,
    val parserVersion: Int? = null,

    // --- 위치 결과 ---
    val locStatus: LocStatus,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Float? = null,
    /** getCurrentLocation 요청 시작 ~ 결과까지 */
    val locElapsedMs: Long? = null,
    /** Location.time (위성/네트워크가 측정한 시각) */
    val locFixTime: Long? = null,
    /** 결과를 받은 시점에 그 좌표가 얼마나 오래된 것인지 */
    val locFixAgeMs: Long? = null,
    val locProvider: String? = null,
    val locFailReason: LocFailReason? = null,
    val locErrorMessage: String? = null,

    // getLastLocation 스냅샷 (새 측정과 별개)
    val lastKnownLat: Double? = null,
    val lastKnownLng: Double? = null,
    val lastKnownAccuracyM: Float? = null,
    val lastKnownAgeMs: Long? = null,

    // --- 수신 시점 기기 상태 ---
    val permFine: Boolean,
    val permCoarse: Boolean,
    val permBackground: Boolean,
    val locationEnabled: Boolean,
    val batteryOptIgnored: Boolean,
    val powerSaveMode: Boolean,
    val deviceIdleMode: Boolean,
    /** ActivityManager importance 값 (100=foreground, 125=fg service, 400=cached ...) */
    val appImportance: Int,
    val appImportanceLabel: String,

    // --- 수동 입력 ---
    val actualPaidAt: Long? = null,
    val actualStoreName: String? = null,
    val channel: Channel = Channel.UNKNOWN,
    val memo: String? = null,
    val refLat: Double? = null,
    val refLng: Double? = null,
    val refAccuracyM: Float? = null,
    val refCapturedAt: Long? = null,
)

enum class LocStatus { PENDING, SUCCESS, FAILED, SKIPPED }

enum class LocFailReason {
    /** FINE/COARSE 둘 다 없음 */
    NO_LOCATION_PERMISSION,

    /** 백그라운드 상태 + 백그라운드 위치 권한 없음 + 결과 없음 */
    NO_BACKGROUND_PERMISSION_WHILE_BACKGROUND,

    /** 기기 위치 서비스 꺼짐 */
    LOCATION_DISABLED,
    TIMEOUT_10S,
    NULL_RESULT,
    SECURITY_EXCEPTION,
    PLAY_SERVICES_ERROR,

    /** 요청 도중 프로세스가 종료되어 PENDING으로 남았던 건 */
    INTERRUPTED,
}

enum class Channel { UNKNOWN, OFFLINE, ONLINE }
