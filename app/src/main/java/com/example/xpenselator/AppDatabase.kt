package com.example.xpenselator

import androidx.room.Database
import androidx.room.RoomDatabase

// OWNER FIX: Version 2 to trigger destructive migration so it doesn't crash on update
@Database(entities = [ExpenseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}