package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.example.data.GroupEntity
import com.example.data.LiveStock
import com.example.ui.StockTrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: StockTrackerViewModel,
    onNavigateToStockDetail: (String) -> Unit,
    onNavigateToGroupDetail: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val refreshInterval by viewModel.refreshInterval.collectAsStateWithLifecycle()
    val isDarkModeState by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val watchlistStocks by viewModel.watchlistStocks.collectAsStateWithLifecycle()
    val liveStocks by viewModel.liveStocks.collectAsStateWithLifecycle()
    val allSavedStocks by viewModel.allSavedStocks.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val isDataSaver by viewModel.isDataSaver.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 for Portfolios, 1 for Watchlist
    val context = LocalContext.current
    var isRefreshingWatchlist by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (searchQuery.trim().isEmpty()) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets(0.dp),
                    modifier = Modifier.testTag("home_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("Portfolios") },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Custom Portfolios"
                            )
                        },
                        modifier = Modifier.testTag("nav_portfolios_tab")
                    )

                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = { Text("Watchlist") },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Personal Watchlist"
                            )
                        },
                        modifier = Modifier.testTag("nav_watchlist_tab")
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 0 && searchQuery.trim().isEmpty()) {
                FloatingActionButton(
                    onClick = { showAddGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("add_group_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create stock portfolio group")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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

            // Elegant Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Stock Tracker",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("app_name_title")
                )

                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Centralized Google Finance Search Area
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Search stocks") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("stock_search_input"),
                prefix = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Body Area: Dynamic routing based on whether search is active
            if (searchQuery.trim().isNotBlank()) {
                Text(
                    text = "Google Finance Search Results",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                if (searchResults.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "No results",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No matching stocks found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("search_results_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults, key = { it.symbol }) { stock ->
                            SearchStockResultItem(
                                stock = stock,
                                onClick = {
                                    viewModel.updateSearchQuery("") // Reset query on tap
                                    onNavigateToStockDetail(stock.symbol)
                                }
                            )
                        }
                    }
                }
            } else {
                if (activeTab == 0) {
                    // Customized Portfolio Groups section
                    Text(
                        text = "Custom portfolios",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    if (groups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Empty folders",
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Create a stock group portfolio",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Keep trackers separated by tapping the + button below.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("portfolio_groups_grid"),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(groups, key = { it.id }) { group ->
                                val stockCount = allSavedStocks.count { it.groupId == group.id }
                                GroupCardItem(
                                    group = group,
                                    stockCount = stockCount,
                                    onClick = { onNavigateToGroupDetail(group.id, group.name) },
                                    onEditColor = { color -> viewModel.updateGroupColor(group, color) },
                                    onDelete = { viewModel.deleteGroup(group) }
                                )
                            }
                        }
                    }
                } else {
                    // Personal Watchlist section
                    var isWatchlistRearrangeMode by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Personal Watchlist",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        // Three dot menu button for Watchlist options
                        var showWatchlistMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showWatchlistMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Watchlist Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showWatchlistMenu,
                                onDismissRequest = { showWatchlistMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rearrange Stocks order") },
                                    onClick = {
                                        showWatchlistMenu = false
                                        isWatchlistRearrangeMode = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = "Rearrange order"
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (isWatchlistRearrangeMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Rearrange Mode Active",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                TextButton(
                                    onClick = { isWatchlistRearrangeMode = false }
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = isRefreshingWatchlist,
                        onRefresh = {
                            isRefreshingWatchlist = true
                            viewModel.refreshAllLivePrices(context) {
                                isRefreshingWatchlist = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (watchlistStocks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Empty watchlist",
                                        modifier = Modifier.size(80.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Your Watchlist is empty",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Search for your favorite tickers and toggle the \"Personal Watchlist\" switch to keep track of them here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("watchlist_stocks_list"),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(watchlistStocks, key = { item -> item.symbol }) { item ->
                                    val livePriceMatch = liveStocks.find { it.symbol == item.symbol }
                                    val index = watchlistStocks.indexOf(item)
                                    val canMoveUp = index > 0
                                    val canMoveDown = index < watchlistStocks.size - 1

                                    WatchlistStockTileItem(
                                        watchlistStock = item,
                                        liveStock = livePriceMatch,
                                        onClick = { onNavigateToStockDetail(item.symbol) },
                                        onLongClick = { isWatchlistRearrangeMode = true },
                                        onEditColor = { color -> viewModel.updateWatchlistStockColor(item.symbol, color) },
                                        onDelete = { viewModel.removeFromWatchlist(item.symbol) },
                                        isRearrangeMode = isWatchlistRearrangeMode,
                                        canMoveUp = canMoveUp,
                                        canMoveDown = canMoveDown,
                                        onMoveUp = {
                                            if (canMoveUp) {
                                                val movedList = watchlistStocks.move(index, index - 1)
                                                viewModel.updateWatchlistStocksOrder(movedList.map { it.symbol })
                                            }
                                        },
                                        onMoveDown = {
                                            if (canMoveDown) {
                                                val movedList = watchlistStocks.move(index, index + 1)
                                                viewModel.updateWatchlistStocksOrder(movedList.map { it.symbol })
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Dialog to Add group
    if (showAddGroupDialog) {
        AddGroupDialog(
            onDismiss = { showAddGroupDialog = false },
            onConfirm = { groupName ->
                if (groupName.isNotBlank()) {
                    viewModel.addGroup(groupName)
                }
                showAddGroupDialog = false
            }
        )
    }

    // Expressive Settings Dialog for dark mode toggle and sync refresh intervals
    if (showSettingsDialog) {
        val lastFetchTime by viewModel.lastGoogleFinanceFetchTime.collectAsStateWithLifecycle()
        SettingsDialog(
            currentInterval = refreshInterval,
            currentDarkMode = isDarkModeState,
            isDataSaver = isDataSaver,
            lastUpdatedEpoch = lastFetchTime,
            onDismiss = { showSettingsDialog = false },
            onDarkModeChanged = { viewModel.toggleDarkMode(it) },
            onDataSaverChanged = { enabled -> viewModel.toggleDataSaver(enabled) },
            onIntervalChanged = { seconds ->
                viewModel.changeRefreshInterval(seconds, context)
                showSettingsDialog = false
            }
        )
    }
}

// Material 3 component for displaying search rows
@Composable
fun SearchStockResultItem(
    stock: LiveStock,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_stock_item_${stock.symbol}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.symbol,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
            if (stock.price > 0.0) {
                Text(
                    text = "₹${String.format("%.2f", stock.price)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                AssistChip(
                    onClick = onClick,
                    label = { Text("Track & Search") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

// Helper function to get the tile color, adapting to dark mode
@Composable
fun getTileCardColor(colorKey: String?): Color {
    val isDark = isSystemInDarkTheme()
    if (colorKey.isNullOrBlank() || colorKey == "default") {
        return MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    }
    return when (colorKey.lowercase()) {
        "red" -> if (isDark) Color(0xFF3E1F21) else Color(0xFFFFEBEE)
        "green" -> if (isDark) Color(0xFF1B3B22) else Color(0xFFE8F5E9)
        "yellow" -> if (isDark) Color(0xFF3E3618) else Color(0xFFFFF9C4)
        "purple" -> if (isDark) Color(0xFF2E1A33) else Color(0xFFF3E5F5)
        "teal" -> if (isDark) Color(0xFF133532) else Color(0xFFE0F2F1)
        "blue" -> if (isDark) Color(0xFF1A2A3A) else Color(0xFFE3F2FD)
        else -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    }
}

@Composable
fun getFolderBubbleColors(colorKey: String?): Pair<Color, Color> {
    val isDark = isSystemInDarkTheme()
    val key = colorKey?.lowercase() ?: "default"
    return when (key) {
        "red" -> if (isDark) Pair(Color(0xFF5D2B2E), Color(0xFFEF9A9A)) else Pair(Color(0xFFFFCDD2), Color(0xFFC62828))
        "green" -> if (isDark) Pair(Color(0xFF244F2D), Color(0xFFA5D6A7)) else Pair(Color(0xFFC8E6C9), Color(0xFF2E7D32))
        "yellow" -> if (isDark) Pair(Color(0xFF534821), Color(0xFFFFF59D)) else Pair(Color(0xFFFFF176), Color(0xFFF57F17))
        "purple" -> if (isDark) Pair(Color(0xFF422549), Color(0xFFE1BEE7)) else Pair(Color(0xFFE1BEE7), Color(0xFF6A1B9A))
        "teal" -> if (isDark) Pair(Color(0xFF1B4945), Color(0xFF80CBC4)) else Pair(Color(0xFFB2DFDB), Color(0xFF00695C))
        "blue" -> if (isDark) Pair(Color(0xFF253B52), Color(0xFF90CAF9)) else Pair(Color(0xFFBBDEFB), Color(0xFF1565C0))
        else -> Pair(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun ColorPickerDialog(
    currentColor: String?,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "default" to "Default (Light Blue)",
        "red" to "Pastel Red",
        "green" to "Pastel Green",
        "blue" to "Pastel Blue",
        "yellow" to "Pastel Yellow",
        "purple" to "Pastel Purple",
        "teal" to "Pastel Teal"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Tile Color",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colors.forEach { (key, name) ->
                    val colorValue = getTileCardColor(key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onColorSelected(key) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colorValue)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if ((currentColor == null && key == "default") || currentColor == key) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (key == "default") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Custom folder tile grid item
@Composable
fun GroupCardItem(
    group: GroupEntity,
    stockCount: Int,
    onClick: () -> Unit,
    onEditColor: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("portfolio_group_${group.id}"),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = CardDefaults.outlinedCardColors(
            containerColor = getTileCardColor(group.colorHex),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                // Folder icon (Standardized to 48dp matching stock tiles)
                val (bubbleCol, iconCol) = getFolderBubbleColors(group.colorHex)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(bubbleCol),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Folder category icon",
                        tint = iconCol,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Color") },
                            onClick = {
                                showMenu = false
                                showColorDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (stockCount == 1) "1 Stock" else "$stockCount Stocks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    if (showColorDialog) {
        ColorPickerDialog(
            currentColor = group.colorHex,
            onDismiss = { showColorDialog = false },
            onColorSelected = { color ->
                onEditColor(color)
                showColorDialog = false
            }
        )
    }
}

@Composable
fun AddGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textState by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Portfolio Group",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Enter a name for your personalized watchlist:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_group_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(textState) },
                enabled = textState.trim().isNotBlank(),
                modifier = Modifier.testTag("confirm_group_btn")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SecondsTickerText(lastUpdatedEpoch: Long, modifier: Modifier = Modifier) {
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
        "Last Updated : Never"
    } else {
        "Last Updated : $secondsAgo sec ago"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
fun SettingsDialog(
    currentInterval: Int,
    currentDarkMode: Boolean?,
    isDataSaver: Boolean,
    lastUpdatedEpoch: Long,
    onDismiss: () -> Unit,
    onDarkModeChanged: (Boolean?) -> Unit,
    onDataSaverChanged: (Boolean) -> Unit,
    onIntervalChanged: (Int) -> Unit
) {
    val intervals = listOf(
        Pair("5s (Live Ticks)", 5),
        Pair("15s (Optimal)", 15),
        Pair("30s (Balanced)", 30),
        Pair("1m (Conservation)", 60),
        Pair("5m (Slow updates)", 300)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize Tracker",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = dismissButton@{ onDismiss() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Theme selection
                Text(
                    text = "Aesthetic theme",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Auto", null, "system_mode"),
                        Triple("Light", false, "light_mode"),
                        Triple("Dark", true, "dark_mode")
                    ).forEach { (label, value, tag) ->
                        val isSelected = currentDarkMode == value
                        ElevatedFilterChip(
                            selected = isSelected,
                            onClick = { onDarkModeChanged(value) },
                            label = { Text(label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tag)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // Data Saver Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Saver Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Limits background refresh updates to 15m to conserve data and battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isDataSaver,
                        onCheckedChange = { onDataSaverChanged(it) },
                        modifier = Modifier.testTag("data_saver_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(24.dp))

                // Interval Selection
                Text(
                    text = "Google Finance Refresh rate",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    items(intervals) { (label, seconds) ->
                        val isSelected = currentInterval == seconds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onIntervalChanged(seconds) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SecondsTickerText(lastUpdatedEpoch)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchlistStockTileItem(
    watchlistStock: com.example.data.WatchlistStockEntity,
    liveStock: LiveStock?,
    onClick: () -> Unit,
    onEditColor: (String) -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {},
    isRearrangeMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { 90.dp.toPx() }

    val (bubbleCol, iconCol) = getFolderBubbleColors(watchlistStock.colorHex)
    val hasAlerts = watchlistStock.targetPrice != null || watchlistStock.stopLoss != null

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("watchlist_stock_tile_${watchlistStock.symbol}")
            .pointerInput(isRearrangeMode, canMoveUp, canMoveDown) {
                if (isRearrangeMode) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragAccumulator = 0f },
                        onDragEnd = { dragAccumulator = 0f },
                        onDragCancel = { dragAccumulator = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumulator += dragAmount.y
                            if (dragAccumulator > itemHeightPx * 0.7f) {
                                if (canMoveDown) {
                                    onMoveDown()
                                    dragAccumulator = 0f
                                }
                            } else if (dragAccumulator < -itemHeightPx * 0.7f) {
                                if (canMoveUp) {
                                    onMoveUp()
                                    dragAccumulator = 0f
                                }
                            }
                        }
                    )
                }
            }
            .combinedClickable(
                onClick = { if (!isRearrangeMode) onClick() },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = getTileCardColor(watchlistStock.colorHex)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isRearrangeMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from watchlist",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Left Side: Rounded Badge Icon (Google Home Aesthetic)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(bubbleCol),
                contentAlignment = Alignment.Center
            ) {
                if (hasAlerts) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Active Price Alert Indicator",
                        tint = iconCol,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (liveStock == null) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Loading Stock Data",
                        tint = iconCol,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    val isUp = liveStock.change >= 0
                    Icon(
                        imageVector = if (isUp) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = if (isUp) "Trending Up Icon" else "Trending Down Icon",
                        tint = iconCol,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // First column: Stock Code / Symbol and Company Name (reverted to previous layout structure)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = watchlistStock.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (hasAlerts) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Active Price Threshold Alerts Set",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = watchlistStock.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isRearrangeMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up"
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down"
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))

                // Second column: Dynamic Live Price and Daily Delta (Color-Coded) & actions (reverted to previous layout structure)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (liveStock == null) {
                            Text(
                                text = "Loading Price...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = "₹${String.format("%.2f", liveStock.price)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            val isUp = liveStock.change >= 0
                            val deltaColor = if (isUp) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            val directionSign = if (isUp) "+" else "-"
                            val changeDisplay = String.format("%.2f", Math.abs(liveStock.change))
                            val percentDisplay = String.format("%.2f", Math.abs(liveStock.changePercent))

                            Text(
                                text = "$directionSign₹$changeDisplay ($directionSign$percentDisplay%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = deltaColor
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("watchlist_item_options_${watchlistStock.symbol}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Color") },
                            onClick = {
                                showMenu = false
                                showColorDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

    if (showColorDialog) {
        ColorPickerDialog(
            currentColor = watchlistStock.colorHex,
            onDismiss = { showColorDialog = false },
            onColorSelected = { color ->
                onEditColor(color)
                showColorDialog = false
            }
        )
    }
}

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val list = this.toMutableList()
    val item = list.removeAt(fromIndex)
    list.add(toIndex, item)
    return list
}
