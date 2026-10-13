package com.bot.tradingpro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var candles: List<Candle> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#334155"); strokeWidth = 1f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#CBD5E1"); textSize = 22f; isAntiAlias = true
    }
    private val emptyPaint = Paint().apply {
        color = Color.parseColor("#CBD5E1"); textSize = 26f; isAntiAlias = true
    }
    private val wickPaint = Paint().apply { strokeWidth = 2f; isAntiAlias = true }
    private val bodyPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val ema9Paint = Paint().apply {
        color = Color.parseColor("#F59E0B"); strokeWidth = 3f
        style = Paint.Style.STROKE; isAntiAlias = true
    }
    private val ema21Paint = Paint().apply {
        color = Color.parseColor("#A78BFA"); strokeWidth = 3f
        style = Paint.Style.STROKE; isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (candles.isEmpty()) {
            canvas.drawText("Tekan CEK SEKARANG untuk memuat grafik...", 30f, height / 2f, emptyPaint)
            return
        }

        val visible = candles.takeLast(60)
        var maxPrice = visible.maxOf { it.high }
        var minPrice = visible.minOf { it.low }
        if (maxPrice == minPrice) maxPrice += 1.0

        val chartW = (width - 90).toFloat()
        val chartH = (height - 60).toFloat()
        val topPad = 20f

        // Grid + label harga
        for (i in 0..4) {
            val y = topPad + chartH * i / 4f
            canvas.drawLine(0f, y, chartW, y, gridPaint)
            val p = maxPrice - (maxPrice - minPrice) * i / 4.0
            canvas.drawText(String.format(Locale.US, "%.0f", p), chartW + 8f, y + 8f, textPaint)
        }

        val stepX = chartW / visible.size
        val bodyWidth = stepX * 0.6f

        fun yOf(value: Double): Float =
            (topPad + (maxPrice - value) / (maxPrice - minPrice) * chartH).toFloat()

        for (i in visible.indices) {
            val c = visible[i]
            val centerX = i * stepX + stepX / 2f
            val isGreen = c.close >= c.open
            val col = if (isGreen) Color.parseColor("#22C55E") else Color.parseColor("#EF4444")
            wickPaint.color = col
            bodyPaint.color = col

            canvas.drawLine(centerX, yOf(c.high), centerX, yOf(c.low), wickPaint)
            val top = min(yOf(c.open), yOf(c.close))
            var bottom = max(yOf(c.open), yOf(c.close))
            if (bottom - top < 2f) bottom = top + 2f
            canvas.drawRect(centerX - bodyWidth / 2f, top, centerX + bodyWidth / 2f, bottom, bodyPaint)
        }

        drawEma(canvas, visible, maxPrice, minPrice, chartW, chartH, topPad, 9, ema9Paint)
        drawEma(canvas, visible, maxPrice, minPrice, chartW, chartH, topPad, 21, ema21Paint)
    }

    private fun drawEma(
        canvas: Canvas, visible: List<Candle>,
        maxPrice: Double, minPrice: Double,
        chartW: Float, chartH: Float, topPad: Float,
        period: Int, paint: Paint
    ) {
        if (candles.size < period) return
        val closes = candles.map { it.close }
        val startIndex = candles.size - visible.size
        val stepX = chartW / visible.size
        val path = Path()
        var started = false
        for (i in visible.indices) {
            val global = startIndex + i
            if (global + 1 < period) continue
            val value = Indicators.ema(closes.subList(0, global + 1), period)
            val x = i * stepX + stepX / 2f
            val y = (topPad + (maxPrice - value) / (maxPrice - minPrice) * chartH).toFloat()
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
    }
}
