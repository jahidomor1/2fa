package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "two_factor_keys")
data class TwoFactorKey(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val label: String,
    val issuer: String,
    val encryptedSecret: String,
    val lastGeneratedTime: Long = System.currentTimeMillis(),
    val createdTime: Long = System.currentTimeMillis()
)
