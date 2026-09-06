package com.hichrawi.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private var deviceId = 0L
    private var licenseJob: Job? = null
    private var allChannels: List<Api.Channel> = emptyList()
    private var packages: List<Api.Package> = emptyList()
    private var settings = AppConfig.AppSettings()
    private var currentSection = "live"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF070A12.toInt(); window.navigationBarColor = 0xFF070A12.toInt()
        deviceId = prefs.getLong("server_device_id", 0L); buildUi(); loadData()
    }

    private fun text(s: String, size: Float, color: Int = 0xFFFFFFFF.toInt(), bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF070A12.toInt()); setPadding(18, 12, 18, 12) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply { setImageResource(R.drawable.hichrawi_live_logo); scaleType = ImageView.ScaleType.CENTER_INSIDE }
        header.addView(logo, LinearLayout.LayoutParams(68, 68))
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        title = text("HICHRAWI LIVE", 22f, bold = true); subtitle = text("Live • VOD • Series", 12f, 0xFF9EA9BC.toInt())
        names.addView(title, LinearLayout.LayoutParams(-1, 34)); names.addView(subtitle, LinearLayout.LayoutParams(-1, 28))
        header.addView(names, LinearLayout.LayoutParams(0, 68, 1f))
        val refresh = Button(this).apply { text = "↻"; textSize = 22f; isAllCaps = false; setOnClickListener { loadData() } }
        header.addView(refresh, LinearLayout.LayoutParams(58, 58)); root.addView(header)

        val heroes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 8) }
        val live = hero("LIVE", "📺", 0xFF1DBA87.toInt()) { currentSection = "live"; renderLive() }
        val vod = hero("VOD", "▶", 0xFFE54B55.toInt()) { currentSection = "vod"; renderContent("vod") }
        val series = hero("SERIES", "▣", 0xFF9B4DD8.toInt()) { currentSection = "series"; renderContent("series") }
        heroes.addView(live, LinearLayout.LayoutParams(0, 132, 1f).apply { setMargins(0, 0, 6, 0) })
        heroes.addView(vod, LinearLayout.LayoutParams(0, 132, 1f).apply { setMargins(3, 0, 3, 0) })
        heroes.addView(series, LinearLayout.LayoutParams(0, 132, 1f).apply { setMargins(6, 0, 0, 0) })
        root.addView(heroes)

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8, 0, 5) }
        val channels = Button(this).apply { text = "القنوات"; isAllCaps = false; background = getDrawable(R.drawable.bg_tab); setOnClickListener { currentSection = "live"; renderLive() } }
        val packagesButton = Button(this).apply { text = "الباقات"; isAllCaps = false; background = getDrawable(R.drawable.bg_tab); setOnClickListener { currentSection = "packages"; renderPackages() } }
        tabs.addView(channels, LinearLayout.LayoutParams(0, 54, 1f).apply { setMargins(0, 0, 5, 0) }); tabs.addView(packagesButton, LinearLayout.LayoutParams(0, 54, 1f).apply { setMargins(5, 0, 0, 0) }); root.addView(tabs)

        val scroll = ScrollView(this); list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(2, 5, 2, 24) }; scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val social = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 4, 0, 2) }
        val contact = Button(this).apply { text = "📞 تواصل معنا"; isAllCaps = false; background = getDrawable(R.drawable.bg_button); setOnClickListener { showContact() } }
        social.addView(contact, LinearLayout.LayoutParams(-1, 54)); root.addView(social)
        setContentView(root)
    }

    private fun hero(label: String, icon: String, color: Int, action: () -> Unit): Button = Button(this).apply {
        text = "$icon\n$label"; textSize = 18f; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt()); background = getDrawable(R.drawable.bg_card); gravity = Gravity.CENTER; isFocusable = true; setOnClickListener { action() }
    }

    private fun loadData() {
        list.removeAllViews(); addLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val state = Api.license(this@ChannelsActivity, deviceId)
                if (state.optJSONObject("subscription")?.optBoolean("active") != true) throw Exception("الاشتراك غير فعال أو منتهي")
                settings = Api.settings(this@ChannelsActivity, deviceId)
                allChannels = Api.channels(this@ChannelsActivity, deviceId)
                packages = Api.packages(this@ChannelsActivity, deviceId, allChannels)
                withContext(Dispatchers.Main) {
                    title.text = settings.appName; subtitle.text = settings.subtitle
                    if (settings.maintenance) showMessage(settings.maintenanceMessage) else renderLive()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { showMessage(e.message ?: "تعذر تحميل البيانات") } }
        }
    }

    private fun addLoading() = list.addView(text("جاري تحميل المحتوى...", 16f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 100))
    private fun showMessage(msg: String) { list.removeAllViews(); list.addView(text(msg, 17f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 150)) }

    private fun renderLive() {
        list.removeAllViews()
        val heading = text("${settings.homeLiveTitle}  •  ${allChannels.size} قناة", 18f, bold = true)
        list.addView(heading, LinearLayout.LayoutParams(-1, 48))
        if (allChannels.isEmpty()) { showMessage("لا توجد قنوات متاحة حالياً"); return }
        allChannels.forEach(::addChannel)
    }

    private fun renderPackages() {
        list.removeAllViews()
        if (packages.isEmpty()) { showMessage("لا توجد باقات متاحة حالياً"); return }
        packages.forEach { p ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = getDrawable(R.drawable.bg_card); setPadding(18, 15, 18, 15); isFocusable = true }
            card.addView(text(p.name, 21f, bold = true), LinearLayout.LayoutParams(-1, 42))
            card.addView(text("${p.channelIds.size} قناة", 14f, 0xFFA9B2C5.toInt()), LinearLayout.LayoutParams(-1, 34))
            val b = Button(this).apply { text = "عرض القنوات"; isAllCaps = false; background = getDrawable(R.drawable.bg_button); setOnClickListener { showPackageChannels(p) } }
            card.addView(b, LinearLayout.LayoutParams(-1, 54)); list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
        }
    }

    private fun showPackageChannels(p: Api.Package) {
        val selected = allChannels.filter { it.id in p.channelIds.toSet() }
        list.removeAllViews(); list.addView(text(p.name, 21f, bold = true), LinearLayout.LayoutParams(-1, 52))
        if (selected.isEmpty()) { showMessage("لا توجد قنوات في هذه الباقة"); return }
        selected.forEach(::addChannel)
    }

    private fun addChannel(ch: Api.Channel) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = getDrawable(R.drawable.bg_card); setPadding(12, 9, 12, 9); isFocusable = true; isClickable = true }
        val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = ch.name }
        card.addView(logo, LinearLayout.LayoutParams(88, 74))
        val middle = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(14, 0, 8, 0) }
        middle.addView(text(ch.name, 18f, bold = true), LinearLayout.LayoutParams(-1, 40)); middle.addView(text("LIVE • بث مباشر", 12f, 0xFF7BD7AD.toInt()), LinearLayout.LayoutParams(-1, 28)); card.addView(middle, LinearLayout.LayoutParams(0, 74, 1f))
        val watch = Button(this).apply { text = "مشاهدة"; isAllCaps = false; textSize = 15f; setTextColor(0xFFFFFFFF.toInt()); background = getDrawable(R.drawable.bg_button); setOnClickListener { play(ch) } }
        card.addView(watch, LinearLayout.LayoutParams(112, 54)); card.setOnClickListener { play(ch) }
        ch.logoUrl?.let { loadImage(it, logo) }
        list.addView(card, LinearLayout.LayoutParams(-1, 94).apply { setMargins(0, 0, 0, 11) })
    }

    private fun renderContent(type: String) {
        list.removeAllViews(); addLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val items = Api.content(this@ChannelsActivity, deviceId, type)
            withContext(Dispatchers.Main) {
                list.removeAllViews()
                if (items.isEmpty()) {
                    val label = if (type == "vod") settings.homeVodTitle else settings.homeSeriesTitle
                    showMessage("$label\nالمحتوى سيظهر هنا عند تفعيله من الإدارة")
                } else items.forEach { addContent(it) }
            }
        }
    }

    private fun addContent(item: Api.ContentItem) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = getDrawable(R.drawable.bg_card); setPadding(12, 10, 12, 10); isFocusable = true }
        val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(poster, LinearLayout.LayoutParams(88, 90))
        card.addView(text(item.title, 18f, bold = true), LinearLayout.LayoutParams(0, 90, 1f).apply { setMargins(14, 0, 10, 0) })
        val open = Button(this).apply { text = "مشاهدة"; isAllCaps = false; background = getDrawable(R.drawable.bg_button); setOnClickListener { item.streamUrl?.let { url -> startActivity(Intent(this@ChannelsActivity, PlayerActivity::class.java).putExtra("direct_url", url).putExtra("channel_name", item.title).putExtra("logo_url", item.posterUrl)) } } }
        card.addView(open, LinearLayout.LayoutParams(112, 54)); item.posterUrl?.let { loadImage(it, poster) }
        list.addView(card, LinearLayout.LayoutParams(-1, 110).apply { setMargins(0, 0, 0, 11) })
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
            val b = Button(this).apply { text = item.first; isAllCaps = false; background = getDrawable(R.drawable.bg_button); setOnClickListener { openSocial(item.first, item.second) } }
            box.addView(b, LinearLayout.LayoutParams(-1, 56).apply { setMargins(0, 0, 0, 8) })
        }
        AlertDialog.Builder(this).setView(box).setNegativeButton("إغلاق", null).show()
    }

    private fun openSocial(name: String, value: String) {
        val url = if (name.contains("WhatsApp")) {
            if (value.startsWith("http", true)) value else "https://wa.me/${value.filter { it.isDigit() }}"
        } else value
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { }
    }

    private fun play(ch: Api.Channel) { startActivity(Intent(this, PlayerActivity::class.java).apply { putExtra("channel_id", ch.id); putExtra("channel_name", ch.name); putExtra("logo_url", ch.logoUrl) }) }

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
