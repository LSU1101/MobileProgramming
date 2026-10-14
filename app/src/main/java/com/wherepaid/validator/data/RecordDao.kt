package com.wherepaid.validator.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PackageCount(val packageName: String, val count: Int, val lastReceivedAt: Long)

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(record: PaymentRecord): Long

    @Query("SELECT * FROM payment_record ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_record ORDER BY receivedAt ASC")
    suspend fun getAll(): List<PaymentRecord>

    @Query("SELECT * FROM payment_record WHERE id = :id")
    fun observe(id: Long): Flow<PaymentRecord?>

    /** 설정 화면: 실제 토스 패키지명을 확인하기 위한 수신 패키지별 집계 */
    @Query(
        "SELECT packageName, COUNT(*) AS count, MAX(receivedAt) AS lastReceivedAt " +
            "FROM payment_record GROUP BY packageName ORDER BY lastReceivedAt DESC"
    )
    fun observePackageCounts(): Flow<List<PackageCount>>

    @Query(
        """UPDATE payment_record SET
            locStatus = :status, lat = :lat, lng = :lng, accuracyM = :accuracyM,
            locElapsedMs = :elapsedMs, locFixTime = :fixTime, locFixAgeMs = :fixAgeMs,
            locProvider = :provider, locFailReason = :failReason, locErrorMessage = :errorMessage,
            lastKnownLat = :lkLat, lastKnownLng = :lkLng, lastKnownAccuracyM = :lkAccuracyM,
            lastKnownAgeMs = :lkAgeMs
           WHERE id = :id"""
    )
    suspend fun updateLocation(
        id: Long, status: LocStatus,
        lat: Double?, lng: Double?, accuracyM: Float?,
        elapsedMs: Long?, fixTime: Long?, fixAgeMs: Long?, provider: String?,
        failReason: LocFailReason?, errorMessage: String?,
        lkLat: Double?, lkLng: Double?, lkAccuracyM: Float?, lkAgeMs: Long?,
    )

    @Query(
        """UPDATE payment_record SET actualPaidAt = :actualPaidAt, actualStoreName = :storeName,
           channel = :channel, memo = :memo WHERE id = :id"""
    )
    suspend fun updateManual(id: Long, actualPaidAt: Long?, storeName: String?, channel: Channel, memo: String?)

    @Query(
        """UPDATE payment_record SET refLat = :lat, refLng = :lng, refAccuracyM = :accuracyM,
           refCapturedAt = :capturedAt WHERE id = :id"""
    )
    suspend fun updateReference(id: Long, lat: Double?, lng: Double?, accuracyM: Float?, capturedAt: Long?)

    /** 프로세스가 죽어 PENDING으로 남은 요청 정리 (프로세스 시작 시 호출) */
    @Query(
        "UPDATE payment_record SET locStatus = 'FAILED', locFailReason = 'INTERRUPTED' " +
            "WHERE locStatus = 'PENDING' AND receivedAt < :processStartedAt"
    )
    suspend fun markPendingInterrupted(processStartedAt: Long): Int

    @Query("DELETE FROM payment_record WHERE id = :id")
    suspend fun delete(id: Long)

    /** 대상 앱이 아닌 일반 알림 기록 삭제 (테스트 기록은 남긴다) */
    @Query("DELETE FROM payment_record WHERE packageName NOT IN (:packages) AND isTest = 0")
    suspend fun deleteNotIn(packages: List<String>): Int

    @Query("DELETE FROM payment_record WHERE isTest = 1")
    suspend fun deleteTests(): Int
}
