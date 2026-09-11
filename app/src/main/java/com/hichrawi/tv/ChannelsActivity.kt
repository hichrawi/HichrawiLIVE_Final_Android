package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChannelsActivity : AppCompatActivity() {
    private enum class Page { HOME, LIVE, SOCIAL, SETTINGS }

    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var title: TextView
    private lateinit var backButton: Button
    private var page = Page.HOME
    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var appSettings: Map<String, String> = emptyMap()
    private var channelInfoName: TextView? = null
    private var channelInfoLogo: ImageView? = null

    private val gold = 0xFFFFC400.toInt()
    private val bg = 0xFF090512.toInt()
    private val purple2 = 0xFF7B2CBF.toInt()
    private val panel = 0xFF151020.toInt()
    private val panel2 = 0xFF211733.toInt()
    private val text = Color.WHITE
    private val muted = 0xFFC4B8D6.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        deviceId = prefs.getLong("firebase_device_id", prefs.getLong("server_device_id", 0L))
        buildShell()
        showHome()
        loadData()
    }

    private fun tv(value: String, size: Float, color: Int = text, bold: Boolean = false, gravity: Int = Gravity.CENTER) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun bgDrawable(c1: Int, c2: Int, radius: Float = 18f, stroke: Int? = null) =
        android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(c1, c2)
        ).apply {
            cornerRadius = radius
            stroke?.let { setStroke(3, it) }
        }

    private fun solid(color: Int, radius: Float = 18f, stroke: Int? = null) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(3, it) }
        }

    private fun buildShell() {
        root = FrameLayout(this).apply {
            setBackgroundColor(bg)
            setPadding(34, 26, 34, 26)
        }

        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        backButton = Button(this).apply {
            text = "‹  رجوع"
            textSize = 17f
            isAllCaps = false
            setTextColor(this@ChannelsActivity.text)
            background = solid(panel2, 12f)
            stateListAnimator = null
            isFocusable = true
            visibility = View.GONE
            setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) solid(gold, 12f) else solid(panel2, 12f)
                (v as Button).setTextColor(if (hasFocus) 0xFF160B20.toInt() else this@ChannelsActivity.text)
            }
            setOnClickListener { showHome() }
        }
        header.addView(backButton, LinearLayout.LayoutParams(125, 56))

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.hichrawi_live_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        header.addView(logo, LinearLayout.LayoutParams(86, 56).apply { setMargins(14, 0, 12, 0) })

        val headText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = tv("HICHRAWI LIVE", 25f, text, true, Gravity.START)
        headText.addView(title, LinearLayout.LayoutParams(-1, 34))
        headText.addView(tv("LIVE • SOCIAL • SETTINGS", 12f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 22))
        header.addView(headText, LinearLayout.LayoutParams(0, 56, 1f))

        val clock = tv("", 18f, text, true, Gravity.END)
        header.addView(clock, LinearLayout.LayoutParams(190, 56))
        lifecycleScope.launch {
            while (!isFinishing) {
                clock.text = SimpleDateFormat("HH:mm  dd/MM/yyyy", Locale.US).format(Date())
                delay(30_000)
            }
        }

        shell.addView(header, LinearLayout.LayoutParams(-1, 70))
        content = FrameLayout(this)
        shell.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(shell)
        setContentView(root)
    }

    private fun showHome() {
        page = Page.HOME
        backButton.visibility = View.GONE
        title.text = "HICHRAWI LIVE"
        content.removeAllViews()

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 24, 20, 10)
        }
        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val live = homeCard("▶", "LIVE", "8 قنوات رياضية", purple2, 0xFF25104C.toInt()) { showLive() }
        val social = homeCard("◎", "SOCIAL", "روابط التواصل", 0xFF1767A8.toInt(), 0xFF10243F.toInt()) { showSocial() }
        val settings = homeCard("⚙", "SETTINGS", "الإعدادات", 0xFF8B3F7D.toInt(), 0xFF321633.toInt()) { showSettings() }
        cards.addView(live, LinearLayout.LayoutParams(0, 350, 1f).apply { setMargins(12, 18, 12, 18) })
        cards.addView(social, LinearLayout.LayoutParams(0, 350, 1f).apply { setMargins(12, 18, 12, 18) })
        cards.addView(settings, LinearLayout.LayoutParams(0, 350, 1f).apply { setMargins(12, 18, 12, 18) })
        wrap.addView(cards, LinearLayout.LayoutParams(-1, 400))
        content.addView(wrap)
        live.requestFocus()
    }

    private fun homeCard(icon: String, label: String, detail: String, c1: Int, c2: Int, action: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bgDrawable(c1, c2, 12f)
            isFocusable = true
            isClickable = true
            stateListAnimator = null
            setPadding(18, 18, 18, 18)
            setOnClickListener { action() }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) bgDrawable(gold, c1, 12f, gold) else bgDrawable(c1, c2, 12f)
                v.scaleX = if (hasFocus) 1.055f else 1f
                v.scaleY = if (hasFocus) 1.055f else 1f
                v.elevation = if (hasFocus) 18f else 4f
            }
        }
        card.addView(tv(icon, 58f, if (label == "LIVE") Color.WHITE else gold, true), LinearLayout.LayoutParams(-1, 105))
        card.addView(tv(label, 27f, text, true), LinearLayout.LayoutParams(-1, 52))
        card.addView(tv(detail, 15f, muted), LinearLayout.LayoutParams(-1, 40))
        return card
    }

    private fun pageHeader(back: () -> Unit) {
        backButton.visibility = View.VISIBLE
        backButton.setOnClickListener { back() }
    }

    private fun showLive() {
        page = Page.LIVE
        pageHeader { showHome() }
        title.text = "LIVE TV"
        content.removeAllViews()

        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 8, 4, 8)
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable(0xFF24113D.toInt(), 0xFF100A1C.toInt(), 10f)
            setPadding(14, 12, 14, 12)
        }
        left.addView(tv("HICHRAWI SPORT", 20f, text, true, Gravity.START), LinearLayout.LayoutParams(-1, 42))
        left.addView(tv("القنوات الرياضية", 13f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 28))

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChannelsActivity)
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            clipToPadding = false
            setPadding(0, 8, 0, 10)
        }
        left.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(left, LinearLayout.LayoutParams(0, -1, 0.42f).apply { setMargins(0, 0, 12, 0) })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bgDrawable(0xFF15101F.toInt(), 0xFF0A0810.toInt(), 10f)
            setPadding(30, 24, 30, 24)
        }
        channelInfoLogo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        info.addView(channelInfoLogo, LinearLayout.LayoutParams(-1, 210))
        channelInfoName = tv("HichrawiSport1", 30f, text, true)
        info.addView(channelInfoName, LinearLayout.LayoutParams(-1, 54))
        info.addView(tv("● LIVE", 18f, 0xFF69E28C.toInt(), true), LinearLayout.LayoutParams(-1, 42))
        info.addView(tv("OK : مشاهدة القناة", 15f, muted), LinearLayout.LayoutParams(-1, 40))
        frame.addView(info, LinearLayout.LayoutParams(0, -1, 0.58f))

        content.addView(frame)
        val visible = sportsOnly()
        list.adapter = LiveAdapter(visible)
        if (visible.isNotEmpty()) {
            updateChannelInfo(visible.first())
            list.post { list.getChildAt(0)?.requestFocus() }
        }
    }

    private fun sportsOnly(): List<Api.Channel> = allChannels
        .filter { it.name.contains("HichrawiSport", ignoreCase = true) }
        .sortedWith(
            compareBy<Api.Channel> {
                Regex("(?i)HichrawiSport(\\d+)")
                    .find(it.name.replace(" ", ""))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 9999
            }.thenBy { it.sortOrder }.thenBy { it.id }
        )

    private inner class LiveAdapter(private val items: List<Api.Channel>) : RecyclerView.Adapter<LiveAdapter.Holder>() {
        inner class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(this@ChannelsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = solid(0xFF2B2140.toInt(), 8f)
                isFocusable = true
                isClickable = true
                setPadding(12, 6, 12, 6)
                stateListAnimator = null
            }
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ch = items[position]
            val row = holder.row
            row.removeAllViews()
            row.addView(tv("${position + 1}", 16f, gold, true), LinearLayout.LayoutParams(42, 48))
            row.addView(tv(ch.name, 18f, text, false, Gravity.CENTER_VERTICAL or Gravity.START), LinearLayout.LayoutParams(0, 48, 1f))
            row.addView(tv("●", 13f, 0xFF65D99A.toInt(), true), LinearLayout.LayoutParams(28, 48))
            row.setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) bgDrawable(0xFF8B2FD0.toInt(), 0xFF5A1E9A.toInt(), 8f, gold) else solid(0xFF2B2140.toInt(), 8f)
                v.scaleX = if (hasFocus) 1.015f else 1f
                v.scaleY = if (hasFocus) 1.015f else 1f
                if (hasFocus) updateChannelInfo(ch)
            }
            row.setOnClickListener { play(ch) }
        }

        override fun getItemCount() = items.size
    }

    private fun updateChannelInfo(ch: Api.Channel) {
        channelInfoName?.text = ch.name
        ch.logoUrl?.let { url -> channelInfoLogo?.let { loadImage(url, it) } }
    }

    private fun showSocial() {
        page = Page.SOCIAL
        pageHeader { showHome() }
        title.text = "SOCIAL"
        content.removeAllViews()

        val scroll = ScrollView(this)
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 14, 8, 18)
        }
        grid.addView(tv("روابط HICHRAWI الرسمية", 20f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 44))

        val items = listOf(
            Triple("Facebook", "facebook", "f"), Triple("TikTok", "tiktok", "♪"),
            Triple("Instagram", "instagram", "◎"), Triple("WEBSITE / UPDATE", "website", "↗"),
            Triple("Telegram", "telegram", "✈"), Triple("WhatsApp", "whatsapp", "☎")
        ).filter { (_, key, _) -> appSettings["${key}Enabled"]?.equals("false", true) != true }

        items.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (name, key, icon) ->
                val url = appSettings[key].orEmpty()
                val card = actionCard(icon, name, if (url.isBlank()) "غير مضاف من الإدارة" else "فتح الرابط") {
                    if (url.isBlank()) Toast.makeText(this, "رابط $name غير مضاف من الإدارة", Toast.LENGTH_SHORT).show()
                    else openSocial(url)
                }
                row.addView(card, LinearLayout.LayoutParams(0, 130, 1f).apply { setMargins(7, 7, 7, 7) })
            }
            if (pair.size == 1) row.addView(Space(this), LinearLayout.LayoutParams(0, 130, 1f))
            grid.addView(row, LinearLayout.LayoutParams(-1, 144))
        }
        scroll.addView(grid)
        content.addView(scroll)
        scroll.post { grid.getChildAt(1)?.requestFocus() }
    }

    private fun actionCard(icon: String, name: String, detail: String, action: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = solid(panel, 12f)
            setPadding(18, 12, 18, 12)
            isFocusable = true
            isClickable = true
            stateListAnimator = null
            setOnClickListener { action() }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = if (hasFocus) solid(purple2, 12f, gold) else solid(panel, 12f)
                v.scaleX = if (hasFocus) 1.03f else 1f
                v.scaleY = if (hasFocus) 1.03f else 1f
            }
        }
        card.addView(tv(icon, 28f, gold, true), LinearLayout.LayoutParams(58, 80))
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(tv(name, 18f, text, true, Gravity.START), LinearLayout.LayoutParams(-1, 38))
        textBox.addView(tv(detail, 12f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 30))
        card.addView(textBox, LinearLayout.LayoutParams(0, 80, 1f))
        return card
    }

    private fun showSettings() {
        page = Page.SETTINGS
        pageHeader { showHome() }
        title.text = "SETTINGS"
        content.removeAllViews()

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 14, 8, 20)
        }
        box.addView(actionCard("🎟", "معلومات الاشتراك", "الحالة وتاريخ الانتهاء") { showSubscription() }, LinearLayout.LayoutParams(-1, 92).apply { setMargins(7, 7, 7, 7) })
        box.addView(actionCard("↻", "تحديث القنوات", "إعادة تحميل البيانات من Firebase") { loadData() }, LinearLayout.LayoutParams(-1, 92).apply { setMargins(7, 7, 7, 7) })
        box.addView(actionCard("ⓘ", "حول التطبيق", "HICHRAWI LIVE • القنوات الرياضية") {
            AlertDialog.Builder(this).setTitle("HICHRAWI LIVE")
                .setMessage("تطبيق HICHRAWI LIVE للقنوات الرياضية.\n\nالإصدار: ${packageManager.getPackageInfo(packageName, 0).versionName}")
                .setPositiveButton("حسناً", null).show()
        }, LinearLayout.LayoutParams(-1, 92).apply { setMargins(7, 7, 7, 7) })
        val appName = appSettings["appName"].orEmpty().ifBlank { "HICHRAWI LIVE" }
        val welcome = appSettings["welcome"].orEmpty().ifBlank { "متصل مباشرة بـ Firebase" }
        box.addView(tv("$appName\n$welcome", 17f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 80).apply { setMargins(16, 20, 16, 8) })
        scroll.addView(box)
        content.addView(scroll)
        scroll.post { box.getChildAt(0)?.requestFocus() }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                if (!isLicenseActive(state)) throw Exception("الاشتراك غير فعال أو منتهي")
                val channels = Api.channels(this@ChannelsActivity, deviceId)
                val settings = Api.settings(this@ChannelsActivity, deviceId)
                withContext(Dispatchers.Main) {
                    allChannels = channels
                    appSettings = settings
                    when (page) {
                        Page.HOME -> showHome()
                        Page.LIVE -> showLive()
                        Page.SOCIAL -> showSocial()
                        Page.SETTINGS -> showSettings()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChannelsActivity, "تعذر تحميل البيانات: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadImage(url: String, image: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bytes = response.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun openSocial(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح الرابط", Toast.LENGTH_SHORT).show() }
    }

    private fun play(ch: Api.Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", ch.id)
            putExtra("channel_name", ch.name)
            putExtra("logo_url", ch.logoUrl)
        })
    }

    private fun showSubscription() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                val active = isLicenseActive(state)
                val activated = firstDate(state, "activated_at", "starts_at", "start_at", "created_at")
                    .ifBlank { prefs.getString("activation_recorded_at", "").orEmpty() }
                val expires = firstDate(state, "expires_at", "expiration_at", "expires")
                    .ifBlank { prefs.getString("subscription_expires_at", "").orEmpty() }
                val msg = if (active) "الحالة: فعال\n\nتاريخ التفعيل: ${formatServerDate(activated)}\nتاريخ الانتهاء: ${formatServerDate(expires)}" else "الحالة: غير فعال أو منتهي"
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ChannelsActivity).setTitle("معلومات الاشتراك").setMessage(msg).setPositiveButton("حسناً", null).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@ChannelsActivity, e.message ?: "تعذر جلب الاشتراك", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun isLicenseActive(obj: org.json.JSONObject): Boolean {
        val sub = obj.optJSONObject("subscription")
        if (sub?.optBoolean("active") == true) return true
        val lic = obj.optJSONObject("license")
        if (lic?.optString("status").equals("active", true)) return true
        return obj.optString("status").equals("active", true)
    }

    private fun firstDate(obj: org.json.JSONObject, vararg keys: String): String {
        val subscription = obj.optJSONObject("subscription")
        val license = obj.optJSONObject("license")
        for (key in keys) {
            val a = subscription?.optString(key).orEmpty()
            if (a.isNotBlank()) return a
            val b = license?.optString(key).orEmpty()
            if (b.isNotBlank()) return b
            val c = obj.optString(key).orEmpty()
            if (c.isNotBlank()) return c
        }
        return ""
    }

    private fun formatServerDate(value: String): String {
        if (value.isBlank()) return "غير متوفر"
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        )
        for (input in formats) {
            try {
                return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(input.parse(value) ?: continue)
            } catch (_: Exception) { }
        }
        return value
    }

    override fun onBackPressed() {
        if (page != Page.HOME) showHome() else super.onBackPressed()
    }

    override fun onStart() {
        super.onStart()
        licenseJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) { Api.license(this@ChannelsActivity, deviceId) }
                    if (!isLicenseActive(state)) {
                        startActivity(Intent(this@ChannelsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
                        break
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onStop() {
        licenseJob?.cancel()
        licenseJob = null
        super.onStop()
    }
}
