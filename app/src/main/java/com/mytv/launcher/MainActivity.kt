package com.mytv.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

class VH(val box: LinearLayout, val icon: ImageView, val name: TextView) :
    RecyclerView.ViewHolder(box)

class MainActivity : Activity() {

    private lateinit var page: LinearLayout
    private lateinit var greeting: TextView
    private lateinit var dateText: TextView
    private lateinit var favHint: TextView
    private lateinit var favList: RecyclerView
    private lateinit var allList: RecyclerView
    private lateinit var favAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var apps: List<AppItem> = emptyList()
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            updateHeader()
            handler.postDelayed(this, 60_000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun col(hex: String): Int = Color.parseColor(hex)

    private fun shape(fill: String, radius: Int, stroke: Boolean): GradientDrawable =
        GradientDrawable().apply {
            setColor(col(fill))
            cornerRadius = dp(radius).toFloat()
            if (stroke) setStroke(dp(2), col("#FFFFFFFF"))
        }

    private fun focusEffect(v: View, has: Boolean, normal: String, radius: Int) {
        val s = if (has) 1.1f else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(150).start()
        v.background = shape(if (has) "#40FFFFFF" else normal, radius, has)
        v.elevation = if (has) dp(10).toFloat() else 0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setContentView(page)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
        loadApps()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // الواجهة الرئيسية ما تطلع بزر الرجوع
    }

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(28), dp(56), dp(12))
            clipChildren = false
            clipToPadding = false
        }

        // ----- الهيدر: ساعة + تاريخ + تحية + إعدادات -----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 60f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        dateText = TextView(this).apply {
            textSize = 18f
            setTextColor(col("#CCFFFFFF"))
        }
        clockBox.addView(clock)
        clockBox.addView(dateText)

        greeting = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        val settingsBtn = chip("⚙  الإعدادات") {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        header.addView(clockBox)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(greeting)
        header.addView(
            settingsBtn,
            LinearLayout.LayoutParams(wrap, wrap).apply { marginStart = dp(32) }
        )
        page.addView(header)

        // ----- المفضلة -----
        page.addView(sectionLabel("⭐  المفضلة"))
        favHint = TextView(this).apply {
            text = "اضغط مطولاً على OK فوق أي تطبيق لإضافته هنا"
            textSize = 16f
            setTextColor(col("#99FFFFFF"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        page.addView(favHint)

        favAdapter = AppAdapter(true)
        favList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = favAdapter
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        page.addView(favList, LinearLayout.LayoutParams(match, wrap))

        // ----- كل التطبيقات -----
        page.addView(sectionLabel("📱  كل التطبيقات"))
        allAdapter = AppAdapter(false)
        val span = ((resources.displayMetrics.widthPixels - dp(112)) / dp(150)).coerceAtLeast(4)
        allList = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = allAdapter
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(24))
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        page.addView(allList, LinearLayout.LayoutParams(match, 0, 1f))

        updateHeader()
    }

    private fun chip(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isFocusable = true
            isClickable = true
            background = shape("#22FFFFFF", 40, false)
            setOnClickListener { onClick() }
            onFocusChangeListener = View.OnFocusChangeListener { v, has ->
                focusEffect(v, has, "#22FFFFFF", 40)
            }
        }

    private fun sectionLabel(t: String): TextView =
        TextView(this).apply {
            text = t
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(col("#E6FFFFFF"))
            setPadding(dp(12), dp(14), dp(12), 0)
        }

    private fun updateHeader() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello: String
        val colors: IntArray
        when (h) {
            in 5..11 -> {
                hello = "صباح الخير"
                colors = intArrayOf(col("#16435C"), col("#237A8F"), col("#3FA7A0"))
            }
            in 12..16 -> {
                hello = "نهارك سعيد"
                colors = intArrayOf(col("#1E3C72"), col("#2A5298"), col("#3E6FB0"))
            }
            in 17..20 -> {
                hello = "مساء الخير"
                colors = intArrayOf(col("#2B1B54"), col("#5B2A86"), col("#B5527A"))
            }
            else -> {
                hello = "تصبح على خير"
                colors = intArrayOf(col("#0A0F24"), col("#131A3A"), col("#1F2A55"))
            }
        }
        greeting.text = hello
        dateText.text = SimpleDateFormat("EEEE، d MMMM", Locale("ar")).format(Date())
        page.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val seen = HashSet<String>()
            val list = ArrayList<AppItem>()
            val cats = listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)
            for (cat in cats) {
                val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(q, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == packageName || !seen.add(pkg)) continue
                    list.add(AppItem(ri.loadLabel(pm).toString(), pkg, ri.loadIcon(pm)))
                }
            }
            list.sortBy { it.label.lowercase() }
            runOnUiThread {
                if (list.map { it.pkg } != apps.map { it.pkg }) {
                    apps = list
                    allAdapter.setItems(list)
                    refreshFavorites()
                    if (currentFocus == null) {
                        allList.post { allList.getChildAt(0)?.requestFocus() }
                    }
                }
            }
        }.start()
    }

    private fun refreshFavorites() {
        val favs = prefs.getStringSet("favs", null) ?: emptySet<String>()
        val list = apps.filter { it.pkg in favs }
        favAdapter.setItems(list)
        favList.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        favHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleFavorite(app: AppItem, fromFavorites: Boolean) {
        val set = HashSet<String>(prefs.getStringSet("favs", null) ?: emptySet<String>())
        val msg = if (set.remove(app.pkg)) {
            "تمت إزالة ${app.label} من المفضلة"
        } else {
            set.add(app.pkg)
            "تمت إضافة ${app.label} للمفضلة"
        }
        prefs.edit().putStringSet("favs", set).apply()
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        refreshFavorites()
        if (fromFavorites) {
            allList.post { allList.getChildAt(0)?.requestFocus() }
        }
    }

    private fun launchApp(app: AppItem) {
        val i = packageManager.getLaunchIntentForPackage(app.pkg)
            ?: packageManager.getLeanbackLaunchIntentForPackage(app.pkg)
        if (i == null) {
            Toast.makeText(this, "ما كدرت أفتح ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "ما كدرت أفتح ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    inner class AppAdapter(private val compact: Boolean) : RecyclerView.Adapter<VH>() {
        private var items: List<AppItem> = emptyList()

        fun setItems(newItems: List<AppItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = this@MainActivity
            val width = if (compact) dp(150) else ViewGroup.LayoutParams.MATCH_PARENT
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                isLongClickable = true
                setPadding(dp(8), dp(14), dp(8), dp(10))
                layoutParams = RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                background = shape("#00000000", 18, false)
            }
            val icon = ImageView(ctx)
            box.addView(icon, LinearLayout.LayoutParams(dp(72), dp(72)))
            val name = TextView(ctx).apply {
                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            box.addView(
                name,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8) }
            )
            box.onFocusChangeListener = View.OnFocusChangeListener { v, has ->
                focusEffect(v, has, "#00000000", 18)
            }
            return VH(box, icon, name)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = items[position]
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.label
            holder.box.scaleX = 1f
            holder.box.scaleY = 1f
            holder.box.setOnClickListener { launchApp(app) }
            holder.box.setOnLongClickListener {
                toggleFavorite(app, compact)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}