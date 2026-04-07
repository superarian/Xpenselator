package com.bytemantis.xpenselator

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sheetId: Int,
    val description: String,
    val amount: String,
    val assignedTo: String = "" // NEW: True Line-Item Tagging
)