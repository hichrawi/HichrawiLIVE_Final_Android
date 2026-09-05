package com.hichrawi.tv

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("hichrawi", MODE_PRIVATE) }
    private lateinit var message: TextView
    private lateinit var codeInput: EditText
    private lateinit var activateButton: Button

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        setPadding(20, 12, 20, 12)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF050507.toInt()
        window.navigationBarColor = 0xFF050507.toInt()
        showSplash()
    }

    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF050507.toInt())
        }
        root.addView(text("HICHRAWI LIVE", 34f, true))
        root.addView(text("مرحباً بكم في تطبيق HICHRAWI LIVE", 18f))
        setContentView(root)
        lifecycleScope.launch(Dispatchers.IO) { AppConfig.refreshRemote(this@MainActivity) }
        lifecycleScope.launch {
            delay(3500)
            continueFromSavedLicense()
        }
    }

    private fun continueFromSavedLicense() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val id = ensureDevice()
                val state = Api.license(this@MainActivity, id)
                val active = state.optJSONObject("subscription")?.optBoolean("active") == true
                withContext(Dispatchers.Main) {
                    if (active) openChannels() else showActivation()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { showActivation() }
            }
        }
    }

    private fun showActivation() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 30, 40, 30)
            setBackgroundColor(0xFF050507.toInt())
        }
        root.addView(text("HICHRAWI LIVE", 30f, true))
        root.addView(text("أدخل كود الاشتراك للمتابعة", 18f))

        codeInput = EditText(this).apply {
            hint = "كود الاشتراك"
            textSize = 20f
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFAAAAAA.toInt())
            setPadding(24, 18, 24, 18)
        }
        root.addView(codeInput, LinearLayout.LayoutParams(-1, 70).apply { setMargins(0, 24, 0, 14) })

        activateButton = Button(this).apply {
            text = "تفعيل الاشتراك"
            textSize = 17f
            isAllCaps = false
        }
        root.addView(activateButton, LinearLayout.LayoutParams(-1, 60))

        message = text("", 15f)
        root.addView(message)
        setContentView(root)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val id = ensureDevice()
                val state = Api.license(this@MainActivity, id)
                val active = state.optJSONObject("subscription")?.optBoolean("active") == true
                withContext(Dispatchers.Main) {
                    message.text = if (active) "الاشتراك فعال" else "أدخل كود الاشتراك للمتابعة"
                    if (active) openChannels()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { message.text = "تعذر الاتصال بالخادم" }
            }
        }

        activateButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isEmpty()) {
                message.text = "اكتب كود الاشتراك أولاً"
                return@setOnClickListener
            }
            activateButton.isEnabled = false
            message.text = "جاري التفعيل..."
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val id = ensureDevice()
                    Api.activate(this@MainActivity, code, id)
                    val state = Api.license(this@MainActivity, id)
                    val active = state.optJSONObject("subscription")?.optBoolean("active") == true
                    if (!active) throw Exception("الكود غير فعال أو منتهي")
                    withContext(Dispatchers.Main) { openChannels() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        message.text = e.message ?: "فشل التفعيل"
                        activateButton.isEnabled = true
                    }
                }
            }
        }
    }

    private suspend fun ensureDevice(): Long = withContext(Dispatchers.IO) {
        val saved = prefs.getLong("server_device_id", 0L)
        if (saved > 0L) return@withContext saved
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val key = prefs.getString("device_key", null) ?:
            ("android-$androidId-${UUID.randomUUID()}").also { prefs.edit().putString("device_key", it).apply() }
        Api.registerDevice(this@MainActivity, key).also { prefs.edit().putLong("server_device_id", it).apply() }
    }

    private fun openChannels() {
        startActivity(Intent(this, ChannelsActivity::class.java))
        finish()
    }
}
