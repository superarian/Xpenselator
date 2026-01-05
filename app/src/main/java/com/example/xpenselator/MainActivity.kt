package com.example.xpenselator

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import java.text.DecimalFormat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    // --- VARIABLES ---
    private var prevInputStr = ""
    private var currentInputStr = ""
    private var operator = ""
    private var runningResult = 0.0
    private var isNewEntry = true

    // Data
    private var grandTotal = 0.0
    private val expenseList = ArrayList<String>()
    private val summaryList = ArrayList<String>()

    // Sheet Logic
    private var currentSheetID = 1
    private var maxSheetID = 1
    private var currentToast: Toast? = null

    // Touch Logic
    private lateinit var gestureDetector: GestureDetectorCompat
    private var touchStartY = 0f
    private var isSheetMode = false
    private var tempSheetID = 1
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    // System
    private var isSoundOn = true
    private var isVibrationOn = true
    private var isDarkMode = true

    // UI ELEMENTS
    private lateinit var mainLayout: LinearLayout
    private lateinit var headerBox: RelativeLayout
    private lateinit var btnSettings: ImageButton
    private lateinit var topd: TextView
    private lateinit var secd: TextView
    private lateinit var hisd: ListView
    private lateinit var summaryView: ListView
    private lateinit var projectName: TextView

    // OVERLAY ELEMENTS
    private lateinit var overlayContainer: RelativeLayout
    private lateinit var overlayText: TextView

    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var summaryAdapter: ArrayAdapter<String>

    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Connect UI
        mainLayout = findViewById(R.id.mainLayout)
        headerBox = findViewById(R.id.headerBox)
        btnSettings = findViewById(R.id.btnSettings)
        topd = findViewById(R.id.grandTotalText)
        secd = findViewById(R.id.inputDisplay)
        hisd = findViewById(R.id.historyList)
        summaryView = findViewById(R.id.summaryList)
        projectName = findViewById(R.id.projectName)

        // CONNECT OVERLAY
        overlayContainer = findViewById(R.id.sheetOverlayContainer)
        overlayText = findViewById(R.id.sheetOverlayText)

        listAdapter = ArrayAdapter(this, R.layout.list_item, expenseList)
        hisd.adapter = listAdapter

        summaryAdapter = ArrayAdapter(this, R.layout.list_item, summaryList)
        summaryView.adapter = summaryAdapter

        loadGlobalSettings()
        loadSheetData(1)

        // --- GESTURE DETECTOR ---
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y

                if (Math.abs(diffY) > 50 && Math.abs(velocityY) > 50) {
                    if (diffY < 0) goToNextSheet()
                    else goToPrevSheet()
                    return true
                }
                return false
            }
        })

        // Listeners
        btnSettings.setOnClickListener { showSettingsDialog() }
        projectName.setOnClickListener { showRenameDialog() }

        setupCalculatorButtons()
        setupCategoryButtons()
        setupEqualButtonTouch()
    }

    // --- SETTINGS DIALOG ---
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val swSound = dialogView.findViewById<Switch>(R.id.swSound)
        val swVib = dialogView.findViewById<Switch>(R.id.swVib)
        val swTheme = dialogView.findViewById<Switch>(R.id.swTheme)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseSettings)

        swSound.isChecked = isSoundOn
        swVib.isChecked = isVibrationOn
        swTheme.isChecked = isDarkMode

        swSound.setOnCheckedChangeListener { _, isChecked -> isSoundOn = isChecked; saveGlobalSettings() }
        swVib.setOnCheckedChangeListener { _, isChecked -> isVibrationOn = isChecked; saveGlobalSettings() }
        swTheme.setOnCheckedChangeListener { _, isChecked -> isDarkMode = isChecked; saveGlobalSettings(); applyTheme() }
        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // --- TOUCH LOGIC (SPB) ---
    private fun setupEqualButtonTouch() {
        val btnEqual = findViewById<Button>(R.id.btnEqual)

        longPressRunnable = Runnable {
            isSheetMode = true
            performHaptic()
            overlayContainer.visibility = View.VISIBLE
            overlayContainer.bringToFront()
            updateOverlayList(currentSheetID)
        }

        btnEqual.setOnTouchListener { _, event ->
            if (gestureDetector.onTouchEvent(event)) {
                handler.removeCallbacks(longPressRunnable!!)
                isSheetMode = false
                overlayContainer.visibility = View.GONE
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    isSheetMode = false
                    tempSheetID = currentSheetID
                    handler.postDelayed(longPressRunnable!!, 300)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isSheetMode) {
                        val diff = (touchStartY - event.rawY).toInt()
                        val steps = diff / 30

                        var potentialSheet = currentSheetID + steps
                        if (potentialSheet < 1) potentialSheet = 1
                        if (potentialSheet > maxSheetID) potentialSheet = maxSheetID

                        if (potentialSheet != tempSheetID) {
                            performHaptic()
                            tempSheetID = potentialSheet
                            updateOverlayList(tempSheetID)
                        }
                    } else {
                        if (abs(touchStartY - event.rawY) > 50) {
                            handler.removeCallbacks(longPressRunnable!!)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable!!)

                    if (isSheetMode) {
                        overlayContainer.visibility = View.GONE
                        isSheetMode = false

                        if (tempSheetID != currentSheetID) {
                            saveSheetData(currentSheetID)
                            currentSheetID = tempSheetID
                            loadSheetData(currentSheetID)
                            showFastToast("Opened ${getSheetName(currentSheetID)}")
                        }
                    } else {
                        performEqualClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateOverlayList(highlightID: Int) {
        val lines = ArrayList<String>()
        val start = highlightID - 3
        val end = highlightID + 3

        for (i in start..end) {
            if (i < 1 || i > maxSheetID) {
                lines.add(" ")
            } else {
                val name = getSheetName(i)
                if (i == highlightID) {
                    lines.add("▶ $name ◀")
                } else {
                    lines.add(name)
                }
            }
        }
        overlayText.text = lines.joinToString("\n")
    }

    // --- MATH ENGINE ---
    private fun performEqualClick() {
        performHaptic()
        val rawExpression = secd.text.toString()
        val result = evaluateExpression(rawExpression)
        secd.text = removeZero(result)
        isNewEntry = true
    }

    private fun evaluateExpression(expr: String): Double {
        if (expr.isEmpty()) return 0.0
        var cleanExpr = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (cleanExpr.isNotEmpty() && "+-*/".contains(cleanExpr.last())) {
            cleanExpr = cleanExpr.dropLast(1)
        }
        try {
            val numbers = ArrayList<Double>()
            val ops = ArrayList<Char>()
            var currentNum = ""
            for (char in cleanExpr) {
                if (char.isDigit() || char == '.') {
                    currentNum += char
                } else if ("+-*/".contains(char)) {
                    if (currentNum.isNotEmpty()) {
                        numbers.add(currentNum.toDoubleOrNull() ?: 0.0)
                        currentNum = ""
                    }
                    ops.add(char)
                }
            }
            if (currentNum.isNotEmpty()) numbers.add(currentNum.toDoubleOrNull() ?: 0.0)
            if (numbers.isEmpty()) return 0.0
            if (numbers.size == 1) return numbers[0]

            var i = 0
            while (i < ops.size) {
                if (ops[i] == '*' || ops[i] == '/') {
                    val n1 = numbers[i]
                    val n2 = numbers[i+1]
                    var res = 0.0
                    if (ops[i] == '*') res = n1 * n2
                    else if (n2 != 0.0) res = n1 / n2
                    numbers[i] = res
                    numbers.removeAt(i+1)
                    ops.removeAt(i)
                } else {
                    i++
                }
            }
            var result = numbers[0]
            for (j in 0 until ops.size) {
                val nextNum = numbers[j+1]
                if (ops[j] == '+') result += nextNum
                else if (ops[j] == '-') result -= nextNum
            }
            return result
        } catch (e: Exception) { return 0.0 }
    }

    // --- BUTTONS ---
    private fun setupCalculatorButtons() {
        val numberButtons = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot)
        for (id in numberButtons) {
            findViewById<Button>(id).setOnClickListener {
                performHaptic()
                val digit = (it as Button).text.toString()
                if (isNewEntry) { secd.text = ""; isNewEntry = false }
                if (secd.text == "Saved!") secd.text = ""
                secd.append(digit)
            }
        }
        val opButtons = mapOf(R.id.btnAdd to "+", R.id.btnSub to "-", R.id.btnMul to "×", R.id.btnDiv to "÷")
        for ((id, op) in opButtons) {
            findViewById<Button>(id).setOnClickListener {
                performHaptic()
                val currentText = secd.text.toString()
                if (isNewEntry) isNewEntry = false
                if (secd.text == "Saved!") { secd.text = "0"; return@setOnClickListener }
                if (currentText.isNotEmpty()) {
                    val lastChar = currentText.last()
                    if ("+-×÷".contains(lastChar)) secd.text = currentText.dropLast(1) + op
                    else secd.append(op)
                }
            }
        }
        findViewById<Button>(R.id.btnAC).setOnClickListener { performHaptic(); secd.text = "0"; isNewEntry = true }
        findViewById<Button>(R.id.btnDel).setOnClickListener {
            performHaptic()
            val s = secd.text.toString()
            if (s.isNotEmpty() && s != "Saved!") {
                secd.text = s.dropLast(1)
                if (secd.text.isEmpty()) secd.text = "0"
            }
        }
        findViewById<Button>(R.id.btnPrint).setOnClickListener { shareReceipt() }
        hisd.setOnItemLongClickListener { _, _, position, _ -> showDeleteDialog(position); true }
    }

    // --- NEW CATEGORY LOGIC (RENAMED) ---
    private fun setupCategoryButtons() {
        val cats = mapOf(
            R.id.catFood to Pair("Food", "🍔"),
            R.id.catRent to Pair("Rent", "🏠"),
            // RENAMED
            R.id.catTravel to Pair("Travel", "🚕"),
            R.id.catFuel to Pair("Fuel", "⛽"),
            R.id.catShop to Pair("Shopping", "🛍️"),
            R.id.catMed to Pair("Health", "💊"),
            R.id.catGrocery to Pair("Grocery", "🛒"),
            R.id.catGym to Pair("Gym", "💪"),
            R.id.catWifi to Pair("Wifi", "🛜"),
            R.id.catPower to Pair("Electricity", "⚡"),
            R.id.catCable to Pair("Cable", "📺"),
            R.id.catWater to Pair("Water", "💧"),
            // RENAMED
            R.id.catRefresh to Pair("Drinks", "🍺"),
            R.id.catSchool to Pair("School", "🏫"),
            R.id.catTuition to Pair("Tuition", "📚"),
            R.id.catHelp to Pair("Househelp", "👨 🧹")
        )

        for ((id, pair) in cats) {
            val btn = findViewById<Button>(id)

            // --- MAGIC TRICK: Make Emoji 2x Bigger! ---
            val content = btn.text.toString()
            val newlineIndex = content.indexOf('\n')
            if (newlineIndex > 0) {
                val span = SpannableString(content)
                span.setSpan(RelativeSizeSpan(2.0f), 0, newlineIndex, 0)
                btn.text = span
            }

            btn.setOnClickListener {
                performHaptic()
                val rawExpression = secd.text.toString()
                if (rawExpression == "Saved!" || rawExpression.isEmpty()) return@setOnClickListener
                val value = evaluateExpression(rawExpression)
                if (value == 0.0) return@setOnClickListener

                grandTotal += value
                topd.text = "₹${removeZero(grandTotal)}"
                expenseList.add("${pair.second} ${pair.first}: ₹${removeZero(value)}")
                listAdapter.notifyDataSetChanged()
                calculateCategoryTotals()
                saveSheetData(currentSheetID)
                hisd.smoothScrollToPosition(expenseList.size - 1)
                secd.text = "Saved!"
                isNewEntry = true
            }
        }
    }

    // --- UTILS ---
    private fun showFastToast(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    private fun shareReceipt() {
        if(expenseList.isEmpty()) return
        performHaptic()
        val builder = StringBuilder()
        builder.append("⬛⬛ TOTAL EXPENSES ⬛⬛\n")
        builder.append("SHEET: ${getSheetName(currentSheetID)}\n")
        builder.append("----------------\n")
        for(item in expenseList) builder.append("$item\n")
        builder.append("----------------\n")
        builder.append("📊 BREAKDOWN:\n")
        for(item in summaryList) builder.append("$item\n")
        builder.append("----------------\n")
        builder.append("💰 TOTAL: ₹${removeZero(grandTotal)}\n")
        builder.append("----------------\n")
        builder.append("Generated by Xpenselator")
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, builder.toString())
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun showRenameDialog() {
        val input = EditText(this)
        input.setText(getSheetName(currentSheetID))
        input.setSelection(input.text.length)
        AlertDialog.Builder(this)
            .setTitle("Rename Workspace")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty()) {
                    saveSheetName(currentSheetID, newName)
                    projectName.text = newName.uppercase()
                    showFastToast("Renamed!")
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun saveSheetName(id: Int, name: String) {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit()
        prefs.putString("NAME_$id", name)
        prefs.apply()
    }

    private fun getSheetName(id: Int): String {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        return prefs.getString("NAME_$id", "SHEET $id") ?: "SHEET $id"
    }

    private fun goToNextSheet() {
        performHaptic()
        saveSheetData(currentSheetID)
        if (currentSheetID == maxSheetID) {
            maxSheetID++
            currentSheetID = maxSheetID
            clearScreenForNewSheet()
            projectName.text = getSheetName(currentSheetID)
            saveGlobalSettings()
            showFastToast("Created New Sheet")
        } else {
            currentSheetID++
            loadSheetData(currentSheetID)
            showFastToast(getSheetName(currentSheetID))
        }
    }

    private fun goToPrevSheet() {
        if (currentSheetID > 1) {
            performHaptic()
            saveSheetData(currentSheetID)
            currentSheetID--
            loadSheetData(currentSheetID)
            showFastToast(getSheetName(currentSheetID))
        } else {
            showFastToast("Top Reached")
        }
    }

    private fun clearScreenForNewSheet() {
        grandTotal = 0.0
        expenseList.clear()
        summaryList.clear()
        secd.text = "0"
        projectName.text = getSheetName(currentSheetID)
        listAdapter.notifyDataSetChanged()
        summaryAdapter.notifyDataSetChanged()
    }

    private fun calculateCategoryTotals() {
        val totals = HashMap<String, Double>()
        for (item in expenseList) {
            try {
                val parts = item.split(":")
                if (parts.size == 2) {
                    val catName = parts[0].trim()
                    val priceStr = parts[1].replace("₹", "").trim()
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    val currentTotal = totals.getOrDefault(catName, 0.0)
                    totals[catName] = currentTotal + price
                }
            } catch (e: Exception) { }
        }
        summaryList.clear()
        for ((name, total) in totals) {
            summaryList.add("$name: ₹${removeZero(total)}")
        }
        summaryAdapter.notifyDataSetChanged()
    }

    private fun saveSheetData(sheetId: Int) {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit()
        prefs.putFloat("TOTAL_$sheetId", grandTotal.toFloat())
        prefs.putString("LIST_$sheetId", expenseList.joinToString("#"))
        prefs.apply()
    }

    private fun loadSheetData(sheetId: Int) {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        grandTotal = prefs.getFloat("TOTAL_$sheetId", 0.0f).toDouble()
        val listString = prefs.getString("LIST_$sheetId", "")
        topd.text = "₹${removeZero(grandTotal)}"
        projectName.text = getSheetName(sheetId)
        expenseList.clear()
        if (!listString.isNullOrEmpty()) {
            expenseList.addAll(listString.split("#"))
        }
        listAdapter.notifyDataSetChanged()
        calculateCategoryTotals()
        secd.text = "0"
    }

    private fun saveGlobalSettings() {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit()
        prefs.putInt("MAX_SHEETS", maxSheetID)
        prefs.putInt("LAST_OPEN_SHEET", currentSheetID)
        prefs.putBoolean("VIB_ON", isVibrationOn)
        prefs.putBoolean("SND_ON", isSoundOn)
        prefs.putBoolean("DARK_MODE", isDarkMode)
        prefs.apply()
    }

    private fun loadGlobalSettings() {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        maxSheetID = prefs.getInt("MAX_SHEETS", 1)
        currentSheetID = prefs.getInt("LAST_OPEN_SHEET", 1)
        isVibrationOn = prefs.getBoolean("VIB_ON", true)
        isSoundOn = prefs.getBoolean("SND_ON", true)
        isDarkMode = prefs.getBoolean("DARK_MODE", true)
        applyTheme()
    }

    private fun applyTheme() {
        if (isDarkMode) {
            mainLayout.setBackgroundColor(Color.parseColor("#121212"))
            headerBox.setBackgroundColor(Color.parseColor("#1E1E1E"))
            btnSettings.setColorFilter(Color.WHITE)
            topd.setTextColor(Color.parseColor("#00FF00"))
            secd.setBackgroundColor(Color.parseColor("#2C2C2C"))
            secd.setTextColor(Color.parseColor("#00FFFF"))
        } else {
            mainLayout.setBackgroundColor(Color.parseColor("#FFFFFF"))
            headerBox.setBackgroundColor(Color.parseColor("#DDDDDD"))
            btnSettings.setColorFilter(Color.BLACK)
            topd.setTextColor(Color.parseColor("#000000"))
            secd.setBackgroundColor(Color.parseColor("#EEEEEE"))
            secd.setTextColor(Color.parseColor("#333333"))
        }
    }

    private fun performHaptic() {
        if (isSoundOn) try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {}
        if (isVibrationOn) if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) else vibrator.vibrate(50)
    }

    private fun showDeleteDialog(pos: Int) {
        val item = expenseList[pos]
        val price = item.substringAfter("₹").toDoubleOrNull() ?: 0.0
        AlertDialog.Builder(this).setTitle("Delete?").setMessage(item).setPositiveButton("Yes") { _,_ ->
            grandTotal -= price; if(grandTotal<0) grandTotal=0.0; topd.text="₹${removeZero(grandTotal)}"
            expenseList.removeAt(pos)
            listAdapter.notifyDataSetChanged()
            calculateCategoryTotals()
            saveSheetData(currentSheetID)
        }.setNegativeButton("No", null).show()
    }

    private fun removeZero(v: Double) = DecimalFormat("#.##").format(v)
}