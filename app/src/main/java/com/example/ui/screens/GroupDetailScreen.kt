package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SavedStockEntity
import com.example.ui.StockTrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: Int,
    groupName: String,
    viewModel: StockTrackerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToStockDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val liveStocks by viewModel.liveStocks.collectAsStateWithLifecycle()
    val allSavedStocks by viewModel.allSavedStocks.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val groupMatch = remember(groups, groupId) { groups.find { it.id == groupId } }
    val groupColorHex = groupMatch?.colorHex

    // Filter saved stocks in this group
    val savedStocks = remember(allSavedStocks, groupId) {
        allSavedStocks.filter { it.groupId == groupId }
    }

    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("group_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to portfolios",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Watchlist Portfolio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            var isRearrangeMode by remember { mutableStateOf(false) }

            // Big Bold Heading showing group name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("group_title_name")
                    )

                    Text(
                        text = "${savedStocks.size} stocks tracked • Auto-Refreshing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var showGroupMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showGroupMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Group options menu"
                        )
                    }

                    DropdownMenu(
                        expanded = showGroupMenu,
                        onDismissRequest = { showGroupMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rearrange Stocks order") },
                            onClick = {
                                showGroupMenu = false
                                isRearrangeMode = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Rearrange order icon"
                                )
                            }
                        )
                    }
                }
            }

            if (isRearrangeMode) {
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
                            onClick = { isRearrangeMode = false }
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SPECIFIC MANDATE: "Also add a clear distinction between the header and the list of stocks."
            // We use a styled, high-impact gradient divider that cleanly separates header from stock cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    .testTag("header_divider_stripe")
            )

            // Stock list with pull to refresh
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.refreshAllLivePrices(context) {
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (savedStocks.isEmpty()) {
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
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "No stocks",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "This group is empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Navigate back to search and add dynamic tickers to this portfolio.",
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
                            .testTag("group_stocks_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(savedStocks, key = { it.id }) { saved ->
                            val livePriceMatch = liveStocks.find { it.symbol == saved.symbol }
                            val index = savedStocks.indexOf(saved)
                            val canMoveUp = index > 0
                            val canMoveDown = index < savedStocks.size - 1

                            GroupStockTileItem(
                                savedStock = saved,
                                liveStock = livePriceMatch,
                                colorHex = groupColorHex,
                                onClick = {
                                    onNavigateToStockDetail(saved.symbol)
                                },
                                onLongClick = {
                                    isRearrangeMode = true
                                },
                                isRearrangeMode = isRearrangeMode,
                                onDeleteClick = {
                                    viewModel.deleteSavedStock(saved.id)
                                },
                                canMoveUp = canMoveUp,
                                canMoveDown = canMoveDown,
                                onMoveUp = {
                                    if (canMoveUp) {
                                        val movedList = savedStocks.move(index, index - 1)
                                        viewModel.updateSavedStocksOrder(movedList.map { it.id })
                                    }
                                },
                                onMoveDown = {
                                    if (canMoveDown) {
                                        val movedList = savedStocks.move(index, index + 1)
                                        viewModel.updateSavedStocksOrder(movedList.map { it.id })
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

// Visual tile display item with specific custom color highlights (Google Finance mock specifications)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupStockTileItem(
    savedStock: SavedStockEntity,
    liveStock: com.example.data.LiveStock?,
    onClick: () -> Unit,
    colorHex: String? = null,
    onLongClick: () -> Unit = {},
    isRearrangeMode: Boolean = false,
    onDeleteClick: () -> Unit = {},
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var dragAccumulator by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemHeightPx = with(density) { 90.dp.toPx() }

    val (bubbleCol, iconCol) = getFolderBubbleColors(colorHex)
    val hasAlerts = savedStock.targetPrice != null || savedStock.stopLoss != null

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("group_stock_tile_${savedStock.symbol}")
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
            containerColor = getTileCardColor(colorHex)
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
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete from group",
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

            // First Column: Symbol and Name (Previous Position layout structure)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = savedStock.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (hasAlerts) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Active Price Alerts Set",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = savedStock.name,
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

                // Second Column: Price / delta-rate layout (reverted to previous look on right side)
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
            }
        }
    }
}

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val list = this.toMutableList()
    val item = list.removeAt(fromIndex)
    list.add(toIndex, item)
    return list
}

@Composable
fun EditAlertsDialog(
    savedStock: SavedStockEntity,
    liveStock: com.example.data.LiveStock?,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (Double?, Double?) -> Unit
) {
    var targetInput by remember { mutableStateOf(savedStock.targetPrice?.let { String.format("%.2f", it) } ?: "") }
    var stopLossInput by remember { mutableStateOf(savedStock.stopLoss?.let { String.format("%.2f", it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Ticker Parameters",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "${savedStock.symbol} • ${savedStock.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (liveStock != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Price:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "MKT ₹${String.format("%.2f", liveStock.price)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "Recalibrate Price targets. You will receive an immediate notification as soon as the live Google Finance feed crosses these levels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    label = { Text("Target Alert Price (₹)") },
                    placeholder = { Text("Notify when price rises above this") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_target_price")
                )

                OutlinedTextField(
                    value = stopLossInput,
                    onValueChange = { stopLossInput = it },
                    label = { Text("Stoploss Alert Price (₹)") },
                    placeholder = { Text("Notify when price drops below this") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_stop_loss")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetVal = targetInput.toDoubleOrNull()
                    val stopLossVal = stopLossInput.toDoubleOrNull()
                    onConfirm(targetVal, stopLossVal)
                },
                modifier = Modifier.testTag("confirm_edit_alert")
            ) {
                Text("Save Targets")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("remove_stock_btn")
                ) {
                    Text("Remove")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
