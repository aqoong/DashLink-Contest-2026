package ai.pleos.contest.dashlink

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.min

/** A schematic of information groups, not a rendering of the connected vehicle or its sensors. */
class VehicleDiagramView(
    context: Context,
    private val onGroupSelected: (String) -> Unit,
) : FrameLayout(context) {
    private data class Pin(val group: String, val title: String)

    private val pins = listOf(
        Pin("lights", "조명"), Pin("airbag", "에어백"), Pin("tires", "타이어"),
        Pin("location", "위치"), Pin("doors", "문"), Pin("battery", "EV\n배터리"),
    )
    private val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink = Color.parseColor(if (night) "#F2F5FA" else "#202938")
    private val muted = Color.parseColor(if (night) "#B8C3D4" else "#556477")
    private val card = Color.parseColor(if (night) "#242D3B" else "#FFFFFF")
    private val line = muted
    private val pinColors = mutableMapOf<String, Int>()
    private val centers = mutableMapOf<String, PointF>()
    private val vehicleRect = RectF()
    private val diagram = Diagram(context)
    private val buttons = linkedMapOf<String, StatusButton>()
    private var shimmer: ValueAnimator? = null
    private var shimmerProgress = 0f

    init {
        clipChildren = false
        minimumHeight = dp(360)
        addView(diagram, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pins.forEach { pin ->
            val button = StatusButton(context).apply {
                id = View.generateViewId()
                tag = "vehicle_group_${pin.group}"
                isAllCaps = false
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                minHeight = dp(76)
                minimumHeight = dp(76)
                includeFontPadding = false
                setAutoSizeTextTypeUniformWithConfiguration(20, 22, 1, TypedValue.COMPLEX_UNIT_SP)
                maxLines = 2
                setTextColor(ink)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(4), dp(5), dp(4), dp(5))
                stateListAnimator = null
                elevation = dp(2).toFloat()
                val shape = GradientDrawable().apply {
                    setColor(card)
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.parseColor(if (night) "#3D4B60" else "#E2E8F0"))
                }
                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor(if (night) "#24FFFFFF" else "#14246BEB")),
                    shape, null,
                )
                setOnClickListener { onGroupSelected(pin.group) }
            }
            buttons[pin.group] = button
            addView(button, LayoutParams(dp(144), dp(76)))
        }
        updateReadings(emptyList())
    }

    fun updateReadings(readings: List<VehicleReading>, connecting: Boolean = false) {
        pins.forEach { pin ->
            val values = readings.filter { it.group == pin.group }
            val states = values.map { it.status }.distinct()
            val status = states.singleOrNull()
            val loading = isVehicleGroupLoading(pin.group, values, connecting)
            val summary = when {
                loading -> "조회 중"
                values.isEmpty() -> "확인 전"
                status == ReadStatus.READABLE -> "조회됨"
                status == ReadStatus.FAILED -> "조회 안 됨"
                status == ReadStatus.UNSUPPORTED -> "정보 미지원"
                status == ReadStatus.WAITING -> "확인 전"
                else -> "조회 ${values.count { it.status == ReadStatus.READABLE }}/${values.size}"
            }
            val color = statusColor(status)
            pinColors[pin.group] = color
            buttons.getValue(pin.group).apply {
                isLoading = loading
                text = pin.title
                setPadding(dp(4), dp(5), dp(4), dp(if (loading) 20 else 5))
                contentDescription = "${pin.title}, $summary, 상세 정보 보기"
            }
        }
        updateShimmer()
        diagram.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateShimmer()
    }

    override fun onDetachedFromWindow() {
        stopShimmer()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) updateShimmer() else stopShimmer()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) updateShimmer() else stopShimmer()
    }

    private fun updateShimmer() {
        if (!isAttachedToWindow) return
        val animate = isShown && windowVisibility == VISIBLE &&
            ValueAnimator.areAnimatorsEnabled() && buttons.values.any { it.isLoading }
        if (!animate) {
            stopShimmer()
        } else if (shimmer == null) {
            shimmer = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    if (!ValueAnimator.areAnimatorsEnabled()) {
                        stopShimmer()
                    } else {
                        shimmerProgress = it.animatedValue as Float
                        buttons.values.filter { button -> button.isLoading }.forEach { button -> button.invalidate() }
                    }
                }
                start()
            }
        }
    }

    private fun stopShimmer() {
        shimmer?.cancel()
        shimmer = null
        shimmerProgress = 0f
        if (isAttachedToWindow) buttons.values.filter { it.isLoading }.forEach { it.invalidate() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wantedHeight = dp(400)
        val measuredHeight = resolveSize(wantedHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY))
        val buttonWidth = if (measuredWidth < dp(600)) min(dp(144), (measuredWidth - dp(24)) / 3) else dp(160)
        buttons.values.forEach {
            it.measure(MeasureSpec.makeMeasureSpec(buttonWidth.coerceAtLeast(dp(48)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(76), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        diagram.layout(0, 0, width, height)
        val compact = width < dp(600)
        val buttonW = if (compact) min(dp(144), (width - dp(24)) / 3) else dp(160)
        val buttonH = dp(76)
        pins.forEachIndexed { i, pin ->
            val x = if (compact) w * ((i % 3 + 0.5f) / 3f) else if (i < 3) buttonW / 2f + dp(6) else w - buttonW / 2f - dp(6)
            val y = if (compact) {
                if (i < 3) buttonH / 2f + dp(4) else h - buttonH / 2f - dp(4)
            } else h * (0.14f + (i % 3) * 0.36f)
            centers[pin.group] = PointF(x, y)
            val x0 = (x - buttonW / 2f).toInt()
            val y0 = (y - buttonH / 2f).toInt()
            buttons.getValue(pin.group).layout(x0, y0, x0 + buttonW, y0 + buttonH)
        }
        val contentTop = if (compact) buttonH + dp(16).toFloat() else dp(12).toFloat()
        val contentBottom = if (compact) h - buttonH - dp(12) else h - dp(12)
        val carH = (contentBottom - contentTop).coerceAtLeast(dp(40).toFloat())
        val availableW = if (compact) w - dp(24) else w - dp(352)
        val carW = min(availableW, carH * 0.58f).coerceAtLeast(dp(24).toFloat())
        vehicleRect.set((w - carW) / 2, contentTop, (w + carW) / 2, contentBottom)
        diagram.invalidate()
    }

    private fun statusColor(status: ReadStatus?): Int = Color.parseColor(when (status) {
        ReadStatus.READABLE -> if (night) "#6EE7B7" else "#157A50"
        ReadStatus.FAILED -> if (night) "#FCA5A5" else "#C43745"
        ReadStatus.UNSUPPORTED -> if (night) "#FCD34D" else "#886000"
        else -> if (night) "#B8C3D4" else "#556477"
    })

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private inner class StatusButton(context: Context) : Button(context) {
        var isLoading = false
        private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val skeletonRect = RectF()
        private val skeletonBase = Color.parseColor(if (night) "#3B4657" else "#E1E7EF")
        private val skeletonHighlight = Color.parseColor(if (night) "#607188" else "#F6F8FC")
        private val skeletonMatrix = Matrix()
        private val skeletonShader = LinearGradient(
            -.5f, 0f, .5f, 0f, intArrayOf(skeletonBase, skeletonHighlight, skeletonBase),
            null, Shader.TileMode.CLAMP,
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isLoading) return
            val barWidth = min(width * .64f, dp(78).toFloat())
            val centerY = height * .84f
            skeletonRect.set((width - barWidth) / 2, centerY - dp(5), (width + barWidth) / 2, centerY + dp(5))
            val sweep = skeletonRect.left - barWidth + shimmerProgress * barWidth * 3f
            skeletonMatrix.setScale(barWidth, 1f)
            skeletonMatrix.postTranslate(sweep, 0f)
            skeletonShader.setLocalMatrix(skeletonMatrix)
            skeletonPaint.shader = if (shimmer != null) skeletonShader else null
            skeletonPaint.color = skeletonBase
            canvas.drawRoundRect(skeletonRect, dp(5).toFloat(), dp(5).toFloat(), skeletonPaint)
            skeletonPaint.shader = null
        }
    }

    private inner class Diagram(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val part = RectF()
        private val trim = Color.parseColor(if (night) "#1B2636" else "#758494")
        private val anchors = mapOf(
            "airbag" to PointF(.37f, .39f), "lights" to PointF(.25f, .12f),
            "tires" to PointF(.12f, .77f), "location" to PointF(.50f, .29f),
            "doors" to PointF(.84f, .53f), "battery" to PointF(.59f, .65f),
        )

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "차량 정보. 위쪽은 차량 앞쪽입니다. 각 부위의 버튼에서 조회 결과를 확인할 수 있습니다."
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawVehicle(canvas)
            pins.forEach { pin ->
                val normalized = anchors.getValue(pin.group)
                val x = vehicleRect.left + normalized.x * vehicleRect.width()
                val y = vehicleRect.top + normalized.y * vehicleRect.height()
                val button = buttons.getValue(pin.group)
                val center = centers[pin.group] ?: return@forEach
                val edgeX = center.x.coerceIn(button.left.toFloat(), button.right.toFloat())
                val startX: Float
                val startY: Float
                if (width < dp(600)) {
                    startX = edgeX
                    startY = if (center.y < height / 2) button.bottom.toFloat() else button.top.toFloat()
                } else {
                    startX = if (center.x < width / 2) button.right.toFloat() else button.left.toFloat()
                    startY = center.y
                }
                paint.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(startX, startY)
                path.lineTo((startX + x) / 2, startY)
                path.lineTo(x, y)
                paint.color = card
                paint.strokeWidth = dp(3).toFloat()
                canvas.drawPath(path, paint)
                paint.color = line
                paint.strokeWidth = dp(1).toFloat()
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                paint.color = card
                canvas.drawCircle(x, y, dp(6).toFloat(), paint)
                paint.color = pinColors[pin.group] ?: muted
                canvas.drawCircle(x, y, dp(3).toFloat(), paint)
            }
        }

        private fun x(value: Float) = vehicleRect.left + value * vehicleRect.width()
        private fun y(value: Float) = vehicleRect.top + value * vehicleRect.height()

        private fun rounded(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
                            color: Int, radius: Float) {
            part.set(x(left), y(top), x(right), y(bottom))
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            canvas.drawRoundRect(part, radius, radius, paint)
        }

        private fun Path.move(px: Float, py: Float) = moveTo(x(px), y(py))
        private fun Path.line(px: Float, py: Float) = lineTo(x(px), y(py))
        private fun Path.curve(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float) =
            cubicTo(x(ax), y(ay), x(bx), y(by), x(px), y(py))

        private fun shape(canvas: Canvas, color: Int, shader: Shader? = null, stroke: Float = 0f, outline: Path.() -> Unit) {
            path.reset()
            path.outline()
            paint.color = color
            paint.shader = shader
            paint.style = if (stroke > 0f) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = stroke
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path, paint)
            paint.shader = null
        }

        private fun metal(left: Float = .14f, right: Float = .86f): Shader = LinearGradient(
            x(left), 0f, x(right), 0f,
            (if (night) arrayOf("#445366", "#8292A5", "#BCC8D5", "#76889B", "#354353")
             else arrayOf("#8798AA", "#D9E3EC", "#F6F9FC", "#C7D3DF", "#8293A5")).map(Color::parseColor).toIntArray(),
            floatArrayOf(0f, .15f, .44f, .76f, 1f), Shader.TileMode.CLAMP,
        )

        private fun bodyOutline(): Path.() -> Unit = {
            move(.50f, .025f)
            curve(.25f, .025f, .16f, .065f, .15f, .19f)
            curve(.14f, .33f, .17f, .39f, .16f, .57f)
            curve(.15f, .72f, .13f, .85f, .20f, .93f)
            curve(.26f, .985f, .74f, .985f, .80f, .93f)
            curve(.87f, .85f, .85f, .72f, .84f, .57f)
            curve(.83f, .39f, .86f, .33f, .85f, .19f)
            curve(.84f, .065f, .75f, .025f, .50f, .025f)
            close()
        }

        private fun drawVehicle(canvas: Canvas) {
            val fine = resources.displayMetrics.density * .7f
            val rubber = Color.parseColor("#24303D")
            // A neutral technical illustration: body surfaces and hardware are decorative, not telemetry.
            canvas.save()
            canvas.translate(dp(2).toFloat(), dp(4).toFloat())
            shape(canvas, Color.parseColor(if (night) "#44000000" else "#1930445A"), stroke = dp(8).toFloat(), outline = bodyOutline())
            canvas.restore()
            for (top in listOf(.20f, .70f)) {
                rounded(canvas, .075f, top, .23f, top + .135f, rubber, dp(4).toFloat())
                rounded(canvas, .77f, top, .925f, top + .135f, rubber, dp(4).toFloat())
                rounded(canvas, .09f, top + .015f, .12f, top + .12f, Color.parseColor("#526173"), dp(2).toFloat())
                rounded(canvas, .88f, top + .015f, .91f, top + .12f, Color.parseColor("#526173"), dp(2).toFloat())
            }
            shape(canvas, Color.WHITE, metal(), outline = bodyOutline())
            shape(canvas, Color.parseColor(if (night) "#91A1B3" else "#A6B5C5"), stroke = fine, outline = bodyOutline())

            // Hood shoulder creases, bumper intake and sculpted rear deck.
            shape(canvas, trim, stroke = fine) {
                move(.24f, .145f); curve(.25f, .20f, .28f, .26f, .29f, .295f)
                move(.76f, .145f); curve(.75f, .20f, .72f, .26f, .71f, .295f)
                move(.28f, .075f); curve(.40f, .057f, .60f, .057f, .72f, .075f)
                move(.24f, .904f); curve(.36f, .93f, .64f, .93f, .76f, .904f)
            }
            shape(canvas, Color.parseColor("#344354")) {
                move(.32f, .055f); curve(.42f, .045f, .58f, .045f, .68f, .055f)
                line(.65f, .068f); curve(.55f, .060f, .45f, .060f, .35f, .068f); close()
            }

            val glazing = LinearGradient(x(.25f), y(.30f), x(.75f), y(.83f),
                intArrayOf(Color.parseColor("#536B83"), Color.parseColor("#1A293C"), Color.parseColor("#354B63")),
                null, Shader.TileMode.CLAMP)
            shape(canvas, Color.WHITE, glazing) {
                move(.29f, .29f); curve(.40f, .265f, .60f, .265f, .71f, .29f)
                curve(.77f, .39f, .77f, .67f, .75f, .77f)
                line(.69f, .855f); curve(.56f, .875f, .44f, .875f, .31f, .855f)
                line(.25f, .77f); curve(.23f, .67f, .23f, .39f, .29f, .29f); close()
            }
            // Glass highlights follow the windshield rather than flat rectangular windows.
            shape(canvas, Color.parseColor("#527F9AB4")) {
                move(.31f, .305f); curve(.40f, .286f, .52f, .285f, .62f, .295f)
                line(.40f, .395f); line(.285f, .382f); close()
            }
            shape(canvas, Color.parseColor("#A6B8C9"), stroke = fine) {
                move(.30f, .29f); curve(.42f, .27f, .58f, .27f, .70f, .29f)
                move(.29f, .84f); curve(.40f, .858f, .60f, .858f, .71f, .84f)
            }
            // Metal roof, framed side glass and the B pillars add depth at compact sizes.
            shape(canvas, Color.WHITE, metal(.29f, .71f)) {
                move(.335f, .405f); curve(.43f, .39f, .57f, .39f, .665f, .405f)
                curve(.69f, .49f, .69f, .66f, .67f, .735f)
                curve(.56f, .752f, .44f, .752f, .33f, .735f)
                curve(.31f, .66f, .31f, .49f, .335f, .405f); close()
            }
            shape(canvas, Color.parseColor(if (night) "#697D93" else "#AEBECD"), stroke = fine) {
                move(.35f, .425f); curve(.33f, .52f, .34f, .65f, .35f, .71f)
                move(.65f, .425f); curve(.67f, .52f, .66f, .65f, .65f, .71f)
            }
            shape(canvas, trim, stroke = fine * 2f) {
                move(.25f, .54f); line(.316f, .545f)
                move(.684f, .545f); line(.75f, .54f)
            }
            shape(canvas, trim, stroke = fine) {
                move(.17f, .555f); line(.24f, .548f)
                move(.76f, .548f); line(.83f, .555f)
                move(.22f, .36f); curve(.19f, .51f, .20f, .70f, .23f, .82f)
                move(.78f, .36f); curve(.81f, .51f, .80f, .70f, .77f, .82f)
            }
            for (side in listOf(.205f, .778f)) {
                rounded(canvas, side, .492f, side + .018f, .525f, trim, dp(1).toFloat())
                rounded(canvas, side, .688f, side + .018f, .72f, trim, dp(1).toFloat())
            }
            // Mirrors, cool white headlamps and restrained red rear lenses identify orientation.
            shape(canvas, Color.WHITE, metal(.04f, .96f)) {
                move(.17f, .36f); line(.07f, .345f); curve(.025f, .347f, .035f, .375f, .07f, .382f); line(.165f, .385f); close()
                move(.83f, .36f); line(.93f, .345f); curve(.975f, .347f, .965f, .375f, .93f, .382f); line(.835f, .385f); close()
            }
            shape(canvas, Color.parseColor("#F2FAFF"), stroke = fine * 2.8f) {
                move(.205f, .12f); curve(.25f, .105f, .32f, .10f, .375f, .102f)
                move(.625f, .102f); curve(.68f, .10f, .75f, .105f, .795f, .12f)
            }
            shape(canvas, Color.parseColor(if (night) "#EA7882" else "#B74656"), stroke = fine * 2.6f) {
                move(.21f, .905f); curve(.27f, .925f, .33f, .929f, .39f, .93f)
                move(.61f, .93f); curve(.67f, .929f, .73f, .925f, .79f, .905f)
            }
            shape(canvas, trim, stroke = fine * 1.5f) {
                move(.32f, .951f); curve(.43f, .96f, .57f, .96f, .68f, .951f)
            }
            paint.style = Paint.Style.FILL
        }
    }
}
