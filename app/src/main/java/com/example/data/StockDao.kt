package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM groups ORDER BY createdAt ASC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("SELECT * FROM saved_stocks ORDER BY displayOrder ASC, addedAt DESC")
    fun getAllSavedStocksFlow(): Flow<List<SavedStockEntity>>

    @Query("SELECT * FROM saved_stocks ORDER BY displayOrder ASC, addedAt DESC")
    suspend fun getAllSavedStocks(): List<SavedStockEntity>

    @Query("SELECT * FROM saved_stocks WHERE id = :id LIMIT 1")
    suspend fun getSavedStockById(id: Int): SavedStockEntity?

    @Query("SELECT * FROM saved_stocks WHERE groupId = :groupId ORDER BY displayOrder ASC, addedAt DESC")
    fun getSavedStocksForGroup(groupId: Int): Flow<List<SavedStockEntity>>

    @Query("SELECT * FROM saved_stocks WHERE groupId = :groupId ORDER BY displayOrder ASC, addedAt DESC")
    suspend fun getSavedStocksForGroupList(groupId: Int): List<SavedStockEntity>

    @Query("SELECT * FROM saved_stocks WHERE symbol = :symbol AND groupId = :groupId LIMIT 1")
    suspend fun getSavedStockBySymbolAndGroup(symbol: String, groupId: Int): SavedStockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedStock(stock: SavedStockEntity): Long

    @Update
    suspend fun updateSavedStock(stock: SavedStockEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Update
    suspend fun updateWatchlistStock(stock: WatchlistStockEntity)

    @Delete
    suspend fun deleteSavedStock(stock: SavedStockEntity)

    @Query("DELETE FROM saved_stocks WHERE id = :id")
    suspend fun deleteSavedStockById(id: Int)

    @Query("UPDATE saved_stocks SET targetHit = :targetHit, stopLossHit = :stopLossHit WHERE id = :id")
    suspend fun updateSavedStockAlertStatus(id: Int, targetHit: Boolean, stopLossHit: Boolean)

    // Watchlist operations
    @Query("SELECT * FROM watchlist_stocks ORDER BY displayOrder ASC, addedAt DESC")
    fun getAllWatchlistStocksFlow(): Flow<List<WatchlistStockEntity>>

    @Query("SELECT * FROM watchlist_stocks ORDER BY displayOrder ASC, addedAt DESC")
    suspend fun getAllWatchlistStocks(): List<WatchlistStockEntity>

    @Query("SELECT * FROM watchlist_stocks WHERE symbol = :symbol LIMIT 1")
    suspend fun getWatchlistStockBySymbol(symbol: String): WatchlistStockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistStock(stock: WatchlistStockEntity)

    @Delete
    suspend fun deleteWatchlistStock(stock: WatchlistStockEntity)

    @Query("DELETE FROM watchlist_stocks WHERE symbol = :symbol")
    suspend fun deleteWatchlistStockBySymbol(symbol: String)

    @Query("UPDATE watchlist_stocks SET targetHit = :targetHit, stopLossHit = :stopLossHit WHERE symbol = :symbol")
    suspend fun updateWatchlistStockAlertStatus(symbol: String, targetHit: Boolean, stopLossHit: Boolean)

    @Query("UPDATE saved_stocks SET displayOrder = :displayOrder WHERE id = :id")
    suspend fun updateSavedStockDisplayOrder(id: Int, displayOrder: Int)

    @Query("UPDATE watchlist_stocks SET displayOrder = :displayOrder WHERE symbol = :symbol")
    suspend fun updateWatchlistStockDisplayOrder(symbol: String, displayOrder: Int)

    // Multiple alerts operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceAlert(alert: PriceAlertEntity): Long

    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAllPriceAlertsFlow(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    suspend fun getAllPriceAlerts(): List<PriceAlertEntity>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol ORDER BY createdAt DESC")
    fun getPriceAlertsForSymbolFlow(symbol: String): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE symbol = :symbol ORDER BY createdAt DESC")
    suspend fun getPriceAlertsForSymbol(symbol: String): List<PriceAlertEntity>

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deletePriceAlertById(id: Int)

    @Query("UPDATE price_alerts SET targetHit = :targetHit, stopLossHit = :stopLossHit WHERE id = :id")
    suspend fun updatePriceAlertStatus(id: Int, targetHit: Boolean, stopLossHit: Boolean)

    @Query("DELETE FROM price_alerts WHERE symbol = :symbol")
    suspend fun deletePriceAlertsBySymbol(symbol: String)
}
