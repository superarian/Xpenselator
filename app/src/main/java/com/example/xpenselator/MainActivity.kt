package com.example.xpenselator

import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import android.text.InputType
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private var isProVersion = false
    private val PRO_PRICE = "₹99"
    private val FREE_SHEET_LIMIT = 3
    private val PAYMENT_LINK = "https://t.me/Xpenselator_Bot"

    private var isNewEntry = true
    private var isDisplayingResult = false
    private var isSheetLocked = false

    private val MAX_INPUT_DIGITS = 9
    private val MAX_TOTAL_LIMIT = BigDecimal("1000000000000")
    private val SPLIT_PREFIX = "↳"
    private val SPLIT_HEADER_TEXT = "--- BILL SPLIT ---"

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

    private lateinit var db: FirebaseFirestore
    private lateinit var repository: ExpenseRepository
    private lateinit var viewModel: ExpenseViewModel

    private lateinit var rootView: RelativeLayout
    private lateinit var mainLayout: LinearLayout
    private lateinit var headerBox: RelativeLayout
    private lateinit var btnSettings: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var topd: TextView
    private lateinit var secd: TextView

    private lateinit var keypadArea: GridLayout
    private lateinit var catLayout: LinearLayout
    private lateinit var keypadContainer: LinearLayout

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var tabHistory: Button
    private lateinit var tabSummary: Button
    private lateinit var hisd: RecyclerView
    private lateinit var summaryRecycler: RecyclerView
    private lateinit var btnSplitGlobal: Button
    private var btnHistorySplit: Button? = null

    private lateinit var projectName: TextView
    private lateinit var overlayContainer: RelativeLayout
    private lateinit var overlayText: TextView
    private lateinit var historyOverlay: RelativeLayout
    private lateinit var fullHistoryList: ListView
    private lateinit var btnCloseHistory: Button
    private lateinit var chartOverlay: RelativeLayout
    private lateinit var chartContainer: LinearLayout
    private lateinit var btnCloseChart: Button

    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var summaryAdapter: SummaryAdapter
    private lateinit var fullHistoryAdapter: ArrayAdapter<String>

    private val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    private lateinit var vibrator: Vibrator

    private val catColors = mapOf(
        "Food" to Color.parseColor("#FF0000"), "Rent" to Color.parseColor("#FF7F00"),
        "Travel" to Color.parseColor("#FFFF00"), "Fuel" to Color.parseColor("#00FF00"),
        "Shopping" to Color.parseColor("#00FFFF"), "Health" to Color.parseColor("#0000FF"),
        "Grocery" to Color.parseColor("#8B00FF"), "Gym" to Color.parseColor("#FF00FF"),
        "Wifi" to Color.parseColor("#C0C0C0"), "Electricity" to Color.parseColor("#FF4500"),
        "Cable" to Color.parseColor("#9400D3"), "Water" to Color.parseColor("#1E90FF"),
        "Drinks" to Color.parseColor("#8B4513"), "School" to Color.parseColor("#32CD32"),
        "Tuition" to Color.parseColor("#FFD700"), "Maid" to Color.parseColor("#00CED1"),
        "Custom" to Color.LTGRAY
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        db = FirebaseFirestore.getInstance()

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN assignedTo TEXT NOT NULL DEFAULT ''")
            }
        }

        val database = androidx.room.Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "xpenselator-db"
        ).addMigrations(MIGRATION_2_3).build()

        repository = ExpenseRepository(this, database.expenseDao(), db)
        viewModel = ViewModelProvider(this, ExpenseViewModelFactory(repository))[ExpenseViewModel::class.java]

        deviceRequestID = repository.getHardwareID()

        rootView = findViewById(R.id.rootView)
        mainLayout = findViewById(R.id.mainLayout)
        headerBox = findViewById(R.id.headerBox)
        btnSettings = findViewById(R.id.btnSettings)
        btnHistory = findViewById(R.id.btnHistory)
        topd = findViewById(R.id.grandTotalText)
        secd = findViewById(R.id.inputDisplay)
        keypadArea = findViewById(R.id.keypadArea)

        val kArea = findViewById<View>(R.id.keypadArea)
        keypadContainer = kArea.parent as LinearLayout
        val cCustom = findViewById<View>(R.id.catCustom)
        catLayout = cCustom.parent as LinearLayout

        viewFlipper = findViewById(R.id.viewFlipper)
        viewFlipper.setInAnimation(this, android.R.anim.fade_in)
        viewFlipper.setOutAnimation(this, android.R.anim.fade_out)

        tabHistory = findViewById(R.id.tabHistory)
        tabSummary = findViewById(R.id.tabSummary)
        btnSplitGlobal = findViewById(R.id.btnSplitGlobal)

        tabHistory.setOnClickListener {
            performHaptic()
            if (viewFlipper.displayedChild != 0) {
                viewFlipper.displayedChild = 0
                updateTabVisuals(0)
            }
        }

        tabSummary.setOnClickListener {
            performHaptic()
            if (viewFlipper.displayedChild != 1) {
                viewFlipper.displayedChild = 1
                updateTabVisuals(1)
            }
        }

        btnSplitGlobal.setOnClickListener {
            performHaptic()
            handleSplitButtonClick()
        }

        hisd = findViewById(R.id.historyList)
        hisd.layoutManager = LinearLayoutManager(this)
        expenseAdapter = ExpenseAdapter(emptyList())
        hisd.adapter = expenseAdapter

        val hisdParent = hisd.parent as ViewGroup
        val hisdIndex = hisdParent.indexOfChild(hisd)
        hisdParent.removeView(hisd)

        val historyWrapper = LinearLayout(this)
        historyWrapper.orientation = LinearLayout.VERTICAL
        historyWrapper.layoutParams = hisd.layoutParams

        val recyclerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        hisd.layoutParams = recyclerParams

        btnHistorySplit = Button(this)
        btnHistorySplit!!.text = "👥 SETUP GROUP"
        btnHistorySplit!!.setTextColor(Color.WHITE)
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = 100f
        gd.setColor(Color.parseColor("#008800"))
        btnHistorySplit!!.background = gd

        val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btnParams.setMargins(0, 20, 0, 0)
        btnHistorySplit!!.layoutParams = btnParams

        btnHistorySplit!!.setOnClickListener {
            performHaptic()
            if (!isSheetLocked) {
                showSetupGroupDialog()
            }
        }

        historyWrapper.addView(hisd)
        historyWrapper.addView(btnHistorySplit)
        hisdParent.addView(historyWrapper, hisdIndex)

        summaryRecycler = findViewById(R.id.summaryList)
        summaryRecycler.layoutManager = LinearLayoutManager(this)
        summaryAdapter = SummaryAdapter(emptyList())
        summaryRecycler.adapter = summaryAdapter

        projectName = findViewById(R.id.projectName)
        overlayContainer = findViewById(R.id.sheetOverlayContainer)
        overlayText = findViewById(R.id.sheetOverlayText)
        historyOverlay = findViewById(R.id.historyOverlay)
        fullHistoryList = findViewById(R.id.fullHistoryList)
        btnCloseHistory = findViewById(R.id.btnCloseHistory)
        chartOverlay = findViewById(R.id.chartOverlay)
        chartContainer = findViewById(R.id.chartContainer)
        btnCloseChart = findViewById(R.id.btnCloseChart)

        fullHistoryAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList()) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                view.setPadding(20, view.paddingTop, view.paddingRight, view.paddingBottom)
                return view
            }
        }
        fullHistoryList.adapter = fullHistoryAdapter

        loadGlobalSettings()
        applyThemeManual()
        switchSheet(currentSheetID)
        updateTabVisuals(0)
        syncProStatusWithServer()

        lifecycleScope.launchWhenStarted {
            viewModel.totalAmount.collect { newTotal ->
                topd.text = newTotal
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.expenses.collect { list ->
                val historyOnly = list.filter { !it.description.startsWith(SPLIT_PREFIX) }
                expenseAdapter.updateData(historyOnly)

                fullHistoryAdapter.clear()
                fullHistoryAdapter.addAll(historyOnly.map {
                    val tag = if(it.assignedTo.isNotEmpty()) " - ${it.assignedTo}" else ""
                    "${it.description}$tag: ₹${it.amount}"
                })
                fullHistoryAdapter.notifyDataSetChanged()

                if (historyOnly.isNotEmpty()) hisd.smoothScrollToPosition(historyOnly.size - 1)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.summaryItems.collect { list ->
                summaryAdapter.updateData(list)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.activeMembers.collect {
                updateSplitButtonState()
            }
        }

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
        btnHistory.setOnClickListener { performHaptic(); historyOverlay.visibility = View.VISIBLE }
        btnCloseHistory.setOnClickListener { performHaptic(); historyOverlay.visibility = View.GONE }
        btnCloseChart.setOnClickListener { performHaptic(); chartOverlay.visibility = View.GONE }

        setupCalculatorButtons()
        setupCategoryButtons()
        setupEqualButtonTouch()
        setupZeroButtonTouch()
        setupACButtonTouch()
    }

    private fun handleSplitButtonClick() {
        if (isSheetLocked) {
            isSheetLocked = false
            repository.setSheetLocked(currentSheetID, false)
            viewModel.removeAllSplits(currentSheetID)
            updateSplitButtonState()
            showFastToast("🔓 SPLIT REMOVED")
        } else {
            showGlobalSplitDialog()
        }
    }

    private fun updateTabVisuals(selected: Int) {
        if (selected == 0) {
            tabHistory.setTextColor(Color.GREEN)
            tabHistory.setBackgroundColor(Color.parseColor("#3300FF00"))
            tabSummary.setTextColor(Color.GRAY)
            tabSummary.setBackgroundColor(Color.TRANSPARENT)
        } else {
            tabHistory.setTextColor(Color.GRAY)
            tabHistory.setBackgroundColor(Color.TRANSPARENT)
            tabSummary.setTextColor(Color.CYAN)
            tabSummary.setBackgroundColor(Color.parseColor("#3300FFFF"))
        }
    }

    private fun updateSplitButtonState() {
        val hasGroup = viewModel.activeMembers.value.isNotEmpty()

        if (hasGroup) {
            btnSplitGlobal.text = "🔒 GROUP ACTIVE"
            btnSplitGlobal.background.setTint(Color.DKGRAY)
            btnSplitGlobal.isEnabled = false
        } else if (isSheetLocked) {
            btnSplitGlobal.text = "🔓 UNLOCK"
            btnSplitGlobal.background.setTint(Color.RED)
            btnSplitGlobal.isEnabled = true
        } else {
            btnSplitGlobal.text = "✂️ SPLIT BILL"
            btnSplitGlobal.background.setTint(Color.parseColor("#008800"))
            btnSplitGlobal.isEnabled = true
        }

        if (isSheetLocked) {
            btnHistorySplit?.text = "🔒 SPLIT ACTIVE"
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.RECTANGLE; gd.cornerRadius = 100f; gd.setColor(Color.DKGRAY)
            btnHistorySplit?.background = gd
            btnHistorySplit?.isEnabled = false
        } else if (hasGroup) {
            btnHistorySplit?.text = "👥 EDIT GROUP"
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.RECTANGLE; gd.cornerRadius = 100f; gd.setColor(Color.parseColor("#008800"))
            btnHistorySplit?.background = gd
            btnHistorySplit?.isEnabled = true
        } else {
            btnHistorySplit?.text = "👥 SETUP GROUP"
            val gd = GradientDrawable()
            gd.shape = GradientDrawable.RECTANGLE; gd.cornerRadius = 100f; gd.setColor(Color.parseColor("#008800"))
            btnHistorySplit?.background = gd
            btnHistorySplit?.isEnabled = true
        }

        secd.hint = ""
    }

    private fun applyThemeManual() {
        if (isDarkMode) {
            window.statusBarColor = Color.BLACK
            rootView.setBackgroundColor(Color.BLACK)
            headerBox.setBackgroundColor(Color.parseColor("#1E1E1E"))
            topd.setTextColor(Color.GREEN)
            btnSettings.setColorFilter(Color.WHITE)
            btnHistory.setColorFilter(Color.LTGRAY)
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
            topd.setTextColor(Color.BLUE)

            secd.setBackgroundColor(Color.WHITE)
            secd.setTextColor(Color.BLACK)
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
        updateTabVisuals(viewFlipper.displayedChild)
    }

    private fun getDynamicTextColor(): Int = if (isDarkMode) Color.WHITE else Color.BLACK

    private fun verifyPaymentWithFirebase(dialog: AlertDialog) {
        showFastToast("Checking database...")
        db.collection("PremiumUsers").document(deviceRequestID.toString())
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.getBoolean("isPro") == true) {
                    isProVersion = true
                    getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("IS_PRO", true)
                        .apply()
                    saveGlobalSettings()
                    showFastToast("🚀 PRO UNLOCKED!")
                    dialog.dismiss()
                } else {
                    showFastToast("❌ Payment not found yet. Try again in a minute.")
                }
            }
            .addOnFailureListener {
                showFastToast("Network error. Check your internet connection.")
            }
    }

    private fun syncProStatusWithServer() {
        if (deviceRequestID == 0 || deviceRequestID == 9999) return
        repository.syncProStatus(deviceRequestID) { isPro ->
            if (isPro != isProVersion) {
                isProVersion = isPro
            }
        }
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
            swSound.setTextColor(Color.BLACK); swVib.setTextColor(Color.BLACK); swTheme.setTextColor(Color.BLACK)
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
        titleView.setPadding(40, 40, 40, 20); titleView.typeface = Typeface.DEFAULT_BOLD; titleView.gravity = Gravity.CENTER
        AlertDialog.Builder(this).setCustomTitle(titleView).setAdapter(adapter) { _, which ->
            when(which) { 0 -> showCurrencyTool(); 1 -> showDistanceTool(); 2 -> showWeightTool() }
        }.setNegativeButton("Close", null).create().apply {
            window?.setBackgroundDrawableResource(if (isDarkMode) android.R.color.background_dark else android.R.color.background_light)
            listView.setBackgroundColor(if(isDarkMode) Color.parseColor("#1E1E1E") else Color.WHITE)
            show()
        }
    }

    private fun showCurrencyTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        val lastRate = prefs.getFloat("LAST_RATE", 85.0f); val textColor = getDynamicTextColor()
        val lbl1 = TextView(this); lbl1.text = "Exchange Rate:"; lbl1.setTextColor(Color.GRAY); layout.addView(lbl1)
        val rateInput = EditText(this); rateInput.setText(lastRate.toString()); rateInput.setTextColor(textColor); layout.addView(rateInput)
        val lbl2 = TextView(this); lbl2.text = "\nAmount (₹):"; lbl2.setTextColor(Color.GRAY); layout.addView(lbl2)
        val amtInput = EditText(this); amtInput.setText(formatBigDecimal(viewModel.rawGrandTotal)); amtInput.setTextColor(textColor); layout.addView(amtInput)
        val resultText = TextView(this); resultText.text = "..."; resultText.textSize = 24f; resultText.gravity = Gravity.CENTER; layout.addView(resultText)
        val titleView = TextView(this); titleView.text = "💱 Currency"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER
        val dialog = AlertDialog.Builder(this).setCustomTitle(titleView).setView(layout).setPositiveButton("CALCULATE") { _, _ -> }.setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }.create()
        dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val rate = rateInput.text.toString().toDoubleOrNull(); val amount = amtInput.text.toString().toDoubleOrNull()
            if (rate != null && amount != null && rate != 0.0) {
                performHaptic(); prefs.edit().putFloat("LAST_RATE", rate.toFloat()).apply()
                val rateBD = BigDecimal.valueOf(rate); val amountBD = BigDecimal.valueOf(amount)
                val res = amountBD.divide(rateBD, 2, RoundingMode.HALF_UP)
                resultText.text = res.toPlainString(); resultText.setTextColor(if(isDarkMode) Color.CYAN else Color.BLUE)
            } else resultText.text = "Invalid"
        }
    }

    private fun showDistanceTool() {
        val scrollView = ScrollView(this); val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30); scrollView.addView(layout)
        val textColor = getDynamicTextColor(); val input = EditText(this); input.hint = "Enter Value"; input.setTextColor(textColor); input.setHintTextColor(Color.LTGRAY); layout.addView(input)
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
        addPair("Km ➡ Mi", 0.621371, "Mi ➡ Km", 1.60934, "Km", "Mi"); addPair("M ➡ Km", 0.001, "Km ➡ M", 1000.0, "m", "Km"); addPair("Ft ➡ M", 0.3048, "M ➡ Ft", 3.28084, "ft", "m")
        layout.addView(resText)
        val titleView = TextView(this); titleView.text = "📏 Distance Lab"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER
        AlertDialog.Builder(this).setCustomTitle(titleView).setView(scrollView).setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }.create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    private fun showWeightTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30); val textColor = getDynamicTextColor()
        val input = EditText(this); input.hint = "Enter Weight"; input.setTextColor(textColor); input.setHintTextColor(Color.LTGRAY); layout.addView(input)
        val resText = TextView(this); resText.text = "---"; resText.textSize = 22f; resText.setTextColor(if(isDarkMode) Color.YELLOW else Color.parseColor("#FF8800")); resText.gravity = Gravity.CENTER; resText.setPadding(0, 30, 0, 0)
        val btnBox = LinearLayout(this); btnBox.orientation = LinearLayout.HORIZONTAL; btnBox.weightSum = 2f; btnBox.setPadding(0, 20, 0, 0)
        val btn1 = Button(this); btn1.text = "Kg ➡ Lbs"; btn1.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val btn2 = Button(this); btn2.text = "Lbs ➡ Kg"; btn2.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        if(isDarkMode) { btn1.setBackgroundColor(Color.DKGRAY); btn2.setBackgroundColor(Color.DKGRAY); btn1.setTextColor(Color.WHITE); btn2.setTextColor(Color.WHITE) }
        else { btn1.setBackgroundColor(Color.LTGRAY); btn2.setBackgroundColor(Color.LTGRAY); btn1.setTextColor(Color.BLACK); btn2.setTextColor(Color.BLACK) }
        btn1.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * 2.20462)} Lbs" } }
        btn2.setOnClickListener { val v = input.text.toString().toDoubleOrNull(); if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v / 2.20462)} Kg" } }
        btnBox.addView(btn1); btnBox.addView(btn2); layout.addView(btnBox); layout.addView(resText)
        val titleView = TextView(this); titleView.text = "⚖️ Weight"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.gravity = Gravity.CENTER
        AlertDialog.Builder(this).setCustomTitle(titleView).setView(layout).setNegativeButton("BACK") { _, _ -> showUtilityDashboard() }.create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    private fun sharePdfReport() {
        if (!isProVersion) { showUpsellDialog(); return }; performHaptic()
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
        showFastToast("Generating Glossy PDF...")

        lifecycleScope.launch(Dispatchers.IO) {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val paintBg = Paint().apply { color = Color.parseColor("#121212"); style = Paint.Style.FILL }
            val paintText = Paint().apply { color = Color.LTGRAY; textSize = 14f; typeface = Typeface.MONOSPACE }
            val paintHeader = Paint().apply { color = Color.GREEN; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            val paintSub = Paint().apply { color = Color.CYAN; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
            val paintBar = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

            for (id in sheetIds) {
                val items = repository.getExpensesForSheet(id)
                val sheetName = getSheetName(id)

                var sheetTotal = BigDecimal.ZERO
                val catTotals = HashMap<String, BigDecimal>()
                val memberTotals = HashMap<String, BigDecimal>()

                for(item in items) {
                    if (item.description.startsWith(SPLIT_PREFIX)) continue
                    val price = item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    sheetTotal = sheetTotal.add(price)

                    val cat = item.description.filter{it.isLetter()}.trim()
                    if (cat.isNotEmpty()) {
                        catTotals[cat] = catTotals.getOrDefault(cat, BigDecimal.ZERO).add(price)
                    }
                    if (item.assignedTo.isNotEmpty()) {
                        memberTotals[item.assignedTo] = memberTotals.getOrDefault(item.assignedTo, BigDecimal.ZERO).add(price)
                    }
                }

                var pageCount = 0
                val itemsPerPage = 25
                val totalPages = if(items.isEmpty()) 1 else (items.size + itemsPerPage - 1) / itemsPerPage

                for (i in 0 until items.size step itemsPerPage) {
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawRect(0f, 0f, 595f, 842f, paintBg)
                    canvas.drawText(title.uppercase(), 297f, 60f, paintHeader)
                    canvas.drawText("Sheet: $sheetName", 50f, 100f, paintSub)
                    canvas.drawText("Page ${pageCount+1}/$totalPages", 500f, 100f, paintText)
                    canvas.drawText("ITEM", 50f, 140f, paintSub)
                    canvas.drawText("AMOUNT", 500f, 140f, Paint().apply { color = Color.CYAN; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT })
                    var y = 170f
                    val end = min(i + itemsPerPage, items.size)
                    for (j in i until end) {
                        val entity = items[j]
                        if (entity.description.startsWith(SPLIT_PREFIX)) {
                            var name = entity.description.replace(SPLIT_PREFIX, "").trim()
                            if (name.contains("]")) name = name.substringAfter("]").trim()
                            canvas.drawText("  ↳ $name", 50f, y, Paint().apply { color = Color.GRAY; textSize = 12f; typeface = Typeface.MONOSPACE })
                            val priceVal = entity.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            canvas.drawText(formatBigDecimal(priceVal), 500f, y, Paint().apply { color = Color.GRAY; textSize = 12f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT })
                        } else {
                            val tag = if(entity.assignedTo.isNotEmpty()) " - ${entity.assignedTo}" else ""
                            canvas.drawText("${entity.description}$tag", 50f, y, paintText)

                            val priceVal = entity.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                            canvas.drawText(formatBigDecimal(priceVal), 500f, y, Paint().apply { color = Color.WHITE; textSize = 14f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT })
                        }
                        y += 20f
                    }

                    if (end == items.size) {
                        y += 30f
                        canvas.drawText("TOTAL: ${formatBigDecimal(sheetTotal)}", 500f, y, Paint().apply { color = Color.GREEN; textSize = 20f; textAlign = Paint.Align.RIGHT; isFakeBoldText = true })

                        if (memberTotals.isNotEmpty()) {
                            y += 50f
                            canvas.drawText("MEMBER TOTALS:", 50f, y, paintSub); y += 30f
                            for ((name, t) in memberTotals) {
                                if(y > 800f) break
                                canvas.drawText("$name: ₹${formatBigDecimal(t)}", 60f, y, paintText)
                                y += 20f
                            }
                        }

                        if (catTotals.isNotEmpty()) {
                            y += 40f
                            canvas.drawText("SPENDING BREAKDOWN:", 50f, y, paintSub); y += 30f
                            val maxVal = catTotals.values.maxOfOrNull { it.toFloat() } ?: 1f
                            val sorted = catTotals.toList().sortedByDescending { it.second }

                            for ((k, v) in sorted) {
                                if(y > 800f) break
                                val barColor = catColors.getOrElse(k) { Color.LTGRAY }
                                val width = (v.toFloat() / maxVal) * 400f
                                val safeWidth = if (width < 10f) 10f else width
                                val rect = RectF(50f, y, 50f + safeWidth, y + 25f)
                                GlossyRender.drawGlossyBar(canvas, rect, barColor, paintBar, isPdf = true)
                                val barTextPaint = Paint().apply {
                                    color = Color.WHITE; textSize = 14f; isFakeBoldText = true; setShadowLayer(3f, 1f, 1f, Color.BLACK)
                                }
                                canvas.drawText("$k: ${v.toInt()}", 60f, y + 18f, barTextPaint)
                                y += 35f
                            }
                        }
                    }
                    pdfDocument.finishPage(page)
                    pageCount++
                }
                if(items.isEmpty()) { val p = pdfDocument.startPage(pageInfo); p.canvas.drawRect(0f,0f,595f,842f,paintBg); p.canvas.drawText("(Empty)", 297f, 400f, paintText); pdfDocument.finishPage(p) }
            }

            withContext(Dispatchers.Main) {
                try {
                    val file = File(File(cacheDir, "reports").apply { mkdirs() }, "ExpenseReport.pdf")
                    val os = FileOutputStream(file); pdfDocument.writeTo(os); pdfDocument.close(); os.close()
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.provider", file)
                    startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setDataAndType(uri, "application/pdf"); putExtra(Intent.EXTRA_STREAM, uri) }, "Share Report"))
                } catch (e: Exception) {
                    showFastToast("PDF Error")
                    pdfDocument.close()
                }
            }
        }
    }

    private fun showUpsellDialog() {
        performHaptic()
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        val titleView = TextView(this)
        titleView.text = "💎 Upgrade to PRO"
        titleView.textSize = 22f
        titleView.setTextColor(Color.CYAN)
        titleView.typeface = Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER
        titleView.setPadding(0, 0, 0, 20)
        layout.addView(titleView)

        val idText = TextView(this)
        idText.text = "Device ID: $deviceRequestID"
        idText.setTextColor(Color.YELLOW)
        idText.textSize = 18f
        idText.typeface = Typeface.DEFAULT_BOLD
        idText.gravity = Gravity.CENTER
        layout.addView(idText)

        val instr = TextView(this)
        instr.text = "\nTo Activate PRO Mode:\n1. Copy UPI ID below & Pay $PRO_PRICE.\n2. Send Screenshot + Device ID to Bot.\n3. Click Verify below once approved."
        instr.setTextColor(Color.LTGRAY)
        instr.textSize = 14f
        layout.addView(instr)

        val upiBox = LinearLayout(this)
        upiBox.orientation = LinearLayout.HORIZONTAL
        upiBox.setPadding(0, 20, 0, 20)
        upiBox.gravity = Gravity.CENTER_VERTICAL

        val upiIdString = "paytmqr2810050501011e876976d7ua@paytm"
        val upiText = TextView(this)
        upiText.text = upiIdString
        upiText.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
        upiText.textSize = 12f
        upiText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        upiBox.addView(upiText)

        val btnCopy = Button(this)
        btnCopy.text = "COPY"
        btnCopy.textSize = 12f
        btnCopy.background.setTint(Color.DKGRAY)
        btnCopy.setTextColor(Color.WHITE)
        btnCopy.setOnClickListener {
            performHaptic()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("UPI ID", upiIdString)
            clipboard.setPrimaryClip(clip)
            showFastToast("✅ UPI ID Copied!")
        }
        upiBox.addView(btnCopy)
        layout.addView(upiBox)

        val btnBuy = Button(this)
        btnBuy.text = "🤖 OPEN TELEGRAM BOT"
        btnBuy.setBackgroundColor(Color.parseColor("#0088cc"))
        btnBuy.setTextColor(Color.WHITE)
        btnBuy.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINK))) }
        layout.addView(btnBuy)

        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .create()

        val btnVerify = Button(this)
        btnVerify.text = "VERIFY PAYMENT"
        btnVerify.setBackgroundColor(Color.parseColor("#00AA00"))
        btnVerify.setTextColor(Color.WHITE)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 20, 0, 0)
        btnVerify.layoutParams = params

        btnVerify.setOnClickListener {
            performHaptic()
            verifyPaymentWithFirebase(dialog)
        }
        layout.addView(btnVerify)

        dialog.apply {
            window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
            show()
        }
    }

    private fun deleteCurrentSheet() { if (maxSheetID <= 1) { showFastToast("Cannot delete only sheet!"); return }; performHaptic(); AlertDialog.Builder(this).setTitle("Delete Sheet?").setMessage("Are you sure?").setPositiveButton("DELETE") { _, _ -> performDeleteSheetLogic() }.setNegativeButton("Cancel", null).show() }

    private fun performDeleteSheetLogic() {
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        viewModel.clearSheet(currentSheetID)

        for (i in currentSheetID until maxSheetID) {
            editor.putString("NAME_$i", prefs.getString("NAME_${i+1}", "SHEET ${i+1}") ?: "SHEET ${i+1}")
        }
        editor.remove("NAME_$maxSheetID")
        maxSheetID--
        editor.putInt("MAX_SHEETS", maxSheetID)

        if (currentSheetID > maxSheetID) currentSheetID = maxSheetID
        editor.putInt("LAST_OPEN_SHEET", currentSheetID)
        editor.apply()

        switchSheet(currentSheetID)
        showFastToast("Sheet Deleted")
    }

    private fun goToNextSheet() {
        performHaptic()
        if (currentSheetID == maxSheetID) {
            if (!isProVersion && maxSheetID >= FREE_SHEET_LIMIT) { showUpsellDialog(); return }
            maxSheetID++
            currentSheetID = maxSheetID
            saveGlobalSettings()
            switchSheet(currentSheetID)
            showFastToast("Created New Sheet")
        } else {
            currentSheetID++
            switchSheet(currentSheetID)
            showFastToast(getSheetName(currentSheetID))
        }
    }

    private fun goToPrevSheet() {
        if (currentSheetID > 1) {
            performHaptic()
            currentSheetID--
            switchSheet(currentSheetID)
            showFastToast(getSheetName(currentSheetID))
        } else {
            showFastToast("Top Reached")
        }
    }

    private fun switchSheet(sheetId: Int) {
        currentSheetID = sheetId
        saveGlobalSettings()
        isSheetLocked = repository.isSheetLocked(sheetId)
        projectName.text = getSheetName(sheetId)
        secd.text = "0"
        isDisplayingResult = false
        updateSplitButtonState()

        viewModel.loadSheet(sheetId)
    }

    inner class ExpenseAdapter(private var data: List<ExpenseEntity>) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.itemName)
            val priceView: TextView = view.findViewById(R.id.itemPrice)
        }

        fun updateData(newData: List<ExpenseEntity>) {
            this.data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entity = data[position]

            val tag = if(entity.assignedTo.isNotEmpty()) " - ${entity.assignedTo}" else ""
            holder.nameView.text = "${entity.description}$tag"

            val pVal = entity.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
            holder.priceView.text = "₹" + formatBigDecimal(pVal)

            holder.nameView.setTextColor(Color.WHITE)
            holder.nameView.textSize = 16f
            holder.priceView.setTextColor(Color.WHITE)
            holder.priceView.textSize = 16f

            holder.itemView.setOnLongClickListener {
                performHaptic()
                confirmDeleteItem(entity)
                true
            }
        }
        override fun getItemCount() = data.size
    }

    private fun confirmDeleteItem(entity: ExpenseEntity) {
        val titleView = TextView(this); titleView.text = "Delete Entry?"; titleView.textSize = 20f; titleView.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.typeface = Typeface.DEFAULT_BOLD; titleView.gravity = Gravity.CENTER
        AlertDialog.Builder(this).setCustomTitle(titleView).setPositiveButton("DELETE") { _, _ ->
            performHaptic()
            viewModel.deleteExpense(entity)
            showFastToast("Deleted")
        }.setNegativeButton("CANCEL", null).create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() }
    }

    inner class SummaryAdapter(private var data: List<String>) : RecyclerView.Adapter<SummaryAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.itemName)
            val priceView: TextView = view.findViewById(R.id.itemPrice)
        }

        fun updateData(newData: List<String>) {
            this.data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = data[position]
            val parts = item.split(":")
            if(parts.size >= 2) {
                holder.nameView.text = parts[0].trim()
                val pVal = parts.last().replace("₹", "").trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                holder.priceView.text = "₹" + formatBigDecimal(pVal)
                val color = catColors.getOrElse(parts[0].trim().filter { it.isLetter() }) { Color.WHITE }
                holder.nameView.setTextColor(color)
            } else {
                holder.nameView.text = item
                holder.priceView.text = ""
                holder.nameView.setTextColor(Color.GRAY)
            }
            holder.priceView.setTextColor(Color.WHITE)
            holder.nameView.textSize = 16f
            holder.priceView.textSize = 16f

            holder.itemView.setOnLongClickListener {
                performHaptic()
                if (item == SPLIT_HEADER_TEXT) {
                    confirmDeleteSplit()
                } else if (!item.startsWith(SPLIT_PREFIX) && item != "--- MEMBER TOTALS ---") {
                    val catName = if(parts.size >= 2) parts[0].trim() else item
                    performDeleteCategory(catName)
                }
                true
            }
        }
        override fun getItemCount() = data.size
    }

    private fun confirmDeleteSplit() {
        AlertDialog.Builder(this).setTitle("Remove Split?").setMessage("Remove the bill split/members? The Total Amount remains.").setPositiveButton("REMOVE") { _, _ ->
            performHaptic()
            isSheetLocked = false
            repository.setSheetLocked(currentSheetID, false)
            viewModel.removeAllSplits(currentSheetID)
            updateSplitButtonState()
            showFastToast("Split Removed")
        }.setNegativeButton("Cancel", null).show()
    }

    private fun performDeleteCategory(category: String) {
        AlertDialog.Builder(this).setTitle("Delete All $category?").setMessage("This will remove every single entry for $category.").setPositiveButton("DELETE ALL") { _, _ ->
            performHaptic()
            viewModel.deleteCategory(currentSheetID, category)
            showFastToast("Deleted all $category")
        }.setNegativeButton("Cancel", null).show()
    }

    // --- FIX FOR CRASH: Added isUpdating lock to prevent infinite loops ---
    private fun showGlobalSplitDialog() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        scrollView.addView(mainLayout)

        val header = TextView(this)
        header.text = "Split Bill (Total: ₹${formatBigDecimal(viewModel.rawGrandTotal)})"
        header.textSize = 20f
        header.setTextColor(Color.CYAN)
        header.gravity = Gravity.CENTER
        mainLayout.addView(header)

        val remainingText = TextView(this)
        remainingText.text = "Remaining: 100%"
        remainingText.setTextColor(Color.GREEN)
        remainingText.gravity = Gravity.CENTER
        remainingText.setPadding(0, 10, 0, 10)
        mainLayout.addView(remainingText)

        val rowsContainer = LinearLayout(this)
        rowsContainer.orientation = LinearLayout.VERTICAL
        mainLayout.addView(rowsContainer)

        val rowList = ArrayList<Pair<EditText, EditText>>()
        var isUpdating = false // SAFETY LOCK FOR TEXTWATCHER

        fun updateRemaining() {
            if (isUpdating) return
            var sumOthers = BigDecimal.ZERO

            for (i in 0 until rowList.size - 1) {
                val v = rowList[i].second.text.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
                sumOthers = sumOthers.add(v)
            }

            val rem = BigDecimal("100").subtract(sumOthers)

            if (rem < BigDecimal.ZERO) remainingText.setTextColor(Color.RED) else remainingText.setTextColor(Color.GREEN)
            remainingText.text = "Remaining: $rem%"

            if (rowList.isNotEmpty() && !rowList.last().second.hasFocus()) {
                val currentText = rowList.last().second.text.toString()
                val newText = rem.toPlainString()

                if (currentText != newText) {
                    isUpdating = true
                    rowList.last().second.setText(newText)
                    isUpdating = false
                }
            }
        }

        fun addRow(nameVal: String = "", percVal: String = "") {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 10, 0, 10)

            val nEd = EditText(this)
            nEd.hint = "Name"
            nEd.setText(nameVal)
            nEd.setTextColor(getDynamicTextColor())
            nEd.setHintTextColor(Color.GRAY)
            nEd.layoutParams = LinearLayout.LayoutParams(0, -2, 1.5f)

            val pEd = EditText(this)
            pEd.hint = "0 %"

            // Set text WITHOUT triggering listeners (safely handled by isUpdating block later if needed)
            pEd.setText(percVal)

            pEd.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            pEd.setTextColor(getDynamicTextColor())
            pEd.setHintTextColor(Color.GRAY)
            pEd.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)

            pEd.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { updateRemaining() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            row.addView(nEd)
            row.addView(pEd)
            rowsContainer.addView(row)
            rowList.add(Pair(nEd, pEd))
        }

        val percString = repository.getGlobalSplitPercentages(currentSheetID)
        val savedPercMap = HashMap<String, String>()
        if (percString.isNotEmpty()) {
            percString.split(",").forEach {
                val parts = it.split("=")
                if(parts.size == 2) savedPercMap[parts[0]] = parts[1]
            }
        }

        val existingSplits = viewModel.expenses.value.filter { it.description.startsWith(SPLIT_PREFIX) && it.description.contains("[BILL SPLIT]") }
        if (existingSplits.isNotEmpty()) {
            isUpdating = true // Pause updates while building existing rows
            for (item in existingSplits) {
                val cleanName = item.description.substringAfter("]").trim()
                val pVal = savedPercMap[cleanName] ?: "0"
                addRow(cleanName, pVal)
            }
            isUpdating = false
            updateRemaining() // Force one final safe update
        } else {
            isUpdating = true
            addRow()
            addRow()
            isUpdating = false
            updateRemaining()
        }

        val btnAddMember = Button(this)
        btnAddMember.text = "+ ADD MEMBER"
        btnAddMember.setBackgroundColor(Color.TRANSPARENT)
        btnAddMember.setTextColor(Color.LTGRAY)
        btnAddMember.setOnClickListener {
            performHaptic()
            addRow()
            updateRemaining()
        }
        mainLayout.addView(btnAddMember)

        val btnEqual = Button(this)
        btnEqual.text = "⚖️ Split Equally"
        btnEqual.setBackgroundColor(Color.DKGRAY)
        btnEqual.setTextColor(Color.WHITE)
        btnEqual.setOnClickListener {
            performHaptic()
            if (rowList.isNotEmpty()) {
                val count = rowList.size
                val basePerc = BigDecimal("100").divide(BigDecimal(count), 2, RoundingMode.FLOOR)
                var remainder = BigDecimal("100")

                isUpdating = true // Lock the text watcher during mass update
                for (i in 0 until count) {
                    if (i == count - 1) {
                        rowList[i].second.setText(remainder.toPlainString())
                    } else {
                        rowList[i].second.setText(basePerc.toPlainString())
                        remainder = remainder.subtract(basePerc)
                    }
                }
                isUpdating = false
                updateRemaining()
            }
        }
        mainLayout.addView(btnEqual)

        val btnSave = Button(this)
        btnSave.text = "SAVE & LOCK"
        btnSave.setBackgroundColor(Color.parseColor("#008800"))
        btnSave.setTextColor(Color.WHITE)
        mainLayout.addView(btnSave)

        val dialog = AlertDialog.Builder(this).setView(scrollView).create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        btnSave.setOnClickListener {
            var sum = BigDecimal.ZERO
            val percMap = HashMap<String, BigDecimal>()
            for (pair in rowList) {
                val n = pair.first.text.toString().trim()
                val pStr = pair.second.text.toString()
                if (n.isNotEmpty() && pStr.isNotEmpty()) {
                    val p = pStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    sum = sum.add(p)
                    percMap[n] = p
                }
            }

            if (sum.compareTo(BigDecimal("100")) == 0 || sum.compareTo(BigDecimal("100.00")) == 0) {
                performHaptic()
                viewModel.saveGlobalSplit(currentSheetID, percMap)
                isSheetLocked = true
                repository.setSheetLocked(currentSheetID, true)
                updateSplitButtonState()
                dialog.dismiss()
                showFastToast("Locked & Saved")
            } else {
                showFastToast("Percentages must sum exactly to 100! (Current: $sum)")
            }
        }
        dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
        dialog.show()
    }

    private fun showSetupGroupDialog() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(50, 40, 50, 10)
        scrollView.addView(mainLayout)

        val header = TextView(this)
        header.text = "Setup Group for Line Items"
        header.textSize = 20f
        header.setTextColor(Color.CYAN)
        header.gravity = Gravity.CENTER
        mainLayout.addView(header)

        val rowsContainer = LinearLayout(this)
        rowsContainer.orientation = LinearLayout.VERTICAL
        mainLayout.addView(rowsContainer)

        val rowList = ArrayList<EditText>()

        fun addRow(nameVal: String = "") {
            val nEd = EditText(this)
            nEd.hint = "Friend's Name"
            nEd.setText(nameVal)
            nEd.setTextColor(getDynamicTextColor())
            nEd.setHintTextColor(Color.GRAY)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 10, 0, 10)
            nEd.layoutParams = params
            rowsContainer.addView(nEd)
            rowList.add(nEd)
        }

        val existingMembers = viewModel.activeMembers.value
        if (existingMembers.isNotEmpty()) {
            existingMembers.forEach { addRow(it) }
        } else {
            addRow()
            addRow()
        }

        val btnAddMember = Button(this)
        btnAddMember.text = "+ ADD MEMBER"
        btnAddMember.setBackgroundColor(Color.TRANSPARENT)
        btnAddMember.setTextColor(Color.LTGRAY)
        btnAddMember.setOnClickListener { performHaptic(); addRow() }
        mainLayout.addView(btnAddMember)

        val btnSave = Button(this)
        btnSave.text = "SAVE GROUP"
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.RECTANGLE
        gd.cornerRadius = 100f
        gd.setColor(Color.parseColor("#008800"))
        btnSave.background = gd
        btnSave.setTextColor(Color.WHITE)
        val btnParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        btnParams.setMargins(0, 20, 0, 0)
        btnSave.layoutParams = btnParams
        mainLayout.addView(btnSave)

        val dialog = AlertDialog.Builder(this).setView(scrollView).create()

        btnSave.setOnClickListener {
            performHaptic()
            val validNames = rowList.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
            viewModel.saveGroupMembers(currentSheetID, validNames)
            dialog.dismiss()
            showFastToast(if (validNames.isEmpty()) "Group Cleared & Sheet Wiped" else "Group Saved")
        }

        dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
        dialog.show()
    }

    private fun addExpenseItem(name: String, emoji: String, priceVal: Double) {
        val priceBD = BigDecimal.valueOf(priceVal)
        val potentialTotal = viewModel.rawGrandTotal.add(priceBD)

        if (priceVal < 0) {
            var currentCatTotal = BigDecimal.ZERO
            for (item in viewModel.expenses.value) {
                if (item.description.startsWith(SPLIT_PREFIX)) continue
                val catName = item.description.filter { it.isLetter() }.trim()
                if (catName == name.filter { it.isLetter() }.trim()) {
                    val p = item.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    currentCatTotal = currentCatTotal.add(p)
                }
            }
            if (currentCatTotal.add(priceBD) < BigDecimal.ZERO) {
                showFastToast("❌ Denied: $name cannot be negative")
                performHaptic()
                return
            }
        }

        if (potentialTotal >= MAX_TOTAL_LIMIT) { showFastToast("Max Total Reached!"); return }

        val activeMembers = viewModel.activeMembers.value
        if (activeMembers.isNotEmpty()) {
            val options = arrayOf("Shared / Everyone") + activeMembers.toTypedArray()

            val titleView = TextView(this)
            titleView.text = "Who paid for this?"
            titleView.textSize = 20f
            titleView.setTextColor(if(isDarkMode) Color.CYAN else Color.BLUE)
            titleView.setPadding(40, 40, 40, 20)
            titleView.typeface = Typeface.DEFAULT_BOLD
            titleView.gravity = Gravity.CENTER

            val customAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(if (isDarkMode) Color.WHITE else Color.BLACK)
                    view.setBackgroundColor(if (isDarkMode) Color.parseColor("#1E1E1E") else Color.WHITE)
                    return view
                }
            }

            AlertDialog.Builder(this)
                .setCustomTitle(titleView)
                .setAdapter(customAdapter) { _, which ->
                    val assignedTo = if (which == 0) "" else options[which]
                    finalizeExpenseSave(name, emoji, priceBD, assignedTo)
                }
                .create().apply {
                    window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light)
                    listView.setBackgroundColor(if (isDarkMode) Color.parseColor("#1E1E1E") else Color.WHITE)
                    show()
                }
        } else {
            finalizeExpenseSave(name, emoji, priceBD, "")
        }
    }

    private fun finalizeExpenseSave(name: String, emoji: String, priceBD: BigDecimal, assignedTo: String) {
        viewModel.addExpense(currentSheetID, "$emoji $name", priceBD, assignedTo)
        secd.text = "Saved!"
        isDisplayingResult = true
        isNewEntry = true
    }

    private fun setupCategoryButtons() {
        findViewById<Button>(R.id.catCustom).setOnClickListener {
            performHaptic(); val rawPrice = secd.text.toString()
            if (isDisplayingResult || rawPrice == "0" || rawPrice.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawPrice); if (value == 0.0) return@setOnClickListener
            val input = EditText(this); input.hint = "Item Name"; input.setTextColor(getDynamicTextColor()); val titleView = TextView(this); titleView.text = "Custom Item"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(20, 20, 20, 20); titleView.typeface = Typeface.DEFAULT_BOLD; titleView.gravity = Gravity.CENTER
            val dialog = AlertDialog.Builder(this).setCustomTitle(titleView).setView(input).setPositiveButton("ADD") { _, _ -> addExpenseItem(if(input.text.toString().isEmpty()) "Custom" else input.text.toString(), "📝", value) }.create(); dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#00FF00")) }; dialog.window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); dialog.show()
        }
        val cats = mapOf(R.id.catFood to Pair("Food", "🍔"), R.id.catRent to Pair("Rent", "🏠"), R.id.catTravel to Pair("Travel", "🚕"), R.id.catFuel to Pair("Fuel", "⛽"), R.id.catShop to Pair("Shopping", "🛍️"), R.id.catMed to Pair("Health", "💊"), R.id.catGrocery to Pair("Grocery", "🛒"), R.id.catGym to Pair("Gym", "💪"), R.id.catWifi to Pair("Wifi", "🛜"), R.id.catPower to Pair("Electricity", "⚡"), R.id.catCable to Pair("Cable", "📺"), R.id.catWater to Pair("Water", "💧"), R.id.catRefresh to Pair("Drinks", "🍺"), R.id.catSchool to Pair("School", "🏫"), R.id.catTuition to Pair("Tuition", "📚"), R.id.catHelp to Pair("Maid", "👩"))
        for ((id, pair) in cats) {
            val btn = findViewById<Button>(id); val content = btn.text.toString(); val nlIndex = content.indexOf('\n'); if (nlIndex > 0) { val span = SpannableString(content); span.setSpan(RelativeSizeSpan(2.0f), 0, nlIndex, 0); btn.text = span }
            btn.setOnClickListener { performHaptic(); val rawExpr = secd.text.toString(); if (isDisplayingResult || rawExpr.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawExpr); if (value == 0.0) return@setOnClickListener; addExpenseItem(pair.first, pair.second, value) }
        }
    }

    private fun setupCalculatorButtons() {
        val numButtons = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        for (id in numButtons) { findViewById<Button>(id).setOnClickListener { performHaptic(); if (secd.text.length >= MAX_INPUT_DIGITS && !isDisplayingResult) return@setOnClickListener; if (isNewEntry || isDisplayingResult) { secd.text = ""; isNewEntry = false; isDisplayingResult = false }; secd.append((it as Button).text.toString()) } }
        findViewById<Button>(R.id.btnDot).setOnClickListener { performHaptic(); if (isNewEntry || isDisplayingResult) { secd.text = "0."; isNewEntry = false; isDisplayingResult = false; return@setOnClickListener }; if (!secd.text.toString().split('+', '-', '×', '÷').last().contains(".")) secd.append(".") }
        val opButtons = mapOf(R.id.btnAdd to "+", R.id.btnSub to "-", R.id.btnMul to "×", R.id.btnDiv to "÷")
        for ((id, op) in opButtons) { findViewById<Button>(id).setOnClickListener { performHaptic(); if (isDisplayingResult) { secd.text = "0"; isDisplayingResult = false; isNewEntry = false }; if (isNewEntry) isNewEntry = false; val current = secd.text.toString(); if (current.isNotEmpty()) { if ("+-×÷".contains(current.last())) secd.text = current.dropLast(1) + op else secd.append(op) } else if (op == "-") secd.append("-") } }
        findViewById<Button>(R.id.btnDel).setOnClickListener { performHaptic(); val s = secd.text.toString(); if (s.isNotEmpty() && !isDisplayingResult) { secd.text = s.dropLast(1); if (secd.text.isEmpty()) secd.text = "0" } }
        findViewById<Button>(R.id.btnShare).setOnClickListener { if (!isProVersion) showUpsellDialog() else sharePdfReport() }
    }

    private fun setupACButtonTouch() {
        val btnAC = findViewById<Button>(R.id.btnAC); btnAC.setOnClickListener { performHaptic(); secd.text = "0"; isNewEntry = true; isDisplayingResult = false }
        btnAC.setOnLongClickListener {
            performHaptic()
            viewModel.clearSheet(currentSheetID)
            isSheetLocked = false
            repository.setSheetLocked(currentSheetID, false)
            updateSplitButtonState()
            secd.text = "0"
            showFastToast("Sheet Wiped")
            isDisplayingResult = false
            true
        }
    }

    private fun showChart() {
        performHaptic()
        chartContainer.removeAllViews()
        chartContainer.orientation = LinearLayout.VERTICAL

        val dataMap = HashMap<String, Float>()
        for(item in viewModel.summaryItems.value) {
            if (item.startsWith(SPLIT_PREFIX) || item == SPLIT_HEADER_TEXT || item == "--- MEMBER TOTALS ---") continue

            val parts = item.split(":")
            if(parts.size == 2) {
                val cat = parts[0].trim().filter{it.isLetter()}
                val amt = parts[1].replace("₹","").trim().toFloatOrNull()?:0f
                if (cat.isNotEmpty()) {
                    dataMap[cat] = amt
                }
            }
        }

        if (dataMap.isNotEmpty()) {
            val maxVal = dataMap.values.maxOrNull() ?: 1f
            val sorted = dataMap.toList().sortedByDescending { it.second }

            var delayIdx = 0L
            for ((k, v) in sorted) {
                val bar = GlossyBarView(this)
                val color = catColors.getOrElse(k) { Color.WHITE }
                val percent = v / maxVal

                bar.setup(k, "₹${v.toInt()}", percent, color)
                chartContainer.addView(bar)
                handler.postDelayed({ bar.animateBar() }, delayIdx * 50)
                delayIdx++
            }
            chartOverlay.visibility = View.VISIBLE
        } else {
            showFastToast("No Data to Chart!")
        }
    }

    private fun setupZeroButtonTouch() {
        val btn0 = findViewById<Button>(R.id.btn0)
        chartRunnable = Runnable { showChart() }
        btn0.setOnTouchListener { _, event ->
            if(event.action == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(chartRunnable!!, 500)
                true
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(chartRunnable!!)
                if (event.eventTime - event.downTime < 500) {
                    performHaptic()
                    if (isNewEntry || isDisplayingResult) { secd.text = ""; isNewEntry = false; isDisplayingResult = false }
                    secd.append("0")
                }
                true
            } else false
        }
    }

    private fun setupEqualButtonTouch() { val btnEqual = findViewById<Button>(R.id.btnEqual); longPressRunnable = Runnable { isSheetMode = true; performHaptic(); overlayContainer.visibility = View.VISIBLE; overlayContainer.bringToFront(); updateOverlayList(currentSheetID) }; btnEqual.setOnTouchListener { _, event -> if (gestureDetector.onTouchEvent(event)) { handler.removeCallbacks(longPressRunnable!!); isSheetMode = false; overlayContainer.visibility = View.GONE; return@setOnTouchListener true }; when (event.action) { MotionEvent.ACTION_DOWN -> { touchStartY = event.rawY; isSheetMode = false; tempSheetID = currentSheetID; handler.postDelayed(longPressRunnable!!, 300); true } MotionEvent.ACTION_MOVE -> { if (isSheetMode) { val steps = ((touchStartY - event.rawY).toInt() / 30); var pot = currentSheetID + steps; if (pot < 1) pot = 1; if (pot > maxSheetID) pot = maxSheetID; if (pot != tempSheetID) { performHaptic(); tempSheetID = pot; updateOverlayList(tempSheetID) } } else if (abs(touchStartY - event.rawY) > 50) handler.removeCallbacks(longPressRunnable!!); true } MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPressRunnable!!); if (isSheetMode) { overlayContainer.visibility = View.GONE; isSheetMode = false; if (tempSheetID != currentSheetID) { switchSheet(tempSheetID); showFastToast("Opened ${getSheetName(currentSheetID)}") } } else performEqualClick(); true } else -> false } } }

    private fun updateOverlayList(highlightID: Int) { val lines = ArrayList<String>(); for (i in (highlightID - 3)..(highlightID + 3)) { if (i < 1 || i > maxSheetID) lines.add(" ") else { val name = getSheetName(i); lines.add(if (i == highlightID) "▶ $name ◀" else name) } }; overlayText.text = lines.joinToString("\n") }

    private fun performEqualClick() {
        if (isDisplayingResult) return; performHaptic(); val result = evaluateExpression(secd.text.toString()); secd.text = formatBigDecimal(BigDecimal.valueOf(result)); isDisplayingResult = false; isNewEntry = true
    }

    private fun evaluateExpression(expr: String): Double {
        if (expr.isEmpty()) return 0.0; var clean = expr.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (clean.startsWith("-")) clean = "0" + clean
        if (clean.isNotEmpty() && "+-*/".contains(clean.last())) clean = clean.dropLast(1)
        try { val nums = ArrayList<BigDecimal>(); val ops = ArrayList<Char>(); var cur = ""
            for (c in clean) { if (c.isDigit() || c == '.') cur += c else if ("+-*/".contains(c)) { if (cur.isNotEmpty()) nums.add(cur.toBigDecimalOrNull() ?: BigDecimal.ZERO); cur = ""; ops.add(c) } }
            if (cur.isNotEmpty()) nums.add(cur.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            if (nums.isEmpty()) return 0.0; if (nums.size == 1) return nums[0].toDouble()
            var i = 0
            while (i < ops.size) { if (ops[i] == '*' || ops[i] == '/') { val n1 = nums[i]; val n2 = nums[i+1]; var res = BigDecimal.ZERO; if (ops[i] == '*') res = n1.multiply(n2) else if (n2.compareTo(BigDecimal.ZERO) != 0) res = n1.divide(n2, 4, RoundingMode.HALF_UP); nums[i] = res; nums.removeAt(i+1); ops.removeAt(i) } else i++ }
            var result = nums[0]; for (j in 0 until ops.size) { if (ops[j] == '+') result = result.add(nums[j+1]) else result = result.subtract(nums[j+1]) }; return result.toDouble()
        } catch (e: Exception) { return 0.0 }
    }

    private fun loadGlobalSettings() {
        isSoundOn = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).getBoolean("SOUND", true)
        isVibrationOn = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).getBoolean("VIB", true)
        isDarkMode = repository.isDarkMode()
        maxSheetID = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).getInt("MAX_SHEETS", 1)
        currentSheetID = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).getInt("LAST_OPEN_SHEET", 1)
        isProVersion = repository.isProVersion()
    }
    private fun saveGlobalSettings() { getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit().putBoolean("SOUND", isSoundOn).putBoolean("VIB", isVibrationOn).putInt("MAX_SHEETS", maxSheetID).putInt("LAST_OPEN_SHEET", currentSheetID).apply(); repository.setDarkMode(isDarkMode); repository.setProVersion(isProVersion) }
    private fun performHaptic() { if (isVibrationOn) { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, -1)) else vibrator.vibrate(50) }; if (isSoundOn) toneGen.startTone(1, 50) }
    private fun showFastToast(msg: String) { currentToast?.cancel(); currentToast = Toast.makeText(this, msg, 0); currentToast?.show() }
    private fun formatBigDecimal(v: BigDecimal): String = try { v.setScale(2, RoundingMode.HALF_UP).toPlainString() } catch (e: Exception) { "0.00" }
    private fun getSheetName(id: Int): String = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).getString("NAME_$id", "SHEET $id") ?: "SHEET $id"
    private fun showRenameDialog() { performHaptic(); val input = EditText(this); input.setText(getSheetName(currentSheetID)); input.setTextColor(getDynamicTextColor()); input.setHintTextColor(Color.GRAY); val titleView = TextView(this); titleView.text = "Rename Sheet"; titleView.textSize = 20f; titleView.setTextColor(if(isDarkMode) Color.WHITE else Color.BLACK); titleView.setPadding(40, 40, 40, 20); titleView.typeface = Typeface.DEFAULT_BOLD; titleView.gravity = Gravity.CENTER; AlertDialog.Builder(this).setCustomTitle(titleView).setView(input).setPositiveButton("SAVE") { _, _ -> val n = input.text.toString().trim(); if (n.isNotEmpty()) { getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit().putString("NAME_$currentSheetID", n).apply(); projectName.text = n; showFastToast("Renamed to $n") } }.setNegativeButton("Cancel", null).create().apply { window?.setBackgroundDrawableResource(if(isDarkMode) android.R.color.background_dark else android.R.color.background_light); show() } }
}