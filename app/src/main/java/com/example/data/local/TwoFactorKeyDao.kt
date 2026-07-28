package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TwoFactorKeyDao {
    @Query("SELECT * FROM two_factor_keys ORDER BY createdTime DESC")
    fun getAllKeys(): Flow<List<TwoFactorKey>>

    @Query("SELECT * FROM two_factor_keys WHERE id = :id LIMIT 1")
    suspend fun getKeyById(id: Int): TwoFactorKey?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: TwoFactorKey): Long

    @Update
    suspend fun updateKey(key: TwoFactorKey)

    @Query("DELETE FROM two_factor_keys WHERE id = :id")
    suspend fun deleteKeyById(id: Int)

    @Query("SELECT COUNT(*) FROM two_factor_keys")
    fun getKeysCount(): Flow<Int>
}
