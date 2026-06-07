package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockTrackerViewModel(
    private val repository: StockRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Theme Settings
    val isDarkMode: StateFlow<Boolean?> = settingsRepository.isDarkMode
    val refreshInterval: StateFlow<Int> = settingsRepository.refreshInterval
    val isDataSaver: StateFlow<Boolean> = settingsRepository.isDataSaver

    // Offline and Global Fetch Sync State Status Flows
    val isOffline: StateFlow<Boolean> = repository.isOfflineFlow
    val lastGoogleFinanceFetchTime: StateFlow<Long> = repository.lastGoogleFinanceFetchTime

    // Portfolio Groups
    val groups: StateFlow<List<GroupEntity>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live Simulated Prices
    val liveStocks: StateFlow<List<LiveStock>> = repository.liveStocksFlow

    // Saved Stocks
    val allSavedStocks: StateFlow<List<SavedStockEntity>> = repository.allSavedStocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Personal Watchlist Stocks
    val watchlistStocks: StateFlow<List<WatchlistStockEntity>> = repository.allWatchlistStocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Not Found symbols list
    val notFoundSymbols: StateFlow<Set<String>> = repository.notFoundSymbols

    fun suggestSimilarStocks(symbol: String): List<LiveStock> =
        repository.suggestSimilarStocks(symbol)

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dynamic Search Results (Google Finance simulator)
    val searchResults: StateFlow<List<LiveStock>> = combine(
        repository.liveStocksFlow,
        _searchQuery
    ) { stocks, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            val matching = stocks.filter {
                it.symbol.contains(trimmed, ignoreCase = true) ||
                it.name.contains(trimmed, ignoreCase = true)
            }
            if (!matching.any { it.symbol.equals(trimmed, ignoreCase = true) } && trimmed.length >= 2) {
                val customSymbol = trimmed.uppercase().replace(" ", "").replace(":", "")
                val customOption = LiveStock(
                    symbol = customSymbol,
                    name = "$customSymbol Stock (Google Finance NSE Ticker)",
                    price = 0.0, // Special price marker
                    openPrice = 0.0,
                    change = 0.0,
                    changePercent = 0.0,
                    history = emptyList(),
                    marketCap = "N/A",
                    peRatio = 0.0,
                    volume = "N/A"
                )
                matching + customOption
            } else {
                matching
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Interactive Functions for Groups
    fun addGroup(name: String) {
        viewModelScope.launch {
            repository.insertGroup(name)
        }
    }

    fun updateGroupColor(group: GroupEntity, colorHex: String?) {
        viewModelScope.launch {
            repository.updateGroupColor(group, colorHex)
        }
    }

    fun updateWatchlistStockColor(symbol: String, colorHex: String?) {
        viewModelScope.launch {
            repository.updateWatchlistStockColor(symbol, colorHex)
        }
    }

    fun deleteGroup(group: GroupEntity) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    // Interactive Functions for Saved Stocks
    fun addSavedStock(symbol: String, name: String, groupId: Int, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) {
        viewModelScope.launch {
            repository.insertSavedStock(symbol, name, groupId, targetPrice, stopLoss, proximityThreshold, googleFinanceUrl)
            repository.refreshStockPrice(symbol)
        }
    }

    fun deleteSavedStock(id: Int) {
        viewModelScope.launch {
            repository.removeSavedStock(id)
        }
    }

    fun updateSavedStockAlerts(id: Int, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) {
        viewModelScope.launch {
            repository.updateSavedStockAlerts(id, targetPrice, stopLoss, proximityThreshold, googleFinanceUrl)
            val match = repository.getSavedStockById(id)
            if (match != null) {
                repository.refreshStockPrice(match.symbol)
            }
        }
    }

    // Interactive Functions for Personal Watchlist Stocks
    fun addToWatchlist(symbol: String, name: String, targetPrice: Double? = null, stopLoss: Double? = null, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) {
        viewModelScope.launch {
            repository.addToWatchlist(symbol, name, targetPrice, stopLoss, proximityThreshold, googleFinanceUrl)
            repository.refreshStockPrice(symbol)
        }
    }

    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch {
            repository.removeFromWatchlist(symbol)
        }
    }

    fun updateWatchlistStockAlerts(symbol: String, targetPrice: Double?, stopLoss: Double?, proximityThreshold: Double? = null, googleFinanceUrl: String? = null) {
        viewModelScope.launch {
            repository.updateWatchlistStockAlerts(symbol, targetPrice, stopLoss, proximityThreshold, googleFinanceUrl)
            repository.refreshStockPrice(symbol)
        }
    }

    fun updateSavedStocksOrder(orderedIds: List<Int>) {
        viewModelScope.launch {
            orderedIds.forEachIndexed { index, id ->
                repository.updateSavedStockDisplayOrder(id, index)
            }
        }
    }

    fun updateWatchlistStocksOrder(orderedSymbols: List<String>) {
        viewModelScope.launch {
            orderedSymbols.forEachIndexed { index, symbol ->
                repository.updateWatchlistStockDisplayOrder(symbol, index)
            }
        }
    }

    fun ensureStockRegistered(symbol: String) {
        repository.ensureStockRegistered(symbol)
    }

    fun refreshStockPrice(symbol: String, context: Context? = null) {
        viewModelScope.launch {
            repository.refreshStockPrice(symbol, context)
        }
    }

    fun refreshAllLivePrices(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.refreshAllLivePrices(context)
            } finally {
                onComplete()
            }
        }
    }

    // Dynamic Settings updates
    fun toggleDarkMode(darkMode: Boolean?) {
        settingsRepository.setDarkMode(darkMode)
    }

    fun toggleDataSaver(enabled: Boolean) {
        settingsRepository.setDataSaver(enabled)
    }

    fun changeRefreshInterval(seconds: Int, context: Context? = null) {
        settingsRepository.setRefreshInterval(seconds)
        viewModelScope.launch {
            repository.refreshAllLivePrices(context)
        }
    }
}

class ViewModelFactory(
    private val repository: StockRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockTrackerViewModel(repository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
