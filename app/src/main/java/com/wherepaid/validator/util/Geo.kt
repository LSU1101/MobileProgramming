package com.wherepaid.validator.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** 두 좌표 사이의 거리(m, 하버사인). 100m 수준에서는 android.location.Location과 차이가 무시할 만하다. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** 둘 중 하나라도 없으면 null. */
    fun distanceMetersOrNull(lat1: Double?, lng1: Double?, lat2: Double?, lng2: Double?): Double? =
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) null
        else distanceMeters(lat1, lng1, lat2, lng2)
}
