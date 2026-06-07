package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String? = null
)

@Entity(
    tableName = "saved_stocks",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SavedStockEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val symbol: String,
    val name: String,
    val groupId: Int,
    val targetPrice: Double? = null,
    val stopLoss: Double? = null,
    val targetHit: Boolean = false,
    val stopLossHit: Boolean = false,
    val proximityThreshold: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val displayOrder: Int = 0,
    val googleFinanceUrl: String? = null
)

@Entity(tableName = "watchlist_stocks")
data class WatchlistStockEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val targetPrice: Double? = null,
    val stopLoss: Double? = null,
    val targetHit: Boolean = false,
    val stopLossHit: Boolean = false,
    val proximityThreshold: Double? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val colorHex: String? = null,
    val displayOrder: Int = 0,
    val googleFinanceUrl: String? = null
)

