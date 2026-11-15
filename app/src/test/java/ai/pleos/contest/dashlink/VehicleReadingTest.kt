package ai.pleos.contest.dashlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleReadingTest {
    @Test fun onlyExactSdkUnsupportedCodesBecomeUnsupported() {
        val sdkType = "ai.pleos.playground.vehicle.callback.exception.a"
        assertEquals(ReadStatus.UNSUPPORTED, ReadingValues.failureStatus(sdkType, "ERROR_NOT_SUPPORTED_PROPERTY"))
        assertEquals(ReadStatus.UNSUPPORTED, ReadingValues.failureStatus(sdkType, "Error code: ERROR_NOT_SUPPORTED_AREA"))
        assertEquals(ReadStatus.FAILED, ReadingValues.failureStatus(sdkType, "ERROR_SECURITY_PERMISSION"))
        assertEquals(ReadStatus.FAILED, ReadingValues.failureStatus("java.lang.Exception", "ERROR_NOT_SUPPORTED_PROPERTY"))
        assertEquals(ReadStatus.FAILED, ReadingValues.failureStatus(sdkType, "Unexpected ERROR_NOT_SUPPORTED_PROPERTY response"))
        assertEquals(ReadStatus.FAILED, ReadingValues.failureStatus(sdkType, null))
    }

    @Test fun batteryPercentageKeepsZeroAndRejectsUnknownOrOutOfRange() {
        assertEquals("0%", ReadingValues.stateOfCharge(0))
        assertEquals("100%", ReadingValues.stateOfCharge(100))
        assertNull(ReadingValues.stateOfCharge(null))
        assertNull(ReadingValues.stateOfCharge(-1))
        assertNull(ReadingValues.stateOfCharge(101))
    }

    @Test fun pressureIsThresholdInKpaNotActualTirePressure() {
        assertEquals("0.0 kPa", ReadingValues.lowPressure(0f))
        assertEquals("180.5 kPa", ReadingValues.lowPressure(180.5f))
        assertNull(ReadingValues.lowPressure(null))
        assertNull(ReadingValues.lowPressure(Float.NaN))
        assertNull(ReadingValues.lowPressure(Float.POSITIVE_INFINITY))
        assertNull(ReadingValues.lowPressure(-0.1f))
    }

    @Test fun doorPositionUsesReturnedBoundsWithoutInventedDegrees() {
        assertEquals("닫힘", ReadingValues.doorPosition(0, 0, 100))
        assertEquals("완전히 열림", ReadingValues.doorPosition(100, 0, 100))
        assertEquals("일부 열림", ReadingValues.doorPosition(40, 0, 100))
        assertNull(ReadingValues.doorPosition(101, 0, 100))
        assertNull(ReadingValues.doorPosition(null, 0, 100))
        assertNull(ReadingValues.doorPosition(0, 1, 100))
        assertNull(ReadingValues.doorPosition(0, 0, 0))
    }
}
