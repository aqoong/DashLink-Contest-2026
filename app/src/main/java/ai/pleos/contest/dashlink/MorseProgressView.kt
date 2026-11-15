package ai.pleos.contest.dashlink

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.widget.TextView

class MorseProgressView(
    context: Context,
    private val message: MorseMessage,
    private val accent: Int,
    private val pending: Int,
) : TextView(context) {
    private val pattern = MorseCode.displayGroups(message).joinToString("  ")
        .replace('.', '•')
    private val total = pattern.count { it != ' ' }

    init {
        textSize = if (resources.configuration.screenWidthDp < 480) 30f else 34f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setLineSpacing(dp(6).toFloat(), 1f)
        minimumHeight = dp(112)
        update(MorseProgress())
    }

    fun update(progress: MorseProgress) {
        val display = SpannableString(pattern)
        var symbolIndex = 0
        pattern.forEachIndexed { position, symbol ->
            if (symbol != ' ') {
                val active = symbolIndex == progress.activeSymbol
                val color = when {
                    active -> if (Color.luminance(accent) > 0.5f) Color.BLACK else Color.WHITE
                    symbolIndex < progress.completedSymbols -> accent
                    else -> pending
                }
                display.setSpan(ForegroundColorSpan(color), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (active) {
                    display.setSpan(BackgroundColorSpan(accent), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    display.setSpan(StyleSpan(Typeface.BOLD), position, position + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                symbolIndex++
            }
        }
        text = display
        contentDescription = "${message.title}, 신호 요청 ${progress.completedSymbols} / $total" +
            (progress.activeSymbol?.let { ", ${it + 1}번째 표현 중" } ?: "")
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
