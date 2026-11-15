package ai.pleos.contest.dashlink

enum class IncidentPhase { DISARMED, READY, CHECKING, SIGNALING, CANCELLED, COMPLETE }
enum class IncidentSource { VEHICLE, SIMULATION }

class IncidentController(private val confirmationMs: Long = 10_000L) {
    init { require(confirmationMs > 0) }

    var phase = IncidentPhase.DISARMED
        private set
    var source: IncidentSource? = null
        private set
    private var deadline = 0L
    private val seats = mutableMapOf<String, Boolean?>()

    fun arm() {
        if (phase == IncidentPhase.CHECKING || phase == IncidentPhase.SIGNALING) return
        phase = IncidentPhase.READY
        source = null
    }

    fun observeAirbag(seat: String, deployed: Boolean?, now: Long) {
        val previous = seats.put(seat, deployed)
        // A latched deployment seen at connection time is not a new crash.
        if (phase == IncidentPhase.READY && previous == false && deployed == true) {
            begin(IncidentSource.VEHICLE, now)
        }
    }

    fun simulate(now: Long) {
        if (phase == IncidentPhase.READY) begin(IncidentSource.SIMULATION, now)
    }

    private fun begin(origin: IncidentSource, now: Long) {
        source = origin
        deadline = now + confirmationMs
        phase = IncidentPhase.CHECKING
    }

    fun secondsRemaining(now: Long): Int =
        if (phase == IncidentPhase.CHECKING) ((deadline - now).coerceAtLeast(0L) + 999L).div(1_000L).toInt() else 0

    fun tick(now: Long) {
        if (phase == IncidentPhase.CHECKING && now >= deadline) phase = IncidentPhase.SIGNALING
    }

    fun cancel() {
        if (phase == IncidentPhase.CHECKING || phase == IncidentPhase.SIGNALING) phase = IncidentPhase.CANCELLED
    }

    fun finish() {
        if (phase == IncidentPhase.SIGNALING) phase = IncidentPhase.COMPLETE
    }

    fun disarm() {
        phase = IncidentPhase.DISARMED
        source = null
        seats.clear()
    }
}
