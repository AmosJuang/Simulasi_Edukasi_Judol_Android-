package com.example.simulasijudolpapb.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SpinDao {
    @Insert
    suspend fun insert(spin: SpinHistory)
    
    @Query("SELECT * FROM spin_history ORDER BY timestamp DESC")
    suspend fun getAllSpins(): List<SpinHistory>
    
    @Query("SELECT SUM(balanceChange) FROM spin_history")
    suspend fun getTotalLoss(): Int?
    
    @Query("DELETE FROM spin_history")
    suspend fun deleteAll()
}
