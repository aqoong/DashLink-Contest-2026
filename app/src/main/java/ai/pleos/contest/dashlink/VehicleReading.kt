package ai.pleos.contest.dashlink

import java.util.Locale

enum class ReadStatus { WAITING, LOADING, READABLE, FAILED, UNSUPPORTED }

data class VehicleReading(
    val id: String,
    val group: String,
    val label: String,
    val status: ReadStatus,
    val value: String = "",
    val detail: String = "",
    val receivedAt: Long = 0L,
)

object ReadingValues {
    // shortcut: SDK 2.3.2 obfuscates its exception accessor; accept only its exact documented error message until fixed.
    fun failureStatus(exceptionType: String, message: String?): ReadStatus {
        val code = message?.removePrefix("Error code:")?.trim()
        return if (exceptionType.startsWith("ai.pleos.playground.vehicle.callback.exception.") &&
            code in setOf("ERROR_NOT_SUPPORTED_PROPERTY", "ERROR_NOT_SUPPORTED_AREA")) {
            ReadStatus.UNSUPPORTED
        } else ReadStatus.FAILED
    }

    fun stateOfCharge(value: Int?): String? = value?.takeIf { it in 0..100 }?.let { "$it%" }

    fun lowPressure(value: Float?): String? = value?.takeIf { it.isFinite() && it >= 0f }
        ?.let { String.format(Locale.ROOT, "%.1f kPa", it) }

    fun doorPosition(value: Int?, minimum: Int?, maximum: Int?): String? {
        if (value == null || minimum != 0 || maximum == null || maximum <= 0 || value !in 0..maximum) return null
        return when (value) { 0 -> "닫힘"; maximum -> "완전히 열림"; else -> "일부 열림" }
    }
}
