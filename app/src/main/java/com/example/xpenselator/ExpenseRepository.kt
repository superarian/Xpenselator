package com.example.xpenselator

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.abs

class ExpenseRepository(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val db: FirebaseFirestore
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)

    // --- SHEET MANAGEMENT (ROOM DATABASE) ---
    fun getExpensesForSheet(sheetId: Int): List<ExpenseEntity> {
        return expenseDao.getExpensesForSheet(sheetId)
    }

    fun addExpense(expense: ExpenseEntity) {
        expenseDao.insert(expense)
    }

    fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.delete(expense)
    }

    fun clearSheet(sheetId: Int) {
        expenseDao.clearSheet(sheetId)
    }

    fun deleteCategory(sheetId: Int, categoryName: String) {
        expenseDao.deleteCategory(sheetId, categoryName)
    }

    // --- GLOBAL SETTINGS ---
    fun isDarkMode(): Boolean = prefs.getBoolean("DARK_MODE", true)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("DARK_MODE", enabled).apply()
    }

    fun isProVersion(): Boolean = prefs.getBoolean("IS_PRO", false)

    fun setProVersion(enabled: Boolean) {
        prefs.edit().putBoolean("IS_PRO", enabled).apply()
    }

    fun isSheetLocked(sheetId: Int): Boolean = prefs.getBoolean("LOCKED_$sheetId", false)

    fun setSheetLocked(sheetId: Int, locked: Boolean) {
        prefs.edit().putBoolean("LOCKED_$sheetId", locked).apply()
    }

    // --- SECURITY & ID ---
    fun getHardwareID(): Int {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "random"
            val hash = abs(androidId.hashCode())
            (hash % 9000) + 1000
        } catch (e: Exception) {
            9999
        }
    }

    // --- FIREBASE SYNC ---
    fun syncProStatus(deviceId: Int, onResult: (Boolean) -> Unit) {
        db.collection("PremiumUsers").document(deviceId.toString())
            .get()
            .addOnSuccessListener { document ->
                val serverIsPro = document != null && document.getBoolean("isPro") == true
                if (isProVersion() != serverIsPro) {
                    setProVersion(serverIsPro)
                }
                onResult(serverIsPro)
            }
            .addOnFailureListener {
                onResult(isProVersion())
            }
    }
}