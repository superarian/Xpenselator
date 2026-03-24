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

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _totalAmount = MutableStateFlow("₹0.00")
    val totalAmount: StateFlow<String> = _totalAmount

    private val _expenses = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    val expenses: StateFlow<List<ExpenseEntity>> = _expenses

    private val _summaryItems = MutableStateFlow<List<String>>(emptyList())
    val summaryItems: StateFlow<List<String>> = _summaryItems

    private val _activeMembers = MutableStateFlow<List<String>>(emptyList())
    val activeMembers: StateFlow<List<String>> = _activeMembers

    var rawGrandTotal = BigDecimal.ZERO

    fun loadSheet(sheetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getExpensesForSheet(sheetId)
            _expenses.value = list

            var total = BigDecimal.ZERO
            val catTotals = HashMap<String, BigDecimal>()
            val memberTotals = HashMap<String, BigDecimal>()
            val splits = ArrayList<String>()

            for (item in list) {
                val amt = item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (!item.description.startsWith("↳")) {
                    total = total.add(amt)
                    val catName = item.description.filter { it.isLetter() }.trim()
                    if (catName.isNotEmpty()) {
                        catTotals[catName] = catTotals.getOrDefault(catName, BigDecimal.ZERO).add(amt)
                    }
                    if (item.assignedTo.isNotEmpty()) {
                        memberTotals[item.assignedTo] = memberTotals.getOrDefault(item.assignedTo, BigDecimal.ZERO).add(amt)
                    }
                } else {
                    splits.add("${item.description}: ₹${item.amount}")
                }
            }

            rawGrandTotal = total
            _totalAmount.value = "₹" + total.setScale(2, RoundingMode.HALF_UP).toPlainString()

            _activeMembers.value = repository.getGroupMembers(sheetId)

            val newSummary = ArrayList<String>()
            for ((name, t) in catTotals) {
                newSummary.add("$name: ₹${t.setScale(2, RoundingMode.HALF_UP).toPlainString()}")
            }

            if (memberTotals.isNotEmpty()) {
                newSummary.add("--- MEMBER TOTALS ---")
                for ((name, t) in memberTotals) {
                    newSummary.add("$name: ₹${t.setScale(2, RoundingMode.HALF_UP).toPlainString()}")
                }
            }

            if (splits.isNotEmpty()) {
                newSummary.add("--- BILL SPLIT ---")
                newSummary.addAll(splits)
            }
            _summaryItems.value = newSummary
        }
    }

    fun saveGroupMembers(sheetId: Int, members: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveGroupMembers(sheetId, members)
            if (members.isEmpty()) {
                repository.clearSheet(sheetId)
            }
            loadSheet(sheetId)
        }
    }

    fun addExpense(sheetId: Int, description: String, amount: BigDecimal, assignedTo: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ExpenseEntity(
                sheetId = sheetId,
                description = description,
                amount = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                assignedTo = assignedTo
            )
            repository.addExpense(entity)
            autoUpdateGlobalSplits(sheetId)
            loadSheet(sheetId)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteExpense(expense)
            autoUpdateGlobalSplits(expense.sheetId)
            loadSheet(expense.sheetId)
        }
    }

    fun clearSheet(sheetId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearSheet(sheetId)
            repository.saveGroupMembers(sheetId, emptyList())
            loadSheet(sheetId)
        }
    }

    fun deleteCategory(sheetId: Int, categoryName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(sheetId, categoryName)
            autoUpdateGlobalSplits(sheetId)
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

    // --- NEW: PERCENTAGE-BASED SAVING ---
    fun saveGlobalSplit(sheetId: Int, percentages: Map<String, BigDecimal>) {
        viewModelScope.launch(Dispatchers.IO) {
            val percString = percentages.map { "${it.key}=${it.value.toPlainString()}" }.joinToString(",")
            repository.saveGlobalSplitConfig(sheetId, percString)

            val currentList = repository.getExpensesForSheet(sheetId)
            for (item in currentList) {
                if (item.description.startsWith("↳")) {
                    repository.deleteExpense(item)
                }
            }

            for ((name, _) in percentages) {
                // Insert placeholders to register names; the auto engine will calculate real values instantly
                repository.addExpense(ExpenseEntity(sheetId = sheetId, description = "↳ [BILL SPLIT] $name", amount = "0.00"))
            }

            autoUpdateGlobalSplits(sheetId)
            loadSheet(sheetId)
        }
    }

    // --- UPDATED: DYNAMIC PERCENTAGE ENGINE ---
    private fun autoUpdateGlobalSplits(sheetId: Int) {
        val currentList = repository.getExpensesForSheet(sheetId)
        var total = BigDecimal.ZERO
        val splitMembers = ArrayList<String>()

        for (item in currentList) {
            if (item.description.startsWith("↳")) {
                val memberName = item.description.replace("↳ [BILL SPLIT]", "").trim()
                if (memberName.isNotEmpty() && !splitMembers.contains(memberName)) splitMembers.add(memberName)
                repository.deleteExpense(item)
            } else {
                val amt = item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                total = total.add(amt)
            }
        }

        if (splitMembers.isNotEmpty()) {
            val percString = repository.getGlobalSplitPercentages(sheetId)
            val percMap = HashMap<String, BigDecimal>()

            if (percString.isNotEmpty()) {
                val pairs = percString.split(",")
                for (p in pairs) {
                    val kv = p.split("=")
                    if (kv.size == 2) percMap[kv[0]] = BigDecimal(kv[1])
                }
            }

            var allocatedTotal = BigDecimal.ZERO

            for (i in 0 until splitMembers.size) {
                val name = splitMembers[i]
                val share: BigDecimal

                if (i == splitMembers.size - 1) {
                    // The last person gets the precise remainder to prevent 0.01 fractional errors
                    share = total.subtract(allocatedTotal)
                } else {
                    val p = percMap[name] ?: BigDecimal.ZERO
                    share = total.multiply(p).divide(BigDecimal("100"), 2, RoundingMode.FLOOR)
                    allocatedTotal = allocatedTotal.add(share)
                }

                val splitEntity = ExpenseEntity(
                    sheetId = sheetId,
                    description = "↳ [BILL SPLIT] $name",
                    amount = share.toPlainString()
                )
                repository.addExpense(splitEntity)
            }
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