package ai.pleos.contest.dashlink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleLoadingTest {
    private fun reading(status: ReadStatus) = VehicleReading("door", "doors", "문", status)

    @Test fun pendingRequestRemainsLoadingUntilEveryRequestedValueReturns() {
        assertTrue(isVehicleGroupLoading("doors", listOf(reading(ReadStatus.READABLE), reading(ReadStatus.LOADING)), false))
        assertFalse(isVehicleGroupLoading("doors", listOf(reading(ReadStatus.READABLE), reading(ReadStatus.FAILED)), false))
    }

    @Test fun waitingAndUnavailableValuesAreNeverShownAsPendingRequests() {
        listOf(ReadStatus.WAITING, ReadStatus.UNSUPPORTED, ReadStatus.FAILED, ReadStatus.READABLE).forEach {
            assertFalse(isVehicleGroupLoading("doors", listOf(reading(it)), true))
        }
    }

    @Test fun emptyVehicleGroupsLoadOnlyWhileCapabilitiesArePending() {
        assertTrue(isVehicleGroupLoading("doors", emptyList(), true))
        assertFalse(isVehicleGroupLoading("doors", emptyList(), false))
        assertFalse(isVehicleGroupLoading("location", emptyList(), true))
    }

    @Test fun locationLoadsOnlyWhenItsOwnRequestIsPending() {
        assertTrue(isVehicleGroupLoading("location", listOf(VehicleReading("position", "location", "위치", ReadStatus.LOADING)), false))
    }
}
