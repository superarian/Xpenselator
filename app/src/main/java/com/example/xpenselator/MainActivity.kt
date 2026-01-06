package com.example.xpenselator

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import android.widget.FrameLayout
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
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    // --- VARIABLES ---
    private var isNewEntry = true
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
    private var chartRunnable: Runnable? = null

    // System
    private var isSoundOn = true
    private var isVibrationOn = true
    private var isDarkMode = true

    // UI ELEMENTS
    private lateinit var mainLayout: LinearLayout
    private lateinit var headerBox: RelativeLayout
    private lateinit var btnSettings: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var topd: TextView
    private lateinit var secd: TextView
    private lateinit var hisd: ListView
    private lateinit var summaryView: ListView
    private lateinit var projectName: TextView

    // OVERLAYS
    private lateinit var overlayContainer: RelativeLayout
    private lateinit var overlayText: TextView
    private lateinit var historyOverlay: RelativeLayout
    private lateinit var fullHistoryList: ListView
    private lateinit var btnCloseHistory: Button

    // CHART OVERLAY
    private lateinit var chartOverlay: RelativeLayout
    private lateinit var chartContainer: FrameLayout
    private lateinit var btnCloseChart: Button

    private lateinit var listAdapter: ArrayAdapter<String>
    private lateinit var summaryAdapter: ArrayAdapter<String>
    private lateinit var fullHistoryAdapter: ArrayAdapter<String>

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
        btnHistory = findViewById(R.id.btnHistory)
        topd = findViewById(R.id.grandTotalText)
        secd = findViewById(R.id.inputDisplay)
        hisd = findViewById(R.id.historyList)
        summaryView = findViewById(R.id.summaryList)
        projectName = findViewById(R.id.projectName)

        // OVERLAYS
        overlayContainer = findViewById(R.id.sheetOverlayContainer)
        overlayText = findViewById(R.id.sheetOverlayText)
        historyOverlay = findViewById(R.id.historyOverlay)
        fullHistoryList = findViewById(R.id.fullHistoryList)
        btnCloseHistory = findViewById(R.id.btnCloseHistory)

        // CHART
        chartOverlay = findViewById(R.id.chartOverlay)
        chartContainer = findViewById(R.id.chartContainer)
        btnCloseChart = findViewById(R.id.btnCloseChart)

        listAdapter = ArrayAdapter(this, R.layout.list_item, expenseList)
        hisd.adapter = listAdapter
        summaryAdapter = ArrayAdapter(this, R.layout.list_item, summaryList)
        summaryView.adapter = summaryAdapter
        fullHistoryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, expenseList)
        fullHistoryList.adapter = fullHistoryAdapter

        loadGlobalSettings()
        loadSheetData(1)

        // GESTURE
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

        // HISTORY LOGIC
        btnHistory.setOnClickListener {
            performHaptic()
            historyOverlay.visibility = View.VISIBLE
            fullHistoryAdapter.notifyDataSetChanged()
        }
        btnCloseHistory.setOnClickListener {
            performHaptic()
            historyOverlay.visibility = View.GONE
        }

        // CHART LOGIC
        btnCloseChart.setOnClickListener { performHaptic(); chartOverlay.visibility = View.GONE }

        setupCalculatorButtons()
        setupCategoryButtons()
        setupEqualButtonTouch()
        setupZeroButtonTouch()
        setupACButtonTouch()
    }

    // --- AC BUTTON (INSTANT RESET) ---
    private fun setupACButtonTouch() {
        val btnAC = findViewById<Button>(R.id.btnAC)

        btnAC.setOnClickListener {
            performHaptic()
            secd.text = "0"
            isNewEntry = true
        }

        btnAC.setOnLongClickListener {
            performHaptic()
            // WIPE SHEET INSTANTLY (No Dialog)
            grandTotal = 0.0
            expenseList.clear()
            summaryList.clear()
            topd.text = "₹0"
            secd.text = "0"
            listAdapter.notifyDataSetChanged()
            summaryAdapter.notifyDataSetChanged()
            saveSheetData(currentSheetID)
            showFastToast("Sheet Wiped")
            true
        }
    }

    // --- CHART LOGIC (BARS) ---
    private fun showChart() {
        performHaptic()
        chartContainer.removeAllViews()

        val dataMap = HashMap<String, Float>()
        for(item in summaryList) {
            val parts = item.split(":")
            if(parts.size == 2) {
                val rawName = parts[0].trim()
                val cleanName = rawName.filter { it.isLetter() }
                val value = parts[1].replace("₹","").trim().toFloatOrNull() ?: 0f
                if(value > 0) dataMap[cleanName] = value
            }
        }

        if (dataMap.isNotEmpty()) {
            val chart = HorizontalBarChart(this, dataMap)
            chartContainer.addView(chart)
            chartOverlay.visibility = View.VISIBLE
        } else {
            showFastToast("No Data to Chart!")
        }
    }

    private fun setupZeroButtonTouch() {
        val btn0 = findViewById<Button>(R.id.btn0)
        chartRunnable = Runnable { showChart() }

        btn0.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.postDelayed(chartRunnable!!, 500)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(chartRunnable!!)
                    if (event.eventTime - event.downTime < 500) {
                        performHaptic()
                        if (isNewEntry) { secd.text = ""; isNewEntry = false }
                        secd.append("0")
                    }
                    true
                }
                else -> false
            }
        }
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
                if (i == highlightID) lines.add("▶ $name ◀") else lines.add(name)
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
        if (cleanExpr.isNotEmpty() && "+-*/".contains(cleanExpr.last())) cleanExpr = cleanExpr.dropLast(1)
        try {
            val numbers = ArrayList<Double>()
            val ops = ArrayList<Char>()
            var currentNum = ""
            for (char in cleanExpr) {
                if (char.isDigit() || char == '.') currentNum += char
                else if ("+-*/".contains(char)) {
                    if (currentNum.isNotEmpty()) { numbers.add(currentNum.toDoubleOrNull() ?: 0.0); currentNum = "" }
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
                    if (ops[i] == '*') res = n1 * n2 else if (n2 != 0.0) res = n1 / n2
                    numbers[i] = res
                    numbers.removeAt(i+1)
                    ops.removeAt(i)
                } else i++
            }
            var result = numbers[0]
            for (j in 0 until ops.size) {
                val nextNum = numbers[j+1]
                if (ops[j] == '+') result += nextNum else if (ops[j] == '-') result -= nextNum
            }
            return result
        } catch (e: Exception) { return 0.0 }
    }

    private fun setupCalculatorButtons() {
        val numberButtons = listOf(R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot)
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
                    if ("+-×÷".contains(lastChar)) secd.text = currentText.dropLast(1) + op else secd.append(op)
                }
            }
        }
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

    private fun setupCategoryButtons() {
        val cats = mapOf(
            R.id.catFood to Pair("Food", "🍔"),
            R.id.catRent to Pair("Rent", "🏠"),
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
            R.id.catRefresh to Pair("Drinks", "🍺"),
            R.id.catSchool to Pair("School", "🏫"),
            R.id.catTuition to Pair("Tuition", "📚"),
            R.id.catHelp to Pair("Househelp", "👨 🧹")
        )

        for ((id, pair) in cats) {
            val btn = findViewById<Button>(id)
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
        topd.text = "₹0" // FIXED: Explicitly clear top display
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

    // --- GHOST FIX: RESET TOTAL TO 0 BEFORE LOADING ---
    private fun loadSheetData(sheetId: Int) {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        val listString = prefs.getString("LIST_$sheetId", "")

        // CRITICAL FIX: Reset everything first
        expenseList.clear()
        grandTotal = 0.0

        if (!listString.isNullOrEmpty()) {
            val items = listString.split("#")
            expenseList.addAll(items)

            // Recalculate strictly from items
            for (item in items) {
                val priceStr = item.substringAfter("₹").trim()
                grandTotal += priceStr.toDoubleOrNull() ?: 0.0
            }
        }

        topd.text = "₹${removeZero(grandTotal)}"
        projectName.text = getSheetName(sheetId)
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
            btnHistory.setColorFilter(Color.WHITE)
            topd.setTextColor(Color.parseColor("#00FF00"))
            secd.setBackgroundColor(Color.parseColor("#2C2C2C"))
            secd.setTextColor(Color.parseColor("#00FFFF"))
        } else {
            mainLayout.setBackgroundColor(Color.parseColor("#FFFFFF"))
            headerBox.setBackgroundColor(Color.parseColor("#DDDDDD"))
            btnSettings.setColorFilter(Color.BLACK)
            btnHistory.setColorFilter(Color.BLACK)
            topd.setTextColor(Color.parseColor("#000000"))
            secd.setBackgroundColor(Color.parseColor("#EEEEEE"))
            secd.setTextColor(Color.parseColor("#333333"))
        }
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

    // --- HORIZONTAL BAR CHART CLASS ---
    class HorizontalBarChart(context: Context, val data: HashMap<String, Float>) : View(context) {
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 35f; textAlign = Paint.Align.LEFT; isFakeBoldText = true }
        // Shadow for text readability
        private val shadowPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 35f; textAlign = Paint.Align.LEFT; isFakeBoldText = true; style = Paint.Style.STROKE; strokeWidth = 3f }

        private val barHeight = 80f
        private val barGap = 30f
        private val padding = 40f

        // 16 Unique Colors
        private val colors = mapOf(
            "Food" to Color.parseColor("#FFA500"), "Rent" to Color.parseColor("#4CAF50"),
            "Travel" to Color.parseColor("#FFC107"), "Fuel" to Color.parseColor("#F44336"),
            "Shopping" to Color.parseColor("#E91E63"), "Health" to Color.parseColor("#00BCD4"),
            "Grocery" to Color.parseColor("#9C27B0"), "Gym" to Color.parseColor("#009688"),
            "Wifi" to Color.parseColor("#2196F3"), "Electricity" to Color.parseColor("#CDDC39"),
            "Cable" to Color.parseColor("#673AB7"), "Water" to Color.parseColor("#3F51B5"),
            "Drinks" to Color.parseColor("#795548"), "School" to Color.parseColor("#8BC34A"),
            "Tuition" to Color.parseColor("#FF9800"), "Househelp" to Color.parseColor("#607D8B")
        )

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val totalHeight = (data.size * (barHeight + barGap) + padding * 2).toInt()
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val maxVal = data.values.maxOrNull() ?: 1f
            var y = padding

            for((key, value) in data) {
                val barWidth = (value / maxVal) * (width - padding * 2)
                val cleanWidth = max(barWidth, 10f)

                paint.color = colors[key] ?: Color.GRAY
                canvas.drawRect(padding, y, padding + cleanWidth, y + barHeight, paint)

                val label = "$key: ₹${value.toInt()}"
                val textX = padding + 20f
                val textY = y + barHeight / 2 + 12f

                canvas.drawText(label, textX, textY, shadowPaint)
                canvas.drawText(label, textX, textY, textPaint)

                y += barHeight + barGap
            }
        }
    }
}