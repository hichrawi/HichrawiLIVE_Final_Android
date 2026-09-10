
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class ChannelsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var content: FrameLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var liveMenu: Button
    private lateinit var socialMenu: Button
    private lateinit var settingsMenu: Button
    private lateinit var refreshButton: Button

    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var appSettings: Map<String, String> = emptyMap()

    private val gold = 0xFFF5B900.toInt()
    private val bg = 0xFF07090D.toInt()
    private val panel = 0xFF11151C.toInt()
    private val panel2 = 0xFF171D27.toInt()
    private val text = Color.WHITE
    private val muted = 0xFFADB7C7.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        deviceId = prefs.getLong("firebase_device_id", prefs.getLong("server_device_id", 0L))
        buildUi()
        showLive()
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

    private fun rounded(color: Int, radius: Float = 16f, stroke: Int? = null) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(2, it) }
        }

    private fun menuButton(label: String) = Button(this).apply {
        text = label
        textSize = 18f
        isAllCaps = false
        setTextColor(this@ChannelsActivity.text)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(panel2, 14f)
        isFocusable = true
        isClickable = true
        stateListAnimator = null
        setPadding(18, 0, 18, 0)
        setOnFocusChangeListener { v, hasFocus ->
            v.background = rounded(if (hasFocus) gold else panel2, 14f)
            (v as Button).setTextColor(if (hasFocus) 0xFF111111.toInt() else this@ChannelsActivity.text)
            v.scaleX = if (hasFocus) 1.03f else 1f
            v.scaleY = if (hasFocus) 1.03f else 1f
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            setPadding(22, 18, 22, 18)
        }

        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(12, 12, 12, 12)
            background = rounded(panel, 18f)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.hichrawi_live_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        sidebar.addView(logo, LinearLayout.LayoutParams(-1, 105))
        sidebar.addView(tv("HICHRAWI LIVE", 21f, text, true), LinearLayout.LayoutParams(-1, 48))

        liveMenu = menuButton("▶  LIVE")
        socialMenu = menuButton("◎  SOCIAL")
        settingsMenu = menuButton("⚙  SETTINGS")
        sidebar.addView(liveMenu, LinearLayout.LayoutParams(-1, 64).apply { setMargins(0, 12, 0, 8) })
        sidebar.addView(socialMenu, LinearLayout.LayoutParams(-1, 64).apply { setMargins(0, 8, 0, 8) })
        sidebar.addView(settingsMenu, LinearLayout.LayoutParams(-1, 64).apply { setMargins(0, 8, 0, 8) })
        sidebar.addView(Space(this), LinearLayout.LayoutParams(-1, 0, 1f))
        sidebar.addView(tv("8 أرقام • اشتراك آمن", 12f, muted), LinearLayout.LayoutParams(-1, 42))
        root.addView(sidebar, LinearLayout.LayoutParams(250, -1).apply { setMargins(0, 0, 18, 0) })

        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = tv("LIVE", 30f, text, true, Gravity.START)
        subtitle = tv("القنوات الرياضية المباشرة", 15f, muted, false, Gravity.START)
        heading.addView(title, LinearLayout.LayoutParams(-1, 42))
        heading.addView(subtitle, LinearLayout.LayoutParams(-1, 34))
        head.addView(heading, LinearLayout.LayoutParams(0, 78, 1f))

        refreshButton = Button(this).apply {
            text = "↻  تحديث"
            textSize = 15f
            isAllCaps = false
            setTextColor(this@ChannelsActivity.text)
            background = rounded(panel2, 12f)
            isFocusable = true
            setOnClickListener { loadData() }
        }
        head.addView(refreshButton, LinearLayout.LayoutParams(125, 58))
        main.addView(head, LinearLayout.LayoutParams(-1, 86))

        content = FrameLayout(this)
        main.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        setContentView(root)

        liveMenu.setOnClickListener { showLive() }
        socialMenu.setOnClickListener { showSocial() }
        settingsMenu.setOnClickListener { showSettings() }
        liveMenu.requestFocus()
    }

    private fun setActive(menu: Button, pageTitle: String, pageSubtitle: String) {
        listOf(liveMenu, socialMenu, settingsMenu).forEach {
            it.background = rounded(panel2, 14f)
            it.setTextColor(this@ChannelsActivity.text)
        }
        menu.background = rounded(gold, 14f)
        menu.setTextColor(0xFF111111.toInt())
        title.text = pageTitle
        subtitle.text = pageSubtitle
    }

    private fun showLive() {
        setActive(liveMenu, "LIVE", "القنوات الرياضية المباشرة")
        refreshButton.visibility = View.VISIBLE
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }
        box.addView(
            tv(if (allChannels.isEmpty()) "جاري تحميل القنوات..." else "${allChannels.size} قناة متاحة", 15f, muted, false, Gravity.START),
            LinearLayout.LayoutParams(-1, 38)
        )
        val list = RecyclerView(this).apply {
            clipToPadding = false
            setPadding(4, 8, 4, 24)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        box.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        content.removeAllViews()
        content.addView(box)
        list.layoutManager = GridLayoutManager(this, channelColumns())
        list.adapter = ChannelAdapter(allChannels)
        if (allChannels.isNotEmpty()) list.post { list.getChildAt(0)?.requestFocus() }
    }

    private fun showSocial() {
        setActive(socialMenu, "SOCIAL", "تواصل مع HICHRAWI عبر المنصات الرسمية")
        refreshButton.visibility = View.GONE

        val scroll = ScrollView(this).apply {
            isFocusable = true
            setPadding(4, 4, 4, 12)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 20)
        }
        box.addView(
            tv("تابع أخبار HICHRAWI LIVE وروابط التواصل الرسمية.", 17f, muted, false, Gravity.START),
            LinearLayout.LayoutParams(-1, 52)
        )

        val items = listOf(
            Triple("Facebook", "facebook", "صفحتنا الرسمية على Facebook"),
            Triple("TikTok", "tiktok", "حساب HICHRAWI على TikTok"),
            Triple("Instagram", "instagram", "حساب HICHRAWI على Instagram"),
            Triple("YouTube", "youtube", "قناتنا على YouTube"),
            Triple("Telegram", "telegram", "قناة HICHRAWI على Telegram"),
            Triple("WhatsApp", "whatsapp", "التواصل عبر WhatsApp")
        )

        items.filter { (_, key, _) ->
            appSettings["${key}Enabled"]?.equals("false", ignoreCase = true) != true
        }.forEach { (name, key, desc) ->
            val url = appSettings[key].orEmpty()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(panel, 16f)
                isFocusable = true
                isClickable = true
                setPadding(22, 10, 22, 10)
                setOnFocusChangeListener { v, hasFocus ->
                    v.background = rounded(if (hasFocus) 0xFF222B38.toInt() else panel, 16f, if (hasFocus) gold else null)
                    v.scaleX = if (hasFocus) 1.015f else 1f
                    v.scaleY = if (hasFocus) 1.015f else 1f
                }
                setOnClickListener {
                    if (url.isBlank()) Toast.makeText(this@ChannelsActivity, "رابط $name غير مضاف من الإدارة", Toast.LENGTH_SHORT).show()
                    else openSocial(url)
                }
            }
            card.addView(
                tv(when (key) {
                    "facebook" -> "f"
                    "tiktok" -> "♪"
                    "instagram" -> "◎"
                    "youtube" -> "▶"
                    "telegram" -> "✈"
                    else -> "☎"
                }, 28f, if (url.isBlank()) muted else gold, true),
                LinearLayout.LayoutParams(62, 68)
            )
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(tv(name, 20f, text, true, Gravity.START), LinearLayout.LayoutParams(-1, 34))
            texts.addView(
                tv(if (url.isBlank()) "$desc • غير متوفر حاليًا" else desc, 13f, muted, false, Gravity.START),
                LinearLayout.LayoutParams(-1, 32)
            )
            card.addView(texts, LinearLayout.LayoutParams(0, 74, 1f))
            box.addView(card, LinearLayout.LayoutParams(-1, 86).apply { setMargins(0, 7, 0, 7) })
        }

        scroll.addView(box)
        content.removeAllViews()
        content.addView(scroll)
        scroll.post { scroll.getChildAt(0)?.requestFocus() }
    }

    private fun showSettings() {
        setActive(settingsMenu, "SETTINGS", "إعدادات تطبيق HICHRAWI LIVE")
        refreshButton.visibility = View.GONE

        val scroll = ScrollView(this).apply {
            isFocusable = true
            setPadding(4, 4, 4, 12)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 20)
        }

        box.addView(actionButton("🎟  معلومات الاشتراك", "عرض حالة الاشتراك وتاريخ الانتهاء") { showSubscription() },
            LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 7, 0, 7) })
        box.addView(actionButton("↻  تحديث القنوات", "إعادة تحميل القنوات من Firebase") {
            showLive()
            loadData()
        }, LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 7, 0, 7) })
        box.addView(actionButton("ⓘ  حول التطبيق", "HICHRAWI LIVE • القنوات الرياضية") {
            AlertDialog.Builder(this)
                .setTitle("HICHRAWI LIVE")
                .setMessage(
                    "تطبيق HICHRAWI LIVE لمشاهدة القنوات الرياضية مباشرة.\n\n" +
                    "القنوات والمصادر والشعارات تتم إدارتها من Firebase.\n" +
                    "الإصدار الحالي: ${packageManager.getPackageInfo(packageName, 0).versionName}"
                )
                .setPositiveButton("حسناً", null)
                .show()
        }, LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 7, 0, 7) })

        val appName = appSettings["appName"].orEmpty().ifBlank { "HICHRAWI LIVE" }
        val welcome = appSettings["welcome"].orEmpty()
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 16f)
            setPadding(22, 18, 22, 18)
        }
        info.addView(tv(appName, 21f, gold, true, Gravity.START), LinearLayout.LayoutParams(-1, 38))
        info.addView(
            tv(if (welcome.isBlank()) "إدارة التطبيق متصلة مباشرة بـ Firebase." else welcome, 15f, muted, false, Gravity.START),
            LinearLayout.LayoutParams(-1, 50)
        )
        box.addView(info, LinearLayout.LayoutParams(-1, 110).apply { setMargins(0, 16, 0, 8) })

        scroll.addView(box)
        content.removeAllViews()
        content.addView(scroll)
        scroll.post { scroll.getChildAt(0)?.requestFocus() }
    }

    private fun actionButton(titleText: String, detail: String, action: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 16f)
            setPadding(22, 10, 22, 10)
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, hasFocus ->
                v.background = rounded(if (hasFocus) 0xFF222B38.toInt() else panel, 16f, if (hasFocus) gold else null)
                v.scaleX = if (hasFocus) 1.015f else 1f
                v.scaleY = if (hasFocus) 1.015f else 1f
            }
        }
        card.addView(tv(titleText, 20f, text, true, Gravity.START), LinearLayout.LayoutParams(-1, 34))
        card.addView(tv(detail, 13f, muted, false, Gravity.START), LinearLayout.LayoutParams(-1, 30))
        return card
    }

    private fun channelColumns(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return when {
            widthDp >= 1700 -> 5
            widthDp >= 1250 -> 4
            widthDp >= 900 -> 3
            else -> 2
        }
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
                    showLive()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChannelsActivity, "تعذر تحميل البيانات: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class ChannelAdapter(private val items: List<Api.Channel>) :
        RecyclerView.Adapter<ChannelAdapter.Holder>() {
        inner class Holder(val card: LinearLayout) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = LinearLayout(this@ChannelsActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = rounded(panel, 16f)
                setPadding(14, 12, 14, 14)
                isFocusable = true
                isClickable = true
                stateListAnimator = null
                elevation = 2f
            }
            return Holder(card)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val ch = items[position]
            val card = holder.card
            card.removeAllViews()
            val logo = ImageView(this@ChannelsActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = ch.name
            }
            card.addView(logo, LinearLayout.LayoutParams(-1, 100))
            card.addView(tv(ch.name, 17f, text, true), LinearLayout.LayoutParams(-1, 36))
            card.addView(tv("● LIVE", 12f, 0xFF65D99A.toInt()), LinearLayout.LayoutParams(-1, 28))

            val watch = Button(this@ChannelsActivity).apply {
                text = "مشاهدة"
                isAllCaps = false
                textSize = 14f
                setTextColor(0xFF111111.toInt())
                background = rounded(gold, 10f)
                isFocusable = false
                setOnClickListener { play(ch) }
            }
            card.addView(watch, LinearLayout.LayoutParams(-1, 50))
            card.setOnClickListener { play(ch) }
            card.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.04f else 1f
                v.scaleY = if (hasFocus) 1.04f else 1f
                v.elevation = if (hasFocus) 12f else 2f
                v.background = rounded(if (hasFocus) 0xFF1D2633.toInt() else panel, 16f, if (hasFocus) gold else null)
            }
            ch.logoUrl?.let { loadImage(it, logo) }
        }

        override fun getItemCount() = items.size
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
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "لا يوجد تطبيق مناسب لفتح هذا الرابط", Toast.LENGTH_SHORT).show()
        }
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
                val message = if (active) {
                    "الحالة: فعال\n\nتاريخ التفعيل: ${formatServerDate(activated)}\nتاريخ الانتهاء: ${formatServerDate(expires)}"
                } else "الحالة: غير فعال أو منتهي"
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ChannelsActivity)
                        .setTitle("معلومات الاشتراك")
                        .setMessage(message)
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChannelsActivity, e.message ?: "تعذر جلب الاشتراك", Toast.LENGTH_LONG).show()
                }
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
                val date = input.parse(value) ?: continue
                return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date)
            } catch (_: Exception) {}
        }
        return value
    }

    private fun play(ch: Api.Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", ch.id)
            putExtra("channel_name", ch.name)
            putExtra("logo_url", ch.logoUrl)
        })
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
                } catch (_: Exception) {}
            }
        }
    }

    override fun onStop() {
        licenseJob?.cancel()
        licenseJob = null
        super.onStop()
    }
}
