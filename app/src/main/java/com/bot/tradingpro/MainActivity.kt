package com.bot.tradingpro

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerPair: Spinner
    private lateinit var spinnerTimeframe: Spinner
    private lateinit var spinnerStrategy: Spinner
    private lateinit var switchLive: SwitchCompat
    private lateinit var chartView: ChartView
    private lateinit var tvPrice: TextView
    private lateinit var tvSignal: TextView
    private lateinit var tvScore: TextView
    private lateinit var tvRsi: TextView
    private lateinit var tvEma: TextView
    private lateinit var tvMacd: TextView
    private lateinit var tvBb: TextView
    private lateinit var tvReasons: TextView
    private lateinit var tvUsdt: TextView
    private lateinit var tvCoin: TextView
    private lateinit var tvEquity: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnStart: Button
    private lateinit var btnBuy: Button
    private lateinit var btnSell: Button
    private lateinit var btnCek: Button
    private lateinit var btnScanner: Button
    private lateinit var btnBacktest: Button
    private lateinit var btnSettings: Button

    private val pairs = listOf("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
        "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "DOTUSDT")
    private val timeframes = listOf("1m", "5m", "15m", "1h", "4h", "1d")
    private val strategies = listOf("COMBO", "EMA_CROSS", "RSI", "MACD", "BOLLINGER")

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()
    private var running = false

    private var apiKey = ""
    private var apiSecret = ""
    private var telegramToken = ""
    private var telegramChatId = ""

    private var modal = 1000.0
    private var takeProfit = 3.0
    private var stopLoss = 2.0
    private var trailingStop = 1.5

    private var usdt = 1000.0
    private var coin = 0.0
    private var entryPrice = 0.0
    private var highestPrice = 0.0
    private var lastPrice = 0.0
    private var trades = 0
    private var win = 0
    private var lose = 0
    private var totalProfit = 0.0

    private val botRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            fetchData(true)
            handler.postDelayed(this, 10000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerPair = findViewById(R.id.spinnerPair)
        spinnerTimeframe = findViewById(R.id.spinnerTimeframe)
        spinnerStrategy = findViewById(R.id.spinnerStrategy)
        switchLive = findViewById(R.id.switchLive)
        chartView = findViewById(R.id.chartView)
        tvPrice = findViewById(R.id.tvPrice)
        tvSignal = findViewById(R.id.tvSignal)
        tvScore = findViewById(R.id.tvScore)
        tvRsi = findViewById(R.id.tvRsi)
        tvEma = findViewById(R.id.tvEma)
        tvMacd = findViewById(R.id.tvMacd)
        tvBb = findViewById(R.id.tvBb)
        tvReasons = findViewById(R.id.tvReasons)
        tvUsdt = findViewById(R.id.tvUsdt)
        tvCoin = findViewById(R.id.tvCoin)
        tvEquity = findViewById(R.id.tvEquity)
        tvProfit = findViewById(R.id.tvProfit)
        tvPosition = findViewById(R.id.tvPosition)
        tvLog = findViewById(R.id.tvLog)
        btnStart = findViewById(R.id.btnStart)
        btnBuy = findViewById(R.id.btnBuy)
        btnSell = findViewById(R.id.btnSell)
        btnCek = findViewById(R.id.btnCek)
        btnScanner = findViewById(R.id.btnScanner)
        btnBacktest = findViewById(R.id.btnBacktest)
        btnSettings = findViewById(R.id.btnSettings)

        fun setupSpinner(spinner: Spinner, data: List<String>) {
            val adapter = ArrayAdapter(this, R.layout.spinner_item, data)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown)
            spinner.adapter = adapter
        }
        setupSpinner(spinnerPair, pairs)
        setupSpinner(spinnerTimeframe, timeframes)
        setupSpinner(spinnerStrategy, strategies)
        spinnerTimeframe.setSelection(2) // 15m

        val prefs = getSharedPreferences("bot_config", Context.MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""
        apiSecret = prefs.getString("api_secret", "") ?: ""
        telegramToken = prefs.getString("telegram_token", "") ?: ""
        telegramChatId = prefs.getString("telegram_chat_id", "") ?: ""
        modal = prefs.getFloat("modal", 1000f).toDouble()
        takeProfit = prefs.getFloat("take_profit", 3f).toDouble()
        stopLoss = prefs.getFloat("stop_loss", 2f).toDouble()
        trailingStop = prefs.getFloat("trailing_stop", 1.5f).toDouble()
        usdt = modal

        btnStart.setOnClickListener { toggleBot() }
        btnBuy.setOnClickListener {
            if (lastPrice > 0) buyPaper(lastPrice, "Manual BUY")
            else Toast.makeText(this, "Harga belum dimuat", Toast.LENGTH_SHORT).show()
        }
        btnSell.setOnClickListener {
            if (lastPrice > 0) sellPaper(lastPrice, "Manual SELL")
            else Toast.makeText(this, "Harga belum dimuat", Toast.LENGTH_SHORT).show()
        }
        btnCek.setOnClickListener { fetchData(false) }
        btnScanner.setOnClickListener { scanner() }
        btnBacktest.setOnClickListener { backtest() }
        btnSettings.setOnClickListener { showSettings() }

        updateWalletUI()
        log("Aplikasi siap. Memuat data pasar...")
        fetchData(false)
    }

    // ---------- BOT CONTROL ----------
    private fun toggleBot() {
        if (running) { stopBot(); return }
        if (switchLive.isChecked) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ MODE LIVE")
                .setMessage("Bot akan trading memakai UANG ASLI di Binance. Lanjutkan?")
                .setPositiveButton("Ya, LIVE") { _, _ -> startBot() }
                .setNegativeButton("Batal", null)
                .show()
        } else startBot()
    }

    private fun startBot() {
        running = true
        btnStart.text = "⏹ STOP BOT"
        btnStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
        handler.post(botRunnable)
        val mode = if (switchLive.isChecked) "LIVE" else "PAPER"
        log("🚀 Bot dimulai ($mode) - ${spinnerPair.selectedItem} ${spinnerTimeframe.selectedItem} ${spinnerStrategy.selectedItem}")
        sendTelegram("🚀 Bot Trading Pro dimulai\nMode: $mode\nPair: ${spinnerPair.selectedItem}")
    }

    private fun stopBot() {
        running = false
        handler.removeCallbacks(botRunnable)
        btnStart.text = "🚀 START BOT"
        btnStart.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16A34A"))
        log("🛑 Bot dihentikan")
        sendTelegram("🛑 Bot Trading Pro dihentikan")
    }

    // ---------- DATA & ANALISIS ----------
    private fun fetchData(trade: Boolean) {
        val pair = spinnerPair.selectedItem.toString()
        val tf = spinnerTimeframe.selectedItem.toString()
        val strategy = spinnerStrategy.selectedItem.toString()
        executor.execute {
            try {
                val fetched = fetchKlines(pair, tf, 100)
                val closes = fetched.map { it.close }
                val result = Indicators.analyze(closes, strategy)
                runOnUiThread {
                    lastPrice = result.price
                    chartView.candles = fetched
                    updatePriceUI(result)
                    updateWalletUI()
                    if (trade && running) doBotTrade(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    log("❌ Error data: ${e.message}")
                    Toast.makeText(this, "Gagal ambil data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun doBotTrade(result: AnalysisResult) {
        val price = result.price
        if (coin > 0 && entryPrice > 0) {
            highestPrice = max(highestPrice, price)
            val change = (price - entryPrice) / entryPrice * 100
            when {
                change >= takeProfit -> {
                    sellPaper(price, String.format(Locale.US, "Take Profit %.2f%%", change)); return
                }
                change <= -stopLoss -> {
                    sellPaper(price, String.format(Locale.US, "Stop Loss %.2f%%", change)); return
                }
                change > 1.5 && highestPrice > 0 &&
                    (highestPrice - price) / highestPrice * 100 >= trailingStop -> {
                    sellPaper(price, String.format(Locale.US, "Trailing Stop %.2f%%", change)); return
                }
            }
        }
        if (result.signal == "BUY" && coin == 0.0) buyPaper(price, "Sinyal BUY (${result.score})")
        else if (result.signal == "SELL" && coin > 0) sellPaper(price, "Sinyal SELL (${result.score})")
    }

    private fun updatePriceUI(result: AnalysisResult) {
        tvPrice.text = money(result.price)
        when (result.signal) {
            "BUY" -> { tvSignal.text = "🟢 SINYAL BUY"; tvSignal.setTextColor(Color.parseColor("#22C55E")) }
            "SELL" -> { tvSignal.text = "🔴 SINYAL SELL"; tvSignal.setTextColor(Color.parseColor("#EF4444")) }
            else -> { tvSignal.text = "⚪ HOLD / TUNGGU"; tvSignal.setTextColor(Color.parseColor("#94A3B8")) }
        }
        tvScore.text = "Skor: ${result.score}  •  ${spinnerStrategy.selectedItem}  •  ${spinnerPair.selectedItem} ${spinnerTimeframe.selectedItem}"
        tvRsi.text = String.format(Locale.US, "RSI (14) : %.1f", result.rsi)
        tvEma.text = String.format(Locale.US, "EMA 9 : %s%nEMA 21: %s", money(result.ema9), money(result.ema21))
        tvMacd.text = String.format(Locale.US, "MACD : %.4f%nSignal: %.4f", result.macd, result.macdSignal)
        tvBb.text = String.format(Locale.US, "Bollinger: %s — %s", money(result.bbLower), money(result.bbUpper))
        tvReasons.text = if (result.reasons.isEmpty()) "Alasan: -"
            else result.reasons.joinToString("\n") { "• $it" }
    }

    private fun updateWalletUI() {
        val equity = usdt + coin * lastPrice
        tvUsdt.text = "Saldo USDT : ${money(usdt)}"
        tvCoin.text = String.format(Locale.US, "Koin : %.6f", coin)
        tvEquity.text = "Total Equity: ${money(equity)}"
        tvProfit.text = "Profit: ${money(totalProfit)}  •  Trade: $trades (W:$win / L:$lose)"
        tvPosition.text = if (coin > 0 && entryPrice > 0) {
            val floatPct = if (lastPrice > 0) (lastPrice - entryPrice) / entryPrice * 100 else 0.0
            String.format(Locale.US, "Posisi: LONG @ %s (Floating %+.2f%%)", money(entryPrice), floatPct)
        } else "Posisi: Tidak ada"
    }

    // ---------- PAPER TRADING ----------
    private fun buyPaper(price: Double, reason: String) {
        val amount = usdt * 0.90
        if (amount < 10) { log("⚠️ Saldo USDT tidak cukup untuk BUY"); return }
        val qty = amount / price * 0.999
        entryPrice = if (coin > 0) (entryPrice * coin + price * qty) / (coin + qty) else price
        coin += qty
        usdt -= amount
        highestPrice = price
        val pair = spinnerPair.selectedItem.toString()
        if (switchLive.isChecked) sendLiveOrder("BUY", pair, amount, qty)
        log("🟢 BUY ${money(amount)} @ ${money(price)} | $reason")
        sendTelegram("🟢 BUY $pair @ ${money(price)}\n$reason")
        updateWalletUI()
    }

    private fun sellPaper(price: Double, reason: String) {
        if (coin <= 0) { Toast.makeText(this, "Tidak ada posisi koin", Toast.LENGTH_SHORT).show(); return }
        val qty = coin
        val revenue = qty * price * 0.999
        val profit = revenue - qty * entryPrice
        val pair = spinnerPair.selectedItem.toString()
        if (switchLive.isChecked) sendLiveOrder("SELL", pair, revenue, qty)
        usdt += revenue
        coin = 0.0
        trades++
        totalProfit += profit
        if (profit > 0) win++ else lose++
        log("🔴 SELL @ ${money(price)} | Profit ${money(profit)} | $reason")
        sendTelegram("🔴 SELL $pair @ ${money(price)}\nProfit: ${money(profit)}\n$reason")
        entryPrice = 0.0
        highestPrice = 0.0
        updateWalletUI()
    }

    // ---------- BACKTEST ----------
    private fun backtest() {
        val pair = spinnerPair.selectedItem.toString()
        val tf = spinnerTimeframe.selectedItem.toString()
        val strategy = spinnerStrategy.selectedItem.toString()
        Toast.makeText(this, "Menjalankan backtest...", Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val candles = fetchKlines(pair, tf, 500)
                val closes = candles.map { it.close }
                var cash = modal
                var coinAmt = 0.0
                var avg = 0.0
                var bTrades = 0; var bWin = 0; var bLose = 0
                var peak = modal; var maxDd = 0.0

                for (i in 60 until closes.size) {
                    val r = Indicators.analyze(closes.subList(0, i + 1), strategy)
                    val p = closes[i]
                    if (r.signal == "BUY" && coinAmt == 0.0) {
                        val spend = cash * 0.90
                        if (spend >= 10) { coinAmt = spend / p * 0.999; avg = p; cash -= spend }
                    } else if (r.signal == "SELL" && coinAmt > 0) {
                        val rev = coinAmt * p * 0.999
                        val prof = rev - coinAmt * avg
                        cash += rev; coinAmt = 0.0; bTrades++
                        if (prof > 0) bWin++ else bLose++
                    }
                    val eq = cash + coinAmt * p
                    if (eq > peak) peak = eq
                    if (peak > 0) maxDd = max(maxDd, (peak - eq) / peak * 100)
                }
                if (coinAmt > 0) {
                    val rev = coinAmt * closes.last() * 0.999
                    val prof = rev - coinAmt * avg
                    cash += rev; bTrades++
                    if (prof > 0) bWin++ else bLose++
                }
                val profit = cash - modal
                val wr = if (bTrades > 0) bWin.toDouble() / bTrades * 100 else 0.0
                val msg = "Pair: $pair ($tf)\nStrategi: $strategy\n\n" +
                    "Modal Awal : ${money(modal)}\nModal Akhir: ${money(cash)}\n" +
                    String.format(Locale.US, "Profit: %s (%+.2f%%)\n", money(profit), profit / modal * 100) +
                    "Total Trade: $bTrades (W:$bWin / L:$bLose)\n" +
                    String.format(Locale.US, "Winrate: %.1f%%\nMax Drawdown: %.2f%%", wr, maxDd)
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("📊 Hasil Backtest")
                        .setMessage(msg)
                        .setPositiveButton("OK", null)
                        .show()
                    log("Backtest selesai: Profit ${money(profit)} | Winrate ${String.format(Locale.US, "%.1f%%", wr)}")
                }
            } catch (e: Exception) {
                runOnUiThread { log("❌ Backtest gagal: ${e.message}") }
            }
        }
    }

    // ---------- SCANNER ----------
    private fun scanner() {
        val tf = spinnerTimeframe.selectedItem.toString()
        Toast.makeText(this, "Scanning 10 koin...", Toast.LENGTH_SHORT).show()
        executor.execute {
            val sb = StringBuilder()
            for (pair in pairs) {
                try {
                    val closes = fetchKlines(pair, tf, 100).map { it.close }
                    val r = Indicators.analyze(closes, "COMBO")
                    val icon = when (r.signal) { "BUY" -> "🟢 BUY "; "SELL" -> "🔴 SELL"; else -> "⚪ HOLD" }
                    sb.append(String.format(Locale.US, "%s %-9s %12s  RSI %.1f  Skor %+d%n",
                        icon, pair, money(r.price), r.rsi, r.score))
                } catch (e: Exception) {
                    sb.append("❌ $pair: ${e.message}\n")
                }
            }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("🔍 Scanner Sinyal ($tf)")
                    .setMessage(sb.toString())
                    .setPositiveButton("OK", null)
                    .show()
                log("Scanner 10 koin selesai")
            }
        }
    }

    // ---------- SETTINGS ----------
    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 8)
        }
        fun field(hint: String, value: String): EditText {
            val et = EditText(this)
            et.hint = hint
            et.setText(value)
            layout.addView(et)
            return et
        }
        val etApiKey = field("Binance API Key", apiKey)
        val etApiSecret = field("Binance API Secret", apiSecret)
        val etTgToken = field("Telegram Bot Token", telegramToken)
        val etTgChat = field("Telegram Chat ID", telegramChatId)
        val etModal = field("Modal USDT (Paper)", modal.toString())
        val etTp = field("Take Profit %", takeProfit.toString())
        val etSl = field("Stop Loss %", stopLoss.toString())
        val etTrail = field("Trailing Stop %", trailingStop.toString())

        AlertDialog.Builder(this)
            .setTitle("⚙️ Setting")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                apiKey = etApiKey.text.toString().trim()
                apiSecret = etApiSecret.text.toString().trim()
                telegramToken = etTgToken.text.toString().trim()
                telegramChatId = etTgChat.text.toString().trim()
                try {
                    modal = etModal.text.toString().toDouble()
                    takeProfit = etTp.text.toString().toDouble()
                    stopLoss = etSl.text.toString().toDouble()
                    trailingStop = etTrail.text.toString().toDouble()
                } catch (e: Exception) {
                    Toast.makeText(this, "Input angka tidak valid", Toast.LENGTH_SHORT).show()
                }
                getSharedPreferences("bot_config", Context.MODE_PRIVATE).edit()
                    .putString("api_key", apiKey)
                    .putString("api_secret", apiSecret)
                    .putString("telegram_token", telegramToken)
                    .putString("telegram_chat_id", telegramChatId)
                    .putFloat("modal", modal.toFloat())
                    .putFloat("take_profit", takeProfit.toFloat())
                    .putFloat("stop_loss", stopLoss.toFloat())
                    .putFloat("trailing_stop", trailingStop.toFloat())
                    .apply()
                if (coin == 0.0 && trades == 0) usdt = modal
                updateWalletUI()
                Toast.makeText(this, "✅ Setting tersimpan", Toast.LENGTH_SHORT).show()
                log("⚙️ Setting disimpan. Modal ${money(modal)} | TP $takeProfit% | SL $stopLoss%")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------- NETWORK BINANCE ----------
    private fun fetchKlines(symbol: String, interval: String, limit: Int): List<Candle> {
        val url = URL("https://api.binance.com/api/v3/klines?symbol=$symbol&interval=$interval&limit=$limit")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.requestMethod = "GET"
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val result = mutableListOf<Candle>()
        for (i in 0 until arr.length()) {
            val k = arr.getJSONArray(i)
            result.add(Candle(k.getLong(0), k.getDouble(1), k.getDouble(2),
                k.getDouble(3), k.getDouble(4), k.getDouble(5)))
        }
        return result
    }

    private fun sendLiveOrder(side: String, pair: String, usdtAmount: Double, qty: Double) {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            log("⚠️ LIVE dilewati: API Key belum diisi di Setting"); return
        }
        executor.execute {
            try {
                val params = linkedMapOf<String, String>(
                    "symbol" to pair, "side" to side, "type" to "MARKET"
                )
                if (side == "BUY") params["quoteOrderQty"] = String.format(Locale.US, "%.2f", usdtAmount)
                else params["quantity"] = String.format(Locale.US, "%.6f", qty)
                val res = signedRequest(params)
                runOnUiThread { log("✅ LIVE $side terkirim: ${res.take(120)}") }
            } catch (e: Exception) {
                runOnUiThread { log("❌ LIVE $side gagal: ${e.message}") }
            }
        }
    }

    private fun signedRequest(params: Map<String, String>): String {
        val all = LinkedHashMap(params)
        all["timestamp"] = System.currentTimeMillis().toString()
        all["recvWindow"] = "5000"
        val query = all.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = mac.doFinal(query.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
        val conn = URL("https://api.binance.com/api/v3/order?$query&signature=$sig")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("X-MBX-APIKEY", apiKey)
        conn.doOutput = true
        conn.outputStream.use { it.write(ByteArray(0)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText() ?: ""
        if (code !in 200..299) throw Exception("Binance $code: $text")
        return text
    }

    private fun sendTelegram(msg: String) {
        if (telegramToken.isBlank() || telegramChatId.isBlank()) return
        executor.execute {
            try {
                val data = "chat_id=${URLEncoder.encode(telegramChatId, "UTF-8")}" +
                    "&text=${URLEncoder.encode(msg, "UTF-8")}"
                val conn = URL("https://api.telegram.org/bot$telegramToken/sendMessage")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                conn.responseCode
            } catch (e: Exception) { /* abaikan */ }
        }
    }

    // ---------- UTIL ----------
    private fun money(v: Double): String = "\$" + String.format(Locale.US, "%,.2f", v)

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        tvLog.post { tvLog.append("\n[$time] $msg") }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        handler.removeCallbacks(botRunnable)
        executor.shutdownNow()
    }
}
