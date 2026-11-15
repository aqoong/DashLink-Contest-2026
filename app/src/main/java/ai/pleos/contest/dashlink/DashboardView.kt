package ai.pleos.contest.dashlink

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class DashboardView(context: Context) : LinearLayout(context) {
    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val ink = Color.parseColor(if (night) "#F4F6FA" else "#191F28")
    val muted = Color.parseColor(if (night) "#ACB6C6" else "#5D6878")
    val surface = Color.parseColor(if (night) "#202A38" else "#F3F6FA")
    val blue = Color.parseColor(if (night) "#8EB6FF" else "#2463D4")
    val danger = Color.parseColor(if (night) "#FFB4AB" else "#B42318")
    val backgroundColor = Color.parseColor(if (night) "#121923" else "#FFFFFF")
    private val narrow = resources.configuration.screenWidthDp < 720
    private val sidePadding = if (narrow) 24 else 36
    val content = column()
    val header = row()
    val badge = text("PLEOS · 에뮬레이터", 18, muted)
    private val footer = column().apply {
        visibility = View.GONE
        setPadding(dp(sidePadding), dp(12), dp(sidePadding), dp(16))
    }
    init {
        orientation = VERTICAL
        setBackgroundColor(backgroundColor)
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.setPadding(dp(sidePadding), dp(20), dp(sidePadding), dp(24))
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(text("DashLink", 26, ink, true), LayoutParams(0, dp(76), 1f))
        if (!narrow) header.addView(badge)
        content.addView(header)
        if (narrow) add(badge, 4)
    }
    fun title(title: String, subtitle: String = "") {
        add(text(title, if (narrow) 30 else 36, ink, true), 22)
        if (subtitle.isNotBlank()) add(text(subtitle, 17, muted), 10)
    }
    fun add(view: View, top: Int = 16, height: Int = LayoutParams.WRAP_CONTENT) {
        content.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height).apply { topMargin = dp(top) })
    }
    fun fixedAction(button: Button) {
        footer.removeAllViews()
        button.minHeight = dp(76)
        button.minimumHeight = dp(76)
        footer.addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        footer.visibility = View.VISIBLE
    }
    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun row() = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    fun text(value: String, size: Int = 18, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.coerceAtLeast(20).toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
        gravity = Gravity.CENTER_VERTICAL
    }
    fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = label; textSize = 22f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else blue)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = dp(76); minimumHeight = dp(76); minWidth = dp(76); minimumWidth = dp(76)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = ripple(if (primary) Color.parseColor("#2463D4") else surface, 16)
        setOnClickListener { action() }
    }
    fun dangerButton(label: String, action: () -> Unit) = button(label, primary = true, action).apply {
        setTextColor(if (night) Color.parseColor("#121923") else Color.WHITE)
        background = ripple(danger, 16)
    }
    fun iconButton(label: String, iconRes: Int, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(blue)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = label
        tooltipText = label
        minimumWidth = dp(76); minimumHeight = dp(76)
        setPadding(dp(22), dp(22), dp(22), dp(22))
        background = ripple(surface, 16)
        isFocusable = true
        setOnClickListener { action() }
    }
    fun styleMessageButton(button: Button, stopping: Boolean) {
        if (button.tag == stopping) return
        button.tag = stopping
        button.setTextColor(if (stopping && night) backgroundColor else Color.WHITE)
        button.background = ripple(if (stopping) danger else Color.parseColor("#2463D4"), 16)
    }
    fun actions(vararg buttons: Button) = (if (narrow) column() else row()).apply {
        buttons.forEachIndexed { i, b ->
            val params = if (narrow) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            else LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            if (i > 0) {
                if (narrow) params.topMargin = dp(12) else params.leftMargin = dp(12)
            }
            addView(b, params)
        }
    }
    fun card(title: String, detail: String, onClick: (() -> Unit)? = null): LinearLayout = column().apply {
        background = if (onClick != null) ripple(surface, 20) else shape(surface, 20)
        setPadding(dp(22), dp(18), dp(22), dp(18))
        addView(text(title, 23, ink, true))
        if (detail.isNotBlank()) addView(text(detail, 16, muted), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        if (onClick != null) { isClickable = true; isFocusable = true; setOnClickListener { onClick() }; contentDescription = "$title, $detail" }
    }
    fun input(hintText: String, value: String = "") = EditText(context).apply {
        hint = hintText; setText(value); textSize = 23f; setTextColor(ink); setHintTextColor(muted)
        isSingleLine = true; minHeight = dp(76); minimumHeight = dp(76); backgroundTintList = ColorStateList.valueOf(blue)
        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
    }
    fun toggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) = Switch(context).apply {
        text = label; textSize = 25f; setTextColor(ink); minHeight = dp(76); minimumHeight = dp(76)
        isChecked = checked; setPadding(dp(20), dp(12), dp(20), dp(12)); background = shape(surface, 20)
        setOnCheckedChangeListener { _, enabled -> changed(enabled) }
    }
    private fun ripple(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(18, 127, 127, 127)), shape(color, radius), shape(Color.WHITE, radius),
    )
    fun shape(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
