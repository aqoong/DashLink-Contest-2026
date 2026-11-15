package ai.pleos.contest.dashlink

data class MorsePulse(val on: Boolean, val durationMs: Long)

data class MorseProgress(val completedSymbols: Int = 0, val activeSymbol: Int? = null)

enum class MorseMessage(val title: String, val wireText: String) {
    SOS("SOS", "SOS"),
    OK("OK", "OK"),
    THANKS("고마워", "THANKS"),
    SORRY("미안해", "SORRY"),
}

object MorseCode {
    // ITU-R M.1677-1 letter timing; the Korean labels send the displayed English words.
    private val letters = mapOf(
        'A' to ".-", 'H' to "....", 'K' to "-.-", 'N' to "-.", 'O' to "---",
        'R' to ".-.", 'S' to "...", 'T' to "-", 'Y' to "-.--",
    )

    fun displayGroups(message: MorseMessage): List<String> =
        if (message == MorseMessage.SOS) List(3) { "...---..." }
        else message.wireText.map { letters.getValue(it) }

    fun sequence(message: MorseMessage, dotMs: Long = 300L): List<MorsePulse> {
        require(dotMs in 100L..2_000L)
        if (message == MorseMessage.SOS) return MorseSos.sequence(dotMs)
        return buildList {
            message.wireText.forEachIndexed { letterIndex, letter ->
                if (letterIndex > 0) add(MorsePulse(false, dotMs * 3))
                letters.getValue(letter).forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) add(MorsePulse(false, dotMs))
                    add(MorsePulse(true, dotMs * if (symbol == '.') 1 else 3))
                }
            }
        }
    }
}

object MorseSos {
    // Emulator demonstration timing, not a certified vehicle lighting profile.
    fun sequence(dotMs: Long = 300L, cycles: Int = 3): List<MorsePulse> {
        require(dotMs in 100L..2_000L && cycles in 1..5)
        return buildList {
            repeat(cycles) { cycle ->
                "...---...".forEachIndexed { index, symbol ->
                    add(MorsePulse(true, dotMs * if (symbol == '.') 1 else 3))
                    if (index < 8) add(MorsePulse(false, dotMs))
                }
                if (cycle < cycles - 1) add(MorsePulse(false, dotMs * 7))
            }
        }
    }
}
