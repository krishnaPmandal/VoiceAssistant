package com.voiceassistant

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintPink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF2D78")
        alpha = 200
    }
    private val paintBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#2D78FF")
        alpha = 160
    }

    private var amplitudes = FloatArray(64) { 0f }
    private var animOffset = 0f
    private var isActive = false
    private val path = Path()

    private val animRunnable = object : Runnable {
        override fun run() {
            if (isActive) {
                animOffset += 0.08f
                // Animate amplitudes
                for (i in amplitudes.indices) {
                    amplitudes[i] = (amplitudes[i] * 0.7f + Random.nextFloat() * 0.3f)
                }
            } else {
                animOffset += 0.03f
                for (i in amplitudes.indices) {
                    amplitudes[i] = amplitudes[i] * 0.95f
                }
            }
            invalidate()
            postDelayed(this, 40)
        }
    }

    fun setActive(active: Boolean) {
        isActive = active
    }

    fun updateAmplitude(amp: Float) {
        for (i in amplitudes.indices) {
            amplitudes[i] = amp * (0.5f + Random.nextFloat() * 0.5f)
        }
        isActive = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(animRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(animRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        // Draw pink wave
        path.reset()
        val step = w / (amplitudes.size - 1)
        for (i in amplitudes.indices) {
            val x = i * step
            val wave = sin((i * 0.3f + animOffset).toDouble()).toFloat()
            val y = midY + wave * amplitudes[i] * midY * 0.8f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paintPink)

        // Draw blue wave (offset)
        path.reset()
        for (i in amplitudes.indices) {
            val x = i * step
            val wave = sin((i * 0.3f + animOffset + 1.2f).toDouble()).toFloat()
            val y = midY + wave * amplitudes[i] * midY * 0.6f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paintBlue)
    }
}
