package com.example.xpenselator

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import androidx.core.content.FileProvider
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
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
    private var grandTotal = 0.0
    private val expenseList = ArrayList<String>()
    private val summaryList = ArrayList<String>()

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
    private lateinit var mainLayout: LinearLayout
    private lateinit var headerBox: RelativeLayout
    private lateinit var btnSettings: ImageButton
    private lateinit var btnHistory: ImageButton
    private lateinit var topd: TextView
    private lateinit var secd: TextView

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
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.BLACK
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        mainLayout = findViewById(R.id.mainLayout)
        headerBox = findViewById(R.id.headerBox)
        btnSettings = findViewById(R.id.btnSettings)
        btnHistory = findViewById(R.id.btnHistory)
        topd = findViewById(R.id.grandTotalText)
        secd = findViewById(R.id.inputDisplay)

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

        fullHistoryAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, expenseList)
        fullHistoryList.adapter = fullHistoryAdapter

        loadGlobalSettings()
        loadSheetData(1)

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

    // --- REFINED SETTINGS DIALOG (Fixed for 000 XML) ---
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val swSound = dialogView.findViewById<Switch>(R.id.swSound)
        val swVib = dialogView.findViewById<Switch>(R.id.swVib)
        val swTheme = dialogView.findViewById<Switch>(R.id.swTheme)

        // FIX: We grab the root view directly as the container (No ID needed)
        val container = dialogView as LinearLayout

        // Create the "OPEN TOOLS" button
        val btnTools = Button(this)
        btnTools.text = "🛠️ OPEN TOOLS"
        btnTools.setTextColor(Color.WHITE)
        btnTools.textSize = 16f
        btnTools.background.setTint(Color.parseColor("#444444"))
        btnTools.setPadding(0, 20, 0, 20)

        // Add it before the "DONE" button
        val doneBtn = dialogView.findViewById<Button>(R.id.btnCloseSettings)
        val index = container.indexOfChild(doneBtn)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 30) // Add some space below it
        btnTools.layoutParams = params

        container.addView(btnTools, index)

        swSound.isChecked = isSoundOn; swVib.isChecked = isVibrationOn; swTheme.isChecked = isDarkMode
        swSound.setOnCheckedChangeListener { _, c -> isSoundOn = c; saveGlobalSettings() }
        swVib.setOnCheckedChangeListener { _, c -> isVibrationOn = c; saveGlobalSettings() }
        swTheme.setOnCheckedChangeListener { _, c -> isDarkMode = c; saveGlobalSettings(); applyTheme() }

        btnTools.setOnClickListener {
            performHaptic()
            dialog.dismiss()
            showUtilityDashboard()
        }

        doneBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- UTILITY DASHBOARD ---
    private fun showUtilityDashboard() {
        val options = arrayOf("💱 Currency Converter", "📏 Distance Converter", "⚖️ Weight Converter")
        AlertDialog.Builder(this)
            .setTitle("UTILITY STATION")
            .setItems(options) { _, which ->
                when(which) {
                    0 -> showCurrencyTool()
                    1 -> showDistanceTool()
                    2 -> showWeightTool()
                }
            }
            .setNegativeButton("Close", null)
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.background_dark)
                listView.setBackgroundColor(Color.parseColor("#1E1E1E"))
                show()
            }
    }

    // --- SMART CURRENCY TOOL (WITH MEMORY) ---
    private fun showCurrencyTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)

        // 1. Get Saved Rate
        val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE)
        val lastRate = prefs.getFloat("LAST_RATE", 85.0f)

        val lbl1 = TextView(this); lbl1.text = "Exchange Rate:"; lbl1.setTextColor(Color.LTGRAY); layout.addView(lbl1)

        val rateInput = EditText(this); rateInput.hint = "e.g. 87"; rateInput.setText(lastRate.toString())
        rateInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        rateInput.setTextColor(Color.WHITE); rateInput.setHintTextColor(Color.GRAY); layout.addView(rateInput)

        val lbl2 = TextView(this); lbl2.text = "\nAmount (₹):"; lbl2.setTextColor(Color.LTGRAY); layout.addView(lbl2)
        val amtInput = EditText(this); amtInput.setText(removeZero(grandTotal))
        amtInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        amtInput.setTextColor(Color.WHITE); layout.addView(amtInput)

        // RESULT TEXT (Hidden at first)
        val resultText = TextView(this)
        resultText.text = "..."
        resultText.textSize = 24f
        resultText.typeface = Typeface.DEFAULT_BOLD
        resultText.setTextColor(Color.GREEN)
        resultText.gravity = Gravity.CENTER
        resultText.setPadding(0, 40, 0, 0)
        layout.addView(resultText)

        AlertDialog.Builder(this)
            .setTitle("💱 Currency")
            .setView(layout)
            .setPositiveButton("CALCULATE") { dialog, _ ->
                // We override this later to prevent closing
            }
            .setNegativeButton("Close", null)
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.background_dark)
                show()
                // Override button to keep dialog open and just update text
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val rate = rateInput.text.toString().toFloatOrNull()
                    val amount = amtInput.text.toString().toDoubleOrNull()

                    if (rate != null && rate > 0 && amount != null) {
                        performHaptic()
                        // Save the rate for next time
                        prefs.edit().putFloat("LAST_RATE", rate).apply()

                        val finalVal = amount / rate
                        resultText.text = "${DecimalFormat("#.##").format(finalVal)}"
                        resultText.setTextColor(Color.CYAN)
                    } else {
                        resultText.text = "Invalid Input"
                        resultText.setTextColor(Color.RED)
                    }
                }
            }
    }

    // --- DISTANCE TOOL ---
    private fun showDistanceTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)

        val input = EditText(this); input.hint = "Enter Distance"; input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); layout.addView(input)

        val resText = TextView(this); resText.text = "---"; resText.textSize = 22f; resText.setTextColor(Color.YELLOW); resText.gravity = Gravity.CENTER; resText.setPadding(0, 30, 0, 0)

        val btnBox = LinearLayout(this); btnBox.orientation = LinearLayout.HORIZONTAL; btnBox.weightSum = 2f; btnBox.setPadding(0, 20, 0, 0)

        val btn1 = Button(this); btn1.text = "Km ➡ Mi"; btn1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btn2 = Button(this); btn2.text = "Mi ➡ Km"; btn2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        btn1.setOnClickListener {
            val v = input.text.toString().toDoubleOrNull()
            if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * 0.621371)} Miles" }
        }
        btn2.setOnClickListener {
            val v = input.text.toString().toDoubleOrNull()
            if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * 1.60934)} Km" }
        }

        btnBox.addView(btn1); btnBox.addView(btn2); layout.addView(btnBox); layout.addView(resText)

        AlertDialog.Builder(this).setTitle("📏 Distance").setView(layout).setNegativeButton("Close", null).create().apply { window?.setBackgroundDrawableResource(android.R.color.background_dark); show() }
    }

    // --- WEIGHT TOOL ---
    private fun showWeightTool() {
        val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(60, 50, 60, 30)

        val input = EditText(this); input.hint = "Enter Weight"; input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); layout.addView(input)

        val resText = TextView(this); resText.text = "---"; resText.textSize = 22f; resText.setTextColor(Color.YELLOW); resText.gravity = Gravity.CENTER; resText.setPadding(0, 30, 0, 0)

        val btnBox = LinearLayout(this); btnBox.orientation = LinearLayout.HORIZONTAL; btnBox.weightSum = 2f; btnBox.setPadding(0, 20, 0, 0)

        val btn1 = Button(this); btn1.text = "Kg ➡ Lbs"; btn1.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val btn2 = Button(this); btn2.text = "Lbs ➡ Kg"; btn2.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

        btn1.setOnClickListener {
            val v = input.text.toString().toDoubleOrNull()
            if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v * 2.20462)} Lbs" }
        }
        btn2.setOnClickListener {
            val v = input.text.toString().toDoubleOrNull()
            if(v!=null) { performHaptic(); resText.text = "${DecimalFormat("#.##").format(v / 2.20462)} Kg" }
        }

        btnBox.addView(btn1); btnBox.addView(btn2); layout.addView(btnBox); layout.addView(resText)

        AlertDialog.Builder(this).setTitle("⚖️ Weight").setView(layout).setNegativeButton("Close", null).create().apply { window?.setBackgroundDrawableResource(android.R.color.background_dark); show() }
    }

    // --- STANDARD FUNCTIONS ---
    private fun generateSecureCode(id: Int): Int { val rawData = SECRET_SALT + id; return abs(rawData.hashCode()) % 1000000 }
    private fun getHardwareID(): Int { try { val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "random"; val hash = abs(androidId.hashCode()); return (hash % 9000) + 1000 } catch (e: Exception) { return 9999 } }

    private fun showUpsellDialog() {
        performHaptic(); val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10)
        val idText = TextView(this); idText.text = "Device ID: $deviceRequestID"; idText.setTextColor(Color.YELLOW); idText.textSize = 24f; idText.typeface = Typeface.DEFAULT_BOLD; idText.textAlignment = View.TEXT_ALIGNMENT_CENTER; layout.addView(idText)
        val instructions = TextView(this); instructions.text = "To Activate PRO Mode:\n\n1. Tap button below.\n2. Send ID + Pay Screenshot.\n3. Get Code."; instructions.setTextColor(Color.LTGRAY); instructions.textSize = 15f; layout.addView(instructions); val spacer1 = TextView(this); spacer1.height = 20; layout.addView(spacer1)
        val btnBuy = Button(this); btnBuy.text = "🤖 OPEN TELEGRAM BOT"; btnBuy.setBackgroundColor(Color.parseColor("#0088cc")); btnBuy.setTextColor(Color.WHITE); btnBuy.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PAYMENT_LINK))) }; layout.addView(btnBuy); val spacer2 = TextView(this); spacer2.height = 30; layout.addView(spacer2)
        val input = EditText(this); input.hint = "Enter Unlock Code"; input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY); layout.addView(input)
        AlertDialog.Builder(this).setTitle("💎 Upgrade to PRO").setView(layout).setPositiveButton("UNLOCK") { _, _ ->
            if ((input.text.toString().toIntOrNull() ?: -1) == generateSecureCode(deviceRequestID)) { isProVersion = true; saveGlobalSettings(); showFastToast("🚀 PRO UNLOCKED!") } else { showFastToast("❌ Wrong Code") }
        }.setNegativeButton("Cancel", null).create().apply { window?.setBackgroundDrawableResource(android.R.color.background_dark); show() }
    }

    private fun deleteCurrentSheet() { if (maxSheetID <= 1) { showFastToast("Cannot delete only sheet!"); return }; performHaptic(); AlertDialog.Builder(this).setTitle("Delete Sheet?").setMessage("Are you sure?").setPositiveButton("DELETE") { _, _ -> performDeleteSheetLogic() }.setNegativeButton("Cancel", null).show() }
    private fun performDeleteSheetLogic() { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); val editor = prefs.edit(); for (i in currentSheetID until maxSheetID) { editor.putFloat("TOTAL_$i", prefs.getFloat("TOTAL_${i+1}", 0f)); editor.putString("LIST_$i", prefs.getString("LIST_${i+1}", "") ?: ""); editor.putString("NAME_$i", prefs.getString("NAME_${i+1}", "SHEET ${i+1}") ?: "SHEET ${i+1}") }; editor.remove("TOTAL_$maxSheetID"); editor.remove("LIST_$maxSheetID"); editor.remove("NAME_$maxSheetID"); maxSheetID--; editor.putInt("MAX_SHEETS", maxSheetID); if (currentSheetID > maxSheetID) currentSheetID = maxSheetID; editor.putInt("LAST_OPEN_SHEET", currentSheetID); editor.apply(); loadSheetData(currentSheetID); showFastToast("Sheet Deleted") }
    private fun goToNextSheet() { performHaptic(); if (currentSheetID == maxSheetID) { if (!isProVersion && maxSheetID >= FREE_SHEET_LIMIT) { showUpsellDialog(); return }; saveSheetData(currentSheetID); maxSheetID++; currentSheetID = maxSheetID; clearScreenForNewSheet(); projectName.text = getSheetName(currentSheetID); saveGlobalSettings(); showFastToast("Created New Sheet") } else { saveSheetData(currentSheetID); currentSheetID++; loadSheetData(currentSheetID); showFastToast(getSheetName(currentSheetID)) } }
    private fun goToPrevSheet() { if (currentSheetID > 1) { performHaptic(); saveSheetData(currentSheetID); currentSheetID--; loadSheetData(currentSheetID); showFastToast(getSheetName(currentSheetID)) } else { showFastToast("Top Reached") } }
    private fun clearScreenForNewSheet() { grandTotal = 0.0; expenseList.clear(); summaryList.clear(); secd.text = "0"; topd.text = "₹0"; projectName.text = getSheetName(currentSheetID); expenseAdapter.notifyDataSetChanged(); summaryAdapter.notifyDataSetChanged() }
    private fun saveSheetData(sheetId: Int) { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit(); prefs.putFloat("TOTAL_$sheetId", grandTotal.toFloat()); prefs.putString("LIST_$sheetId", expenseList.joinToString("#")); prefs.apply() }
    private fun loadSheetData(sheetId: Int) { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); val listString = prefs.getString("LIST_$sheetId", ""); expenseList.clear(); grandTotal = 0.0; if (!listString.isNullOrEmpty()) { val items = listString.split("#"); expenseList.addAll(items); for (item in items) { grandTotal += item.substringAfter("₹").trim().toDoubleOrNull() ?: 0.0 } }; topd.text = "₹${removeZero(grandTotal)}"; projectName.text = getSheetName(sheetId); expenseAdapter.notifyDataSetChanged(); calculateCategoryTotals(); secd.text = "0" }

    inner class ExpenseAdapter(private val data: ArrayList<String>) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() { inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val textView: TextView = view.findViewById(android.R.id.text1) }; override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder { return ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false)) }; override fun onBindViewHolder(holder: ViewHolder, position: Int) { holder.textView.text = data[position]; holder.textView.setTextColor(Color.WHITE) }; override fun getItemCount() = data.size }
    inner class SummaryAdapter(private val data: ArrayList<String>) : RecyclerView.Adapter<SummaryAdapter.ViewHolder>() { inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) { val textView: TextView = view.findViewById(android.R.id.text1) }; override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder { return ViewHolder(layoutInflater.inflate(R.layout.item_compact, parent, false)) }; override fun onBindViewHolder(holder: ViewHolder, position: Int) { holder.textView.text = data[position]; holder.textView.setTextColor(Color.CYAN) }; override fun getItemCount() = data.size }

    private fun addExpenseItem(name: String, emoji: String, priceVal: Double) { grandTotal += priceVal; topd.text = "₹${removeZero(grandTotal)}"; expenseList.add("$emoji $name: ₹${removeZero(priceVal)}"); expenseAdapter.notifyDataSetChanged(); fullHistoryAdapter.notifyDataSetChanged(); calculateCategoryTotals(); saveSheetData(currentSheetID); if (expenseList.isNotEmpty()) { hisd.smoothScrollToPosition(expenseList.size - 1) }; secd.text = "Saved!"; isNewEntry = true }
    private fun deleteItem(pos: Int) { performHaptic(); val item = expenseList[pos]; val price = item.substringAfter("₹").toDoubleOrNull() ?: 0.0; grandTotal -= price; if(grandTotal < 0) grandTotal = 0.0; topd.text = "₹${removeZero(grandTotal)}"; expenseList.removeAt(pos); expenseAdapter.notifyItemRemoved(pos); fullHistoryAdapter.notifyDataSetChanged(); calculateCategoryTotals(); saveSheetData(currentSheetID); showFastToast("Deleted") }
    private fun deleteCategory(catName: String) { performHaptic(); val iterator = expenseList.iterator(); var deletedAmount = 0.0; while(iterator.hasNext()){ val item = iterator.next(); if(item.contains(catName)) { deletedAmount += item.substringAfter("₹").toDoubleOrNull() ?: 0.0; iterator.remove() } }; grandTotal -= deletedAmount; if(grandTotal < 0) grandTotal = 0.0; topd.text = "₹${removeZero(grandTotal)}"; expenseAdapter.notifyDataSetChanged(); fullHistoryAdapter.notifyDataSetChanged(); calculateCategoryTotals(); saveSheetData(currentSheetID); showFastToast("Deleted All $catName") }
    private fun calculateCategoryTotals() { val totals = HashMap<String, Double>(); for (item in expenseList) { try { val parts = item.split(":"); if (parts.size == 2) { val catName = parts[0].trim(); val price = parts[1].replace("₹", "").trim().toDoubleOrNull() ?: 0.0; totals[catName] = totals.getOrDefault(catName, 0.0) + price } } catch (e: Exception) { } }; summaryList.clear(); for ((name, total) in totals) { summaryList.add("$name: ₹${removeZero(total)}") }; summaryAdapter.notifyDataSetChanged() }

    private fun setupCategoryButtons() {
        findViewById<Button>(R.id.catCustom).setOnClickListener { performHaptic(); val rawPrice = secd.text.toString(); if (rawPrice == "Saved!" || rawPrice == "0" || rawPrice.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawPrice); if (value == 0.0) return@setOnClickListener; val input = EditText(this); input.hint = "Item Name"; input.setTextColor(Color.WHITE); AlertDialog.Builder(this).setTitle("Custom Item").setView(input).setPositiveButton("ADD") { _, _ -> addExpenseItem(if(input.text.toString().isEmpty()) "Custom" else input.text.toString(), "📝", value) }.create().apply { window?.setBackgroundDrawableResource(android.R.color.background_dark); show() } }
        val cats = mapOf(R.id.catFood to Pair("Food", "🍔"), R.id.catRent to Pair("Rent", "🏠"), R.id.catTravel to Pair("Travel", "🚕"), R.id.catFuel to Pair("Fuel", "⛽"), R.id.catShop to Pair("Shopping", "🛍️"), R.id.catMed to Pair("Health", "💊"), R.id.catGrocery to Pair("Grocery", "🛒"), R.id.catGym to Pair("Gym", "💪"), R.id.catWifi to Pair("Wifi", "🛜"), R.id.catPower to Pair("Electricity", "⚡"), R.id.catCable to Pair("Cable", "📺"), R.id.catWater to Pair("Water", "💧"), R.id.catRefresh to Pair("Drinks", "🍺"), R.id.catSchool to Pair("School", "🏫"), R.id.catTuition to Pair("Tuition", "📚"), R.id.catHelp to Pair("Maid", "👩"))
        for ((id, pair) in cats) { val btn = findViewById<Button>(id); val content = btn.text.toString(); val newlineIndex = content.indexOf('\n'); if (newlineIndex > 0) { val span = SpannableString(content); span.setSpan(RelativeSizeSpan(2.0f), 0, newlineIndex, 0); btn.text = span }; btn.setOnClickListener { performHaptic(); val rawExpression = secd.text.toString(); if (rawExpression == "Saved!" || rawExpression.isEmpty()) return@setOnClickListener; val value = evaluateExpression(rawExpression); if (value == 0.0) return@setOnClickListener; addExpenseItem(pair.first, pair.second, value) } }
    }
    private fun setupCalculatorButtons() {
        val numberButtons = listOf(R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9)
        for (id in numberButtons) { findViewById<Button>(id).setOnClickListener { performHaptic(); val digit = (it as Button).text.toString(); if (isNewEntry) { secd.text = ""; isNewEntry = false }; if (secd.text == "Saved!") secd.text = ""; secd.append(digit) } }
        findViewById<Button>(R.id.btnDot).setOnClickListener { performHaptic(); if (isNewEntry) { secd.text = "0."; isNewEntry = false; return@setOnClickListener }; if (!secd.text.toString().split('+', '-', '×', '÷').last().contains(".")) secd.append(".") }
        val opButtons = mapOf(R.id.btnAdd to "+", R.id.btnSub to "-", R.id.btnMul to "×", R.id.btnDiv to "÷"); for ((id, op) in opButtons) { findViewById<Button>(id).setOnClickListener { performHaptic(); val current = secd.text.toString(); if (isNewEntry) isNewEntry = false; if (secd.text == "Saved!") { secd.text = "0"; return@setOnClickListener }; if (current.isNotEmpty()) { if ("+-×÷".contains(current.last())) { secd.text = current.dropLast(1) + op } else { secd.append(op) } } } }
        findViewById<Button>(R.id.btnDel).setOnClickListener { performHaptic(); val s = secd.text.toString(); if (s.isNotEmpty() && s != "Saved!") { secd.text = s.dropLast(1); if (secd.text.isEmpty()) secd.text = "0" } }
        findViewById<Button>(R.id.btnShare).setOnClickListener { if (!isProVersion) showUpsellDialog() else sharePdfReport() }
    }

    private fun sharePdfReport() { if (expenseList.isEmpty()) { showFastToast("Nothing to Print!"); return }; performHaptic(); val input = EditText(this); input.setText("BYTESKULL FINANCE"); input.setTextColor(Color.WHITE); input.selectAll(); AlertDialog.Builder(this).setTitle("Report Title").setView(input).setPositiveButton("GENERATE") { _, _ -> generateAndSharePdf(input.text.toString().ifEmpty { "EXPENSE REPORT" }) }.setNegativeButton("Cancel", null).create().apply { window?.setBackgroundDrawableResource(android.R.color.background_dark); show() } }
    private fun generateAndSharePdf(title: String) {
        showFastToast("Generating PDF..."); val pdfDocument = PdfDocument(); val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val paintBg = Paint().apply { color = Color.parseColor("#121212"); style = Paint.Style.FILL }; val paintText = Paint().apply { color = Color.LTGRAY; textSize = 14f; typeface = Typeface.MONOSPACE }; val paintHeader = Paint().apply { color = Color.GREEN; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        var pageCount = 0; val itemsPerPage = 25
        for (i in 0 until expenseList.size step itemsPerPage) { val page = pdfDocument.startPage(pageInfo); val canvas = page.canvas; canvas.drawRect(0f, 0f, 595f, 842f, paintBg); canvas.drawText(title.uppercase(), 297f, 60f, paintHeader); var y = 100f; val end = min(i + itemsPerPage, expenseList.size); for (j in i until end) { val parts = expenseList[j].split(":"); if (parts.size == 2) { canvas.drawText(parts[0].trim(), 50f, y, paintText); canvas.drawText(parts[1].trim(), 500f, y, paintText); y += 20f } }; pdfDocument.finishPage(page); pageCount++ }

        val chartPage = pdfDocument.startPage(pageInfo); val canvas = chartPage.canvas; canvas.drawRect(0f, 0f, 595f, 842f, paintBg); canvas.drawText("CATEGORY BREAKDOWN", 297f, 60f, paintHeader); var y = 120f; val barPaint = Paint().apply { color = Color.CYAN }; val textP = Paint().apply { color = Color.WHITE; textSize = 14f }
        val dataMap = HashMap<String, Float>(); for(item in summaryList) { val parts = item.split(":"); if(parts.size==2) dataMap[parts[0].trim().filter{it.isLetter()}] = parts[1].replace("₹","").trim().toFloatOrNull()?:0f }
        val maxVal = dataMap.values.maxOrNull() ?: 1f
        for((k,v) in dataMap) { val width = (v/maxVal)*400f; canvas.drawRect(50f, y, 50f+width, y+30f, barPaint); canvas.drawText("$k: ₹${v.toInt()}", 60f, y+20f, textP); y += 50f }
        canvas.drawText("TOTAL: ₹${removeZero(grandTotal)}", 500f, y+50f, Paint().apply { color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.RIGHT; isFakeBoldText = true })
        pdfDocument.finishPage(chartPage)

        try { val file = File(File(cacheDir, "reports").apply { mkdirs() }, "ExpenseReport.pdf"); val os = FileOutputStream(file); pdfDocument.writeTo(os); pdfDocument.close(); os.close(); val uri = FileProvider.getUriForFile(this, "$packageName.provider", file); startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); setDataAndType(uri, "application/pdf"); putExtra(Intent.EXTRA_STREAM, uri) }, "Share Report")) } catch (e: Exception) { showFastToast("PDF Error"); pdfDocument.close() }
    }

    private fun saveGlobalSettings() { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit(); prefs.putInt("MAX_SHEETS", maxSheetID); prefs.putInt("LAST_OPEN_SHEET", currentSheetID); prefs.putBoolean("VIB_ON", isVibrationOn); prefs.putBoolean("SND_ON", isSoundOn); prefs.putBoolean("DARK_MODE", isDarkMode); prefs.putBoolean("IS_PRO", isProVersion); prefs.apply() }
    private fun loadGlobalSettings() { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); maxSheetID = prefs.getInt("MAX_SHEETS", 1); currentSheetID = prefs.getInt("LAST_OPEN_SHEET", 1); isVibrationOn = prefs.getBoolean("VIB_ON", true); isSoundOn = prefs.getBoolean("SND_ON", true); isDarkMode = prefs.getBoolean("DARK_MODE", true); isProVersion = prefs.getBoolean("IS_PRO", false); deviceRequestID = getHardwareID(); applyTheme() }
    private fun applyTheme() { if (isDarkMode) { mainLayout.setBackgroundColor(Color.parseColor("#121212")); headerBox.setBackgroundColor(Color.parseColor("#1E1E1E")); btnSettings.setColorFilter(Color.WHITE); btnHistory.setColorFilter(Color.WHITE); topd.setTextColor(Color.parseColor("#00FF00")); secd.setBackgroundColor(Color.parseColor("#2C2C2C")); secd.setTextColor(Color.parseColor("#00FFFF")) } else { mainLayout.setBackgroundColor(Color.parseColor("#FFFFFF")); headerBox.setBackgroundColor(Color.parseColor("#DDDDDD")); btnSettings.setColorFilter(Color.BLACK); btnHistory.setColorFilter(Color.BLACK); topd.setTextColor(Color.parseColor("#000000")); secd.setBackgroundColor(Color.parseColor("#EEEEEE")); secd.setTextColor(Color.parseColor("#333333")) } }
    private fun showFastToast(message: String) { currentToast?.cancel(); currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT); currentToast?.show() }
    private fun showRenameDialog() { val input = EditText(this); input.setText(getSheetName(currentSheetID)); input.setSelection(input.text.length); AlertDialog.Builder(this).setTitle("Rename Workspace").setView(input).setPositiveButton("Save") { _, _ -> val newName = input.text.toString(); if (newName.isNotEmpty()) { saveSheetName(currentSheetID, newName); projectName.text = newName.uppercase(); showFastToast("Renamed!") } }.setNegativeButton("Cancel", null).show() }
    private fun saveSheetName(id: Int, name: String) { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE).edit(); prefs.putString("NAME_$id", name); prefs.apply() }
    private fun getSheetName(id: Int): String { val prefs = getSharedPreferences("XpenselatorData", Context.MODE_PRIVATE); return prefs.getString("NAME_$id", "SHEET $id") ?: "SHEET $id" }
    private fun performHaptic() { if (isSoundOn) try { toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150) } catch (e: Exception) {}; if (isVibrationOn) if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) else vibrator.vibrate(50) }
    private fun removeZero(v: Double) = DecimalFormat("#.##").format(v)
    private fun setupACButtonTouch() { val btnAC = findViewById<Button>(R.id.btnAC); btnAC.setOnClickListener { performHaptic(); secd.text = "0"; isNewEntry = true }; btnAC.setOnLongClickListener { performHaptic(); grandTotal = 0.0; expenseList.clear(); summaryList.clear(); topd.text = "₹0"; secd.text = "0"; expenseAdapter.notifyDataSetChanged(); summaryAdapter.notifyDataSetChanged(); saveSheetData(currentSheetID); showFastToast("Sheet Wiped"); true } }
    private fun showChart() { performHaptic(); chartContainer.removeAllViews(); val dataMap = HashMap<String, Float>(); for(item in summaryList) { val parts = item.split(":"); if(parts.size==2) dataMap[parts[0].trim().filter{it.isLetter()}] = parts[1].replace("₹","").trim().toFloatOrNull()?:0f }; if (dataMap.isNotEmpty()) { chartContainer.addView(HorizontalBarChart(this, dataMap)); chartOverlay.visibility = View.VISIBLE } else { showFastToast("No Data to Chart!") } }
    private fun setupZeroButtonTouch() { val btn0 = findViewById<Button>(R.id.btn0); chartRunnable = Runnable { showChart() }; btn0.setOnTouchListener { _, event -> if(event.action == MotionEvent.ACTION_DOWN) { handler.postDelayed(chartRunnable!!, 500); true } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) { handler.removeCallbacks(chartRunnable!!); if (event.eventTime - event.downTime < 500) { performHaptic(); if (isNewEntry) { secd.text = ""; isNewEntry = false }; secd.append("0") }; true } else false } }
    private fun setupEqualButtonTouch() { val btnEqual = findViewById<Button>(R.id.btnEqual); longPressRunnable = Runnable { isSheetMode = true; performHaptic(); overlayContainer.visibility = View.VISIBLE; overlayContainer.bringToFront(); updateOverlayList(currentSheetID) }; btnEqual.setOnTouchListener { _, event -> if (gestureDetector.onTouchEvent(event)) { handler.removeCallbacks(longPressRunnable!!); isSheetMode = false; overlayContainer.visibility = View.GONE; return@setOnTouchListener true }; when (event.action) { MotionEvent.ACTION_DOWN -> { touchStartY = event.rawY; isSheetMode = false; tempSheetID = currentSheetID; handler.postDelayed(longPressRunnable!!, 300); true } MotionEvent.ACTION_MOVE -> { if (isSheetMode) { val steps = ((touchStartY - event.rawY).toInt() / 30); var potential = currentSheetID + steps; if (potential < 1) potential = 1; if (potential > maxSheetID) potential = maxSheetID; if (potential != tempSheetID) { performHaptic(); tempSheetID = potential; updateOverlayList(tempSheetID) } } else { if (abs(touchStartY - event.rawY) > 50) handler.removeCallbacks(longPressRunnable!!) }; true } MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(longPressRunnable!!); if (isSheetMode) { overlayContainer.visibility = View.GONE; isSheetMode = false; if (tempSheetID != currentSheetID) { saveSheetData(currentSheetID); currentSheetID = tempSheetID; loadSheetData(currentSheetID); showFastToast("Opened ${getSheetName(currentSheetID)}") } } else { performEqualClick() }; true } else -> false } } }
    private fun updateOverlayList(highlightID: Int) { val lines = ArrayList<String>(); val start = highlightID - 3; val end = highlightID + 3; for (i in start..end) { if (i < 1 || i > maxSheetID) lines.add(" ") else { val name = getSheetName(i); if (i == highlightID) lines.add("▶ $name ◀") else lines.add(name) } }; overlayText.text = lines.joinToString("\n") }
    private fun performEqualClick() { performHaptic(); val result = evaluateExpression(secd.text.toString()); secd.text = removeZero(result); isNewEntry = true }
    private fun evaluateExpression(expr: String): Double { if (expr.isEmpty()) return 0.0; var cleanExpr = expr.replace(" ", "").replace("×", "*").replace("÷", "/"); if (cleanExpr.isNotEmpty() && "+-*/".contains(cleanExpr.last())) cleanExpr = cleanExpr.dropLast(1); try { val numbers = ArrayList<Double>(); val ops = ArrayList<Char>(); var currentNum = ""; for (char in cleanExpr) { if (char.isDigit() || char == '.') currentNum += char else if ("+-*/".contains(char)) { if (currentNum.isNotEmpty()) { numbers.add(currentNum.toDoubleOrNull() ?: 0.0); currentNum = "" }; ops.add(char) } }; if (currentNum.isNotEmpty()) numbers.add(currentNum.toDoubleOrNull() ?: 0.0); if (numbers.isEmpty()) return 0.0; if (numbers.size == 1) return numbers[0]; var i = 0; while (i < ops.size) { if (ops[i] == '*' || ops[i] == '/') { val n1 = numbers[i]; val n2 = numbers[i+1]; var res = 0.0; if (ops[i] == '*') res = n1 * n2 else if (n2 != 0.0) res = n1 / n2; numbers[i] = res; numbers.removeAt(i+1); ops.removeAt(i) } else i++ }; var result = numbers[0]; for (j in 0 until ops.size) { if (ops[j] == '+') result += numbers[j+1] else result -= numbers[j+1] }; return result } catch (e: Exception) { return 0.0 } }
    class HorizontalBarChart(context: Context, val data: HashMap<String, Float>) : View(context) { val colors = mapOf("Food" to Color.parseColor("#FFA500"), "Rent" to Color.parseColor("#4CAF50"), "Travel" to Color.parseColor("#FFC107"), "Fuel" to Color.parseColor("#F44336"), "Shopping" to Color.parseColor("#E91E63"), "Health" to Color.parseColor("#00BCD4"), "Grocery" to Color.parseColor("#9C27B0"), "Gym" to Color.parseColor("#009688"), "Wifi" to Color.parseColor("#2196F3"), "Electricity" to Color.parseColor("#CDDC39"), "Cable" to Color.parseColor("#673AB7"), "Water" to Color.parseColor("#3F51B5"), "Drinks" to Color.parseColor("#795548"), "School" to Color.parseColor("#8BC34A"), "Tuition" to Color.parseColor("#FF9800"), "Maid" to Color.parseColor("#00BFFF"), "Custom" to Color.WHITE); private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }; private val textPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 35f; textAlign = Paint.Align.LEFT; isFakeBoldText = true }; private val shadowPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 35f; textAlign = Paint.Align.LEFT; isFakeBoldText = true; style = Paint.Style.STROKE; strokeWidth = 8f }; override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (data.size * 110 + 80)) }; override fun onDraw(canvas: Canvas) { super.onDraw(canvas); val maxVal = data.values.maxOrNull() ?: 1f; var y = 40f; val sortedData = data.toList().sortedByDescending { it.second }; for((key, value) in sortedData) { val barWidth = (value / maxVal) * (width - 80f); val cleanWidth = max(barWidth, 10f); paint.color = colors.getOrElse(key) { Color.WHITE }; canvas.drawRect(40f, y, 40f + cleanWidth, y + 80f, paint); val label = "$key: ₹${value.toInt()}"; val textX = 60f; val textY = y + 52f; canvas.drawText(label, textX, textY, shadowPaint); canvas.drawText(label, textX, textY, textPaint); y += 110f } } }
}