package com.example.simulasijudolpapb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spin_history")
data class SpinHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val result: String,
    val balanceChange: Int,
    val timestamp: Long
)
