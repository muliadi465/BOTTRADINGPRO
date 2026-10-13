package com.bot.tradingpro

import kotlin.math.max
import kotlin.math.sqrt

object Indicators {

    fun ema(data: List<Double>, period: Int): Double {
        if (data.isEmpty()) return 0.0
        if (data.size < period) return data.average()
        var result = data.take(period).average()
        val k = 2.0 / (period + 1)
        for (i in period until data.size) {
            result = data[i] * k + result * (1 - k)
        }
        return result
    }

    fun rsi(data: List<Double>, period: Int = 14): Double {
        if (data.size <= period) return 50.0
        val deltas = (1 until data.size).map { data[it] - data[it - 1] }
        var avgGain = deltas.take(period).sumOf { max(it, 0.0) } / period
        var avgLoss = deltas.take(period).sumOf { max(-it, 0.0) } / period
        for (i in period until deltas.size) {
            avgGain = (avgGain * (period - 1) + max(deltas[i], 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + max(-deltas[i], 0.0)) / period
        }
        if (avgLoss == 0.0) return 100.0
        return 100 - (100 / (1 + avgGain / avgLoss))
    }

    private fun macdSeries(data: List<Double>): List<Double> {
        val series = mutableListOf<Double>()
        if (data.size < 35) return series
        for (i in 35..data.size) {
            val sub = data.subList(0, i)
            series.add(ema(sub, 12) - ema(sub, 26))
        }
        return series
    }

    fun bollinger(data: List<Double>, period: Int = 20): Pair<Double, Double> {
        if (data.size < period) return Pair(0.0, 0.0)
        val last = data.takeLast(period)
        val mean = last.average()
        val std = sqrt(last.sumOf { (it - mean) * (it - mean) } / period)
        return Pair(mean + 2 * std, mean - 2 * std)
    }

    fun analyze(closes: List<Double>, strategy: String): AnalysisResult {
        val price = closes.last()
        val r = rsi(closes)
        val e9 = ema(closes, 9)
        val e21 = ema(closes, 21)
        val prev = closes.dropLast(1)
        val e9p = ema(prev, 9)
        val e21p = ema(prev, 21)

        val series = macdSeries(closes)
        val macdValue = series.lastOrNull() ?: 0.0
        val macdSig = if (series.size >= 9) ema(series, 9) else 0.0
        val macdPrev = if (series.size >= 2) series[series.size - 2] else macdValue
        val macdSigPrev = if (series.size >= 10) ema(series.dropLast(1), 9) else macdSig

        val (upper, lower) = bollinger(closes)

        var signal = "HOLD"
        var score = 0
        val reasons = mutableListOf<String>()

        when (strategy) {
            "EMA_CROSS" -> {
                if (e9p <= e21p && e9 > e21) signal = "BUY"
                else if (e9p >= e21p && e9 < e21) signal = "SELL"
            }
            "RSI" -> {
                if (r < 30) signal = "BUY"
                else if (r > 70) signal = "SELL"
            }
            "MACD" -> {
                if (macdPrev <= macdSigPrev && macdValue > macdSig) signal = "BUY"
                else if (macdPrev >= macdSigPrev && macdValue < macdSig) signal = "SELL"
            }
            "BOLLINGER" -> {
                if (lower > 0 && price < lower) signal = "BUY"
                else if (upper > 0 && price > upper) signal = "SELL"
            }
            else -> { // COMBO
                if (e9 > e21) { score += 1; reasons.add("EMA Uptrend (+1)") }
                else { score -= 1; reasons.add("EMA Downtrend (-1)") }

                if (r < 30) { score += 2; reasons.add(String.format("RSI Oversold %.1f (+2)", r)) }
                else if (r > 70) { score -= 2; reasons.add(String.format("RSI Overbought %.1f (-2)", r)) }
                else if (r < 45) { score += 1; reasons.add(String.format("RSI Rendah %.1f (+1)", r)) }
                else if (r > 60) { score -= 1; reasons.add(String.format("RSI Tinggi %.1f (-1)", r)) }

                if (macdValue > macdSig) { score += 1; reasons.add("MACD Bullish (+1)") }
                else { score -= 1; reasons.add("MACD Bearish (-1)") }

                if (lower > 0 && price < lower) { score += 1; reasons.add("Harga di bawah Bollinger (+1)") }
                else if (upper > 0 && price > upper) { score -= 1; reasons.add("Harga di atas Bollinger (-1)") }

                if (score >= 2) signal = "BUY"
                else if (score <= -2) signal = "SELL"
            }
        }

        return AnalysisResult(signal, score, price, r, e9, e21, macdValue, macdSig, upper, lower, reasons)
    }
}
