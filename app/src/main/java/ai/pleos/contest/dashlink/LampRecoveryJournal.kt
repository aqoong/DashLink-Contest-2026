package ai.pleos.contest.dashlink

import ai.pleos.playground.vehicle.constant.control.LightSwitch
import java.util.UUID

data class LampRecoveryState(
    val required: Boolean = false,
    val lamp: Lamp? = null,
    val original: LightSwitch? = null,
    val inFlight: String? = null,
    val revision: String? = null,
)

// Read durable state each time so a late callback cannot overwrite a newer gateway's write.
class LampRecoveryJournal(
    private val read: () -> LampRecoveryState,
    private val save: (LampRecoveryState) -> Boolean,
) {
    private var storageFailed = false
    val state: LampRecoveryState get() = read().let { if (storageFailed) it.copy(required = true) else it }

    private fun persist(next: LampRecoveryState): Boolean {
        val previous = read()
        if (save(next)) return true
        storageFailed = true
        // SharedPreferences changes memory even when commit fails; restore the old latch in memory too.
        save(previous)
        return false
    }

    fun capture(lamp: Lamp, original: LightSwitch): Boolean {
        if (storageFailed || state.required || state.inFlight != null) return false
        return persist(LampRecoveryState(required = true, lamp = lamp, original = original, revision = UUID.randomUUID().toString()))
    }

    fun beginWrite(): String? {
        val current = state
        if (storageFailed || !current.required || current.lamp == null || current.original == null || current.inFlight != null) return null
        val token = UUID.randomUUID().toString()
        return token.takeIf { persist(current.copy(inFlight = it, revision = it)) }
    }

    fun finishWrite(token: String): Boolean {
        val current = state
        if (storageFailed || current.inFlight != token) return false
        return persist(current.copy(inFlight = null))
    }

    fun clearIfVerified(lamp: Lamp, observed: LightSwitch?, controlAllowed: Boolean, expectedWriteToken: String): Boolean {
        val current = state
        if (storageFailed || !controlAllowed || !current.required || current.lamp != lamp || current.original == null ||
            observed != current.original || current.inFlight != null || current.revision != expectedWriteToken) return false
        return persist(LampRecoveryState())
    }
}
