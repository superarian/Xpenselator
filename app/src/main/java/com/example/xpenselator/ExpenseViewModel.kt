package com.example.xpenselator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

// --- THE CHEF (ViewModel) ---
class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _totalAmount = MutableStateFlow("₹0.00")
    val totalAmount: StateFlow<String> = _totalAmount

    // Holds the raw DB entities for the History tab
    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses

    // Holds the formatted strings for the Summary tab
    private val _summaryItems = MutableStateFlow<List<String>>(emptyList())
    val summaryItems: StateFlow<List<String>> = _summaryItems

    var rawGrandTotal = BigDecimal.ZERO // Kept for quick math checks

    fun loadSheet(sheetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getExpensesForSheet(sheetId)
            _expenses.value = list

            var total = BigDecimal.ZERO
            val catTotals = HashMap<String, BigDecimal>()
            val splits = ArrayList<String>()

            for (item in list) {
                val amt = item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (!item.description.startsWith("↳")) {
                    total = total.add(amt)
                    val catName = item.description.filter { it.isLetter() }.trim()
                    if (catName.isNotEmpty()) {
                        catTotals[catName] = catTotals.getOrDefault(catName, BigDecimal.ZERO).add(amt)
                    }
                } else {
                    splits.add("${item.description}: ₹${item.amount}")
                }
            }

            rawGrandTotal = total
            _totalAmount.value = "₹" + total.setScale(2, RoundingMode.HALF_UP).toPlainString()

            val newSummary = ArrayList<String>()
            for ((name, t) in catTotals) {
                newSummary.add("$name: ₹${t.setScale(2, RoundingMode.HALF_UP).toPlainString()}")
            }
            if (splits.isNotEmpty()) {
                newSummary.add("--- BILL SPLIT ---")
                newSummary.addAll(splits)
            }
            _summaryItems.value = newSummary
        }
    }

    fun addExpense(sheetId: Int, description: String, amount: BigDecimal) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ExpenseEntity(
                sheetId = sheetId,
                description = description,
                amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            )
            repository.addExpense(entity)
            loadSheet(sheetId)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteExpense(expense)
            loadSheet(expense.sheetId)
        }
    }

    fun clearSheet(sheetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearSheet(sheetId)
            loadSheet(sheetId)
        }
    }

    fun deleteCategory(sheetId: Int, categoryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(sheetId, categoryName)
            loadSheet(sheetId)
        }
    }

    fun removeAllSplits(sheetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = repository.getExpensesForSheet(sheetId)
            for (item in currentList) {
                if (item.description.startsWith("↳")) {
                    repository.deleteExpense(item)
                }
            }
            loadSheet(sheetId)
        }
    }

    // NEW CONTRACTOR FIX: Safe Background Transaction for Splits
    fun replaceSplits(sheetId: Int, newSplits: List<ExpenseEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Clear old splits
            val currentList = repository.getExpensesForSheet(sheetId)
            for (item in currentList) {
                if (item.description.startsWith("↳")) {
                    repository.deleteExpense(item)
                }
            }
            // 2. Add new splits
            for (split in newSplits) {
                repository.addExpense(split)
            }
            // 3. Reload UI
            loadSheet(sheetId)
        }
    }
}

class ExpenseViewModelFactory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}