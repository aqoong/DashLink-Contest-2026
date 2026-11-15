package ai.pleos.contest.dashlink

import org.junit.Assert.*
import org.junit.Test

class SosPlaybackTest {
    @Test fun capturesBeforeWritingAndWaitsForAckBeforeTimingPulse() {
        val h = Harness()
        h.player.start()
        assertTrue(h.player.busy)
        assertTrue(h.writes.isEmpty())
        h.captured(true)
        assertEquals(listOf(true), h.writes.map { it.first })
        assertTrue(h.scheduled.isEmpty())
        h.writes[0].second(true, "ack")
        assertEquals(300L, h.scheduled.single().first)
        h.next()
        assertEquals(listOf(true, false), h.writes.map { it.first })
    }

    @Test fun completesThreeCyclesThenWaitsForRestoration() {
        val h = Harness()
        h.player.start()
        h.captured(true)
        repeat(53) { index ->
            assertEquals(index + 1, h.writes.size)
            assertEquals(0, h.restoreCount)
            h.writes[index].second(true, "ack")
            h.next()
        }
        assertEquals(27, h.writes.count { it.first })
        assertEquals(24_900L, h.delays.sum())
        assertEquals(1, h.restoreCount)
        assertTrue(h.player.busy)
        assertTrue(h.finished.isEmpty())
        h.restored(true)
        assertFalse(h.player.busy)
        assertEquals(listOf(true), h.finished)
        assertTrue(h.player.completedSignal)
    }

    @Test fun snapshotFailureNeverTurnsLampOn() {
        val h = Harness()
        h.player.start()
        h.captured(false)
        h.captured(true)
        assertTrue(h.writes.isEmpty())
        assertEquals(0, h.restoreCount)
        assertFalse(h.player.busy)
        assertEquals(listOf(false), h.finished)
    }

    @Test fun stopDuringCaptureWaitsThenRestoresWithoutWriting() {
        val h = Harness()
        h.player.start()
        h.player.stop()
        assertTrue(h.player.busy)
        assertEquals(0, h.restoreCount)
        h.captured(true)
        assertTrue(h.writes.isEmpty())
        assertEquals(1, h.restoreCount)
        h.restored(true)
        assertEquals(listOf(true), h.finished)
    }

    @Test fun stopDuringWriteWaitsForItsAckBeforeRestoring() {
        val h = Harness()
        h.player.start()
        h.captured(true)
        h.player.stop()
        assertEquals(0, h.restoreCount)
        h.writes.single().second(true, "ack")
        assertTrue(h.scheduled.isEmpty())
        assertEquals(1, h.restoreCount)
        h.player.stop()
        h.writes.single().second(true, "duplicate")
        assertEquals(1, h.restoreCount)
        h.restored(true)
        assertEquals(listOf(true), h.finished)
    }

    @Test fun stopCancelsWaitAndIgnoresAlreadyQueuedTimer() {
        val h = Harness()
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        val staleTimer = h.scheduled.single().second
        h.player.stop()
        assertTrue(h.scheduled.isEmpty())
        staleTimer()
        assertEquals(1, h.writes.size)
        assertEquals(1, h.restoreCount)
    }

    @Test fun writeFailureStillRestoresAndReportsRestorationResult() {
        val h = Harness()
        h.player.start()
        h.captured(true)
        h.writes.single().second(false, "disconnected")
        assertEquals(1, h.restoreCount)
        assertTrue(h.scheduled.isEmpty())
        h.restored(true)
        assertEquals(listOf(true), h.finished)
        assertTrue(h.updates.any { "disconnected" in it.first })
        assertFalse(h.player.completedSignal)
    }

    @Test fun restorationFailureFinishesOnceWithoutClaimingSuccess() {
        val h = Harness()
        h.player.start()
        h.player.stop()
        h.captured(true)
        h.restored(false)
        h.restored(true)
        h.player.stop()
        assertEquals(listOf(false), h.finished)
        assertFalse(h.player.busy)
    }

    @Test fun oldCallbacksCannotWriteOrFinishAfterRestart() {
        val h = Harness()
        h.player.start()
        val oldCapture = h.captureCallback!!
        h.captured(true)
        val oldWrite = h.writes.single().second
        h.player.stop()
        oldWrite(true, "ack")
        val oldRestore = h.restoreCallback!!
        oldRestore(true, "restored")
        h.player.start()
        oldCapture(true, "late")
        oldWrite(true, "late")
        oldRestore(false, "late")
        assertTrue(h.player.busy)
        assertEquals(1, h.writes.size)
        assertEquals(listOf(true), h.finished)
        h.captured(true)
        assertEquals(2, h.writes.size)
    }

    @Test fun repeatedStartCannotReplaceSnapshotOfActivePlayback() {
        val h = Harness()
        h.player.stop()
        h.player.start()
        h.player.start()
        h.captured(true)
        h.player.start()
        assertEquals(1, h.captureCount)
        assertEquals(1, h.writes.size)
        assertTrue(h.finished.isEmpty())
    }

    @Test fun synchronousWriteExceptionStillRestoresBeforeFinishing() {
        var restores = 0
        val finished = mutableListOf<Boolean>()
        val player = SosPlayback(
            schedule = { _, _ -> fail("Failed write must not schedule a pulse") },
            cancelScheduled = {},
            capture = { it(true, "snapshot") },
            write = { _, _ -> throw IllegalStateException("write failure") },
            restore = { restores++; it(true, "restore") },
            onUpdate = { _, _ -> },
            onFinished = { finished += it },
        )
        player.start()
        assertEquals(1, restores)
        assertEquals(listOf(true), finished)
        assertFalse(player.busy)
        assertFalse(player.completedSignal)
    }

    @Test fun oneShotMessageIgnoresRepeatedTapAndFinishesExactlyOnce() {
        for (message in listOf(MorseMessage.OK, MorseMessage.THANKS, MorseMessage.SORRY)) {
            val pulses = MorseCode.sequence(message)
            val h = Harness(pulses, message.title)
            h.player.start()
            h.player.start()
            h.captured(true)
            pulses.forEachIndexed { index, pulse ->
                h.player.start()
                assertEquals(pulse.on, h.writes[index].first)
                h.writes[index].second(true, "ack")
                h.next()
            }
            assertEquals(1, h.captureCount)
            assertEquals(pulses.size, h.writes.size)
            assertEquals(pulses.map { it.durationMs }, h.delays)
            assertEquals(1, h.restoreCount)
            assertTrue(h.player.completedSignal)
            h.restored(true)
            assertEquals(listOf(true), h.finished)
            assertTrue(h.scheduled.isEmpty())
            assertTrue(h.updates.any { it.first == message.title + " 전송 중" })
        }
    }

    @Test fun cancellationDuringFinalPulseDoesNotClaimWholeSignalCompleted() {
        val pulses = MorseCode.sequence(MorseMessage.OK)
        val h = Harness(pulses)
        h.player.start()
        h.captured(true)
        repeat(pulses.size - 1) { index ->
            h.writes[index].second(true, "ack")
            h.next()
        }
        h.writes.last().second(true, "ack")
        h.player.stop()
        h.restored(true)
        assertFalse(h.player.completedSignal)
        assertTrue(h.scheduled.isEmpty())
        assertEquals(listOf(true), h.finished)
    }

    @Test fun schedulerFailureRestoresButDoesNotClaimCompletedSignal() {
        val h = Harness(failSchedule = true)
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        assertEquals(1, h.restoreCount)
        h.restored(true)
        assertEquals(listOf(true), h.finished)
        assertFalse(h.player.completedSignal)
    }

    @Test fun restartResetsPreviousCompletion() {
        val h = Harness(listOf(MorsePulse(true, 100L)))
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        h.next()
        h.restored(true)
        assertTrue(h.player.completedSignal)
        h.player.start()
        assertFalse(h.player.completedSignal)
    }

    @Test fun completedPatternAndFailedRestorationRemainSeparateResults() {
        val h = Harness(listOf(MorsePulse(true, 100L)))
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        h.next()
        h.restored(false)
        assertTrue(h.player.completedSignal)
        assertEquals(listOf(false), h.finished)
    }

    @Test fun progressWaitsForAcceptedOnAndOffRequests() {
        val h = Harness(MorseCode.sequence(MorseMessage.OK))
        h.player.start()
        h.captured(true)
        assertEquals(listOf(MorseProgress()), h.progress)
        h.writes[0].second(true, "ack")
        assertEquals(MorseProgress(0, 0), h.progress.last())
        h.next()
        assertEquals(MorseProgress(0, 0), h.progress.last())
        h.writes[1].second(true, "ack")
        assertEquals(MorseProgress(1), h.progress.last())
        h.next()
        h.writes[2].second(false, "failed")
        assertEquals(MorseProgress(1), h.progress.last())
        h.writes[2].second(true, "late")
        assertEquals(MorseProgress(1), h.progress.last())
    }

    @Test fun finalProgressCompletesOnlyAfterFinalPulseTimeAndDoesNotClaimRestoration() {
        val h = Harness(listOf(MorsePulse(true, 100L)))
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        assertEquals(MorseProgress(0, 0), h.progress.last())
        h.next()
        assertEquals(MorseProgress(1), h.progress.last())
        assertTrue(h.player.completedSignal)
        assertTrue(h.finished.isEmpty())
        h.restored(false)
        assertEquals(MorseProgress(1), h.progress.last())
        assertEquals(listOf(false), h.finished)
    }

    @Test fun stopDoesNotMarkAnInterruptedSymbolComplete() {
        val h = Harness(listOf(MorsePulse(true, 100L)))
        h.player.start()
        h.captured(true)
        h.writes.single().second(true, "ack")
        h.player.stop()
        assertEquals(MorseProgress(), h.progress.last())
        assertFalse(h.player.completedSignal)
    }

    @Test fun malformedPulseSequencesAreRejectedBeforeCapturingLamp() {
        listOf(
            emptyList(),
            listOf(MorsePulse(true, 0L)),
            listOf(MorsePulse(false, 100L)),
            listOf(MorsePulse(true, 100L), MorsePulse(true, 100L)),
        ).forEach { pulses ->
            assertThrows(IllegalArgumentException::class.java) { Harness(pulses) }
        }
    }

    private class Harness(
        pulses: List<MorsePulse> = MorseSos.sequence(),
        signalLabel: String = "SOS",
        failSchedule: Boolean = false,
    ) {
        var captureCount = 0
        var restoreCount = 0
        var captureCallback: ((Boolean, String) -> Unit)? = null
        var restoreCallback: ((Boolean, String) -> Unit)? = null
        val writes = mutableListOf<Pair<Boolean, (Boolean, String) -> Unit>>()
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val delays = mutableListOf<Long>()
        val finished = mutableListOf<Boolean>()
        val updates = mutableListOf<Pair<String, Boolean?>>()
        val progress = mutableListOf<MorseProgress>()
        val player = SosPlayback(
            schedule = { delay, action ->
                if (failSchedule) error("scheduler failure")
                scheduled += delay to action
                delays += delay
            },
            cancelScheduled = { scheduled.clear() },
            capture = { captureCount++; captureCallback = it },
            write = { on, done -> writes += on to done },
            restore = { restoreCount++; restoreCallback = it },
            onUpdate = { detail, requestedOn -> updates += detail to requestedOn },
            onFinished = { finished += it },
            pulses = pulses,
            signalLabel = signalLabel,
            onProgress = { progress += it },
        )

        fun captured(ok: Boolean) = captureCallback!!(ok, "snapshot")
        fun restored(ok: Boolean) = restoreCallback!!(ok, "restore")
        fun next() = scheduled.removeAt(0).second()
    }
}
