package com.hichrawi.tv

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * HICHRAWI LIVE data layer.
 *
 * The Android app now talks directly to the HICHRAWI Firebase project.
 * No VPS/PHP API is required for subscriptions, channels or playback.
 */
object Api {
    data class Channel(val id: Long, val name: String, val logoUrl: String?, val sortOrder: Int, val streamUrl: String? = null)
    data class Package(val id: Long, val name: String, val channelIds: List<Long>)

    @Volatile private var initialized = false

    private fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            initialized = FirebaseApp.getApps(context).isNotEmpty()
            if (!initialized) throw IllegalStateException("Firebase initialization failed")
        }
    }

    private fun db(context: Context): FirebaseFirestore {
        init(context)
        return FirebaseFirestore.getInstance()
    }

    private fun auth(context: Context): FirebaseAuth {
        init(context)
        return FirebaseAuth.getInstance()
    }

    private fun ensureAnonymousAuth(context: Context) {
        val a = auth(context)
        if (a.currentUser == null) {
            Tasks.await(a.signInAnonymously(), 20, TimeUnit.SECONDS)
        }
    }

    private fun deviceKey(context: Context): String = AppConfig.deviceKey(context)

    fun registerDevice(context: Context, deviceKey: String): Long {
        // Device records are now created/updated only by trusted Cloud Functions.
        // The Android client only authenticates anonymously here and returns the
        // same stable legacy ID used by the rest of the existing app.
        ensureAnonymousAuth(context)
        return stableDeviceId(deviceKey)
    }

    fun activate(context: Context, code: String, deviceId: Long): org.json.JSONObject {
        ensureAnonymousAuth(context)
        val clean = code.trim().replace(" ", "").replace("-", "")
        if (!Regex("[A-Za-z0-9]{13}").matches(clean)) throw Exception("كود الاشتراك يجب أن يكون 13 خانة")
        val key = deviceKey(context)
        val subId = sha256(clean)
        val licenseId = sha256(key)
        val firestore = db(context)
        val subRef = firestore.collection("subscriptions").document(subId)
        val licenseRef = firestore.collection("licenses").document(licenseId)
        val uid = auth(context).currentUser?.uid ?: throw Exception("تعذر تسجيل الدخول")
        val result = Tasks.await(firestore.runTransaction { tx ->
            val subSnap = tx.get(subRef)
            val licenseSnap = tx.get(licenseRef)
            if (!subSnap.exists()) throw Exception("كود غير صحيح أو معطل")
            val d = subSnap.data ?: emptyMap<String, Any>()
            val active = d["active"] == true
            val status = d["status"]?.toString()?.lowercase() ?: ""
            val maxDevices = (d["maxDevices"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
            val devices = (d["deviceIds"] as? List<*>)?.mapNotNull { it?.toString() }?.toMutableList() ?: mutableListOf()
            val alreadyLinked = devices.contains(key)
            if (status !in listOf("ready", "unused", "active")) throw Exception("كود غير صحيح أو معطل")
            if (alreadyLinked && licenseSnap.exists() && licenseSnap.data?.get("authUid")?.toString() == uid) {
                return@runTransaction mapOf("ok" to true, "durationDays" to ((d["durationDays"] as? Number)?.toLong() ?: 30L), "subscriptionId" to subId)
            }
            if (!alreadyLinked && devices.size >= maxDevices) throw Exception("تم بلوغ الحد الأقصى للأجهزة")
            val durationDays = (d["durationDays"] as? Number)?.toLong()?.coerceIn(1L, 3650L) ?: 30L
            val firstActivation = !active && status in listOf("ready", "unused")
            if (!firstActivation && !(active && status == "active")) throw Exception("كود غير صحيح أو معطل")
            if (!alreadyLinked) devices.add(key)
            val subscriptionActivatedAt: Any = if (firstActivation) FieldValue.serverTimestamp() else (d["activatedAt"] ?: FieldValue.serverTimestamp())
            val licenseActivatedAt: Any = FieldValue.serverTimestamp()
            tx.update(subRef, mapOf(
                "active" to true,
                "status" to "active",
                "deviceIds" to devices,
                "activatedAt" to subscriptionActivatedAt,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(licenseRef, mapOf(
                "authUid" to uid,
                "deviceKey" to key,
                "subscriptionId" to subId,
                "activatedAt" to licenseActivatedAt,
                "durationDays" to durationDays,
                "active" to true
            ))
            mapOf("ok" to true, "durationDays" to durationDays, "subscriptionId" to subId)
        })
        return mapToJson(result)
    }

    fun license(context: Context, deviceId: Long): org.json.JSONObject {
        ensureAnonymousAuth(context)
        val key = deviceKey(context)
        val uid = auth(context).currentUser?.uid ?: return inactiveLicense()
        val licenseId = sha256(key)
        val snap = Tasks.await(db(context).collection("licenses").document(licenseId).get(), 20, TimeUnit.SECONDS)
        if (!snap.exists()) return inactiveLicense()
        val d = snap.data ?: return inactiveLicense()
        if (d["authUid"]?.toString() != uid || d["deviceKey"]?.toString() != key) return inactiveLicense()
        val active = d["active"] == true
        val activatedAt = d["activatedAt"] as? com.google.firebase.Timestamp
        val days = (d["durationDays"] as? Number)?.toLong() ?: 30L
        val expiresAt = activatedAt?.toDate()?.time?.plus(days.coerceAtLeast(1L) * 86400000L)
        val stillActive = active && (expiresAt == null || expiresAt > System.currentTimeMillis())
        return org.json.JSONObject().put("ok", true)
            .put("license", org.json.JSONObject().put("status", if (stillActive) "active" else "inactive")
                .put("activated_at", activatedAt?.toDate()?.let { iso(it) } ?: "")
                .put("expires_at", expiresAt?.let { iso(Date(it)) } ?: ""))
            .put("subscription", org.json.JSONObject().put("active", stillActive))
    }

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun channels(context: Context, deviceId: Long): List<Channel> {
        ensureAnonymousAuth(context)
        val snap = Tasks.await(db(context).collection("channels").get(), 20, TimeUnit.SECONDS)
        return snap.documents.mapIndexedNotNull { index, doc ->
            val d = doc.data ?: return@mapIndexedNotNull null
            if (d["enabled"] == false) return@mapIndexedNotNull null
            val id = number(d["channelId"] ?: d["id"]) ?: stableChannelId(doc.id)
            val name = d["name"]?.toString()?.ifBlank { "Channel" } ?: "Channel"
            val logo = (d["logoUrl"] ?: d["logo"])?.toString()?.takeIf { it.isNotBlank() }
            val stream = (d["streamUrl"] ?: d["stream"] ?: d["url"])?.toString()?.takeIf { it.isNotBlank() }
            val sort = number(d["sortOrder"] ?: d["sort_order"])?.toInt() ?: index
            Channel(id, name, logo, sort, stream)
        }.sortedWith(compareBy<Channel> { it.sortOrder }.thenBy { it.id })
    }

    fun playback(context: Context, deviceId: Long, channelId: Long): String {
        ensureAnonymousAuth(context)
        val channels = channels(context, deviceId)
        val ch = channels.firstOrNull { it.id == channelId } ?: throw Exception("القناة غير موجودة")
        return ch.streamUrl?.takeIf { it.isNotBlank() } ?: throw Exception("رابط البث غير متوفر")
    }

    fun packages(context: Context, deviceId: Long, channels: List<Channel>): List<Package> {
        ensureAnonymousAuth(context)
        return try {
            val snap = Tasks.await(db(context).collection("packages").get(), 20, TimeUnit.SECONDS)
            val result = snap.documents.mapIndexedNotNull { index, doc ->
                val d = doc.data ?: return@mapIndexedNotNull null
                val ids = (d["channelIds"] as? List<*>)?.mapNotNull { number(it)?.toLong() }.orEmpty()
                Package(number(d["packageId"] ?: d["id"]) ?: stableChannelId(doc.id), d["name"]?.toString() ?: "الباقة", ids)
            }
            if (result.isEmpty()) fallbackPackages(channels) else result
        } catch (_: Exception) { fallbackPackages(channels) }
    }

    private fun fallbackPackages(channels: List<Channel>): List<Package> {
        val sports = channels.filter { it.name.contains("sport", true) || it.name.contains("سبورت", true) || it.name.contains("رياض", true) }
        val result = mutableListOf<Package>()
        if (sports.isNotEmpty()) result += Package(1, "الباقة الرياضية", sports.map { it.id })
        if (channels.isNotEmpty()) result += Package(2, "الباقة الكاملة", channels.map { it.id })
        return result
    }

    private fun mapToJson(value: Map<*, *>): org.json.JSONObject {
        val out = org.json.JSONObject()
        for ((k, v) in value) {
            if (k == null) continue
            out.put(k.toString(), when (v) {
                is Map<*, *> -> mapToJson(v)
                is List<*> -> org.json.JSONArray(v)
                else -> v
            })
        }
        return out
    }

    private fun inactiveLicense() = org.json.JSONObject().put("ok", true)
        .put("license", org.json.JSONObject().put("status", "inactive"))
        .put("subscription", org.json.JSONObject().put("active", false))

    private fun number(v: Any?): Number? = when (v) {
        is Number -> v
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()
        else -> null
    }

    private fun stableDeviceId(value: String): Long = (value.hashCode().toLong() and 0x7fffffffL).coerceAtLeast(1L)
    private fun stableChannelId(value: String): Long = (value.hashCode().toLong() and 0x7fffffffL).coerceAtLeast(1L)

    private fun iso(date: Date?): String = date?.let {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(it)
    } ?: ""
}
