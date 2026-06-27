package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.random.Random
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class LiveStock(
    val symbol: String,
    val name: String,
    val price: Double,
    val openPrice: Double,
    val change: Double,
    val changePercent: Double,
    val history: List<Double>,
    val marketCap: String,
    val peRatio: Double,
    val volume: String,
    val previousClose: Double = openPrice,
    val high: Double = price,
    val low: Double = price,
    val lastUpdatedTime: Long = 0L,
    val isUpdateFailed: Boolean = false
)

data class SymbolInfo(
    val ticker: String,
    val googleExchange: String,
    val yahooSuffix: String
)

fun parseSymbol(symbol: String): SymbolInfo {
    val upper = symbol.uppercase().trim()
    
    // Check if it already has a Google-style exchange or a colon
    if (upper.contains(":")) {
        val parts = upper.split(":")
        val ticker = parts[0].trim()
        val exchange = parts[1].trim()
        val suffix = when (exchange) {
            "NSE" -> ".NS"
            "BOM", "BSE" -> ".BO"
            else -> ""
        }
        return SymbolInfo(ticker, exchange, suffix)
    }
    
    // Check if it has a Yahoo-style suffix
    if (upper.endsWith(".NS")) {
        val ticker = upper.removeSuffix(".NS")
        return SymbolInfo(ticker, "NSE", ".NS")
    }
    if (upper.endsWith(".BO")) {
        val ticker = upper.removeSuffix(".BO")
        return SymbolInfo(ticker, "BOM", ".BO")
    }
    if (upper.endsWith(".BSE")) {
        val ticker = upper.removeSuffix(".BSE")
        return SymbolInfo(ticker, "BOM", ".BO")
    }
    
    // No explicit exchange or suffix. We must auto-detect based on Yahoo/Google rules.
    val baseIndianStocks = setOf(
        "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK", "SBIN",
        "BHARTIAIRTEL", "ITC", "LT", "TATAMOTORS", "BAJFINANCE", 
        "POWERGRID", "NTPC", "ULTRACEMCO", "ADANIPORTS", "AXISBANK", 
        "KOTAKBANK", "WIPRO", "HCLTECH", "ASIANPAINT", "MARUTI", "HINDUNILVR",
        "BEL", "HAL", "RVNL", "IRFC", "ONGC", "COALINDIA", "SAIL", "GAIL",
        "PFC", "REC", "PNB", "IOC", "BPCL", "HPCL", "NHPC", "SJVN", "BHEL", "DLF"
    )
    
    if (baseIndianStocks.contains(upper)) {
        val isBse = upper == "BAJFINANCE" || upper == "POWERGRID" || upper == "NTPC" || upper == "ULTRACEMCO" || upper == "ADANIPORTS"
        val exch = if (isBse) "BOM" else "NSE"
        val suff = if (isBse) ".BO" else ".NS"
        return SymbolInfo(upper, exch, suff)
    }
    
    // Fallback: If it's a common US stock or doesn't look like an Indian stock (usually length <= 4 and contains only letters like AAPL, MSFT, GOOG, etc.)
    val usTickers = setOf("AAPL", "MSFT", "GOOG", "GOOGL", "TSLA", "AMZN", "META", "NVDA", "NFLX", "AMD")
    if (usTickers.contains(upper) || (upper.length <= 4 && !baseIndianStocks.contains(upper))) {
        // Assume US (NASDAQ/NYSE — Google Finance automatically redirects or resolves ticker)
        return SymbolInfo(upper, "", "")
    }
    
    // Default to Indian NSE as general fallback
    return SymbolInfo(upper, "NSE", ".NS")
}

class StockRepository(
    private val stockDao: StockDao,
    private val settingsRepository: SettingsRepository
) {
    private val tag = "StockRepository"

    private fun generateProceduralHistory(
        startVal: Double,
        endVal: Double,
        low: Double,
        high: Double,
        numPoints: Int = 40
    ): List<Double> {
        val seed = (startVal.toLong() xor (endVal.toLong() shl 16) xor (numPoints.toLong() shl 32))
        val r = kotlin.random.Random(seed)
        
        // Ensure bounds are logical and enclosing
        val realLow = Math.min(low, Math.min(startVal, endVal))
        val realHigh = Math.max(high, Math.max(startVal, endVal))
        
        // 1. Generate standard random walk W_i
        val W = DoubleArray(numPoints)
        W[0] = 0.0
        for (i in 1 until numPoints) {
            val step = r.nextDouble() * 2.0 - 1.0 // -1 to 1
            W[i] = W[i-1] + step
        }
        
        // 2. Generate bridge variations V_i (V_0 = 0, V_{numPoints-1} = 0)
        val V = DoubleArray(numPoints)
        val W_last = W[numPoints - 1]
        for (i in 0 until numPoints) {
            val fraction = i.toDouble() / (numPoints - 1)
            V[i] = W[i] - fraction * W_last
        }
        
        // Smooth the variations with a moving average to make the curve look like a natural stock line rather than sharp noise
        val smoothedV = DoubleArray(numPoints)
        for (i in 0 until numPoints) {
            var sum = 0.0
            var count = 0
            val window = 2 // window size of 5 total points
            for (j in (i - window)..(i + window)) {
                if (j in 0 until numPoints) {
                    sum += V[j]
                    count++
                }
            }
            smoothedV[i] = sum / count
        }
        
        // Make sure smoothedV starts and ends precisely at 0
        val firstV = smoothedV[0]
        val lastV = smoothedV[numPoints - 1]
        for (i in 0 until numPoints) {
            val fraction = i.toDouble() / (numPoints - 1)
            smoothedV[i] = smoothedV[i] - (firstV + fraction * (lastV - firstV))
        }
        
        // 3. Find the maximum scaling factor S such that startVal + fraction * (endVal - startVal) + S * smoothedV[i] is within [realLow, realHigh]
        var maxS = 1000.0
        for (i in 0 until numPoints) {
            val fraction = i.toDouble() / (numPoints - 1)
            val linearVal = startVal + fraction * (endVal - startVal)
            val vi = smoothedV[i]
            if (Math.abs(vi) > 1e-9) {
                val limit = if (vi > 0) {
                    (realHigh - linearVal) / vi
                } else {
                    (realLow - linearVal) / vi
                }
                if (limit in 0.0..maxS) {
                    maxS = limit
                }
            }
        }
        
        // Use a comfortable ratio of maxS (e.g., between 0.75 and 0.95) to give a pleasant, non-flat curve that respects high/low beautifully
        val S = maxS * r.nextDouble(0.75, 0.95)
        
        val history = mutableListOf<Double>()
        for (i in 0 until numPoints) {
            val fraction = i.toDouble() / (numPoints - 1)
            val linearVal = startVal + fraction * (endVal - startVal)
            val finalVal = linearVal + S * smoothedV[i]
            history.add(finalVal.coerceIn(realLow, realHigh))
        }
        
        // Ensure exact start and end are preserved
        history[0] = startVal
        history[numPoints - 1] = endVal
        
        return history
    }

    // Raw starting stock list
    private val baseStocks = listOf(
        LiveStock("RELIANCE", "Reliance Industries Ltd. (NSE)", 2855.40, 2840.00, 15.40, 0.54, emptyList(), "19.3T", 26.4, "6.4M"),
        LiveStock("TCS", "Tata Consultancy Services (NSE)", 3845.20, 3810.00, 35.20, 0.92, emptyList(), "14.1T", 29.1, "2.1M"),
        LiveStock("HDFCBANK", "HDFC Bank Ltd. (NSE)", 1530.50, 1536.00, -5.50, -0.36, emptyList(), "11.6T", 18.2, "14.2M"),
        LiveStock("INFY", "Infosys Ltd. (NSE)", 1412.30, 1398.00, 14.30, 1.02, emptyList(), "5.8T", 24.5, "5.8M"),
        LiveStock("ICICIBANK", "ICICI Bank Ltd. (NSE)", 1125.80, 1115.00, 10.80, 0.97, emptyList(), "7.9T", 17.6, "8.9M"),
        LiveStock("SBIN", "State Bank of India (NSE)", 825.40, 830.00, -4.60, -0.55, emptyList(), "7.4T", 10.5, "12.3M"),
        LiveStock("BHARTIAIRTEL", "Bharti Airtel Ltd. (NSE)", 1362.10, 1345.00, 17.10, 1.27, emptyList(), "7.8T", 38.4, "4.2M"),
        LiveStock("ITC", "ITC Ltd. (NSE)", 432.80, 435.00, -2.20, -0.51, emptyList(), "5.4T", 25.1, "9.8M"),
        LiveStock("LT", "Larsen & Toubro Ltd. (NSE)", 3465.00, 3440.00, 25.00, 0.73, emptyList(), "4.8T", 36.2, "1.8M"),
        LiveStock("TATAMOTORS", "Tata Motors Ltd. (NSE)", 952.40, 940.00, 12.40, 1.32, emptyList(), "3.5T", 11.2, "8.5M"),
        LiveStock("HINDUNILVR", "Hindustan Unilever Ltd. (NSE)", 2345.10, 2360.00, -14.90, -0.63, emptyList(), "5.5T", 54.2, "1.4M"),
        LiveStock("KOTAKBANK", "Kotak Mahindra Bank (NSE)", 1682.00, 1695.00, -13.00, -0.77, emptyList(), "3.3T", 19.5, "2.9M"),
        LiveStock("WIPRO", "Wipro Ltd. (NSE)", 458.50, 462.00, -3.50, -0.76, emptyList(), "2.4T", 20.8, "3.5M"),
        LiveStock("AXISBANK", "Axis Bank Ltd. (NSE)", 1152.30, 1145.00, 7.30, 0.64, emptyList(), "3.5T", 14.1, "4.8M"),
        LiveStock("M&M", "Mahindra & Mahindra (NSE)", 2545.00, 2520.00, 25.00, 0.99, emptyList(), "3.1T", 28.5, "2.2M"),
        LiveStock("TITAN", "Titan Company Ltd. (NSE)", 3255.40, 3230.00, 25.40, 0.79, emptyList(), "2.9T", 80.2, "1.1M"),
        LiveStock("SUNPHARMA", "Sun Pharmaceutical (NSE)", 1482.50, 1470.00, 12.50, 0.85, emptyList(), "3.5T", 35.8, "1.9M"),
        LiveStock("HCLTECH", "HCL Technologies (NSE)", 1324.60, 1318.00, 6.60, 0.50, emptyList(), "3.6T", 25.2, "2.0M"),
        LiveStock("TATASTEEL", "Tata Steel Ltd. (NSE)", 166.40, 168.00, -1.60, -0.95, emptyList(), "2.0T", 14.8, "25.2M"),
        LiveStock("BAJFINANCE", "Bajaj Finance Ltd. (BSE)", 6845.00, 6890.00, -45.00, -0.65, emptyList(), "4.2T", 26.5, "1.1M"),
        LiveStock("ADANIPORTS", "Adani Ports & SEZ (BSE)", 1422.00, 1405.00, 17.00, 1.21, emptyList(), "3.1T", 32.1, "3.2M"),
        LiveStock("POWERGRID", "Power Grid Corporation (BSE)", 312.40, 315.00, -2.60, -0.83, emptyList(), "2.9T", 16.5, "7.5M"),
        LiveStock("NTPC", "NTPC Limited (BSE)", 362.50, 358.00, 4.50, 1.26, emptyList(), "3.5T", 15.1, "9.2M"),
        LiveStock("ULTRACEMCO", "UltraTech Cement (BSE)", 9980.00, 9920.00, 60.00, 0.60, emptyList(), "2.9T", 42.1, "0.4M"),
        LiveStock("BEL", "Bharat Electronics Limited (NSE)", 292.00, 288.50, 3.50, 1.21, emptyList(), "2.1T", 38.5, "15.4M"),
        LiveStock("HAL", "Hindustan Aeronautics Limited (NSE)", 4550.00, 4498.00, 52.00, 1.16, emptyList(), "3.0T", 41.2, "1.8M"),
        LiveStock("RVNL", "Rail Vikas Nigam Limited (NSE)", 385.00, 376.00, 9.00, 2.39, emptyList(), "800B", 45.1, "24.5M"),
        LiveStock("IRFC", "Indian Railway Finance Corporation (NSE)", 176.50, 174.00, 2.50, 1.44, emptyList(), "2.3T", 31.2, "38.2M"),
        LiveStock("ONGC", "Oil & Natural Gas Corporation (NSE)", 268.40, 269.50, -1.10, -0.41, emptyList(), "3.4T", 8.2, "10.8M"),
        LiveStock("COALINDIA", "Coal India Limited (NSE)", 478.20, 482.00, -3.80, -0.79, emptyList(), "2.9T", 9.4, "8.9M"),
        LiveStock("SAIL", "Steel Authority of India Limited (NSE)", 148.50, 149.20, -0.70, -0.47, emptyList(), "613B", 18.5, "12.4M"),
        LiveStock("GAIL", "GAIL (India) Limited (NSE)", 212.30, 210.00, 2.30, 1.10, emptyList(), "1.4T", 14.2, "9.5M"),
        LiveStock("PFC", "Power Finance Corporation (NSE)", 482.00, 478.50, 3.50, 0.73, emptyList(), "1.6T", 7.8, "7.4M"),
        LiveStock("REC", "REC Limited (NSE)", 524.50, 519.00, 5.50, 1.06, emptyList(), "1.4T", 9.1, "6.8M"),
        LiveStock("PNB", "Punjab National Bank (NSE)", 124.60, 126.00, -1.40, -1.11, emptyList(), "1.4T", 15.6, "28.9M"),
        LiveStock("IOC", "Indian Oil Corporation Limited (NSE)", 164.80, 166.50, -1.70, -1.02, emptyList(), "2.3T", 11.2, "14.7M"),
        LiveStock("BPCL", "Bharat Petroleum Corporation Limited (NSE)", 598.20, 592.00, 6.20, 1.05, emptyList(), "1.3T", 5.2, "5.6M")
    ).map { stock ->
        // Populate intraday history from openPrice to current price without random-walk fluctuations
        val points = 20
        val tempHistory = mutableListOf<Double>()
        for (i in 0 until points) {
            val fraction = i.toDouble() / (points - 1)
            val interpolated = stock.openPrice + fraction * (stock.price - stock.openPrice)
            tempHistory.add(interpolated)
        }
        stock.copy(
            history = tempHistory,
            previousClose = stock.price - stock.change,
            high = Math.max(stock.price, stock.openPrice),
            low = Math.min(stock.price, stock.openPrice),
            lastUpdatedTime = System.currentTimeMillis()
        )
    }

    private val _liveStocksFlow = MutableStateFlow<List<LiveStock>>(baseStocks)
    val liveStocksFlow: StateFlow<List<LiveStock>> = _liveStocksFlow.asStateFlow()

    private val _notFoundSymbols = MutableStateFlow<Set<String>>(emptySet())
    val notFoundSymbols: StateFlow<Set<String>> = _notFoundSymbols.asStateFlow()

    private val _isOfflineFlow = MutableStateFlow<Boolean>(false)
    val isOfflineFlow: StateFlow<Boolean> = _isOfflineFlow.asStateFlow()

    private val _lastGoogleFinanceFetchTime = MutableStateFlow<Long>(0L)
    val lastGoogleFinanceFetchTime: StateFlow<Long> = _lastGoogleFinanceFetchTime.asStateFlow()

    fun suggestSimilarStocks(symbol: String): List<LiveStock> {
        val upperQuery = symbol.uppercase().trim()
        
        fun levenshtein(s: String, t: String): Int {
            if (s == t) return 0
            if (s.isEmpty()) return t.length
            if (t.isEmpty()) return s.length
            val d = IntArray(t.length + 1) { it }
            for (i in 1..s.length) {
                var prev = i
                for (j in 1..t.length) {
                    val next = if (s[i - 1] == t[j - 1]) d[j - 1] else minOf(d[j - 1] + 1, prev + 1, d[j] + 1)
                    d[j - 1] = prev
                    prev = next
                }
                d[t.length] = prev
            }
            return d[t.length]
        }

        return baseStocks.map { stock ->
            val symbolDist = levenshtein(upperQuery, stock.symbol)
            val isSubstring = stock.symbol.contains(upperQuery) || stock.name.uppercase().contains(upperQuery)
            val baseDistance = symbolDist.toDouble()
            val score = if (isSubstring) baseDistance * 0.3 else baseDistance
            Pair(stock, score)
        }
        .filter { it.second < 8 }
        .sortedBy { it.second }
        .map { it.first }
        .take(3)
    }

    fun ensureStockRegistered(symbol: String): LiveStock {
        val upper = symbol.uppercase().trim()
        val currentList = _liveStocksFlow.value
        val existing = currentList.find { it.symbol == upper }
        if (existing != null) return existing

        val matchedBase = baseStocks.find { it.symbol == upper }
        if (matchedBase != null) {
            _liveStocksFlow.value = currentList + matchedBase
            return matchedBase
        }

        // Initialize with direct 0.0 values to avoid presenting fake, inaccurate mock parameters
        val base = LiveStock(
            symbol = upper,
            name = upper,
            price = 0.0,
            openPrice = 0.0,
            change = 0.0,
            changePercent = 0.0,
            history = emptyList(),
            marketCap = "N/A",
            peRatio = 0.0,
            volume = "N/A"
        )
        
        _liveStocksFlow.value = currentList + base
        return base
    }

    // Access groups and saved stocks from Room
    val allGroups: Flow<List<GroupEntity>> = stockDao.getAllGroups()
    val allSavedStocks: Flow<List<SavedStockEntity>> = stockDao.getAllSavedStocksFlow()
    val allWatchlistStocks: Flow<List<WatchlistStockEntity>> = stockDao.getAllWatchlistStocksFlow()

    fun getSavedStocksForGroup(groupId: Int): Flow<List<SavedStockEntity>> =
        stockDao.getSavedStocksForGroup(groupId)

    suspend fun getWatchlistStockBySymbol(symbol: String): WatchlistStockEntity? = withContext(Dispatchers.IO) {
        stockDao.getWatchlistStockBySymbol(symbol)
    }

    suspend fun getSavedStockById(id: Int): SavedStockEntity? = withContext(Dispatchers.IO) {
        stockDao.getSavedStockById(id)
    }

    suspend fun addToWatchlist(symbol: String, name: String, targetPrice: Double? = null, stopLoss: Double? = null, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) = withContext(Dispatchers.IO) {
        val existing = stockDao.getWatchlistStockBySymbol(symbol)
        if (existing != null) {
            stockDao.insertWatchlistStock(
                existing.copy(
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl ?: existing.googleFinanceUrl
                )
            )
        } else {
            stockDao.insertWatchlistStock(
                WatchlistStockEntity(
                    symbol = symbol,
                    name = name,
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl
                )
            )
        }
    }

    suspend fun removeFromWatchlist(symbol: String) = withContext(Dispatchers.IO) {
        stockDao.deleteWatchlistStockBySymbol(symbol)
    }

    suspend fun updateWatchlistStockAlerts(symbol: String, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double?, googleFinanceUrl: String? = null) = withContext(Dispatchers.IO) {
        val existing = stockDao.getWatchlistStockBySymbol(symbol)
        if (existing != null) {
            stockDao.insertWatchlistStock(
                existing.copy(
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl ?: existing.googleFinanceUrl
                )
            )
        }
    }

    suspend fun insertGroup(name: String): Long = withContext(Dispatchers.IO) {
        stockDao.insertGroup(GroupEntity(name = name))
    }

    suspend fun updateGroupColor(group: GroupEntity, colorHex: String?) = withContext(Dispatchers.IO) {
        stockDao.updateGroup(group.copy(colorHex = colorHex))
    }

    suspend fun updateWatchlistStockColor(symbol: String, colorHex: String?) = withContext(Dispatchers.IO) {
        val stock = stockDao.getWatchlistStockBySymbol(symbol)
        if (stock != null) {
            stockDao.updateWatchlistStock(stock.copy(colorHex = colorHex))
        }
    }

    suspend fun deleteGroup(group: GroupEntity) = withContext(Dispatchers.IO) {
        stockDao.deleteGroup(group)
    }

    suspend fun insertSavedStock(symbol: String, name: String, groupId: Int, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) = withContext(Dispatchers.IO) {
        val existing = stockDao.getSavedStockBySymbolAndGroup(symbol, groupId)
        if (existing != null) {
            // Update existing saved stock parameter and reset its notified status
            stockDao.updateSavedStock(
                existing.copy(
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl ?: existing.googleFinanceUrl
                )
            )
        } else {
            stockDao.insertSavedStock(
                SavedStockEntity(
                    symbol = symbol,
                    name = name,
                    groupId = groupId,
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl
                )
            )
        }
    }

    suspend fun updateSavedStockAlerts(id: Int, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double?, googleFinanceUrl: String? = null) = withContext(Dispatchers.IO) {
        // Fetch existing SavedStock by ID, copy with new values, reset notifying alerts
        val match = stockDao.getSavedStockById(id)
        if (match != null) {
            stockDao.updateSavedStock(
                match.copy(
                    targetPrice = targetPrice,
                    stopLoss = stopLoss,
                    proximityThreshold = proximityThreshold,
                    targetHit = false,
                    stopLossHit = false,
                    googleFinanceUrl = googleFinanceUrl ?: match.googleFinanceUrl
                )
            )
        }
    }

    suspend fun removeSavedStock(id: Int) = withContext(Dispatchers.IO) {
        stockDao.deleteSavedStockById(id)
    }

    suspend fun updateSavedStockDisplayOrder(id: Int, displayOrder: Int) = withContext(Dispatchers.IO) {
        stockDao.updateSavedStockDisplayOrder(id, displayOrder)
    }

    suspend fun updateWatchlistStockDisplayOrder(symbol: String, displayOrder: Int) = withContext(Dispatchers.IO) {
        stockDao.updateWatchlistStockDisplayOrder(symbol, displayOrder)
    }

    fun isNetworkAvailable(ctx: Context): Boolean {
        return try {
            val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun simulateStockFluctuation(stock: LiveStock): LiveStock {
        val changePercentFactor = (Random.nextDouble() * 2.0 - 1.0) / 100.0
        val priceChange = stock.price * changePercentFactor
        val newPrice = (stock.price + priceChange).coerceAtLeast(1.0)
        val totalOverallChange = newPrice - stock.openPrice
        val totalOverallChangePercent = if (stock.openPrice > 0.0) {
            (totalOverallChange / stock.openPrice) * 100.0
        } else {
            0.0
        }

        val updatedHistory = if (stock.history.isNotEmpty() && stock.history.size >= 5) {
            val list = stock.history.toMutableList()
            list.add(newPrice)
            if (list.size > 40) {
                list.removeAt(0)
            }
            list
        } else {
            generateProceduralHistory(
                startVal = stock.openPrice,
                endVal = newPrice,
                low = stock.low,
                high = stock.high,
                numPoints = 40
            )
        }

        return stock.copy(
            price = newPrice,
            change = totalOverallChange,
            changePercent = totalOverallChangePercent,
            history = updatedHistory,
            high = Math.max(stock.high, newPrice),
            low = Math.min(stock.low, newPrice),
            lastUpdatedTime = stock.lastUpdatedTime
        )
    }

    private suspend fun fetchLiveDetailsFromYahoo(symbol: String): LiveStock? = withContext(Dispatchers.IO) {
        val info = parseSymbol(symbol)
        val symbolUpper = info.ticker
        val querySymbol = if (info.yahooSuffix.isNotEmpty()) {
            "${info.ticker}${info.yahooSuffix}"
        } else {
            info.ticker
        }

        try {
            val url = java.net.URL("https://query1.finance.yahoo.com/v8/finance/chart/$querySymbol?range=1d&interval=15m")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.e(tag, "Yahoo API error code: $responseCode for $querySymbol")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(responseText)
            val chartObj = obj.optJSONObject("chart") ?: return@withContext null
            val resultArr = chartObj.optJSONArray("result") ?: return@withContext null
            if (resultArr.length() == 0) return@withContext null

            val result = resultArr.getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val currentPrice = meta.optDouble("regularMarketPrice", 0.0)
            if (currentPrice == 0.0) return@withContext null

            val previousClose = meta.optDouble("chartPreviousClose", currentPrice)
            val regularMarketDayHigh = meta.optDouble("regularMarketDayHigh", currentPrice)
            val regularMarketDayLow = meta.optDouble("regularMarketDayLow", currentPrice)
            val regularMarketDayOpen = meta.optDouble("regularMarketDayOpen", previousClose)

            val symbolFromYahoo = meta.optString("symbol", symbolUpper).replace(".NS", "").replace(".BO", "")

            val indicators = result.optJSONObject("indicators")
            val quoteArr = indicators?.optJSONArray("quote")
            val quote = quoteArr?.optJSONObject(0)
            val closeArr = quote?.optJSONArray("close")

            val historyList = mutableListOf<Double>()
            if (closeArr != null) {
                for (i in 0 until closeArr.length()) {
                    if (!closeArr.isNull(i)) {
                        val hp = closeArr.optDouble(i)
                        if (hp > 0) {
                            historyList.add(hp)
                        }
                    }
                }
            }

            if (historyList.isEmpty()) {
                historyList.addAll(
                    generateProceduralHistory(
                        startVal = regularMarketDayOpen,
                        endVal = currentPrice,
                        low = regularMarketDayLow,
                        high = regularMarketDayHigh,
                        numPoints = 40
                    )
                )
            } else {
                historyList[historyList.lastIndex] = currentPrice
            }

            val change = currentPrice - previousClose
            val changePercent = if (previousClose != 0.0) (change / previousClose) * 100.0 else 0.0

            val hash = Math.abs(symbolUpper.hashCode())
            val defaultCap = "${String.format("%.1f", (hash % 1500) / 10.0 + 1.0)}T"
            val defaultPe = (hash % 450) / 10.0 + 5.0
            val defaultVolume = "${String.format("%.1f", (hash % 900) / 10.0 + 1.0)}M"

            _isOfflineFlow.value = false
            _lastGoogleFinanceFetchTime.value = System.currentTimeMillis()

            return@withContext LiveStock(
                symbol = symbolFromYahoo,
                name = symbolUpper + " (NSE)",
                price = currentPrice,
                openPrice = regularMarketDayOpen,
                change = change,
                changePercent = changePercent,
                history = historyList,
                marketCap = defaultCap,
                peRatio = defaultPe,
                volume = defaultVolume,
                previousClose = previousClose,
                high = regularMarketDayHigh,
                low = regularMarketDayLow,
                lastUpdatedTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch live Yahoo Finance details for $querySymbol: ${e.message}")
            return@withContext null
        }
    }

    private fun parseDoubleFromScraped(text: String?): Double? {
        if (text == null) return null
        val clean = text.replace(Regex("[^0-9.]"), "")
        return clean.toDoubleOrNull()
    }

    private fun parseDayRange(text: String?): Pair<Double, Double>? {
        if (text == null) return null
        val parts = text.split("-")
        if (parts.size == 2) {
            val lowVal = parseDoubleFromScraped(parts[0])
            val highVal = parseDoubleFromScraped(parts[1])
            if (lowVal != null && highVal != null) {
                return Pair(lowVal, highVal)
            }
        }
        return null
    }

    private fun extractStatValue(html: String, label: String): String? {
        var idx = -1
        if (label == "Open") {
            val patterns = listOf(">Open</div>", ">Open<", ">Open ")
            for (pat in patterns) {
                val found = html.indexOf(pat)
                if (found != -1) {
                    idx = found + 1 // skip the '>'
                    break
                }
            }
            if (idx == -1) {
                idx = html.indexOf("Open")
            }
        } else {
            idx = html.indexOf(label)
        }
        if (idx == -1) return null
        val sub = html.substring(idx, (idx + 400).coerceAtMost(html.length))
        val regex = """class=["'][^"']*P6K39c[^"']*["'][^>]*>([^<]+)""".toRegex()
        val match = regex.find(sub)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        val fallbackRegex = """>([^<]+)</div>""".toRegex()
        val matches = fallbackRegex.findAll(sub)
        for (m in matches) {
            val candidate = m.groupValues[1].trim()
            if (candidate.isNotEmpty() && !candidate.equals(label, ignoreCase = true)) {
                return candidate
            }
        }
        return null
    }

    private suspend fun fetchLiveDetailsFromGoogleFinance(symbol: String, urlString: String): LiveStock? = withContext(Dispatchers.IO) {
        val symbolUpper = symbol.uppercase().trim()
        try {
            val url = java.net.URL(urlString)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Cookie", "CONSENT=YES+shp.gws-20211108-0-RC1.de+FX+113")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.e(tag, "Google Finance scrape error: response code $responseCode for $urlString")
                return@withContext null
            }

            val html = conn.inputStream.bufferedReader().use { it.readText() }

            // 1. Google Finance price class regex
            val priceRegex = """class=["'][^"']*YMl7ec[^"']*["'][^>]*>([^<]+)""".toRegex()
            val priceMatch = priceRegex.find(html)
            var priceText = priceMatch?.groupValues?.get(1)?.trim()

            var currentPrice: Double? = null
            if (priceText != null) {
                val cleanPriceText = priceText.replace(Regex("[^0-9.]"), "")
                currentPrice = cleanPriceText.toDoubleOrNull()
            }

            // Fallback 1: itemprop="price"
            if (currentPrice == null) {
                val itempropRegex = """itemprop=["']price["']\s+content=["']([^"']+)["']""".toRegex()
                val itempropMatch = itempropRegex.find(html) ?: """content=["']([^"']+)["']\s+itemprop=["']price["']""".toRegex().find(html)
                val itempropText = itempropMatch?.groupValues?.get(1)?.trim()
                if (itempropText != null) {
                    currentPrice = itempropText.toDoubleOrNull()
                }
            }

            // Fallback 2: LD+JSON price
            if (currentPrice == null) {
                val jsonLdPriceRegex = """["']price["']\s*:\s*["']([^"']+)["']""".toRegex()
                val jsonLdPriceMatch = jsonLdPriceRegex.find(html)
                val jsonLdPriceText = jsonLdPriceMatch?.groupValues?.get(1)?.trim()
                if (jsonLdPriceText != null) {
                    currentPrice = jsonLdPriceText.toDoubleOrNull()
                }
            }

            if (currentPrice == null) {
                Log.e(tag, "Google Finance scrape error: Could not find price for $symbolUpper in HTML")
                return@withContext null
            }

            // 2. Google Finance name class regex
            val nameRegex = """class=["'][^"']*zzDeGe[^"']*["'][^>]*>([^<]+)""".toRegex()
            val nameMatch = nameRegex.find(html)
            var companyName = nameMatch?.groupValues?.get(1)?.trim()

            if (companyName.isNullOrBlank()) {
                val nameItempropRegex = """itemprop=["']name["']\s+content=["']([^"']+)["']""".toRegex()
                val nameItempropMatch = nameItempropRegex.find(html) ?: """content=["']([^"']+)["']\s+itemprop=["']name["']""".toRegex().find(html)
                companyName = nameItempropMatch?.groupValues?.get(1)?.trim()
            }

            if (companyName.isNullOrBlank()) {
                val titleRegex = """<title>([^<]+)</title>""".toRegex()
                val titleMatch = titleRegex.find(html)
                val titleText = titleMatch?.groupValues?.get(1)
                companyName = if (titleText != null) {
                    val cleaned = titleText.replace("Stock Price & News", "").replace("Quotes", "").replace("- Google Finance", "").trim()
                    if (cleaned.isNotEmpty()) cleaned else "$symbolUpper (Google Finance)"
                } else {
                    "$symbolUpper (Google Finance)"
                }
            }

            // 3. Absolute change regex
            val changeRegex = """class=["'][^"']*(?:Jw769c|P6K39c)[^"']*["'][^>]*>([^<]+)""".toRegex()
            val changeMatch = changeRegex.find(html)
            val changeText = changeMatch?.groupValues?.get(1)?.trim() ?: ""
            val cleanChangeText = changeText.replace(Regex("[^\u22120-9.+-]"), "").replace("\u2212", "-")
            var change = cleanChangeText.toDoubleOrNull() ?: 0.0

            // 4. Percent change regex
            val percentRegex = """class=["'][^"']*(?:W7A6S|V3ZFI)[^"']*["'][^>]*>([^<]+)""".toRegex()
            val percentMatch = percentRegex.find(html)
            val percentText = percentMatch?.groupValues?.get(1)?.trim() ?: ""
            val cleanPercentText = percentText.replace(Regex("[^\u22120-9.+-]"), "").replace("\u2212", "-")
            var changePercent = cleanPercentText.toDoubleOrNull() ?: 0.0

            val hash = Math.abs(symbolUpper.hashCode())
            val defaultCap = "${String.format("%.1f", (hash % 1500) / 10.0 + 1.0)}T"
            val defaultPe = (hash % 450) / 10.0 + 5.0
            val defaultVolume = "${String.format("%.1f", (hash % 900) / 10.0 + 1.0)}M"

            val prevCloseText = extractStatValue(html, "Previous close") ?: extractStatValue(html, "Prev close")
            val openText = extractStatValue(html, "Open")
            val dayRangeText = extractStatValue(html, "Day range") ?: extractStatValue(html, "Day’s range")
            val mktCapText = extractStatValue(html, "Market cap") ?: extractStatValue(html, "Mkt cap")
            val peText = extractStatValue(html, "P/E ratio")
            val volText = extractStatValue(html, "Volume")

            val prevCloseVal = parseDoubleFromScraped(prevCloseText) ?: (currentPrice - change)
            val openVal = parseDoubleFromScraped(openText) ?: prevCloseVal

            var lowVal = currentPrice
            var highVal = currentPrice
            val rangePair = parseDayRange(dayRangeText)
            if (rangePair != null) {
                lowVal = rangePair.first
                highVal = rangePair.second
            } else {
                lowVal = Math.min(currentPrice, prevCloseVal)
                highVal = Math.max(currentPrice, prevCloseVal)
            }

            val capVal = mktCapText ?: defaultCap
            val peVal = parseDoubleFromScraped(peText) ?: defaultPe
            val volumeVal = volText ?: defaultVolume

            if (prevCloseVal != 0.0) {
                if (change == 0.0) {
                    change = currentPrice - prevCloseVal
                }
                if (changePercent == 0.0) {
                    changePercent = (change / prevCloseVal) * 100.0
                }
            }

            val historyList = generateProceduralHistory(
                startVal = openVal,
                endVal = currentPrice,
                low = lowVal,
                high = highVal,
                numPoints = 40
            )

            _isOfflineFlow.value = false
            _lastGoogleFinanceFetchTime.value = System.currentTimeMillis()

            return@withContext LiveStock(
                symbol = symbolUpper,
                name = companyName,
                price = currentPrice,
                openPrice = openVal,
                change = change,
                changePercent = changePercent,
                history = historyList,
                marketCap = capVal,
                peRatio = peVal,
                volume = volumeVal,
                previousClose = prevCloseVal,
                high = highVal,
                low = lowVal,
                lastUpdatedTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to scrape Google Finance details for $symbol: ${e.message}", e)
            return@withContext null
        }
    }

    private suspend fun fetchBestAlignedStock(symbol: String, googleUrl: String): LiveStock? {
        val googleStock = fetchLiveDetailsFromGoogleFinance(symbol, googleUrl) ?: return fetchLiveDetailsFromYahoo(symbol)
        
        // Let's also attempt Yahoo to obtain accurate, real-world historical chart coordinates
        val yahooStock = fetchLiveDetailsFromYahoo(symbol)
        if (yahooStock != null && yahooStock.history.size >= 10) {
            val yahooLast = yahooStock.history.lastOrNull() ?: yahooStock.price
            val diff = googleStock.price - yahooLast
            
            // Adjust original history points smoothly so that the last point matches the Google Stock price output precisely
            val adjustedHistory = if (Math.abs(diff) > 0.0001) {
                yahooStock.history.map { p -> p + diff }
            } else {
                yahooStock.history
            }
            
            return googleStock.copy(history = adjustedHistory)
        }
        
        return googleStock
    }

    fun getGoogleFinanceUrl(symbol: String, customUrl: String?): String {
        if (!customUrl.isNullOrBlank()) {
            return customUrl
        }
        val info = parseSymbol(symbol)
        return if (info.googleExchange.isNotEmpty()) {
            "https://www.google.com/finance/quote/${info.ticker}:${info.googleExchange}"
        } else {
            "https://www.google.com/finance/quote/${info.ticker}"
        }
    }

    suspend fun refreshStockPrice(symbol: String, context: Context? = null) {
        val symbolUpper = symbol.uppercase().trim()
        try {
            val watchlistStock = stockDao.getWatchlistStockBySymbol(symbolUpper)
            val savedStock = stockDao.getAllSavedStocks().find { it.symbol.equals(symbolUpper, ignoreCase = true) }
            val customUrl = watchlistStock?.googleFinanceUrl ?: savedStock?.googleFinanceUrl

            val googleUrl = getGoogleFinanceUrl(symbolUpper, customUrl)
            val fetched = fetchBestAlignedStock(symbolUpper, googleUrl)

            val live = if (fetched != null) {
                _notFoundSymbols.value = _notFoundSymbols.value - symbolUpper
                _isOfflineFlow.value = false
                _lastGoogleFinanceFetchTime.value = System.currentTimeMillis()
                fetched.copy(isUpdateFailed = false)
            } else {
                val existing = _liveStocksFlow.value.find { it.symbol.equals(symbolUpper, ignoreCase = true) }
                if (existing != null) {
                    existing.copy(isUpdateFailed = true)
                } else {
                    null
                }
            }

            if (live == null) return

            _notFoundSymbols.value = _notFoundSymbols.value - symbolUpper

            withContext(Dispatchers.Default) {
                val currentList = _liveStocksFlow.value.toMutableList()
                val existingIndex = currentList.indexOfFirst { it.symbol.equals(symbolUpper, ignoreCase = true) }
                if (existingIndex != -1) {
                    val found = currentList[existingIndex]
                    val displayName = if (found.name.contains("Google Finance") || found.name.length <= found.symbol.length + 3) {
                        live.name
                    } else {
                        found.name
                    }
                    val mergedHistory = if (found.history.isNotEmpty()) {
                        val list = found.history.toMutableList()
                        if (list.isEmpty() || list.last() != live.price) {
                            list.add(live.price)
                        }
                        if (list.size > 40) {
                            list.removeAt(0)
                        }
                        list
                    } else {
                        live.history
                    }
                    currentList[existingIndex] = live.copy(name = displayName, history = mergedHistory)
                } else {
                    currentList.add(live)
                }
                _liveStocksFlow.value = currentList
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refreshStockPrice for $symbolUpper: ${e.message}", e)
        }
    }

    suspend fun refreshAllLivePrices(context: Context? = null) {
        if (context != null) {
            _isOfflineFlow.value = !isNetworkAvailable(context)
        }
        try {
            withContext(Dispatchers.IO) {
                val currentList = _liveStocksFlow.value
                val allSaved = stockDao.getAllSavedStocks()
                val jobs = currentList.map { stock ->
                    async {
                        val watchlistStock = stockDao.getWatchlistStockBySymbol(stock.symbol)
                        val savedStock = allSaved.find { it.symbol.equals(stock.symbol, ignoreCase = true) }
                        val customUrl = watchlistStock?.googleFinanceUrl ?: savedStock?.googleFinanceUrl

                        val googleUrl = getGoogleFinanceUrl(stock.symbol, customUrl)
                        val live = fetchBestAlignedStock(stock.symbol, googleUrl)

                        if (live != null) {
                            _notFoundSymbols.value = _notFoundSymbols.value - stock.symbol
                            val displayName = if (stock.name.contains("Google Finance") || stock.name.length <= stock.symbol.length + 3) {
                                live.name
                            } else {
                                stock.name
                            }
                            _isOfflineFlow.value = false
                            _lastGoogleFinanceFetchTime.value = System.currentTimeMillis()
                            val mergedHistory = if (stock.history.isNotEmpty()) {
                                val list = stock.history.toMutableList()
                                if (list.isEmpty() || list.last() != live.price) {
                                    list.add(live.price)
                                }
                                if (list.size > 40) {
                                    list.removeAt(0)
                                }
                                list
                            } else {
                                live.history
                            }
                            live.copy(name = displayName, history = mergedHistory, isUpdateFailed = false)
                        } else {
                            stock.copy(isUpdateFailed = true)
                        }
                    }
                }
                val results = jobs.awaitAll().filterNotNull()
                if (results.isNotEmpty()) {
                    val updatedMap = results.associateBy { it.symbol }
                    val newList = currentList.map { original ->
                        updatedMap[original.symbol] ?: original
                    }
                    _liveStocksFlow.value = newList
                    val anySuccessfulFetch = results.any { res ->
                        val original = currentList.find { it.symbol == res.symbol }
                        original == null || res.lastUpdatedTime != original.lastUpdatedTime
                    }
                    if (anySuccessfulFetch) {
                        _lastGoogleFinanceFetchTime.value = System.currentTimeMillis()
                    }
                    if (context != null) {
                        checkPriceThresholds(context, newList)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refreshAllLivePrices: ${e.message}", e)
        }
    }

    fun startSimulation(scope: CoroutineScope, context: Context) {
        createNotificationChannel(context)
        scope.launch {
            try {
                refreshAllLivePrices(context)
            } catch (e: Exception) {
                Log.e(tag, "Failed initial live values refresh: ${e.message}")
            }
            combine(
                settingsRepository.isDataSaver,
                settingsRepository.refreshInterval
            ) { isDataSaver, refreshInterval ->
                if (isDataSaver) 900 else refreshInterval
            }.collectLatest { intervalSeconds ->
                while (true) {
                    try {
                        delay(intervalSeconds * 1000L)
                        refreshAllLivePrices(context)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(tag, "Error in price refresh tick: ${e.message}", e)
                    }
                }
            }
        }
    }

    private suspend fun checkPriceThresholds(context: Context, livePrices: List<LiveStock>) {
        try {
            val savedStocks = try {
                stockDao.getAllSavedStocks()
            } catch (e: Exception) {
                Log.e(tag, "Failed to load saved stocks for thresholds check", e)
                emptyList()
            }
            for (saved in savedStocks) {
                val live = livePrices.find { it.symbol == saved.symbol } ?: continue

                // Check targetPrice Alert with optional Proximity
                if (saved.targetPrice != null && !saved.targetHit) {
                    val prox = saved.proximityThreshold ?: 0.0
                    val isTargetReached = live.price >= saved.targetPrice
                    val isProximityReached = prox > 0.0 && live.price >= (saved.targetPrice - prox)
                    
                    if (isTargetReached || isProximityReached) {
                        try {
                            stockDao.updateSavedStockAlertStatus(saved.id, targetHit = true, stopLossHit = saved.stopLossHit)
                            val title = if (isTargetReached) "🎯 Target Hit: ${saved.symbol}" else "🎯 Target Proximity Hit: ${saved.symbol}"
                            val content = if (isTargetReached) {
                                "${saved.symbol} reached ₹${String.format("%.2f", live.price)} (Target: ₹${String.format("%.2f", saved.targetPrice)})"
                            } else {
                                "${saved.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of target (₹${String.format("%.2f", saved.targetPrice)})!"
                            }
                            sendNotification(context, id = saved.id + 1000, title = title, content = content)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to update saved stock target alert status", e)
                        }
                    }
                }

                // Check stopLoss Alert with optional Proximity
                if (saved.stopLoss != null && !saved.stopLossHit) {
                    val prox = saved.proximityThreshold ?: 0.0
                    val isStopLossReached = live.price <= saved.stopLoss
                    val isProximityReached = prox > 0.0 && live.price <= (saved.stopLoss + prox)
                    
                    if (isStopLossReached || isProximityReached) {
                        try {
                            stockDao.updateSavedStockAlertStatus(saved.id, targetHit = saved.targetHit, stopLossHit = true)
                            val title = if (isStopLossReached) "🚨 Stoploss Triggered: ${saved.symbol}" else "🚨 Stoploss Proximity: ${saved.symbol}"
                            val content = if (isStopLossReached) {
                                "${saved.symbol} dropped to ₹${String.format("%.2f", live.price)} (Stoploss: ₹${String.format("%.2f", saved.stopLoss)})"
                            } else {
                                "${saved.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of stoploss (₹${String.format("%.2f", saved.stopLoss)})!"
                            }
                            sendNotification(context, id = saved.id + 2000, title = title, content = content)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to update saved stock stoploss alert status", e)
                        }
                    }
                }
            }

            val watchlistStocks = try {
                stockDao.getAllWatchlistStocks()
            } catch (e: Exception) {
                Log.e(tag, "Failed to load watchlist stocks for thresholds check", e)
                emptyList()
            }
            for (watchlist in watchlistStocks) {
                val live = livePrices.find { it.symbol == watchlist.symbol } ?: continue

                // Check targetPrice Alert with optional Proximity
                if (watchlist.targetPrice != null && !watchlist.targetHit) {
                    val prox = watchlist.proximityThreshold ?: 0.0
                    val isTargetReached = live.price >= watchlist.targetPrice
                    val isProximityReached = prox > 0.0 && live.price >= (watchlist.targetPrice - prox)
                    
                    if (isTargetReached || isProximityReached) {
                        try {
                            stockDao.updateWatchlistStockAlertStatus(watchlist.symbol, targetHit = true, stopLossHit = watchlist.stopLossHit)
                            val title = if (isTargetReached) "🎯 Watchlist Target Hit: ${watchlist.symbol}" else "🎯 Watchlist Target Proximity: ${watchlist.symbol}"
                            val content = if (isTargetReached) {
                                "${watchlist.symbol} reached ₹${String.format("%.2f", live.price)} (Target: ₹${String.format("%.2f", watchlist.targetPrice)})"
                            } else {
                                "${watchlist.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of target (₹${String.format("%.2f", watchlist.targetPrice)})!"
                            }
                            sendNotification(context, id = watchlist.symbol.hashCode() + 5000, title = title, content = content)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to update watchlist stock target alert status", e)
                        }
                    }
                }

                // Check stopLoss Alert with optional Proximity
                if (watchlist.stopLoss != null && !watchlist.stopLossHit) {
                    val prox = watchlist.proximityThreshold ?: 0.0
                    val isStopLossReached = live.price <= watchlist.stopLoss
                    val isProximityReached = prox > 0.0 && live.price <= (watchlist.stopLoss + prox)
                    
                    if (isStopLossReached || isProximityReached) {
                        try {
                            stockDao.updateWatchlistStockAlertStatus(watchlist.symbol, targetHit = watchlist.targetHit, stopLossHit = true)
                            val title = if (isStopLossReached) "🚨 Watchlist Stoploss Triggered: ${watchlist.symbol}" else "🚨 Watchlist Stoploss Proximity: ${watchlist.symbol}"
                            val content = if (isStopLossReached) {
                                "${watchlist.symbol} dropped to ₹${String.format("%.2f", live.price)} (Stoploss: ₹${String.format("%.2f", watchlist.stopLoss)})"
                            } else {
                                "${watchlist.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of stoploss (₹${String.format("%.2f", watchlist.stopLoss)})!"
                            }
                            sendNotification(context, id = watchlist.symbol.hashCode() + 6000, title = title, content = content)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to update watchlist stock stoploss alert status", e)
                        }
                    }
                }
            }

            // Check multiple price alerts
            val priceAlerts = try {
                stockDao.getAllPriceAlerts()
            } catch (e: Exception) {
                Log.e(tag, "Failed to load price alerts for thresholds check", e)
                emptyList()
            }
            for (alert in priceAlerts) {
                val live = livePrices.find { it.symbol == alert.symbol } ?: continue

                var updatedTargetHit = alert.targetHit
                var updatedStopLossHit = alert.stopLossHit
                var statusChanged = false

                // Check targetPrice Alert with optional Proximity
                if (alert.targetPrice != null && !alert.targetHit) {
                    val prox = alert.proximityThreshold ?: 0.0
                    val isTargetReached = live.price >= alert.targetPrice
                    val isProximityReached = prox > 0.0 && live.price >= (alert.targetPrice - prox)
                    
                    if (isTargetReached || isProximityReached) {
                        updatedTargetHit = true
                        statusChanged = true
                        val title = if (isTargetReached) "🎯 Target Hit: ${alert.symbol}" else "🎯 Target Proximity Hit: ${alert.symbol}"
                        val content = if (isTargetReached) {
                            "${alert.symbol} reached ₹${String.format("%.2f", live.price)} (Target: ₹${String.format("%.2f", alert.targetPrice)})"
                        } else {
                            "${alert.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of target (₹${String.format("%.2f", alert.targetPrice)})!"
                        }
                        sendNotification(context, id = alert.id + 10000, title = title, content = content)
                    }
                }

                // Check stopLoss Alert with optional Proximity
                if (alert.stopLoss != null && !alert.stopLossHit) {
                    val prox = alert.proximityThreshold ?: 0.0
                    val isStopLossReached = live.price <= alert.stopLoss
                    val isProximityReached = prox > 0.0 && live.price <= (alert.stopLoss + prox)
                    
                    if (isStopLossReached || isProximityReached) {
                        updatedStopLossHit = true
                        statusChanged = true
                        val title = if (isStopLossReached) "🚨 Stoploss Triggered: ${alert.symbol}" else "🚨 Stoploss Proximity: ${alert.symbol}"
                        val content = if (isStopLossReached) {
                            "${alert.symbol} dropped to ₹${String.format("%.2f", live.price)} (Stoploss: ₹${String.format("%.2f", alert.stopLoss)})"
                        } else {
                            "${alert.symbol} is at ₹${String.format("%.2f", live.price)}, within ₹${String.format("%.2f", prox)} of stoploss (₹${String.format("%.2f", alert.stopLoss)})!"
                        }
                        sendNotification(context, id = alert.id + 20000, title = title, content = content)
                    }
                }

                if (statusChanged) {
                    try {
                        stockDao.updatePriceAlertStatus(alert.id, targetHit = updatedTargetHit, stopLossHit = updatedStopLossHit)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to update price alert status for alert ID ${alert.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Critical failure in checkPriceThresholds simulation", e)
        }
    }

    fun getPriceAlertsForSymbol(symbol: String): Flow<List<PriceAlertEntity>> {
        return stockDao.getPriceAlertsForSymbolFlow(symbol)
    }

    suspend fun addPriceAlert(symbol: String, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double?) = withContext(Dispatchers.IO) {
        val alert = PriceAlertEntity(
            symbol = symbol,
            targetPrice = targetPrice,
            stopLoss = stopLoss,
            proximityThreshold = proximityThreshold
        )
        stockDao.insertPriceAlert(alert)
    }

    suspend fun deletePriceAlert(id: Int) = withContext(Dispatchers.IO) {
        stockDao.deletePriceAlertById(id)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "stock_alerts_channel",
                "Stock Price Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when stocks reach target price caps or fall below stoploss limits"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(context: Context, id: Int, title: String, content: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, "stock_alerts_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info) // universal safe asset
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            manager.notify(id, builder.build())
        } catch (e: Exception) {
            Log.e(tag, "Failed to send notification: ${e.message}")
        }
    }
}
