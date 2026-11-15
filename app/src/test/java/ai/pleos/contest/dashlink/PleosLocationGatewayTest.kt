package ai.pleos.contest.dashlink

import ai.pleos.playground.location.fused.model.LocationData
import org.junit.Assert.*
import org.junit.Test

class PleosLocationGatewayTest {
    @Test fun rejectsNonFiniteAndOutOfRangeCoordinates() {
        val invalid = listOf(
            Double.NaN to 0.0,
            0.0 to Double.NaN,
            Double.POSITIVE_INFINITY to 0.0,
            0.0 to Double.NEGATIVE_INFINITY,
            90.001 to 0.0,
            -90.001 to 0.0,
            0.0 to 180.001,
            0.0 to -180.001,
        )
        invalid.forEach { (latitude, longitude) ->
            val data = LocationData(latitude, longitude, 0.0, 0f, 0f, 1L)
            assertNull(data.toPositionFix(20L, cached = false))
        }
    }

    @Test fun acceptsBoundaryAndZeroCoordinatesWithoutInventingDefaults() {
        listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0).forEach { (latitude, longitude) ->
            val data = LocationData(latitude, longitude, 0.0, 0f, 0f, 1L)
            val fix = requireNotNull(data.toPositionFix(20L, cached = false))
            assertEquals(latitude, fix.latitude, 0.0)
            assertEquals(longitude, fix.longitude, 0.0)
        }
    }

    @Test fun retainsRawTimestampWithoutTreatingRecentReceiptAsFreshPosition() {
        listOf(-1L, 0L, 1_000L, Long.MAX_VALUE).forEach { rawTimestamp ->
            val data = LocationData(37.0, 127.0, 0.0, 0f, 0.8f, rawTimestamp)
            val fix = requireNotNull(data.toPositionFix(50_000L, cached = true))
            assertEquals(rawTimestamp, fix.sourceTimestamp)
            assertEquals(50_000L, fix.receivedElapsedMs)
            assertTrue(fix.quality.contains("마지막 확인 위치(캐시)"))
            assertTrue(fix.quality.contains("최신성 미검증"))
        }
    }

    @Test fun streamingUpdateDoesNotClaimVerifiedAgeOrAccuracy() {
        val data = LocationData(37.0, 127.0, 0.0, 0f, Float.NaN, 500L)
        val fix = requireNotNull(data.toPositionFix(700L, cached = false))
        assertTrue(fix.quality.contains("SDK 위치 업데이트"))
        assertTrue(fix.quality.contains("최신성 미검증"))
        assertTrue(fix.quality.contains("신뢰도 해석 미확인"))
        assertTrue(fix.quality.contains("값 없음"))
    }
}
