package ai.pleos.contest.dashlink

import org.junit.Assert.*
import org.junit.Test

class MorseSosTest {
    @Test fun sosIsContinuousNineElementsWithNoLetterGaps() {
        val pulses = MorseSos.sequence(dotMs = 300L, cycles = 1)
        assertEquals(9, pulses.count { it.on })
        assertEquals(listOf(300L,300L,300L,900L,900L,900L,300L,300L,300L),
            pulses.filter { it.on }.map { it.durationMs })
        assertEquals(23L * 300L, pulses.sumOf { it.durationMs })
        assertTrue(pulses.filter { !it.on }.all { it.durationMs == 300L })
    }

    @Test fun repetitionHasSevenUnitGapAndIsBounded() {
        val pulses = MorseSos.sequence(200L, 3)
        assertEquals(27, pulses.count { it.on })
        assertEquals(2, pulses.count { !it.on && it.durationMs == 1_400L })
        assertEquals((23L * 3 + 7L * 2) * 200L, pulses.sumOf { it.durationMs })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedOrInvalidPlayback() { MorseSos.sequence(0L, 0) }

    @Test fun ordinaryMessagesHaveExactLettersAndRunOnce() {
        val expected = mapOf(
            MorseMessage.OK to "--- -.-",
            MorseMessage.THANKS to "- .... .- -. -.- ...",
            MorseMessage.SORRY to "... --- .-. .-. -.--",
        )
        val totalUnits = mapOf(MorseMessage.OK to 23L, MorseMessage.THANKS to 49L, MorseMessage.SORRY to 55L)
        expected.forEach { (message, code) ->
            val pulses = MorseCode.sequence(message, 100L)
            val observed = buildString {
                pulses.forEach { pulse ->
                    if (pulse.on) append(if (pulse.durationMs == 100L) '.' else '-')
                    else if (pulse.durationMs == 300L) append(' ')
                }
            }
            assertEquals(message.wireText, code, observed)
            assertEquals(totalUnits.getValue(message) * 100L, pulses.sumOf { it.durationMs })
            assertTrue(pulses.first().on)
            assertTrue(pulses.last().on)
            assertTrue(pulses.zipWithNext().all { (a, b) -> a.on != b.on })
            assertTrue(pulses.filter { !it.on }.all { it.durationMs == 100L || it.durationMs == 300L })
        }
    }

    @Test fun sosMessagePreservesExistingThreeCycleProsign() {
        assertEquals(MorseSos.sequence(), MorseCode.sequence(MorseMessage.SOS))
    }

    @Test fun displayedSymbolsMatchEveryTransmittedPulse() {
        MorseMessage.entries.forEach { message ->
            val displayed = MorseCode.displayGroups(message).joinToString("")
            val sent = MorseCode.sequence(message, 100L).filter { it.on }
                .joinToString("") { if (it.durationMs == 100L) "." else "-" }
            assertEquals(message.wireText, sent, displayed)
        }
        assertEquals(listOf("---", "-.-"), MorseCode.displayGroups(MorseMessage.OK))
        assertEquals(3, MorseCode.displayGroups(MorseMessage.SOS).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ordinaryMessageRejectsInvalidDotDuration() { MorseCode.sequence(MorseMessage.OK, 99L) }
}
