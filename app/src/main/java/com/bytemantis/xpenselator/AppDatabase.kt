package com.bytemantis.xpenselator

import androidx.room.Database
import androidx.room.RoomDatabase

// Bumped to Version 3 for the new 'assignedTo' column
@Database(entities = [ExpenseEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}