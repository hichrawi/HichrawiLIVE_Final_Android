package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var contentList: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var packages: List<Api.Package> = emptyList()
    private var settings = AppConfig.AppSettings()
    private var currentSection = "live"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF070A12.toInt()
        window.navigationBarColor = 0xFF070A12.toInt()
        deviceId = prefs.getLong("server_device_id", 0L)
        buildUi()
        loadData()
    }

    private fun text(value: String, size: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF070A12.toInt())
            setPadding(18, 12, 18, 12)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.hichrawi_live_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        header.addView(logo, LinearLayout.LayoutParams(70, 70))

        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        title = text("HICHRAWI LIVE", 22f, bold = true)
        subtitle = text("LIVE • VOD • SERIES", 12f, 0xFF9EA9BC.toInt())
        names.addView(title, LinearLayout.LayoutParams(0, 34, 1f))
        names.addView(subtitle, LinearLayout.LayoutParams(0, 28, 1f))
        header.addView(names, LinearLayout.LayoutParams(0, 70, 1f))

        val refresh = Button(this).apply {
            text = "↻"
            textSize = 23f
            isAllCaps = false
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_tab)
            contentDescription = "تحديث"
            setOnClickListener { loadData() }
        }
        header.addView(refresh, LinearLayout.LayoutParams(58, 58))
        root.addView(header)

        val heroes = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 8)
        }
        heroes.addView(hero("LIVE", "▶", 0xFF1DBA87.toInt()) { currentSection = "live"; renderLive() }, LinearLayout.LayoutParams(0, 142, 1f).apply { setMargins(0, 0, 5, 0) })
        heroes.addView(hero("VOD", "▶", 0xFFE54B55.toInt()) { currentSection = "vod"; renderContent("vod") }, LinearLayout.LayoutParams(0, 142, 1f).apply { setMargins(5, 0, 5, 0) })
        heroes.addView(hero("SERIES", "▣", 0xFF9B4DD8.toInt()) { currentSection = "series"; renderContent("series") }, LinearLayout.LayoutParams(0, 142, 1f).apply { setMargins(5, 0, 0, 0) })
        root.addView(heroes)

        search = EditText(this).apply {
            hint = "بحث في القنوات..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF788398.toInt())
            background = getDrawable(R.drawable.bg_input)
            setPadding(18, 0, 18, 0)
            visibility = View.VISIBLE
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (currentSection == "live") renderLive() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 58).apply { setMargins(0, 5, 0, 8) })

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val channels = Button(this).apply {
            text = "القنوات"
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { currentSection = "live"; search.visibility = View.VISIBLE; renderLive() }
        }
        val packagesButton = Button(this).apply {
            text = "الباقات"
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { currentSection = "packages"; search.visibility = View.GONE; renderPackages() }
        }
        val contact = Button(this).apply {
            text = "تواصل"
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { showContact() }
        }
        tabs.addView(channels, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(0, 0, 4, 0) })
        tabs.addView(packagesButton, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(4, 0, 4, 0) })
        tabs.addView(contact, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(4, 0, 0, 0) })
        root.addView(tabs)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        contentList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 10, 2, 24)
        }
        scroll.addView(contentList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val fav = Button(this).apply {
            text = "☆ المفضلة"
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { currentSection = "favorites"; search.visibility = View.VISIBLE; renderFavorites() }
        }
        footer.addView(fav, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(0, 3, 4, 0) })
        val account = Button(this).apply {
            text = "الاشتراك"
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener { showSubscription() }
        }
        footer.addView(account, LinearLayout.LayoutParams(0, 52, 1f).apply { setMargins(4, 3, 0, 0) })
        root.addView(footer)

        setContentView(root)
    }

    private fun hero(label: String, icon: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = "$icon\n$label"
        textSize = 19f
        isAllCaps = false
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        background = GradientDrawableFactory.rounded(color, 20)
        isFocusable = true
        isClickable = true
        setOnClickListener { action() }
    }

    private fun loadData() {
        contentList.removeAllViews()
        addLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true) throw Exception("الاشتراك غير فعال أو منتهي")
                settings = Api.settings(this@ChannelsActivity, deviceId)
                allChannels = Api.channels(this@ChannelsActivity, deviceId)
                packages = Api.packages(this@ChannelsActivity, deviceId, allChannels)
                withContext(Dispatchers.Main) {
                    title.text = settings.appName
                    subtitle.text = settings.subtitle
                    if (settings.maintenance) showMessage(settings.maintenanceMessage) else renderLive()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showMessage(e.message ?: "تعذر تحميل البيانات") }
            }
        }
    }

    private fun addLoading() = contentList.addView(text("جاري تحميل المحتوى...", 16f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 110))

    private fun showMessage(msg: String) {
        contentList.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(20, 30, 20, 30) }
        box.addView(text(msg, 17f, 0xFFA9B2C5.toInt(), true), LinearLayout.LayoutParams(-1, 110))
        contentList.addView(box)
    }

    private fun renderLive() {
        contentList.removeAllViews()
        val query = search.text.toString().trim()
        val filtered = allChannels.filter { query.isBlank() || it.name.contains(query, true) }
        contentList.addView(text("${settings.homeLiveTitle}  •  ${filtered.size} قناة", 19f, bold = true), LinearLayout.LayoutParams(-1, 50))
        if (filtered.isEmpty()) { showMessage(if (query.isBlank()) "لا توجد قنوات متاحة حالياً" else "لا توجد نتائج للبحث"); return }
        filtered.forEach(::addChannel)
    }

    private fun renderFavorites() {
        contentList.removeAllViews()
        val query = search.text.toString().trim()
        val favorites = allChannels.filter { isFavorite(it.id) && (query.isBlank() || it.name.contains(query, true)) }
        contentList.addView(text("القنوات المفضلة  •  ${favorites.size}", 19f, bold = true), LinearLayout.LayoutParams(-1, 50))
        if (favorites.isEmpty()) { showMessage("لم تضف أي قناة إلى المفضلة بعد"); return }
        favorites.forEach(::addChannel)
    }

    private fun renderPackages() {
        contentList.removeAllViews()
        contentList.addView(text("الباقات", 21f, bold = true), LinearLayout.LayoutParams(-1, 52))
        if (packages.isEmpty()) { showMessage("لا توجد باقات متاحة حالياً"); return }
        packages.forEach { p ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.bg_card)
                setPadding(18, 16, 18, 16)
                isFocusable = true
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            p.logoUrl?.let { url ->
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
                row.addView(image, LinearLayout.LayoutParams(76, 60)); loadImage(url, image)
            }
            row.addView(text(p.name, 21f, bold = true), LinearLayout.LayoutParams(0, 60, 1f).apply { setMargins(12, 0, 0, 0) })
            card.addView(row)
            card.addView(text("${p.channelIds.size} قناة", 14f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 32))
            val b = Button(this).apply { text = "عرض القنوات"; isAllCaps = false; background = getDrawable(R.drawable.bg_button); setOnClickListener { showPackageChannels(p) } }
            card.addView(b, LinearLayout.LayoutParams(-1, 54))
            contentList.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
        }
    }

    private fun showPackageChannels(p: Api.Package) {
        search.visibility = View.GONE
        currentSection = "packages"
        val selected = allChannels.filter { it.id in p.channelIds.toSet() }
        contentList.removeAllViews()
        contentList.addView(text(p.name, 21f, bold = true), LinearLayout.LayoutParams(-1, 52))
        val back = Button(this).apply { text = "← كل الباقات"; isAllCaps = false; background = getDrawable(R.drawable.bg_tab); setOnClickListener { renderPackages() } }
        contentList.addView(back, LinearLayout.LayoutParams(-1, 50).apply { setMargins(0, 0, 0, 10) })
        if (selected.isEmpty()) { showMessage("لا توجد قنوات في هذه الباقة"); return }
        selected.forEach(::addChannel)
    }

    private fun addChannel(ch: Api.Channel) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.bg_card)
            setPadding(12, 9, 10, 9)
            isFocusable = true
            isClickable = true
        }
        val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = ch.name }
        card.addView(logo, LinearLayout.LayoutParams(82, 72))
        val middle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 0, 8, 0) }
        middle.addView(text(ch.name, 18f, bold = true), LinearLayout.LayoutParams(-1, 39))
        middle.addView(text("LIVE • بث مباشر", 12f, 0xFF7BD7AD.toInt()), LinearLayout.LayoutParams(-1, 28))
        card.addView(middle, LinearLayout.LayoutParams(0, 72, 1f))
        val star = Button(this).apply {
            text = if (isFavorite(ch.id)) "★" else "☆"
            textSize = 20f
            isAllCaps = false
            background = getDrawable(R.drawable.bg_tab)
            setOnClickListener {
                toggleFavorite(ch.id)
                text = if (isFavorite(ch.id)) "★" else "☆"
            }
        }
        card.addView(star, LinearLayout.LayoutParams(52, 54).apply { setMargins(3, 0, 3, 0) })
        val watch = Button(this).apply {
            text = "مشاهدة"
            isAllCaps = false
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.bg_button)
            setOnClickListener { play(ch) }
        }
        card.addView(watch, LinearLayout.LayoutParams(105, 54))
        card.setOnClickListener { play(ch) }
        ch.logoUrl?.let { loadImage(it, logo) }
        contentList.addView(card, LinearLayout.LayoutParams(-1, 94).apply { setMargins(0, 0, 0, 11) })
    }

    private fun renderContent(type: String) {
        search.visibility = View.GONE
        contentList.removeAllViews(); addLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val items = Api.content(this@ChannelsActivity, deviceId, type)
            withContext(Dispatchers.Main) {
                contentList.removeAllViews()
                val label = if (type == "vod") settings.homeVodTitle else settings.homeSeriesTitle
                contentList.addView(text(label, 21f, bold = true), LinearLayout.LayoutParams(-1, 52))
                if (items.isEmpty()) {
                    showMessage("$label\nالمحتوى سيظهر هنا عند تفعيله من الإدارة")
                    return@withContext
                }
                items.forEach { item ->
                    val card = LinearLayout(this@ChannelsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = getDrawable(R.drawable.bg_card)
                        setPadding(12, 10, 12, 10)
                        isFocusable = true
                        setOnClickListener { playContent(item) }
                    }
                    val poster = ImageView(this@ChannelsActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    card.addView(poster, LinearLayout.LayoutParams(92, 112))
                    val info = LinearLayout(this@ChannelsActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(15, 0, 8, 0) }
                    info.addView(text(item.title, 18f, bold = true), LinearLayout.LayoutParams(-1, 50))
                    info.addView(text("اضغط للمشاهدة", 13f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 32))
                    card.addView(info, LinearLayout.LayoutParams(0, 112, 1f))
                    item.posterUrl?.let { loadImage(it, poster) }
                    contentList.addView(card, LinearLayout.LayoutParams(-1, 132).apply { setMargins(0, 0, 0, 12) })
                }
            }
        }
    }

    private fun playContent(item: Api.ContentItem) {
        if (item.streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "رابط التشغيل غير متوفر حالياً", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("direct_url", item.streamUrl)
            putExtra("channel_name", item.title)
            putExtra("logo_url", item.posterUrl)
        })
    }

    private fun showContact() {
        val s = settings.social
        val links = listOf(
            Triple("🔵 Facebook", s.facebook, s.facebookEnabled),
            Triple("🎵 TikTok", s.tiktok, s.tiktokEnabled),
            Triple("🟢 WhatsApp", s.whatsapp, s.whatsappEnabled)
        ).filter { it.third && it.second.isNotBlank() }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 18, 22, 10) }
        box.addView(text("تواصل معنا", 22f, bold = true), LinearLayout.LayoutParams(-1, 52))
        if (links.isEmpty()) box.addView(text("لا توجد وسائل تواصل مفعلة حالياً", 15f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 70))
        links.forEach { item ->
            val b = Button(this).apply {
                text = item.first
                isAllCaps = false
                background = getDrawable(R.drawable.bg_button)
                setOnClickListener { openSocial(item.first, item.second) }
            }
            box.addView(b, LinearLayout.LayoutParams(-1, 56).apply { setMargins(0, 0, 0, 8) })
        }
        AlertDialog.Builder(this).setView(box).setNegativeButton("إغلاق", null).show()
    }

    private fun showSubscription() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                val sub = state.optJSONObject("subscription")
                val active = sub?.optBoolean("active") == true
                val expires = sub?.optString("expires_at").orEmpty()
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ChannelsActivity)
                        .setTitle("الاشتراك")
                        .setMessage(if (active) "الحالة: فعال\nالانتهاء: $expires" else "الحالة: غير فعال")
                        .setPositiveButton("حسناً", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@ChannelsActivity, e.message ?: "تعذر جلب الاشتراك", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun openSocial(name: String, value: String) {
        val url = if (name.contains("WhatsApp")) {
            if (value.startsWith("http", true)) value else "https://wa.me/${value.filter { it.isDigit() }}"
        } else value
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
    }

    private fun play(ch: Api.Channel) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", ch.id)
            putExtra("channel_name", ch.name)
            putExtra("logo_url", ch.logoUrl)
        })
    }

    private fun loadImage(url: String, image: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    val bytes = r.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    withContext(Dispatchers.Main) { image.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun isFavorite(id: Long) = prefs.getBoolean("fav_$id", false)
    private fun toggleFavorite(id: Long) = prefs.edit().putBoolean("fav_$id", !isFavorite(id)).apply()

    override fun onStart() {
        super.onStart()
        licenseJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                try {
                    val state = withContext(Dispatchers.IO) { Api.license(this@ChannelsActivity, deviceId) }
                    if (state.optJSONObject("subscription")?.optBoolean("active") != true) {
                        startActivity(Intent(this@ChannelsActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish(); break
                    }
                } catch (_: Exception) { }
            }
        }
    }

    override fun onStop() { licenseJob?.cancel(); licenseJob = null; super.onStop() }
}

private object GradientDrawableFactory {
    fun rounded(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * 3f
        }
}
