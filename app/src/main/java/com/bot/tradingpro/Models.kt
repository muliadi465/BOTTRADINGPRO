package com.bot.tradingpro

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class AnalysisResult(
    val signal: String,
    val score: Int,
    val price: Double,
    val rsi: Double,
    val ema9: Double,
    val ema21: Double,
    val macd: Double,
    val macdSignal: Double,
    val bbUpper: Double,
    val bbLower: Double,
    val reasons: List<String>
)
