package com.example.xpenselator

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    // --- FREEMIUM & SECURITY ---
    private var isProVersion = false
    private val FREE_SHEET_LIMIT = 3
    private val SECRET_SALT = "BYTESKULL_MAKES_APPS_2026"
    private val PAYMENT_LINK = "https://t.me/Xpenselator_Bot"

    private var isNewEntry = true
    // FIXED: Use BigDecimal for Banking-Level Precision
    private var grandTotal = BigDecimal.ZERO
    private val expenseList = ArrayList<String>()
    private val summaryList = ArrayList<String>()

    // --- LIMITS ---
    private val MAX_INPUT_DIGITS = 9
    private val MAX_TOTAL_LIMIT = BigDecimal("1000000000000") // 1 Trillion

    private var currentSheetID = 1
    private var maxSheetID = 1
    private var deviceRequestID = 0
    private var currentToast: Toast? = null

    private lateinit var gestureDetector: GestureDetectorCompat
    private var touchStartY = 0f
    private var isSheetMode = false
    private var tempSheetID = 1
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var chartRunnable: Runnable? = null

    private var isSoundOn = true
    private var isVibrationOn = true
    private var isDarkMode = true

    // UI ELEMENTS
    private lateinit var rootView: RelativeLayout
    private lateinit var mainLayout: LinearLayout
    private lateinit var headerBox: RelativeLayout
    private lateinit var btnSettings: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var topd: TextView
    private lateinit var secd: TextView

    private lateinit var keypadArea: GridLayout
    private lateinit var catLayout: LinearLayout
    private lateinit var historyContainer: LinearLayout
    private lateinit var keypadContainer: LinearLayout

    private lateinit var hisd: RecyclerView
    private lateinit var summaryRecycler: RecyclerView

    private lateinit var projectName: TextView
    private lateinit var overlayContainer: RelativeLayout
    private lateinit var overlayText: TextView
    private lateinit var historyOverlay: RelativeLayout
    private lateinit var fullHistoryList: ListView
    private lateinit var btnCloseHistory: Button
    private lateinit var chartOverlay: RelativeLayout
    private lateinit var chartContainer: FrameLayout
    private lateinit var btnCloseChart: Button

    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var summaryAdapter: SummaryAdapter
    private lateinit var fullHistoryAdapter: ArrayAdapter<String>

    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        deviceRequestID = getHardwareID()

        // Bind Views
        rootView = findViewById(R.id.rootView)
        mainLayout = findViewById(R.id.mainLayout)
        headerBox = findViewById(R.id.headerBox)
        btnSettings = findViewById(R.id.btnSettings)
        btnHistory = findViewById(R.id.btnHistory)
        topd = findViewById(R.id.grandTotalText)
        secd = findViewById(R.id.inputDisplay)
        keypadArea = findViewById(R.id.keypadArea)

        val hList = findViewById<View>(R.id.historyList)
        historyContainer = hList.parent as LinearLayout

        val kArea = findViewById<View>(R.id.keypadArea)
        keypadContainer = kArea.parent as LinearLayout

        val cCustom = findViewById<View>(R.id.catCustom)
        catLayout = cCustom.parent as LinearLayout

        hisd = findViewById(R.id.historyList)
        hisd.layoutManager = LinearLayoutManager(this)
        expenseAdapter = ExpenseAdapter(expenseList)
        hisd.adapter = expenseAdapter

        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { deleteItem(viewHolder.adapterPosition) }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(hisd)

        summaryRecycler = findViewById(R.id.summaryList)
        summaryRecycler.layoutManager = LinearLayoutManager(this)
        summaryAdapter = SummaryAdapter(summaryList)
        summaryRecycler.adapter = summaryAdapter

        val rightSwipe = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if(pos < summaryList.size) { deleteCategory(summaryList[pos].split(":")[0].trim()) }
            }
        }
        ItemTouchHelper(rightSwipe).attachToRecyclerView(summaryRecycler)

        projectName = findViewById(R.id.projectName)
        overlayContainer = findViewById(R.id.sheetOverlayContainer)
        overlayText = findViewById(R.id.sheetOverlayText)
        historyOverlay = findViewById(R.id.historyOverlay)
        fullHistoryList = findViewById(R.id.fullHistoryList)
        btnCloseHistory = findViewById(R.id.btnCloseHistory)
        chartOverlay = findViewById(R.id.chartOverlay)
        chartContainer = findViewById(R.id.chartContainer)
        btnCloseChart = findViewById(R.id.btnCloseChart)

        // Time Machine Text Color Logic
        fullHistoryAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, expenseList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                return view
            }
        }
        fullHistoryList.adapter = fullHistoryAdapter

        loadGlobalSettings()
        applyThemeManual()
        loadSheetData(currentSheetID)

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                if (Math.abs(diffY) > 50 && Math.abs(velocityY) > 50) {
                    if (diffY < 0) goToNextSheet() else goToPrevSheet()
                    return true
                }
                return false
            }
        })

        btnSettings.setOnClickListener { showSettingsDialog() }
        projectName.setOnClickListener { showRenameDialog() }
        projectName.setOnLongClickListener { deleteCurrentSheet(); true }

        btnHistory.setOnClickListener { performHaptic(); historyOverlay.visibility = View.VISIBLE; fullHistoryAdapter.notifyDataSetChanged() }
        btnCloseHistory.setOnClickListener { performHaptic(); historyOverlay.visibility = View.GONE }
        btnCloseChart.setOnClickListener { performHaptic(); chartOverlay.visibility = View.GONE }

        setupCalculatorButtons()
        setupCategoryButtons()
        setupEqualButtonTouch()
        setupZeroButtonTouch()
        setupACButtonTouch()
    }

    // --- THEME ENGINE ---
    private fun applyThemeManual() {
        if (isDarkMode) {
            window.statusBarColor = Color.BLACK
            rootView.setBackgroundColor(Color.BLACK)
            headerBox.setBackgroundColor(Color.parseColor("#1E1E1E"))
            topd.setTextColor(Color.GREEN)
            btnSettings.setColorFilter(Color.WHITE)
            btnHistory.setColorFilter(Color.LTGRAY)
            historyContainer.background.setTint(Color.parseColor("#1A1A1A"))
            keypadContainer.background.setTint(Color.parseColor("#050505"))
            historyOverlay.setBackgroundColor(Color.parseColor("#151515"))
            secd.setBackgroundColor(Color.parseColor("#2C2C2C"))
            secd.setTextColor(Color.CYAN)

            for (i in 0 until keypadArea.childCount) {
                val child = keypadArea.getChildAt(i)
                if (child is Button) {
                    val isSpecial = child.text == "=" || child.text == "AC" || child.text == "0"
                    child.setBackgroundResource(if(isSpecial) R.drawable.btn_spb else R.drawable.btn_cyber)
                    child.background.setTintList(null)

                    if ("+-×÷".contains(child.text)) child.setTextColor(Color.WHITE)
                    else if (child.text == "0") child.setTextColor(Color.GREEN)
                    else if (!isSpecial && child.text != "⌫") child.setTextColor(Color.WHITE)
                }
            }
        } else {
            window.statusBarColor = Color.parseColor("#E0E0E0")
            rootView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            headerBox.setBackgroundColor(Color.WHITE)
            topd.setTextColor(Color.parseColor("#006400"))
            secd.setBackgroundColor(Color.WHITE)
            secd.setTextColor(Color.BLACK)
            // THIS LINE KEEPS THE LIST BACKGROUND BLACK EVEN IN LIGHT MODE
            historyContainer.background.setTint(Color.BLACK)
            historyOverlay.setBackgroundColor(Color.WHITE)
            btnSettings.setColorFilter(Color.DKGRAY)
            btnHistory.setColorFilter(Color.DKGRAY)
            keypadContainer.background.setTint(Color.parseColor("#E0E0E0"))

            for (i in 0 until keypadArea.childCount) {
                val child = keypadArea.getChildAt(i)
                if (child is Button) {
                    val isSpecial = child.text == "=" || child.text == "AC" || child.text == "0"
                    val gd = GradientDrawable()
                    gd.shape = GradientDrawable.RECTANGLE
                    gd.cornerRadius = 20f
                    gd.setColor(Color.WHITE)

                    if (child.text == "AC") {
                        gd.setStroke(6, Color.RED)
                        child.setTextColor(Color.RED)
                    } else if (child.text == "0") {
                        gd.setStroke(6, Color.GREEN)
                        child.setTextColor(Color.BLACK)
                    } else if (child.text == "=") {
                        gd.setStroke(6, Color.GREEN)
                        gd.setColor(Color.parseColor("#E0FFE0"))
                        child.setTextColor(Color.parseColor("#00AA00"))
                    } else {
                        gd.setStroke(3, Color.BLACK)
                        if ("+-×÷".contains(child.text)) child.setTextColor(Color.BLUE)
                        else if (child.text == "⌫") child.setTextColor(Color.RED)
                        else child.setTextColor(Color.BLACK)
                    }
                    child.background = gd
                }
            }
        }
        expenseAdapter.notifyDataSetChanged()
        summaryAdapter.notifyDataSetChanged()
    }

    private fun getDynamicTextColor(): Int {
        return if (isDarkMode) Color.WHITE else Color.BLACK
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val swSound = dialogView.findViewById<Switch>(R.id.swSound)
        val swVib = dialogView.findViewById<Switch>(R.id.swVib)
        val swTheme = dialogView.findViewById<Switch>(R.id.swTheme)

        val bg = dialogView as LinearLayout
        if(!isDarkMode) {
            bg.background.setTint(Color.WHITE)
            (bg.getChildAt(0) as TextView).setTextColor(Color.BLACK)
            swSound.setTextColor(Color.BLACK)
            swVib.setTextColor(Color.BLACK)
            swTheme.setTextColor(Color.BLACK)
        }

        val container = dialogView as ViewGroup
        val btnTools = Button(this)
        btnTools.text = "🛠️ OPEN TOOLS"
        btnTools.setTextColor(Color.WHITE)
        btnTools.textSize = 16f
        btnTools.background.setTint(Color.parseColor("#444444"))
        btnTools.setPadding(0, 20, 0, 20)

        val doneBtn = dialogView.findViewById<Button>(R.id.btnCloseSettings)
        val index = container.indexOfChild(doneBtn)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 30)
        btnTools.layoutParams = params

        container.addView(btnTools, index)

        swSound.isChecked = isSoundOn; swVib.isChecked = isVibrationOn; swTheme.isChecked = isDarkMode
        swSound.setOnCheckedChangeListener { _, c -> isSoundOn = c; saveGlobalSettings() }
        swVib.setOnCheckedChangeListener { _, c -> isVibrationOn = c; saveGlobalSettings() }

        swTheme.setOnCheckedChangeListener { _, c ->
            isDarkMode = c
            saveGlobalSettings()
            applyThemeManual()

            if(!isDarkMode) {
                bg.background.setTint(Color.WHITE)
                (bg.getChildAt(0) as TextView).setTextColor(Color.BLACK)
                swSound.setTextColor(Color.BLACK); swVib.setTextColor(Color.BLACK); swTheme.setTextColor(Color.BLACK)
            } else {
                bg.background.setTint(Color.parseColor("#EE111111"))
                (bg.getChildAt(0) as TextView).setTextColor(Color.GREEN)
                swSound.setTextColor(Color.WHITE); swVib.setTextColor(Color.WHITE); swTheme.setTextColor(Color.WHITE)
            }
        }

        btnTools.setOnClickListener { performHaptic(); dialog.dismiss(); showUtilityDashboard() }
        doneBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showUtilityDashboard() {
        val options = arrayOf("💱 Currency Converter", "📏 Distance (Km, M, Ft)", "⚖️ Weight Converter")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                view.textSize = 18f
                return view
            }
        }

        val titleView = TextView(this)
        titleView.text = "UTILITY STATION"
        titleView.textSize = 22f
        titleView.setTextColor(if(isDarkMode) Color.CYAN else Color.BLUE)
        titleView.setPadding(40, 40, 40, 20)
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER

        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setAdapter(adapter) { _, which ->
                when(which) {
                    0 -> showCurrencyTool()
                    1 -> showDistanceTool()
                    2 -> showWeightTool()
                }
            }
            .setNegativeButton("Close", null)
            .create().apply {
                window?.setBackgroundDrawableResource(if (isDarkMode) android.R.color.background_dark else android.R.color.background_light)
                listView.setBackgroundColor(if(isDarkMode) Color.parseColor("#1E1E1E") else Color.WHITE)
                show()
            }
    }

    private fun showCurrencyTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        val lastRate = prefs.getFloat("LAST_RATE", 85.0f)
        val textColor = getDynamicTextColor()

        val lbl1 = TextView(this); lbl1.text = "Exchange Rate:"; lbl1.setTextColor(Color.GRAY); layout.addView(lbl1)
        val rateInput = EditText(this); rateInput.setText(lastRate.toString()); rateInput.setTextColor(textColor); layout.addView(rateInput)
        val lbl2 = TextView(this); lbl2.text = "\nAmount (₹):"; lbl2.setTextColor(Color.GRAY); layout.addView(lbl2)
        val amtInput = EditText(this); amtInput.setText(formatBigDecimal(grandTotal)); amtInput.setTextColor(textColor); layout.addView(amtInput)
        val resultText = TextView(this); resultText.text = "..."; resultText.textSize = 24f; resultText.gravity = Gravity.CENTER; layout.addView(resultText)

        val titleView = TextView(this); titleView.text = "💱 Currency"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER

        val dialog = AlertDialog.Builder(this).setCustomTitle(titleView).setView(layout)
            .setPositiveButton("CALCULATE") { _, _ -> }
            .setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }
            .create()

        dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val rate = rateInput.text.toString().toDoubleOrNull()
            val amount = amtInput.text.toString().toDoubleOrNull()
            if (rate != null && amount != null && rate != 0.0) {
                performHaptic(); prefs.edit().putFloat("LAST_RATE", rate.toFloat()).apply()
                // Use BigDecimal for Currency calculation too
                val rateBD = BigDecimal.valueOf(rate)
                val amountBD = BigDecimal.valueOf(amount)
                val res = amountBD.divide(rateBD, 2, RoundingMode.HALF_UP)
                resultText.text = res.toPlainString()
                resultText.setTextColor(if(isDarkMode) Color.CYAN else Color.BLUE)
            } else resultText.text = "Invalid"
        }
    }

    private fun showDistanceTool() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)
        scrollView.addView(layout)
        val textColor = getDynamicTextColor()

        val input = EditText(this); input.hint = "Enter Value"; input.setTextColor(textColor); input.setHintTextColor(Color.LTGRAY); layout.addView(input)
        val resText = TextView(this); resText.text = "---"; resText.textSize = 22f; resText.setTextColor(if(isDarkMode) Color.YELLOW else Color.parseColor("#FF8800")); resText.gravity = Gravity.CENTER; resText.setPadding(0, 30, 0, 0)

        fun addPair(b1t: String, f1: Double, b2t: String, f2: Double, u1: String, u2: String) {
            val box = LinearLayout(this); box.orientation = LinearLayout.HORIZONTAL; box.weightSum = 2f
            val b1 = Button(this); b1.text = b1t; val b2 = Button(this); b2.text = b2t
            b1.layoutParams = LinearLayout.LayoutParams(0, -2, 1f); b2.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)

            if(isDarkMode) { b1.setBackgroundColor(Color.DKGRAY); b2.setBackgroundColor(Color.DKGRAY); b1.setTextColor(Color.WHITE); b2.setTextColor(Color.WHITE) }
            else { b1.setBackgroundColor(Color.LTGRAY); b2.setBackgroundColor(Color.LTGRAY); b1.setTextColor(Color.BLACK); b2.setTextColor(Color.BLACK) }

            b1.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * f1)} $u2" } }
            b2.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * f2)} $u1" } }
            box.addView(b1); box.addView(b2); layout.addView(box)
        }
        addPair("Km ➡ Mi", 0.621371, "Mi ➡ Km", 1.60934, "Km", "Mi");
        addPair("M ➡ Km", 0.001, "Km ➡ M", 1000.0, "m", "Km");
        addPair("Ft ➡ M", 0.3048, "M ➡ Ft", 3.28084, "ft", "m")
        layout.addView(resText)

        val titleView = TextView(this); titleView.text = "📏 Distance Lab"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER

        AlertDialog.Builder(this).setCustomTitle(titleView).setView(scrollView)
            .setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }
            .create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    private fun showWeightTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)
        val textColor = getDynamicTextColor()
        val input = EditText(this); input.hint = "Enter Weight"; input.setTextColor(textColor); input.setHintTextColor(Color.LTGRAY); layout.addView(input)
        val resText = TextView(this); resText.text = "---"; resText.textSize = 22f;
        resText.setTextColor(if(isDarkMode) Color.YELLOW else Color.parseColor("#FF8800"))
        resText.gravity = Gravity.CENTER; resText.setPadding(0, 30, 0, 0)

        val btnBox = LinearLayout(this); btnBox.orientation = LinearLayout.HORIZONTAL; btnBox.weightSum = 2f; btnBox.setPadding(0, 20, 0, 0)
        val btn1 = Button(this); btn1.text = "Kg ➡ Lbs"; btn1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btn2 = Button(this); btn2.text = "Lbs ➡ Kg"; btn2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        if(isDarkMode) { btn1.setBackgroundColor(Color.DKGRAY); btn2.setBackgroundColor(Color.DKGRAY); btn1.setTextColor(Color.WHITE); btn2.setTextColor(Color.WHITE) }
        else { btn1.setBackgroundColor(Color.LTGRAY); btn2.setBackgroundColor(Color.LTGRAY); btn1.setTextColor(Color.BLACK); btn2.setTextColor(Color.BLACK) }

        btn1.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * 2.20462)} Lbs" } }
        btn2.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v / 2.20462)} Kg" } }
        btnBox.addView(btn1); btnBox.addView(btn2); layout.addView(btnBox); layout.addView(resText)

        val titleView = TextView(this)
        titleView.text = "⚖️ Weight"
        titleView.textSize = 20f
        titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK)
        titleView.setPadding(40, 40, 40, 20)
        titleView.gravity = Gravity.CENTER

        AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(layout)
            .setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }
            .create().apply {
                window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
                show()
            }
    }

    private fun sharePdfReport() {
        if (!isProVersion) { showUpsellDialog(); return }
        performHaptic()
        val scrollView = ScrollView(this); val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10); scrollView.addView(layout)
        val titleInput = EditText(this); titleInput.hint = "Report Title"; titleInput.setTextColor(getDynamicTextColor()); titleInput.setHintTextColor(Color.GRAY); layout.addView(titleInput)
        val sub = TextView(this); sub.text = "\nSelect Sheets:"; sub.setTextColor(Color.CYAN); layout.addView(sub)

        val checkBoxList = ArrayList<CheckBox>()
        for (i in 1..maxSheetID) {
            val cb = CheckBox(this); cb.text = getSheetName(i); cb.setTextColor(if(isDarkMode) Color.LTGRAY else Color.DKGRAY)
            if(i == currentSheetID) cb.isChecked = true; checkBoxList.add(cb); layout.addView(cb); cb.tag = i
        }

        val titleView = TextView(this); titleView.text = "📄 Generate Report"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER

        AlertDialog.Builder(this).setCustomTitle(titleView).setView(scrollView).setPositiveButton("GENERATE PDF") { _, _ ->
            val selectedIDs = ArrayList<Int>()
            for(cb in checkBoxList) { if(cb.isChecked) selectedIDs.add(cb.tag as Int) }
            if(selectedIDs.isNotEmpty()) generateMultiSheetPdf(titleInput.text.toString().ifEmpty { "EXPENSE REPORT" }, selectedIDs)
            else showFastToast("Select one sheet!")
        }.setNegativeButton("Cancel", null).create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    private fun generateMultiSheetPdf(title: String, sheetIds: ArrayList<Int>) {
        showFastToast("Generating PDF...")
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val paintBg = Paint().apply { color = Color.parseColor("#121212"); style = Paint.Style.FILL }
        val paintText = Paint().apply { color = Color.LTGRAY; textSize = 14f; typeface = Typeface.MONOSPACE }
        val paintHeader = Paint().apply { color = Color.GREEN; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val paintSub = Paint().apply { color = Color.CYAN; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
        val paintBar = Paint().apply { style = Paint.Style.FILL }

        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)

        for (id in sheetIds) {
            val listString = prefs.getString("LIST_$id", "")
            val sheetName = prefs.getString("NAME_$id", "SHEET $id") ?: "SHEET $id"
            val items = if (listString.isNullOrEmpty()) ArrayList<String>() else ArrayList(listString.split("#"))

            // --- OWNER FIX: Use BigDecimal for PDF Summation (Prevent Money Leaks) ---
            var sheetTotal = BigDecimal.ZERO
            val catTotals = HashMap<String, BigDecimal>()

            for(item in items) {
                val priceStr = item.substringAfter("₹").trim()
                val price = priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                sheetTotal = sheetTotal.add(price)

                val parts = item.split(":")
                if(parts.size == 2) {
                    val cat = parts[0].trim().filter{it.isLetter()}
                    val currentCatTotal = catTotals.getOrDefault(cat, BigDecimal.ZERO)
                    catTotals[cat] = currentCatTotal.add(price)
                }
            }

            var pageCount = 0; val itemsPerPage = 25; val totalPages = if(items.isEmpty()) 1 else (items.size + itemsPerPage - 1) / itemsPerPage

            for (i in 0 until items.size step itemsPerPage) {
                val page = pdfDocument.startPage(pageInfo); val canvas = page.canvas
                canvas.drawRect(0f, 0f, 595f, 842f, paintBg)
                canvas.drawText(title.uppercase(), 297f, 60f, paintHeader)
                canvas.drawText("Sheet: $sheetName", 50f, 100f, paintSub)
                canvas.drawText("Page ${pageCount+1}/$totalPages", 500f, 100f, paintText)

                canvas.drawText("ITEM", 50f, 140f, paintSub)
                canvas.drawText("AMOUNT", 500f, 140f, Paint().apply { color = Color.CYAN; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT })

                var y = 170f
                val end = min(i + itemsPerPage, items.size)

                for (j in i until end) {
                    val parts = items[j].split(":")
                    if (parts.size == 2) {
                        canvas.drawText(parts[0].trim(), 50f, y, paintText)
                        val priceVal = parts[1].replace("₹", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val priceClean = formatBigDecimal(priceVal)
                        canvas.drawText(priceClean, 500f, y, Paint().apply { color = Color.WHITE; textSize = 14f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT })
                        y += 20f
                    }
                }

                if (end == items.size) {
                    y += 30f
                    canvas.drawText("TOTAL: ${formatBigDecimal(sheetTotal)}", 500f, y, Paint().apply { color = Color.GREEN; textSize = 20f; textAlign = Paint.Align.RIGHT; isFakeBoldText = true })
                    if (catTotals.isNotEmpty()) {
                        y += 60f
                        canvas.drawText("SPENDING BREAKDOWN:", 50f, y, paintSub)
                        y += 30f

                        // Convert back to Float purely for graphical bar drawing (non-financial)
                        val maxVal = catTotals.values.maxOfOrNull { it.toFloat() } ?: 1f
                        val colors = mapOf("Food" to Color.parseColor("#FFA500"), "Rent" to Color.parseColor("#4CAF50"), "Travel" to Color.parseColor("#FFC107"), "Fuel" to Color.parseColor("#F44336"), "Shopping" to Color.parseColor("#E91E63"), "Health" to Color.parseColor("#00BCD4"))
                        val sorted = catTotals.toList().sortedByDescending { it.second }

                        for ((k, v) in sorted) {
                            if(y > 800f) break
                            val valFloat = v.toFloat()
                            val w = (valFloat / maxVal) * 350f
                            paintBar.color = colors.getOrElse(k) { Color.GRAY }
                            canvas.drawRect(50f, y, 50f + max(w, 10f), y + 15f, paintBar)
                            canvas.drawText("$k: ${v.toInt()}", 50f + max(w, 10f) + 10f, y + 12f, Paint().apply { color = Color.LTGRAY; textSize = 10f })
                            y += 25f
                        }
                    }
                }
                pdfDocument.finishPage(page); pageCount++
            }
            if(items.isEmpty()) { val p = pdfDocument.startPage(pageInfo); p.canvas.drawRect(0f,0f,595f,842f,paintBg); p.canvas.drawText("(Empty)", 297f, 400f, paintText); pdfDocument.finishPage(p) }
        }

        try {
            val file = File(File(cacheDir, "reports").apply { mkdirs() }, "ExpenseReport.pdf")
            val os = FileOutputStream(file); pdfDocument.writeTo(os); pdfDocument.close(); os.close()
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setDataAndType(uri, "application/pdf"); putExtra(Intent.EXTRA_STREAM, uri) }, "Share Report"))
        } catch (e: Exception) { showFastToast("PDF Error"); pdfDocument.close() }
    }

    private fun generateSecureCode(id: Int): Int { val rawData = SECRET_SALT + id; return abs(rawData.hashCode()) % 1000000 }
    private fun getHardwareID(): Int { try { val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "random"; val hash = abs(androidId.hashCode()); return (hash % 9000) + 1000 } catch (e: Exception) { return 9999 } }

    private fun showUpsellDialog() {
        performHaptic(); val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10)
        val idText = TextView(this); idText.text = "Device ID: $deviceRequestID"; idText.setTextColor(Color.YELLOW); idText.textSize = 24f; idText.typeface = Typeface.DEFAULT_BOLD; idText.textAlignment = View.TEXT_ALIGNMENT_CENTER; layout.addView(idText)
        val instructions = TextView(this); instructions.text = "To Activate PRO Mode:\n\n1. Tap button below.\n2. Send ID + Pay Screenshot.\n3. Get Code."; instructions.setTextColor(Color.LTGRAY); instructions.textSize = 15f; layout.addView(instructions); val spacer1 = TextView(this); spacer1.height = 20; layout.addView(spacer1)
        val btnBuy = Button(this); btnBuy.text = "🤖 OPEN TELEGRAM BOT"; btnBuy.setBackgroundColor(Color.parseColor("#0088cc")); btnBuy.setTextColor(Color.WHITE); btnBuy.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINK))) }; layout.addView(btnBuy); val spacer2 = TextView(this); spacer2.height = 30; layout.addView(spacer2)
        val input = EditText(this); input.hint = "Enter Unlock Code"; input.setTextColor(getDynamicTextColor()); input.setHintTextColor(Color.GRAY); layout.addView(input)
        AlertDialog.Builder(this).setTitle("💎 Upgrade to PRO").setView(layout).setPositiveButton("UNLOCK") { _, _ ->
            if ((input.text.toString().toIntOrNull() ?: -1) == generateSecureCode(deviceRequestID)) { isProVersion = true; saveGlobalSettings(); showFastToast("🚀 PRO UNLOCKED!") } else { showFastToast("❌ Wrong Code") }
        }.setNegativeButton("Cancel", null).create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    private fun deleteCurrentSheet() { if (maxSheetID <= 1) { showFastToast("Cannot delete only sheet!"); return }; performHaptic(); AlertDialog.Builder(this).setTitle("Delete Sheet?").setMessage("Are you sure?").setPositiveButton("DELETE") { _, _ -> performDeleteSheetLogic() }.setNegativeButton("Cancel", null).show() }
    private fun performDeleteSheetLogic() { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); val editor = prefs.edit(); for (i in currentSheetID until maxSheetID) { editor.putFloat("TOTAL_$i", prefs.getFloat("TOTAL_${i+1}", 0f)); editor.putString("LIST_$i", prefs.getString("LIST_${i+1}", "") ?: ""); editor.putString("NAME_$i", prefs.getString("NAME_${i+1}", "SHEET ${i+1}") ?: "SHEET ${i+1}") }; editor.remove("TOTAL_$maxSheetID"); editor.remove("LIST_$maxSheetID"); editor.remove("NAME_$maxSheetID"); maxSheetID--; editor.putInt("MAX_SHEETS", maxSheetID); if (currentSheetID > maxSheetID) currentSheetID = maxSheetID; editor.putInt("LAST_OPEN_SHEET", currentSheetID); editor.apply(); loadSheetData(currentSheetID); showFastToast("Sheet Deleted") }
    private fun goToNextSheet() { performHaptic(); if (currentSheetID == maxSheetID) { if (!isProVersion && maxSheetID >= FREE_SHEET_LIMIT) { showUpsellDialog(); return }; saveSheetData(currentSheetID); maxSheetID++; currentSheetID = maxSheetID; clearScreenForNewSheet(); projectName.text = getSheetName(currentSheetID); saveGlobalSettings(); showFastToast("Created New Sheet") } else { saveSheetData(currentSheetID); currentSheetID++; loadSheetData(currentSheetID); showFastToast(getSheetName(currentSheetID)) } }
    private fun goToPrevSheet() { if (currentSheetID > 1) { performHaptic(); saveSheetData(currentSheetID); currentSheetID--; loadSheetData(currentSheetID); showFastToast(getSheetName(currentSheetID)) } else { showFastToast("Top Reached") } }
    private fun clearScreenForNewSheet() { grandTotal = BigDecimal.ZERO; expenseList.clear(); summaryList.clear(); secd.text = "0"; topd.text = "₹0"; projectName.text = getSheetName(currentSheetID); expenseAdapter.notifyDataSetChanged(); summaryAdapter.notifyDataSetChanged() }
    private fun saveSheetData(sheetId: Int) { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit(); prefs.putString("TOTAL_BD_$sheetId", grandTotal.toPlainString()); prefs.putString("LIST_$sheetId", expenseList.joinToString("#")); prefs.apply() }

    private fun loadSheetData(sheetId: Int) {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); val listString = prefs.getString("LIST_$sheetId", "");
        expenseList.clear();
        grandTotal = BigDecimal.ZERO;

        if (!listString.isNullOrEmpty()) {
            val items = listString.split("#"); expenseList.addAll(items);
            for (item in items) {
                val priceStr = item.substringAfter("₹").trim()
                val priceBD = priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                grandTotal = grandTotal.add(priceBD)
            }
        }; topd.text = "₹${formatBigDecimal(grandTotal)}";
        projectName.text = getSheetName(sheetId);
        expenseAdapter.notifyDataSetChanged();
        calculateCategoryTotals();
        secd.text = "0"
    }

    // --- LEFT PANEL: Expense History (AUTO-SIZE ENABLED) ---
    inner class ExpenseAdapter(private val data: ArrayList<String>) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.itemName)
            val priceView: TextView = view.findViewById(R.id.itemPrice)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder { return ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false)) }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            // NO setTextSize here -> Allows XML Auto-Sizing to shrink long text!

            val item = data[position]
            val parts = item.split(":")
            if(parts.size == 2) {
                holder.nameView.text = parts[0].trim()
                val priceVal = parts[1].replace("₹", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                holder.priceView.text = "₹" + formatBigDecimal(priceVal)
            } else {
                holder.nameView.text = item
                holder.priceView.text = ""
            }
            holder.nameView.setTextColor(Color.WHITE)
            holder.priceView.setTextColor(Color.WHITE)
        }
        override fun getItemCount() = data.size
    }

    // --- RIGHT PANEL: Summary (FIXED SIZE - AUTO-SIZE KILLED) ---
    inner class SummaryAdapter(private val data: ArrayList<String>) : RecyclerView.Adapter<SummaryAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.itemName)
            val priceView: TextView = view.findViewById(R.id.itemPrice)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder { return ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false)) }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            // --- THE FIX: TURN OFF AUTO-SIZE ENGINE ---
            // This prevents the "XML Rule" from shrinking the text randomly
            TextViewCompat.setAutoSizeTextTypeWithDefaults(
                holder.nameView,
                TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            )

            // Now safely lock the size to 14sp
            holder.nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

            val item = data[position]
            val parts = item.split(":")
            if(parts.size == 2) {
                holder.nameView.text = parts[0].trim()
                val priceVal = parts[1].replace("₹", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                holder.priceView.text = "₹" + formatBigDecimal(priceVal)
            } else {
                holder.nameView.text = item
                holder.priceView.text = ""
            }

            // Keep Cyan Color
            holder.nameView.setTextColor(Color.CYAN)
            holder.priceView.setTextColor(Color.CYAN)
        }
        override fun getItemCount() = data.size
    }

    private fun addExpenseItem(name: String, emoji: String, priceVal: Double) {
        val priceBD = BigDecimal.valueOf(priceVal)
        val potentialTotal = grandTotal.add(priceBD)

        // --- LIMIT FIX: SAFETY LOCK AT 12 DIGITS (1 TRILLION) ---
        if (potentialTotal >= MAX_TOTAL_LIMIT) {
            showFastToast("Max Total Reached! (12 Digits)")
            return
        }

        grandTotal = potentialTotal
        topd.text = "₹${formatBigDecimal(grandTotal)}";
        expenseList.add("$emoji $name: ₹${formatBigDecimal(priceBD)}");
        expenseAdapter.notifyDataSetChanged();
        fullHistoryAdapter.notifyDataSetChanged();
        calculateCategoryTotals();
        saveSheetData(currentSheetID);
        if (expenseList.isNotEmpty()) { hisd.smoothScrollToPosition(expenseList.size - 1) };
        secd.text = "Saved!"; isNewEntry = true
    }

    private fun deleteItem(pos: Int) {
        performHaptic(); val item = expenseList[pos];
        val priceStr = item.substringAfter("₹").trim()
        val priceBD = priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

        grandTotal = grandTotal.subtract(priceBD); if(grandTotal < BigDecimal.ZERO) grandTotal = BigDecimal.ZERO;

        topd.text = "₹${formatBigDecimal(grandTotal)}";
        expenseList.removeAt(pos);
        expenseAdapter.notifyItemRemoved(pos);
        fullHistoryAdapter.notifyDataSetChanged();
        calculateCategoryTotals();
        saveSheetData(currentSheetID); showFastToast("Deleted")
    }

    private fun deleteCategory(catName: String) {
        performHaptic(); val iterator = expenseList.iterator();
        var deletedAmount = BigDecimal.ZERO;

        while(iterator.hasNext()){
            val item = iterator.next(); if(item.contains(catName)) {
                val priceStr = item.substringAfter("₹").trim()
                val priceBD = priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                deletedAmount = deletedAmount.add(priceBD); iterator.remove()
            }
        }; grandTotal = grandTotal.subtract(deletedAmount);
        if(grandTotal < BigDecimal.ZERO) grandTotal = BigDecimal.ZERO;

        topd.text = "₹${formatBigDecimal(grandTotal)}";
        expenseAdapter.notifyDataSetChanged();
        fullHistoryAdapter.notifyDataSetChanged();
        calculateCategoryTotals();
        saveSheetData(currentSheetID); showFastToast("Deleted All $catName")
    }

    private fun calculateCategoryTotals() {
        val totals = HashMap<String, BigDecimal>(); for (item in expenseList) {
            try {
                val parts = item.split(":"); if (parts.size == 2) {
                    val catName = parts[0].trim(); val priceStr = parts[1].replace("₹", "").trim();
                    val priceBD = priceStr.toBigDecimalOrNull() ?: BigDecimal.ZERO

                    val current = totals.getOrDefault(catName, BigDecimal.ZERO)
                    totals[catName] = current.add(priceBD)
                }
            } catch (e: Exception) { }
        }; summaryList.clear();
        for ((name, total) in totals) {
            summaryList.add("$name: ₹${formatBigDecimal(total)}")
        }; summaryAdapter.notifyDataSetChanged()
    }

    private fun setupCategoryButtons() {
        findViewById<Button>(R.id.catCustom).setOnClickListener { performHaptic();
            val rawPrice = secd.text.toString(); if (rawPrice == "Saved!" || rawPrice == "0" || rawPrice.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawPrice);
            if (value == 0.0) return@setOnClickListener; val input = EditText(this); input.hint = "Item Name"; input.setTextColor(getDynamicTextColor());
            val titleView = TextView(this)
            titleView.text = "Custom Item"
            titleView.textSize = 20f
            titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK)
            titleView.setPadding(20, 20, 20, 20)
            titleView.typeface = Typeface.DEFAULT_BOLD
            titleView.gravity = Gravity.CENTER

            val dialog = AlertDialog.Builder(this).setCustomTitle(titleView).setView(input).setPositiveButton("ADD") { _, _ -> addExpenseItem(if(input.text.toString().isEmpty()) "Custom" else input.text.toString(), "📝", value) }.create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00FF00")) // NEON GREEN
            }
            dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
            dialog.show()
        }

        val cats = mapOf(R.id.catFood to Pair("Food", "🍔"), R.id.catRent to Pair("Rent", "🏠"), R.id.catTravel to Pair("Travel", "🚕"), R.id.catFuel to Pair("Fuel", "⛽"), R.id.catShop to Pair("Shopping", "🛍️"), R.id.catMed to Pair("Health", "💊"), R.id.catGrocery to Pair("Grocery", "🛒"), R.id.catGym to Pair("Gym", "💪"), R.id.catWifi to Pair("Wifi", "🛜"), R.id.catPower to Pair("Electricity", "⚡"), R.id.catCable to Pair("Cable", "📺"), R.id.catWater to Pair("Water", "💧"), R.id.catRefresh to Pair("Drinks", "🍺"), R.id.catSchool to Pair("School", "🏫"), R.id.catTuition to Pair("Tuition", "📚"), R.id.catHelp to Pair("Maid", "👩"))
        for ((id, pair) in cats) { val btn = findViewById<Button>(id);
            val content = btn.text.toString(); val newlineIndex = content.indexOf('\n'); if (newlineIndex > 0) { val span = SpannableString(content);
                span.setSpan(RelativeSizeSpan(2.0f), 0, newlineIndex, 0); btn.text = span }; btn.setOnClickListener { performHaptic(); val rawExpression = secd.text.toString();
                if (rawExpression == "Saved!" || rawExpression.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawExpression); if (value == 0.0) return@setOnClickListener;
                addExpenseItem(pair.first, pair.second, value) } }
    }
    private fun setupCalculatorButtons() {
        val numberButtons = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        for (id in numberButtons) { findViewById<Button>(id).setOnClickListener { performHaptic();
            // --- LIMIT FIX: 9 DIGITS MAX ---
            if (secd.text.length >= MAX_INPUT_DIGITS && secd.text != "Saved!") return@setOnClickListener

            val digit = (it as Button).text.toString();
            if (isNewEntry) { secd.text = ""; isNewEntry = false }; if (secd.text == "Saved!") secd.text = "";
            secd.append(digit) } }
        findViewById<Button>(R.id.btnDot).setOnClickListener { performHaptic(); if (isNewEntry) { secd.text = "0.";
            isNewEntry = false; return@setOnClickListener }; if (!secd.text.toString().split('+', '-', '×', '÷').last().contains(".")) secd.append(".") }
        val opButtons = mapOf(R.id.btnAdd to "+", R.id.btnSub to "-", R.id.btnMul to "×", R.id.btnDiv to "÷");
        for ((id, op) in opButtons) { findViewById<Button>(id).setOnClickListener { performHaptic(); val current = secd.text.toString(); if (isNewEntry) isNewEntry = false;
            if (secd.text == "Saved!") { secd.text = "0"; return@setOnClickListener };
            if (current.isNotEmpty()) { if ("+-×÷".contains(current.last())) { secd.text = current.dropLast(1) + op } else { secd.append(op) } } } }
        findViewById<Button>(R.id.btnDel).setOnClickListener { performHaptic();
            val s = secd.text.toString(); if (s.isNotEmpty() && s != "Saved!") { secd.text = s.dropLast(1);
                if (secd.text.isEmpty()) secd.text = "0" } }
        findViewById<Button>(R.id.btnShare).setOnClickListener { if (!isProVersion) showUpsellDialog() else sharePdfReport() }
    }

    private fun setupACButtonTouch() { val btnAC = findViewById<Button>(R.id.btnAC);
        btnAC.setOnClickListener { performHaptic(); secd.text = "0"; isNewEntry = true }; btnAC.setOnLongClickListener { performHaptic(); grandTotal = BigDecimal.ZERO; expenseList.clear(); summaryList.clear();
            topd.text = "₹0"; secd.text = "0"; expenseAdapter.notifyDataSetChanged(); summaryAdapter.notifyDataSetChanged(); saveSheetData(currentSheetID); showFastToast("Sheet Wiped");
            true } }
    private fun showChart() { performHaptic(); chartContainer.removeAllViews(); val dataMap = HashMap<String, Float>();
        for(item in summaryList) { val parts = item.split(":"); if(parts.size==2) dataMap[parts[0].trim().filter{it.isLetter()}] = parts[1].replace("₹","").trim().toFloatOrNull()?:0f }; if (dataMap.isNotEmpty()) { chartContainer.addView(HorizontalBarChart(this, dataMap));
            chartOverlay.visibility = View.VISIBLE } else { showFastToast("No Data to Chart!") } }
    private fun setupZeroButtonTouch() { val btn0 = findViewById<Button>(R.id.btn0);
        chartRunnable = Runnable { showChart() }; btn0.setOnTouchListener { _, event -> if(event.action == MotionEvent.ACTION_DOWN) { handler.postDelayed(chartRunnable!!, 500);
            true } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) { handler.removeCallbacks(chartRunnable!!);
            if (event.eventTime - event.downTime < 500) { performHaptic(); if (isNewEntry) { secd.text = ""; isNewEntry = false }; secd.append("0") };
            true } else false } }
    private fun setupEqualButtonTouch() { val btnEqual = findViewById<Button>(R.id.btnEqual);
        longPressRunnable = Runnable { isSheetMode = true; performHaptic(); overlayContainer.visibility = View.VISIBLE; overlayContainer.bringToFront(); updateOverlayList(currentSheetID) };
        btnEqual.setOnTouchListener { _, event -> if (gestureDetector.onTouchEvent(event)) { handler.removeCallbacks(longPressRunnable!!); isSheetMode = false; overlayContainer.visibility = View.GONE; return@setOnTouchListener true };
            when (event.action) { MotionEvent.ACTION_DOWN -> { touchStartY = event.rawY; isSheetMode = false; tempSheetID = currentSheetID; handler.postDelayed(longPressRunnable!!, 300);
                true } MotionEvent.ACTION_MOVE -> { if (isSheetMode) { val steps = ((touchStartY - event.rawY).toInt() / 30);
                var potential = currentSheetID + steps; if (potential < 1) potential = 1; if (potential > maxSheetID) potential = maxSheetID;
                if (potential != tempSheetID) { performHaptic(); tempSheetID = potential; updateOverlayList(tempSheetID) } } else { if (abs(touchStartY - event.rawY) > 50) handler.removeCallbacks(longPressRunnable!!) };
                true } MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPressRunnable!!); if (isSheetMode) { overlayContainer.visibility = View.GONE; isSheetMode = false;
                if (tempSheetID != currentSheetID) { saveSheetData(currentSheetID); currentSheetID = tempSheetID; loadSheetData(currentSheetID); showFastToast("Opened ${getSheetName(currentSheetID)}") } } else { performEqualClick() };
                true } else -> false } } }
    private fun updateOverlayList(highlightID: Int) { val lines = ArrayList<String>();
        val start = highlightID - 3; val end = highlightID + 3;
        for (i in start..end) { if (i < 1 || i > maxSheetID) lines.add(" ") else { val name = getSheetName(i);
            if (i == highlightID) lines.add("▶ $name ◀") else lines.add(name) } };
        overlayText.text = lines.joinToString("\n") }
    private fun performEqualClick() { performHaptic(); val result = evaluateExpression(secd.text.toString()); secd.text = formatBigDecimal(BigDecimal.valueOf(result));
        isNewEntry = true }

    // --- OWNER FIX: Use BigDecimal Internally to Stop Leaks ---
    private fun evaluateExpression(expr: String): Double {
        if (expr.isEmpty()) return 0.0
        var cleanExpr = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (cleanExpr.isNotEmpty() && "+-*/".contains(cleanExpr.last())) cleanExpr = cleanExpr.dropLast(1)

        try {
            val numbers = ArrayList<BigDecimal>()
            val ops = ArrayList<Char>()
            var currentNum = ""

            for (char in cleanExpr) {
                if (char.isDigit() || char == '.') {
                    currentNum += char
                } else if ("+-*/".contains(char)) {
                    if (currentNum.isNotEmpty()) {
                        numbers.add(currentNum.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                        currentNum = ""
                    }
                    ops.add(char)
                }
            }
            if (currentNum.isNotEmpty()) numbers.add(currentNum.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            if (numbers.isEmpty()) return 0.0

            if (numbers.size == 1) return numbers[0].toDouble()

            var i = 0
            while (i < ops.size) {
                if (ops[i] == '*' || ops[i] == '/') {
                    val n1 = numbers[i]
                    val n2 = numbers[i+1]
                    var res = BigDecimal.ZERO
                    if (ops[i] == '*') {
                        res = n1.multiply(n2)
                    } else if (n2.compareTo(BigDecimal.ZERO) != 0) {
                        res = n1.divide(n2, 4, RoundingMode.HALF_UP)
                    }
                    // Handle division by zero implicitly (res remains 0 if n2 is 0)

                    numbers[i] = res
                    numbers.removeAt(i+1)
                    ops.removeAt(i)
                } else {
                    i++
                }
            }

            var result = numbers[0]
            for (j in 0 until ops.size) {
                if (ops[j] == '+') result = result.add(numbers[j+1])
                else result = result.subtract(numbers[j+1])
            }
            return result.toDouble()
        } catch (e: Exception) {
            return 0.0
        }
    }

    class HorizontalBarChart(context: Context, val data: HashMap<String, Float>) : View(context) { val colors = mapOf("Food" to Color.parseColor("#FFA500"), "Rent" to Color.parseColor("#4CAF50"), "Travel" to Color.parseColor("#FFC107"), "Fuel" to Color.parseColor("#F44336"), "Shopping" to Color.parseColor("#E91E63"), "Health" to Color.parseColor("#00BCD4"), "Grocery" to Color.parseColor("#9C27B0"), "Gym" to Color.parseColor("#009688"), "Wifi" to Color.parseColor("#2196F3"), "Electricity" to Color.parseColor("#CDDC39"), "Cable" to Color.parseColor("#673AB7"), "Water" to Color.parseColor("#3F51B5"), "Drinks" to Color.parseColor("#795548"), "School" to Color.parseColor("#8BC34A"), "Tuition" to Color.parseColor("#FF9800"), "Maid" to Color.parseColor("#00BFFF"), "Custom" to Color.WHITE);
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL };
        private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 35f; textAlign = Paint.Align.LEFT;
            isFakeBoldText = true }; private val shadowPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 35f;
            textAlign = Paint.Align.LEFT; isFakeBoldText = true; style = Paint.Style.STROKE; strokeWidth = 8f };
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (data.size * 110 + 80)) }; override fun onDraw(canvas: Canvas) { super.onDraw(canvas);
            val maxVal = data.values.maxOrNull() ?: 1f; var y = 40f; val sortedData = data.toList().sortedByDescending { it.second };
            for((key, value) in sortedData) { val barWidth = (value / maxVal) * (width - 80f); val cleanWidth = max(barWidth, 10f);
                paint.color = colors.getOrElse(key) { Color.WHITE }; canvas.drawRect(40f, y, 40f + cleanWidth, y + 80f, paint); val label = "$key: ₹${value.toInt()}";
                val textX = 60f; val textY = y + 52f; canvas.drawText(label, textX, textY, shadowPaint); canvas.drawText(label, textX, textY, textPaint);
                y += 110f } } }

    private fun loadGlobalSettings() {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        isSoundOn = prefs.getBoolean("SOUND", true)
        isVibrationOn = prefs.getBoolean("VIB", true)
        isDarkMode = prefs.getBoolean("DARK_MODE", true)
        isProVersion = prefs.getBoolean("IS_PRO", false)
        maxSheetID = prefs.getInt("MAX_SHEETS", 1)
        currentSheetID = prefs.getInt("LAST_OPEN_SHEET", 1)
    }

    private fun saveGlobalSettings() {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("SOUND", isSoundOn)
            .putBoolean("VIB", isVibrationOn)
            .putBoolean("DARK_MODE", isDarkMode)
            .putBoolean("IS_PRO", isProVersion)
            .putInt("MAX_SHEETS", maxSheetID)
            .putInt("LAST_OPEN_SHEET", currentSheetID)
            .apply()
    }

    private fun applyTheme() {
        // Not used, using applyThemeManual instead
    }

    // --- SOUND FIX: Allow sound even if Vibration is OFF ---
    private fun performHaptic() {
        if (isVibrationOn) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
        if (isSoundOn) toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }

    private fun showFastToast(msg: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    // --- FIX: USE BIGDECIMAL TO PREVENT SCIENTIFIC NOTATION ---
    private fun formatBigDecimal(value: BigDecimal): String {
        return try {
            // Force 2 decimal places and strip trailing zeros if needed
            value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } catch (e: Exception) {
            "0.00"
        }
    }

    // Kept for backward compatibility with old Double methods if needed
    private fun removeZero(value: Double): String {
        return formatBigDecimal(BigDecimal.valueOf(value))
    }

    private fun getSheetName(id: Int): String {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        return prefs.getString("NAME_$id", "SHEET $id") ?: "SHEET $id"
    }

    // --- RENAME SHEET VISIBILITY FIX ---
    private fun showRenameDialog() {
        performHaptic()
        val input = EditText(this)
        input.setText(getSheetName(currentSheetID))
        input.setTextColor(getDynamicTextColor())
        input.setHintTextColor(Color.GRAY)

        val titleView = TextView(this)
        titleView.text = "Rename Sheet"
        titleView.textSize = 20f
        titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK)
        titleView.setPadding(40, 40, 40, 20)
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER

        AlertDialog.Builder(this)
            .setCustomTitle(titleView) // Custom Title
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
                    prefs.edit().putString("NAME_$currentSheetID", newName).apply()
                    projectName.text = newName
                    showFastToast("Renamed to $newName")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
                show()
            }
    }
}