package ai.pleos.contest.dashlink

// Call methods and callbacks on one serialized thread; the gateway supplies request timeouts.
class SosPlayback(
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
    private val capture: ((Boolean, String) -> Unit) -> Unit,
    private val write: (Boolean, (Boolean, String) -> Unit) -> Unit,
    private val restore: ((Boolean, String) -> Unit) -> Unit,
    private val onUpdate: (String, Boolean?) -> Unit,
    private val onFinished: (Boolean) -> Unit,
    pulses: List<MorsePulse> = MorseSos.sequence(),
    private val signalLabel: String = "SOS",
    private val onProgress: (MorseProgress) -> Unit = {},
    private val repeatIntervalMs: Long? = null,
    private val canStartBatch: () -> Boolean = { true },
) {
    private enum class Phase { IDLE, CAPTURE, WRITE, WAIT, RESTORE, PAUSE }

    private val pulses = pulses.toList()
    private var phase = Phase.IDLE
    private var operation = 0L
    private var pulseIndex = 0
    private var stopping = false
    private var progress = MorseProgress()

    val busy: Boolean get() = phase != Phase.IDLE
    val waitingToRepeat: Boolean get() = phase == Phase.PAUSE
    var batchNumber: Int = 0
        private set
    var completedSignal: Boolean = false
        private set

    init {
        require(this.pulses.isNotEmpty() && this.pulses.first().on && this.pulses.last().on)
        require(this.pulses.all { it.durationMs > 0 } && this.pulses.zipWithNext().all { (a, b) -> a.on != b.on })
        require(repeatIntervalMs == null || repeatIntervalMs > 0)
    }

    fun start() {
        if (busy) return
        stopping = false
        batchNumber = 0
        startBatch()
    }

    private fun startBatch() {
        pulseIndex = 0
        completedSignal = false
        phase = Phase.CAPTURE
        progress = MorseProgress()
        onProgress(progress)
        if (!runCatching { canStartBatch() }.getOrDefault(false)) {
            finish(false, "조명 출력 조건이 바뀌어 중지됨")
            return
        }
        batchNumber++
        onUpdate("원래 조명 상태 확인 중", null)
        request(capture) { ok, detail ->
            when {
                !ok -> finish(false, "원래 상태 확인 실패: $detail")
                stopping -> restoreOriginal()
                else -> writePulse()
            }
        }
    }

    fun stop() {
        if (!busy) return
        stopping = true
        when (phase) {
            Phase.PAUSE -> {
                operation++
                runCatching { cancelScheduled() }
                finish(true, "반복 중지 · 원래 조명 복귀 확인")
            }
            Phase.RESTORE -> Unit
            Phase.WAIT -> restoreOriginal()
            else -> onUpdate("진행 중 조명 요청 응답 대기", null)
        }
    }

    private fun writePulse() {
        if (stopping || pulseIndex == pulses.size) {
            completedSignal = !stopping && pulseIndex == pulses.size
            restoreOriginal()
            return
        }
        val pulse = pulses[pulseIndex]
        phase = Phase.WRITE
        onUpdate(if (repeatIntervalMs != null) "$signalLabel ${batchNumber}회차 전송 중" else "$signalLabel 전송 중", pulse.on)
        if (stopping) {
            restoreOriginal()
            return
        }
        request({ done -> write(pulse.on, done) }) { ok, detail ->
            when {
                !ok -> {
                    onUpdate("$signalLabel 요청 실패: $detail", pulse.on)
                    restoreOriginal()
                }
                stopping -> restoreOriginal()
                else -> {
                    // Progress follows accepted SDK requests, not physical lamp observations.
                    progress = if (pulse.on) progress.copy(activeSymbol = progress.completedSymbols)
                    else MorseProgress(progress.completedSymbols + 1)
                    onProgress(progress)
                    if (stopping) restoreOriginal() else waitForPulse(pulse.durationMs)
                }
            }
        }
    }

    private fun waitForPulse(durationMs: Long) {
        phase = Phase.WAIT
        val expected = ++operation
        try {
            schedule(durationMs) {
                if (phase == Phase.WAIT && expected == operation) {
                    operation++
                    if (pulseIndex == pulses.lastIndex) {
                        progress = MorseProgress(progress.completedSymbols + 1)
                        onProgress(progress)
                    }
                    pulseIndex++
                    writePulse()
                }
            }
        } catch (e: Exception) {
            if (phase == Phase.WAIT && expected == operation) {
                onUpdate("$signalLabel 예약 실패: ${e.message.orEmpty()}", null)
                restoreOriginal()
            }
        }
    }

    private fun restoreOriginal() {
        if (!busy || phase == Phase.RESTORE) return
        phase = Phase.RESTORE
        operation++
        // A late timer is harmless even if scheduler cancellation fails.
        runCatching { cancelScheduled() }
        progress = progress.copy(activeSymbol = null)
        onProgress(progress)
        onUpdate("원래 조명 상태 복귀 요청 중", null)
        request(restore) { ok, detail ->
            if (ok && completedSignal && !stopping && repeatIntervalMs != null) waitForRepeat(repeatIntervalMs)
            else finish(ok, if (ok) "조명 복귀 요청 완료" else "조명 복귀 실패: $detail")
        }
    }

    private fun waitForRepeat(delayMs: Long) {
        phase = Phase.PAUSE
        val expected = ++operation
        onUpdate("${batchNumber}회차 완료 · 다음 SOS 대기", null)
        if (stopping || phase != Phase.PAUSE) return
        try {
            schedule(delayMs) {
                if (phase == Phase.PAUSE && expected == operation) {
                    operation++
                    startBatch()
                }
            }
        } catch (e: Exception) {
            if (phase == Phase.PAUSE && expected == operation) {
                completedSignal = false
                runCatching { cancelScheduled() }
                finish(false, "$signalLabel 반복 예약 실패: ${e.message.orEmpty()}")
            }
        }
    }

    private fun request(
        action: ((Boolean, String) -> Unit) -> Unit,
        result: (Boolean, String) -> Unit,
    ) {
        val expected = ++operation
        val done: (Boolean, String) -> Unit = { ok, detail ->
            if (busy && expected == operation) {
                operation++
                result(ok, detail)
            }
        }
        try {
            action(done)
        } catch (e: Exception) {
            done(false, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun finish(restored: Boolean, detail: String) {
        phase = Phase.IDLE
        operation++
        onUpdate(detail, null)
        onFinished(restored)
    }
}
