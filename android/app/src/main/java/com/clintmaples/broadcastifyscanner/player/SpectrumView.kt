package com.clintmaples.broadcastifyscanner.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor
import kotlin.math.max

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val levels = FloatArray(SpectrumAudioProcessor.SPECTRUM_BARS)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0E14.toInt() }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var barShader: LinearGradient? = null
    private var shaderHeight = -1

    init {
        setBackgroundColor(0x00000000)
        contentDescription = "Audio spectrum"
    }

    fun setLevels(src: FloatArray) {
        val n = minOf(levels.size, src.size)
        for (i in 0 until n) levels[i] = src[i]
        for (i in n until levels.size) levels[i] *= 0.85f
        invalidate()
    }

    fun clear() {
        levels.fill(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        if (barShader == null || shaderHeight != height) {
            barShader = LinearGradient(
                0f,
                h,
                0f,
                0f,
                intArrayOf(0xFF1A9B3C.toInt(), 0xFF3ECF5A.toInt(), 0xFFC9C22A.toInt(), 0xFFF0D030.toInt()),
                floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP,
            )
            shaderHeight = height
            barPaint.shader = barShader
        }

        val bars = levels.size
        val gap = max(1f, w * 0.012f)
        val barW = max(1f, floor((w - gap * (bars - 1)) / bars))
        val total = bars * barW + (bars - 1) * gap
        var x = floor((w - total) / 2f)
        val minH = max(1f, h * 0.05f)

        for (i in 0 until bars) {
            val bh = max(minH, levels[i] * h * 0.95f)
            canvas.drawRect(x, h - bh, x + barW, h, barPaint)
            x += barW + gap
        }
    }
}
