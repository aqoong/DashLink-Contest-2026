package ai.pleos.contest.dashlink

import ai.pleos.playground.vehicle.constant.control.LightSwitch
import org.junit.Assert.*
import org.junit.Test

class LampRecoveryJournalTest {
    private class Store(var state: LampRecoveryState = LampRecoveryState()) {
        var writable = true
        fun journal() = LampRecoveryJournal({ state }) { next ->
            state = next
            writable
        }
    }

    @Test fun originalSurvivesNewJournalAndOnlyMatchingReadUnlocks() {
        val store = Store()
        assertTrue(store.journal().capture(Lamp.HIGH_BEAM, LightSwitch.AUTOMATIC))
        val restored = store.journal()
        assertTrue(restored.state.required)
        val token = requireNotNull(restored.beginWrite())
        assertTrue(restored.finishWrite(token))
        assertFalse(restored.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, token))
        assertFalse(restored.clearIfVerified(Lamp.TAIL, LightSwitch.AUTOMATIC, true, token))
        assertFalse(restored.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.AUTOMATIC, false, token))
        assertTrue(restored.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.AUTOMATIC, true, token))
        assertEquals(LampRecoveryState(), store.state)
    }

    @Test fun legacyLatchWithoutOriginalCannotBeCapturedWrittenOrCleared() {
        val store = Store(LampRecoveryState(required = true))
        val journal = store.journal()
        assertFalse(journal.capture(Lamp.HIGH_BEAM, LightSwitch.OFF))
        assertNull(journal.beginWrite())
        assertFalse(journal.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, "unknown"))
        assertTrue(store.state.required)
    }

    @Test fun unresolvedWriteSurvivesRestartAndBlocksRestoreUntilItsTerminalCallback() {
        val store = Store()
        val journal = store.journal()
        assertTrue(journal.capture(Lamp.HIGH_BEAM, LightSwitch.OFF))
        val token = requireNotNull(journal.beginWrite())
        val restarted = store.journal()
        assertNull(restarted.beginWrite())
        assertFalse(restarted.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, token))
        assertFalse(restarted.finishWrite("another request"))
        assertTrue(restarted.finishWrite(token))
        assertTrue(store.state.required)
        assertNotNull(restarted.beginWrite())
    }

    @Test fun lateOldCallbackCannotClearANewerWrite() {
        val store = Store()
        val journal = store.journal()
        journal.capture(Lamp.REAR_FOG, LightSwitch.OFF)
        val first = requireNotNull(journal.beginWrite())
        assertTrue(journal.finishWrite(first))
        val second = requireNotNull(journal.beginWrite())
        assertFalse(journal.finishWrite(first))
        assertEquals(second, store.state.inFlight)
    }

    @Test fun failedPersistenceNeverAuthorizesWriteOrUnlock() {
        val store = Store()
        var journal = store.journal()
        store.writable = false
        assertFalse(journal.capture(Lamp.HIGH_BEAM, LightSwitch.OFF))
        assertNull(journal.beginWrite())
        store.writable = true
        journal = store.journal()
        assertTrue(journal.capture(Lamp.HIGH_BEAM, LightSwitch.OFF))
        store.writable = false
        assertNull(journal.beginWrite())
        assertFalse(journal.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, "unknown"))
        assertTrue(store.state.required)
    }

    @Test fun failedClearRestoresLatchEvenWhenStoreAlreadyChangedMemory() {
        val store = Store()
        val journal = store.journal()
        journal.capture(Lamp.HIGH_BEAM, LightSwitch.OFF)
        val token = requireNotNull(journal.beginWrite())
        assertTrue(journal.finishWrite(token))
        store.writable = false
        assertFalse(journal.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, token))
        assertTrue(store.state.required)
        assertEquals(LightSwitch.OFF, store.journal().state.original)
    }

    @Test fun finishingWriteDoesNotClearOriginalEvenWhenItFailed() {
        val store = Store()
        val journal = store.journal()
        journal.capture(Lamp.TAIL, LightSwitch.ON)
        val token = requireNotNull(journal.beginWrite())
        assertTrue(journal.finishWrite(token))
        assertEquals(LightSwitch.ON, store.state.original)
        assertTrue(store.state.required)
    }

    @Test fun staleReadCannotClearAfterANewerGatewayWriteWasAcknowledged() {
        val store = Store()
        val oldGateway = store.journal()
        oldGateway.capture(Lamp.HIGH_BEAM, LightSwitch.OFF)
        val oldRestore = requireNotNull(oldGateway.beginWrite())
        assertTrue(oldGateway.finishWrite(oldRestore))
        val newGateway = store.journal()
        val newerWrite = requireNotNull(newGateway.beginWrite())
        assertTrue(newGateway.finishWrite(newerWrite))
        assertFalse(oldGateway.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, oldRestore))
        assertTrue(store.state.required)
        assertEquals(newerWrite, store.state.revision)
        assertTrue(newGateway.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, newerWrite))
    }

    @Test fun staleReadCannotClearANewCaptureWithTheSameOriginal() {
        val store = Store()
        val oldGateway = store.journal()
        oldGateway.capture(Lamp.HIGH_BEAM, LightSwitch.OFF)
        val oldRestore = requireNotNull(oldGateway.beginWrite())
        assertTrue(oldGateway.finishWrite(oldRestore))
        assertTrue(oldGateway.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, oldRestore))
        val newGateway = store.journal()
        assertTrue(newGateway.capture(Lamp.HIGH_BEAM, LightSwitch.OFF))
        assertFalse(oldGateway.clearIfVerified(Lamp.HIGH_BEAM, LightSwitch.OFF, true, oldRestore))
        assertTrue(store.state.required)
        assertNotEquals(oldRestore, store.state.revision)
    }
}
