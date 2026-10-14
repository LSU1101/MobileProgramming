package com.wherepaid.validator.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {
    @Test
    fun `같은 점은 0m`() {
        assertEquals(0.0, Geo.distanceMeters(37.5, 127.0, 37.5, 127.0), 1e-9)
    }

    @Test
    fun `위도 0_001도는 약 111m`() {
        assertEquals(111.2, Geo.distanceMeters(37.5000, 127.0, 37.5010, 127.0), 0.5)
    }

    @Test
    fun `서울역-시청 약 1_4km`() {
        // 서울역(37.5547,126.9707) - 서울시청(37.5663,126.9779): 약 1.45km
        assertEquals(1450.0, Geo.distanceMeters(37.5547, 126.9707, 37.5663, 126.9779), 30.0)
    }

    @Test
    fun `좌표가 하나라도 없으면 null`() {
        assertNull(Geo.distanceMetersOrNull(37.5, null, 37.5, 127.0))
    }
}
