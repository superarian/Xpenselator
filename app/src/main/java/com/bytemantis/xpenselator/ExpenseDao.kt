package com.example.xpenselator

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE sheetId = :sheetId")
    fun getExpensesForSheet(sheetId: Int): List<ExpenseEntity>

    @Insert
    fun insert(expense: ExpenseEntity)

    @Delete
    fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE sheetId = :sheetId")
    fun clearSheet(sheetId: Int)

    // Advanced Owner Logic: Deletes items where description contains the category name
    @Query("DELETE FROM expenses WHERE sheetId = :sheetId AND description LIKE '%' || :category || '%'")
    fun deleteCategory(sheetId: Int, category: String)
}