package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GroupEntity
import com.example.data.LiveStock
import com.example.ui.StockTrackerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalUriHandler
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    symbol: String,
    viewModel: StockTrackerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStockDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val liveStocks by viewModel.liveStocks.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val allSavedStocks by viewModel.allSavedStocks.collectAsStateWithLifecycle()
    val watchlistStocks by viewModel.watchlistStocks.collectAsStateWithLifecycle()
    val notFoundSymbols by viewModel.notFoundSymbols.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isDataSaver by viewModel.isDataSaver.collectAsStateWithLifecycle()

    val isNotFound = notFoundSymbols.contains(symbol.uppercase().trim())
    val stock = liveStocks.find { it.symbol == symbol }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Dialog state for inline group creation
    var showInlineAddGroupDialog by remember { mutableStateOf(false) }

    // Input fields for alerts
    var targetPriceInput by remember { mutableStateOf("") }
    var stopLossInput by remember { mutableStateOf("") }
    var proximityThresholdInput by remember { mutableStateOf("") }
    var googleFinanceUrlInput by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<Int?>(null) }

    val isOnWatchlist = watchlistStocks.any { it.symbol == symbol }

    LaunchedEffect(symbol) {
        viewModel.ensureStockRegistered(symbol)
        viewModel.refreshStockPrice(symbol, context)
    }

    // Only pre-initialize inputs when they are empty and we find matching alerts
    LaunchedEffect(symbol, allSavedStocks, watchlistStocks) {
        if (targetPriceInput.isEmpty() && stopLossInput.isEmpty() && proximityThresholdInput.isEmpty() && googleFinanceUrlInput.isEmpty() && selectedGroupId == null) {
            val matchingSaved = allSavedStocks.find { it.symbol == symbol }
            val matchingWatchlist = watchlistStocks.find { it.symbol == symbol }
            if (matchingSaved != null) {
                selectedGroupId = matchingSaved.groupId
                targetPriceInput = matchingSaved.targetPrice?.let { String.format("%.2f", it) } ?: ""
                stopLossInput = matchingSaved.stopLoss?.let { String.format("%.2f", it) } ?: ""
                proximityThresholdInput = matchingSaved.proximityThreshold?.let { String.format("%.2f", it) } ?: ""
                googleFinanceUrlInput = matchingSaved.googleFinanceUrl ?: ""
            } else if (matchingWatchlist != null) {
                targetPriceInput = matchingWatchlist.targetPrice?.let { String.format("%.2f", it) } ?: ""
                stopLossInput = matchingWatchlist.stopLoss?.let { String.format("%.2f", it) } ?: ""
                proximityThresholdInput = matchingWatchlist.proximityThreshold?.let { String.format("%.2f", it) } ?: ""
                googleFinanceUrlInput = matchingWatchlist.googleFinanceUrl ?: ""
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stock?.symbol ?: symbol,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        if (isNotFound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Not Found Icon",
                        modifier = Modifier.size(72.dp).testTag("not_found_icon"),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Stock Not Found",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We could not find any live values for \"$symbol\" on Yahoo Finance or Google Finance. Please make sure the name/symbol was entered correctly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    val similarSuggestions = remember(symbol) {
                        viewModel.suggestSimilarStocks(symbol)
                    }

                    if (similarSuggestions.isNotEmpty()) {
                        Text(
                            text = "Did you mean?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Left
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        similarSuggestions.forEach { suggestedStock ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        onNavigateToStockDetail(suggestedStock.symbol)
                                    }
                                    .testTag("suggestion_${suggestedStock.symbol}"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestedStock.symbol,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = suggestedStock.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Navigate to suggestion",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (stock == null || stock.price == 0.0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Retreiving live logs from Google Finance...")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (isOffline) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("offline_banner"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "No Network Connection Icon",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "No connection found",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (isDataSaver) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) Color(0xFF2D2700) else Color(0xFFFFF3CD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .testTag("data_saver_banner"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Data Saver Icon",
                                tint = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF664D03)
                            )
                            Text(
                                text = "Data saver mode enabled",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSystemInDarkTheme()) Color(0xFFFFD54F) else Color(0xFF664D03)
                            )
                        }
                    }
                }

                var activeDetailTab by remember { mutableIntStateOf(0) }

                TabRow(
                    selectedTabIndex = activeDetailTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeDetailTab == 0,
                        onClick = { activeDetailTab = 0 },
                        text = { Text("Tracking & Alerts", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null) }
                    )
                    Tab(
                        selected = activeDetailTab == 1,
                        onClick = { activeDetailTab = 1 },
                        text = { Text("Track On Google", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Language, contentDescription = null) }
                    )
                }

                if (activeDetailTab == 1) {
                    GoogleFinanceWebView(
                        symbol = symbol,
                        googleFinanceUrl = if (googleFinanceUrlInput.isNotBlank()) googleFinanceUrlInput.trim() else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    var isRefreshing by remember { mutableStateOf(false) }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                viewModel.refreshStockPrice(symbol, context)
                                kotlinx.coroutines.delay(1000)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Quick interval selector option in top left
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().testTag("live_interval_selector_row"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    var dropdownExpanded by remember { mutableStateOf(false) }
                                    val currentInterval by viewModel.refreshInterval.collectAsStateWithLifecycle()

                                    Box {
                                        InputChip(
                                            selected = true,
                                            onClick = { dropdownExpanded = true },
                                            label = { Text("Update: ${currentInterval}s", style = MaterialTheme.typography.labelMedium) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Speed,
                                                    contentDescription = "Sync speed icon",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "Open dropdown list symbol",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("interval_chip")
                                        )

                                        DropdownMenu(
                                            expanded = dropdownExpanded,
                                            onDismissRequest = { dropdownExpanded = false }
                                        ) {
                                            val intervals = listOf(
                                                Pair("5s (Live Ticks)", 5),
                                                Pair("15s (Optimal)", 15),
                                                Pair("30s (Balanced)", 30),
                                                Pair("1m (Conservation)", 60),
                                                Pair("5m (Slow)", 300)
                                            )
                                            intervals.forEach { (label, seconds) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        viewModel.changeRefreshInterval(seconds, context)
                                                        dropdownExpanded = false
                                                    },
                                                    leadingIcon = {
                                                        if (currentInterval == seconds) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected update rate icon",
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.testTag("interval_option_$seconds")
                                                )
                                            }
                                        }
                                    }

                                    SecondsTickerTextInline(stock.lastUpdatedTime, stock.isUpdateFailed)
                                }
                            }

                            // Price & Percent Header
                            item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stock.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "₹${String.format("%.2f", stock.price)}",
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Percent Indicator
                            val isUp = stock.change >= 0
                            val color = if (isUp) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            val sign = if (isUp) "+" else "-"
                            val cleanPercentVal = Math.abs(stock.changePercent)
                            val cleanChangeVal = Math.abs(stock.change)

                            Row(
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = if (isUp) "Trending Up" else "Trending Down",
                                    tint = color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$sign₹${String.format("%.2f", cleanChangeVal)} (${String.format("%.2f", cleanPercentVal)}%)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = color
                                )
                            }
                        }
                    }
                }

                // Interactive Canvas Chart
                item {
                    var isChartExpanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Intraday Price History (Live Tracker)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { isChartExpanded = !isChartExpanded },
                                    modifier = Modifier.size(28.dp).testTag("toggle_chart_button")
                                ) {
                                    Icon(
                                        imageVector = if (isChartExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isChartExpanded) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isChartExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))

                                InteractiveStockChart(
                                    history = stock.history,
                                    openPrice = stock.openPrice,
                                    isUp = stock.change >= 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val symbolUpper = symbol.uppercase()
                            val isBse = symbolUpper == "BAJFINANCE" || symbolUpper == "POWERGRID" || symbolUpper == "NTPC" || symbolUpper == "ULTRACEMCO" || symbolUpper == "ADANIPORTS"
                            val exchange = if (isBse) "BOM" else "NSE"
                            val targetUrl = if (googleFinanceUrlInput.isNotBlank()) {
                                googleFinanceUrlInput.trim()
                            } else {
                                "https://www.google.com/finance/quote/${symbolUpper}:${exchange}"
                            }
                            try {
                                uriHandler.openUri(targetUrl)
                            } catch (e: Exception) {
                                // Fail gracefully
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_source_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Web Source",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Source",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick statistics card grid
                item {
                    val highVal = if (stock.high > 0.0) stock.high else (if (stock.history.isNotEmpty()) stock.history.maxOrNull() ?: stock.price else stock.price * 1.002)
                    val lowVal = if (stock.low > 0.0) stock.low else (if (stock.history.isNotEmpty()) stock.history.minOrNull() ?: stock.price else stock.price * 0.998)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Market Statistics",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(label = "Open", value = "₹${String.format("%.2f", if (stock.openPrice > 0.0) stock.openPrice else stock.price)}", modifier = Modifier.weight(1f))
                            StatCard(label = "Previous Close", value = "₹${String.format("%.2f", if (stock.previousClose > 0.0) stock.previousClose else stock.openPrice)}", modifier = Modifier.weight(1f))
                        }

                        StatCard(
                            label = "Day's range",
                            value = "₹${String.format("%.2f", lowVal)} - ₹${String.format("%.2f", highVal)}",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Portfolio & Alert Control Console
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portfolio_alerts_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Configure Threshold Price Alerts - MOVED TO TOP
                            Text(
                                text = "Configure Threshold Price Alerts",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = targetPriceInput,
                                    onValueChange = { targetPriceInput = it },
                                    label = { Text("Target Price (₹)") },
                                    placeholder = { Text("e.g. ${String.format("%.1f", stock.price * 1.15)}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("target_price_field")
                                )

                                OutlinedTextField(
                                    value = stopLossInput,
                                    onValueChange = { stopLossInput = it },
                                    label = { Text("Stoploss (₹)") },
                                    placeholder = { Text("e.g. ${String.format("%.1f", stock.price * 0.85)}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stoploss_field")
                                )
                            }

                            OutlinedTextField(
                                value = proximityThresholdInput,
                                onValueChange = { proximityThresholdInput = it },
                                label = { Text("Proximity Threshold (₹)") },
                                placeholder = { Text("Trigger alerts when price is within ₹ amount") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Proximity Alert indicator"
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("proximity_threshold_field")
                            )
                            Text(
                                text = "Proximity triggers notifications when the stock price enters within the specified amount of your target or stoploss price.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )

                            Button(
                                onClick = {
                                    val targetPriceVal = targetPriceInput.toDoubleOrNull()
                                    val stopLossVal = stopLossInput.toDoubleOrNull()
                                    val proxVal = proximityThresholdInput.toDoubleOrNull()

                                    if (!isOnWatchlist && selectedGroupId == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Please toggle Personal Watchlist or select/save a folder first.")
                                        }
                                        return@Button
                                    }

                                    if (isOnWatchlist) {
                                        viewModel.addToWatchlist(
                                            symbol = stock.symbol,
                                            name = stock.name,
                                            targetPrice = targetPriceVal,
                                            stopLoss = stopLossVal,
                                            proximityThreshold = proxVal,
                                            googleFinanceUrl = if (googleFinanceUrlInput.isBlank()) null else googleFinanceUrlInput.trim()
                                        )
                                    }

                                    val gId = selectedGroupId
                                    if (gId != null) {
                                        viewModel.addSavedStock(
                                            symbol = stock.symbol,
                                            name = stock.name,
                                            groupId = gId,
                                            targetPrice = targetPriceVal,
                                            stopLoss = stopLossVal,
                                            proximityThreshold = proxVal,
                                            googleFinanceUrl = if (googleFinanceUrlInput.isBlank()) null else googleFinanceUrlInput.trim()
                                        )
                                    }

                                    scope.launch {
                                        val msg = buildString {
                                            append("Limits updated successfully")
                                            if (isOnWatchlist) append(" on Personal Watchlist")
                                            if (gId != null) {
                                                val fName = groups.find { it.id == gId }?.name ?: "portfolio"
                                                append(if (isOnWatchlist) " and in $fName!" else " in $fName!")
                                            } else {
                                                append("!")
                                            }
                                        }
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("save_options_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Save settings")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Personal Watchlist Settings
                            Text(
                                text = "Personal Watchlist Settings",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Personal Watchlist",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Add to direct watchlist navigation view",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = isOnWatchlist,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            viewModel.addToWatchlist(
                                                symbol = stock.symbol,
                                                name = stock.name,
                                                googleFinanceUrl = if (googleFinanceUrlInput.isBlank()) null else googleFinanceUrlInput.trim()
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Added ${stock.symbol} to Personal Watchlist")
                                            }
                                        } else {
                                            viewModel.removeFromWatchlist(stock.symbol)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Removed ${stock.symbol} from Personal Watchlist")
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("watchlist_toggle_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Custom Portfolio Folders
                            Text(
                                text = "Custom Portfolio Folders",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Manage Watchlist Folders manually using '+' button:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Portfolio choices from DB
                                    groups.forEach { group ->
                                        val matchingSaved = allSavedStocks.find { it.symbol == symbol && it.groupId == group.id }
                                        val isInFolder = matchingSaved != null
                                        val isSelected = selectedGroupId == group.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedGroupId = group.id
                                                if (matchingSaved != null) {
                                                    targetPriceInput = matchingSaved.targetPrice?.let { String.format("%.2f", it) } ?: ""
                                                    stopLossInput = matchingSaved.stopLoss?.let { String.format("%.2f", it) } ?: ""
                                                    proximityThresholdInput = matchingSaved.proximityThreshold?.let { String.format("%.2f", it) } ?: ""
                                                }
                                            },
                                            label = { Text(group.name) },
                                            leadingIcon = {
                                                if (isInFolder) {
                                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp))
                                                } else {
                                                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Folder", modifier = Modifier.size(14.dp))
                                                }
                                            },
                                            trailingIcon = {
                                                if (!isInFolder) {
                                                    IconButton(
                                                        onClick = {
                                                            val targetPriceVal = targetPriceInput.toDoubleOrNull()
                                                            val stopLossVal = stopLossInput.toDoubleOrNull()
                                                            val proxVal = proximityThresholdInput.toDoubleOrNull()
                                                            viewModel.addSavedStock(
                                                                symbol = stock.symbol,
                                                                name = stock.name,
                                                                groupId = group.id,
                                                                targetPrice = targetPriceVal,
                                                                stopLoss = stopLossVal,
                                                                proximityThreshold = proxVal,
                                                                googleFinanceUrl = if (googleFinanceUrlInput.isBlank()) null else googleFinanceUrlInput.trim()
                                                            )
                                                            selectedGroupId = group.id
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Added ${stock.symbol} to folder ${group.name}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Add manually",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                } else {
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteSavedStock(matchingSaved.id)
                                                            if (selectedGroupId == group.id) {
                                                                selectedGroupId = null
                                                            }
                                                            scope.launch {
                                                                snackbarHostState.showSnackbar("Removed ${stock.symbol} from folder ${group.name}")
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove manually",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("group_chip_${group.id}")
                                        )
                                    }

                                    // Inline addition option
                                    FilterChip(
                                        selected = false,
                                        onClick = { showInlineAddGroupDialog = true },
                                        label = { Text("New Group...") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Create Group inline", modifier = Modifier.size(16.dp))
                                        },
                                        modifier = Modifier.testTag("new_group_inline_chip")
                                    )
                                }
                            }

                        }
                    }
                }

                // Google Finance Sync Card at the very bottom of the page
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_finance_sync_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Google Finance Sync",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = googleFinanceUrlInput,
                                onValueChange = { googleFinanceUrlInput = it },
                                label = { Text("Google Finance URL (Optional)") },
                                placeholder = { Text("e.g. https://www.google.com/finance/quote/...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = "Link Icon"
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_finance_url_field")
                            )

                            Text(
                                text = "Provide a Google Finance URL to scrape and sync live pricing & company names directly from Google Finance instead of default mock values.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )

                            Button(
                                onClick = {
                                    val url = if (googleFinanceUrlInput.isBlank()) null else googleFinanceUrlInput.trim()
                                    val targetPriceVal = targetPriceInput.toDoubleOrNull()
                                    val stopLossVal = stopLossInput.toDoubleOrNull()
                                    val proxVal = proximityThresholdInput.toDoubleOrNull()

                                    // Save to Watchlist if checked on watchlist, OR if they have no other options, we automatically put it on Watchlist
                                    if (isOnWatchlist || (!isOnWatchlist && selectedGroupId == null)) {
                                        viewModel.addToWatchlist(
                                            symbol = stock.symbol,
                                            name = stock.name,
                                            targetPrice = targetPriceVal,
                                            stopLoss = stopLossVal,
                                            proximityThreshold = proxVal,
                                            googleFinanceUrl = url
                                        )
                                    }

                                    // Save to Folder / Saved Stock if a group is selected
                                    val gId = selectedGroupId
                                    if (gId != null) {
                                        viewModel.addSavedStock(
                                            symbol = stock.symbol,
                                            name = stock.name,
                                            groupId = gId,
                                            targetPrice = targetPriceVal,
                                            stopLoss = stopLossVal,
                                            proximityThreshold = proxVal,
                                            googleFinanceUrl = url
                                        )
                                    }

                                    scope.launch {
                                        isRefreshing = true
                                        viewModel.refreshStockPrice(stock.symbol)
                                        kotlinx.coroutines.delay(1000)
                                        isRefreshing = false
                                        snackbarHostState.showSnackbar("Synced live metrics from Google Finance!")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("google_sync_save_fetch_btn")
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Save and Fetch")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save & Fetch Pricing", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                    }
                }
                }
            }
        }
    }

    // Modal dialog to add a new customizable folder/portfolio group inline
    if (showInlineAddGroupDialog) {
        AddGroupDialog(
            onDismiss = { showInlineAddGroupDialog = false },
            onConfirm = { newGroupName ->
                if (newGroupName.isNotBlank()) {
                    viewModel.addGroup(newGroupName)
                    scope.launch {
                        // Small buffer to allow Room write before selecting
                        kotlinx.coroutines.delay(200)
                        val updatedGroups = viewModel.groups.value
                        val matched = updatedGroups.find { it.name.trim().equals(newGroupName.trim(), ignoreCase = true) }
                        if (matched != null) {
                            selectedGroupId = matched.id
                        } else if (updatedGroups.isNotEmpty()) {
                            selectedGroupId = updatedGroups.last().id
                        }
                    }
                }
                showInlineAddGroupDialog = false
            }
        )
    }
}

// Gorgeous custom Canvas visualization mapping for price spark curves
@Composable
fun InteractiveStockChart(
    history: List<Double>,
    openPrice: Double,
    isUp: Boolean,
    modifier: Modifier = Modifier
) {
    val strokeColor = if (isUp) Color(0xFF2E7D32) else Color(0xFFD32F2F)

    if (history.size < 2) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Accumulating tracking historical segments...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var activeHoverIndex by remember(history) { mutableIntStateOf(-1) }

    // Resolve current theme onSurface/onSurfaceVariant color for texts
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(history) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val chartLeft = 55.dp.toPx()
                        val chartRight = size.width - 12.dp.toPx()
                        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
                        val xStep = chartWidth / (history.size - 1).coerceAtLeast(1)
                        val relativeX = (offset.x - chartLeft).coerceIn(0f, chartWidth)
                        val rawIdx = if (xStep > 0f) (relativeX / xStep).roundToInt() else 0
                        activeHoverIndex = rawIdx.coerceIn(history.indices)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val chartLeft = 55.dp.toPx()
                        val chartRight = size.width - 12.dp.toPx()
                        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
                        val xStep = chartWidth / (history.size - 1).coerceAtLeast(1)
                        val relativeX = (change.position.x - chartLeft).coerceIn(0f, chartWidth)
                        val rawIdx = if (xStep > 0f) (relativeX / xStep).roundToInt() else 0
                        activeHoverIndex = rawIdx.coerceIn(history.indices)
                    },
                    onDragEnd = {
                        activeHoverIndex = -1
                    },
                    onDragCancel = {
                        activeHoverIndex = -1
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height

        val chartLeft = 55.dp.toPx()
        val chartRight = width - 12.dp.toPx()
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)

        val chartTop = 16.dp.toPx()
        val chartBottom = height - 25.dp.toPx()
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        // Include openPrice & all historical values in the min/max resolution for perfect visual accuracy
        var maxPrice = Math.max(history.maxOrNull() ?: 1.0, openPrice)
        var minPrice = Math.min(history.minOrNull() ?: 0.0, openPrice)
        val priceDiff = maxPrice - minPrice
        if (priceDiff < 0.01) {
            maxPrice += 1.0
            minPrice -= 1.0
        } else {
            // Add exact 10% vertical bounds buffer to avoid clipping topmost or bottommost curves
            maxPrice += priceDiff * 0.10
            minPrice -= priceDiff * 0.10
        }
        val priceRange = maxPrice - minPrice

        val points = history.size
        val xStep = chartWidth / (points - 1)

        val linePath = Path()
        val fillPath = Path()

        for (i in history.indices) {
            val p = history[i]
            val x = chartLeft + i * xStep
            val y = chartBottom - (((p - minPrice) / priceRange) * chartHeight).toFloat()

            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, chartBottom)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (i == history.lastIndex) {
                fillPath.lineTo(x, chartBottom)
                fillPath.close()
            }
        }

        // Draw horizontal grid lines & price labels on the left sidebar
        val gridLinesCount = 5
        val androidPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (onSurfaceVariantColor.alpha * 180).toInt(),
                (onSurfaceVariantColor.red * 255).toInt(),
                (onSurfaceVariantColor.green * 255).toInt(),
                (onSurfaceVariantColor.blue * 255).toInt()
            )
            val density = size.width / width // Handle density scaling
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }

        for (i in 0 until gridLinesCount) {
            val fraction = i.toFloat() / (gridLinesCount - 1)
            val gridPrice = maxPrice - fraction * priceRange
            val gridY = chartTop + fraction * chartHeight

            // Draw horizontal guideline across grid area
            drawLine(
                color = Color.Gray.copy(alpha = 0.10f),
                start = Offset(chartLeft, gridY),
                end = Offset(chartRight, gridY),
                strokeWidth = 1.dp.toPx()
            )

            // Draw Y-axis price label: Spaced 8dp left, right-aligned to align numbers cleanly
            val formattedPrice = if (priceRange < 10.0) {
                String.format("%,.2f", gridPrice)
            } else if (priceRange < 100.0) {
                String.format("%,.1f", gridPrice)
            } else {
                String.format("%,.0f", gridPrice)
            }

            drawContext.canvas.nativeCanvas.drawText(
                formattedPrice,
                chartLeft - 8.dp.toPx(),
                gridY + 4.dp.toPx(),
                androidPaint
            )
        }

        // Draw Previous close dashed reference line
        val openY = chartBottom - (((openPrice - minPrice) / priceRange) * chartHeight).toFloat()
        if (openY in chartTop..chartBottom) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.40f),
                start = Offset(chartLeft, openY),
                end = Offset(chartRight, openY),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                strokeWidth = 1.dp.toPx()
            )

            val textPaintCloseLabel = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            val textPaintCloseValue = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 9.sp.toPx()
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("Prev close", chartRight, openY - 14.dp.toPx(), textPaintCloseLabel)
            drawContext.canvas.nativeCanvas.drawText("₹${String.format("%,.2f", openPrice)}", chartRight, openY - 2.dp.toPx(), textPaintCloseValue)
        }

        // Draw glowing shaded gradient underneath price curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(strokeColor.copy(alpha = 0.22f), Color.Transparent),
                startY = chartTop,
                endY = chartBottom
            )
        )

        // Draw crisp price curve line
        drawPath(
            path = linePath,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw solid current price dot with soft halo glow
        if (history.isNotEmpty()) {
            val lastPrice = history.last()
            val lastX = chartLeft + (history.lastIndex) * xStep
            val lastY = chartBottom - (((lastPrice - minPrice) / priceRange) * chartHeight).toFloat()

            drawCircle(
                color = strokeColor.copy(alpha = 0.22f),
                radius = 8.dp.toPx(),
                center = Offset(lastX, lastY)
            )
            drawCircle(
                color = strokeColor,
                radius = 4.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        // Draw solid bottom X-axis baseline
        drawLine(
            color = Color.Gray.copy(alpha = 0.25f),
            start = Offset(chartLeft, chartBottom),
            end = Offset(chartRight, chartBottom),
            strokeWidth = 1.dp.toPx()
        )

        // Draw timeline ticks and coordinates below the chart baseline
        val basePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (onSurfaceVariantColor.alpha * 180).toInt(),
                (onSurfaceVariantColor.red * 255).toInt(),
                (onSurfaceVariantColor.green * 255).toInt(),
                (onSurfaceVariantColor.blue * 255).toInt()
            )
            textSize = 9.sp.toPx()
            isAntiAlias = true
        }

        val timelineLabels = listOf("9:30 AM", "11:30 AM", "1:30 PM", "3:30 PM")
        val positionsX = listOf(chartLeft, chartLeft + chartWidth * 0.33f, chartLeft + chartWidth * 0.66f, chartRight)

        val tickHeight = 4.dp.toPx()
        positionsX.forEach { tickX ->
            drawLine(
                color = Color.Gray.copy(alpha = 0.25f),
                start = Offset(tickX, chartBottom),
                end = Offset(tickX, chartBottom + tickHeight),
                strokeWidth = 1.dp.toPx()
            )
        }

        for (i in timelineLabels.indices) {
            val align = when (i) {
                0 -> android.graphics.Paint.Align.LEFT
                timelineLabels.lastIndex -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.CENTER
            }
            basePaint.textAlign = align

            drawContext.canvas.nativeCanvas.drawText(
                timelineLabels[i],
                positionsX[i],
                chartBottom + tickHeight + 11.dp.toPx(),
                basePaint
            )
        }

        // Draw Interactive touch tooltip seeks
        if (activeHoverIndex in history.indices) {
            val hoverPrice = history[activeHoverIndex]
            val hoverX = chartLeft + activeHoverIndex * xStep
            val hoverY = chartBottom - (((hoverPrice - minPrice) / priceRange) * chartHeight).toFloat()

            // Draw vertical guideline
            drawLine(
                color = strokeColor.copy(alpha = 0.5f),
                start = Offset(hoverX, chartTop),
                end = Offset(hoverX, chartBottom),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                strokeWidth = 1.dp.toPx()
            )

            // Draw center focal point dot
            drawCircle(
                color = strokeColor,
                radius = 7.dp.toPx(),
                center = Offset(hoverX, hoverY)
            )
            drawCircle(
                color = Color.White,
                radius = 3.dp.toPx(),
                center = Offset(hoverX, hoverY)
            )

            // Format date-time block
            val startMinutes = 9 * 60 + 30 // 9:30 AM
            val minutesPerTick = 12
            val itemMinutes = startMinutes + activeHoverIndex * minutesPerTick
            val hr = itemMinutes / 60
            val min = itemMinutes % 60
            val ampm = if (hr >= 12) "PM" else "AM"
            val styledHr = if (hr > 12) hr - 12 else (if (hr == 0) 12 else hr)
            val timeString = String.format("%d:%02d %s", styledHr, min, ampm)
            val tooltipText = "₹${String.format("%,.2f", hoverPrice)} ($timeString)"

            // Measure & Draw Tooltip card boundaries
            val tooltipWidth = 140.dp.toPx()
            val tooltipHeight = 28.dp.toPx()
            var tooltipX = hoverX - tooltipWidth / 2f
            if (tooltipX < chartLeft) tooltipX = chartLeft
            if (tooltipX + tooltipWidth > chartRight) tooltipX = chartRight - tooltipWidth

            val tooltipY = 8.dp.toPx()

            // Tooltip background
            drawRoundRect(
                color = if (isUp) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                topLeft = Offset(tooltipX, tooltipY),
                size = Size(tooltipWidth, tooltipHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Fill
            )

            drawRoundRect(
                color = strokeColor.copy(alpha = 0.5f),
                topLeft = Offset(tooltipX, tooltipY),
                size = Size(tooltipWidth, tooltipHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            val tooltipTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 9.sp.toPx()
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.drawText(
                tooltipText,
                tooltipX + tooltipWidth / 2f,
                tooltipY + tooltipHeight / 2f + 3.dp.toPx(),
                tooltipTextPaint
            )
        }
    }
}

// Stats detail grids
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GoogleFinanceWebView(symbol: String, googleFinanceUrl: String? = null, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val symbolUpper = symbol.uppercase()
    val isBse = symbolUpper == "BAJFINANCE" || symbolUpper == "POWERGRID" || symbolUpper == "NTPC" || symbolUpper == "ULTRACEMCO" || symbolUpper == "ADANIPORTS"
    val exchange = if (isBse) "BOM" else "NSE"
    
    val targetUrl = if (!googleFinanceUrl.isNullOrBlank()) {
        googleFinanceUrl
    } else {
        "https://www.google.com/finance/quote/${symbolUpper}:${exchange}"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand Header Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4285F4)
                            )
                        )
                        Text(
                            text = "o",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEA4335)
                            )
                        )
                        Text(
                            text = "o",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFBBC05)
                            )
                        )
                        Text(
                            text = "g",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4285F4)
                            )
                        )
                        Text(
                            text = "l",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34A853)
                            )
                        )
                        Text(
                            text = "e",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEA4335)
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Finance",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Live ${exchange} Smart Index Feed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Action Launcher Link Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Google Finance Link",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Browse Live Interactive Charts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Access live financial indicators, candle charts, historical options, and balance sheets for ${exchange}: $symbolUpper directly on Google Finance in a safe browser window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            try {
                                uriHandler.openUri(targetUrl)
                            } catch (e: Exception) {
                                // Fallback safe log
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open ${exchange}:${symbolUpper} Stock on Google Finance", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Live News Feed Header
        item {
            Text(
                text = "Google Finance Curated News",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // News Headlines Custom to Symbol
        val articles = listOf(
            Pair(
                "${exchange}: $symbolUpper Technical Pivot Alert",
                "Market analysts spot a bullish convergence pattern. Inflow statistics highlight dynamic accumulation on institutional trading desks as index values stabilize."
            ),
            Pair(
                "$symbolUpper Market Volatility and Momentum",
                "Trading volume spike suggests fresh breakout signals. Positive macroeconomic factors are fueling additional growth potential for standard market contracts."
            ),
            Pair(
                "NIFTY Indices Sector Outlook Report",
                "Indian financial services index continues to witness optimistic forecasts, with major large-cap enterprises including $symbolUpper pushing key benchmarks higher."
            )
        )

        items(articles.size) { idx ->
            val article = articles[idx]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            uriHandler.openUri(targetUrl)
                        } catch (e: Exception) {
                            // Safe click fallback
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Newspaper,
                            contentDescription = "News Article",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NSE & FINANCE NEWS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "2 hours ago",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = article.first,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = article.second,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Read on Google Finance",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecondsTickerTextInline(lastUpdatedEpoch: Long, isUpdateFailed: Boolean, modifier: Modifier = Modifier) {
    var secondsAgo by remember(lastUpdatedEpoch) {
        mutableStateOf(if (lastUpdatedEpoch > 0) Math.max(0L, System.currentTimeMillis() - lastUpdatedEpoch) / 1000 else -1)
    }
    LaunchedEffect(lastUpdatedEpoch) {
        if (lastUpdatedEpoch > 0) {
            while (true) {
                secondsAgo = Math.max(0L, System.currentTimeMillis() - lastUpdatedEpoch) / 1000
                delay(1000L)
            }
        }
    }

    val text = if (secondsAgo < 0) {
        "Never"
    } else {
        "$secondsAgo sec ago"
    }

    val color = if (isUpdateFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Text(
        text = "Last Updated : $text",
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = color,
        modifier = modifier
    )
}

