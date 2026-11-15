package ai.pleos.contest.dashlink

import org.junit.Assert.*
import org.junit.Test

class IncidentControllerTest {
    @Test fun initialDeployedAndUnknownAreNotFreshAccidents() {
        val controller = IncidentController()
        controller.arm()
        controller.observeAirbag("driver", true, 100L)
        assertEquals(IncidentPhase.READY, controller.phase)
        controller.observeAirbag("driver", null, 200L)
        controller.observeAirbag("driver", true, 300L)
        assertEquals(IncidentPhase.READY, controller.phase)
    }

    @Test fun freshDeploymentStartsOneCountdownAndDoesNotRestartOnDuplicates() {
        val controller = IncidentController(10_000L)
        controller.observeAirbag("driver", false, 0L)
        controller.arm()
        controller.observeAirbag("driver", true, 1_000L)
        assertEquals(IncidentPhase.CHECKING, controller.phase)
        assertEquals(IncidentSource.VEHICLE, controller.source)
        controller.observeAirbag("driver", true, 8_000L)
        controller.tick(10_999L)
        assertEquals(IncidentPhase.CHECKING, controller.phase)
        controller.tick(11_000L)
        assertEquals(IncidentPhase.SIGNALING, controller.phase)
    }

    @Test fun cancellationAndRearmingDoNotRetriggerADeployedSeat() {
        val controller = IncidentController()
        controller.observeAirbag("driver", false, 0L)
        controller.arm()
        controller.observeAirbag("driver", true, 1L)
        controller.cancel()
        controller.tick(100_000L)
        assertEquals(IncidentPhase.CANCELLED, controller.phase)
        controller.arm()
        controller.observeAirbag("driver", true, 100_001L)
        assertEquals(IncidentPhase.READY, controller.phase)
    }

    @Test fun backgroundDisarmsAndDropsOldBaselines() {
        val controller = IncidentController()
        controller.observeAirbag("driver", false, 0L)
        controller.arm()
        controller.disarm()
        controller.arm()
        controller.observeAirbag("driver", true, 5_000L)
        assertEquals(IncidentPhase.READY, controller.phase)
    }

    @Test fun simulationRequiresArmingAndKeepsItsSource() {
        val controller = IncidentController(5_000L)
        controller.simulate(0L)
        assertEquals(IncidentPhase.DISARMED, controller.phase)
        controller.arm()
        controller.simulate(100L)
        assertEquals(IncidentSource.SIMULATION, controller.source)
        assertEquals(5, controller.secondsRemaining(100L))
        controller.tick(5_100L)
        controller.finish()
        assertEquals(IncidentPhase.COMPLETE, controller.phase)
    }
}
