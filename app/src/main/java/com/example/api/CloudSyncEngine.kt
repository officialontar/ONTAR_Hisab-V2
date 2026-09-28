package com.example.api

import android.util.Log
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import android.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

data class SyncPayload(
    val user: User,
    val stockItems: List<StockItem> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val dealers: List<Dealer> = emptyList(),
    val transactions: List<TransactionRecord> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val registrationTimestamp: Long? = null,
    val additionalShops: List<User> = emptyList()
)

object CloudSyncEngine {
    private const val TAG = "CloudSyncEngine"
    private var dynamicBaseUrl: String = "https://ontar-hisab-eb1ea-default-rtdb.firebaseio.com/"

    fun initialize(url: String) {
        val clean = url.trim()
        if (clean.isNotBlank()) {
            dynamicBaseUrl = if (clean.endsWith("/")) clean else "$clean/"
        }
    }

    private fun getBaseUrl(): String {
        return dynamicBaseUrl
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        })
        .build()

    private fun getSanitizedKey(email: String): String {
        return "user_" + email.lowercase()
            .trim()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    private fun getSanitizedRedirectKey(identifier: String): String {
        return "redirect_" + identifier.lowercase()
            .trim()
            .replace("@", "_at_")
            .replace(".", "_dot_")
            .replace("-", "")
            .replace(" ", "")
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    private fun compress(str: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzos ->
            gzos.write(str.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun decompress(compressedStr: String): String {
        val bytes = Base64.decode(compressedStr, Base64.NO_WRAP)
        val bis = ByteArrayInputStream(bytes)
        val gis = GZIPInputStream(bis)
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        while (gis.read(buffer).also { len = it } > 0) {
            bos.write(buffer, 0, len)
        }
        return bos.toString("UTF-8")
    }

    private fun fetchPayloadByUrl(url: String): SyncPayload? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBody = response.body?.string() ?: return null
                    val cleanRaw = rawBody.trim()
                    if (cleanRaw.isEmpty() || cleanRaw == "null") return null
                    
                    val jsonToParse = if (cleanRaw.startsWith("{") || cleanRaw.startsWith("[")) {
                        cleanRaw
                    } else {
                        val unquoted = if (cleanRaw.startsWith("\"") && cleanRaw.endsWith("\"") && cleanRaw.length >= 2) {
                            cleanRaw.substring(1, cleanRaw.length - 1)
                                .replace("\\\\", "\\")
                                .replace("\\\"", "\"")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                        } else {
                            cleanRaw
                        }
                        try {
                            decompress(unquoted)
                        } catch (e: Exception) {
                            Log.d(TAG, "Gzip decompression notice, falling back to raw body: ${e.message}")
                            unquoted
                        }
                    }
                    payloadAdapter.fromJson(jsonToParse)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Network notice fetching payload directly: ${e.message}")
            null
        }
    }

    private fun fetchStringByUrl(url: String): String? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string()?.trim() ?: return null
                    if (raw == "null" || raw.isEmpty()) return null
                    if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
                        raw.substring(1, raw.length - 1)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                    } else {
                        raw
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Network notice fetching string directly: ${e.message}")
            null
        }
    }

    /**
     * Upload payload to Firebase cloud store
     */
    suspend fun uploadPayload(email: String, payload: SyncPayload): Boolean {
        if (email.isBlank()) return false
        val key = getSanitizedKey(email)
        val url = "${getBaseUrl()}users/$key.json"

        val uploadDirectSuccess = try {
            val json = payloadAdapter.toJson(payload)
            val compressedPayload = try {
                compress(json)
            } catch (e: Exception) {
                Log.d(TAG, "Gzip compression notice, falling back to raw json: ${e.message}")
                json
            }

            // Wrap as a safe and valid JSON string format for Realtime Database
            val escaped = compressedPayload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val jsonString = "\"$escaped\""
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded sync payload successfully to Firebase for $email (Compressed: ${compressedPayload.length} chars, Original: ${json.length} chars)")
                    true
                } else {
                    Log.d(TAG, "Notice upload sync payload response code: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice uploading sync payload to Firebase: ${e.message}")
            false
        }

        if (uploadDirectSuccess) {
            try {
                uploadRedirectsForUser(payload.user)
            } catch (e: Exception) {
                Log.d(TAG, "Notice matching redirects: ${e.message}")
            }
        }

        return uploadDirectSuccess
    }

    /**
     * Upload redirects to resolve phones and names back to primary email
     */
    private fun uploadRedirectsForUser(user: User) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val primaryEmail = user.email.trim().lowercase()
            val redirectIdentifiers = mutableSetOf<String>()

            // Add primary phones
            user.phone.split(",").forEach {
                val clean = it.trim().lowercase()
                if (clean.isNotEmpty() && clean != primaryEmail) {
                    redirectIdentifiers.add(clean)
                    val ultraClean = clean.replace("-", "").replace(" ", "").replace("+", "")
                    if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                        redirectIdentifiers.add(ultraClean)
                        if (ultraClean.length >= 11) {
                            redirectIdentifiers.add(ultraClean.takeLast(11))
                        }
                    }
                }
            }

            // Add joint owners
            try {
                val owners = com.example.data.OwnerParser.deserialize(user.ownerName, user.phone, user.email)
                owners.forEach { owner ->
                    val oPhone = owner.phone.trim().lowercase()
                    val oEmail = owner.email.trim().lowercase()
                    if (oPhone.isNotEmpty() && oPhone != primaryEmail) {
                        redirectIdentifiers.add(oPhone)
                        val ultraClean = oPhone.replace("-", "").replace(" ", "").replace("+", "")
                        if (ultraClean.isNotEmpty() && ultraClean != primaryEmail) {
                            redirectIdentifiers.add(ultraClean)
                            if (ultraClean.length >= 11) {
                                redirectIdentifiers.add(ultraClean.takeLast(11))
                            }
                        }
                    }
                    if (oEmail.isNotEmpty() && oEmail != primaryEmail) {
                        redirectIdentifiers.add(oEmail)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Notice parsing owner names for redirect upload: ${e.message}")
            }

            // Upload redirect keys to Firebase in parallel/asynchronously
            redirectIdentifiers.forEach { id ->
                val redirectKey = getSanitizedRedirectKey(id)
                val url = "${getBaseUrl()}users/$redirectKey.json"
                try {
                    val escapedEmail = primaryEmail.replace("\\", "\\\\").replace("\"", "\\\"")
                    val jsonString = "\"$escapedEmail\""
                    val requestBody = jsonString.toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .put(requestBody)
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d(TAG, "Uploaded user mapping redirect for $id -> $primaryEmail")
                        } else {
                            Log.d(TAG, "Notice upload redirect for $id, code ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Notice saving redirect for $id: ${e.message}")
                }
            }
        }
    }

    /**
     * Download payload from Firebase cloud store with redirection support
     */
    suspend fun downloadPayload(email: String): SyncPayload? = coroutineScope {
        if (email.isBlank()) return@coroutineScope null
        val trimmedRaw = email.trim().lowercase()

        // 1. Direct lookup
        val directKey = getSanitizedKey(trimmedRaw)
        val directUrl = "${getBaseUrl()}users/$directKey.json"
        val directResult = fetchPayloadByUrl(directUrl)
        if (directResult != null) return@coroutineScope directResult

        // 2. If it was already a valid email, skip phone redirect lookups
        if (trimmedRaw.contains("@")) return@coroutineScope null

        // 3. Try redirect key mapping (for phone number or custom identifier login)
        val redirectKey = getSanitizedRedirectKey(trimmedRaw)
        val redirectUrl = "${getBaseUrl()}users/$redirectKey.json"
        val redirectedEmail = fetchStringByUrl(redirectUrl)
        if (!redirectedEmail.isNullOrBlank()) {
            val resolvedUrl = "${getBaseUrl()}users/${getSanitizedKey(redirectedEmail)}.json"
            val redirectedResult = fetchPayloadByUrl(resolvedUrl)
            if (redirectedResult != null) return@coroutineScope redirectedResult
        }

        // 4. Try normalizing raw identifier to 11-digit mobile number if applicable
        val ultraCleanRaw = trimmedRaw.replace("-", "").replace(" ", "").replace("+", "")
        if (ultraCleanRaw.length >= 11) {
            val shortPhone = ultraCleanRaw.takeLast(11)
            val shortRedirectKey = getSanitizedRedirectKey(shortPhone)
            val shortRedirectUrl = "${getBaseUrl()}users/$shortRedirectKey.json"
            val shortRedirectedEmail = fetchStringByUrl(shortRedirectUrl)
            if (!shortRedirectedEmail.isNullOrBlank()) {
                val resolvedUrl = "${getBaseUrl()}users/${getSanitizedKey(shortRedirectedEmail)}.json"
                val phoneResult = fetchPayloadByUrl(resolvedUrl)
                if (phoneResult != null) return@coroutineScope phoneResult
            }
        }

        null
    }

    /**
     * Fetch all registered payloads from Firebase in a single concurrent pass
     */
    suspend fun fetchAllRegisteredPayloads(): List<SyncPayload> = coroutineScope {
        val list = java.util.Collections.synchronizedList(mutableListOf<SyncPayload>())
        try {
            val url = "${getBaseUrl()}users.json?shallow=true"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val raw = response.body?.string() ?: return@coroutineScope emptyList()
                    if (raw == "null" || raw.trim().isEmpty()) return@coroutineScope emptyList()
                    
                    // Parse keys from JSON object like {"user_foo": true, "user_bar": true}
                    val regex = Regex("\"(user_[a-zA-Z0-9_]+)\"")
                    val keys = regex.findAll(raw).map { it.groupValues[1] }.toList().distinct()
                    
                    val deferreds = keys.map { key ->
                        async(Dispatchers.IO) {
                            try {
                                val userPayloadUrl = "${getBaseUrl()}users/$key.json"
                                val payload = fetchPayloadByUrl(userPayloadUrl)
                                if (payload != null) {
                                    list.add(payload)
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Notice parsing user key data: $key: ${e.message}")
                            }
                        }
                    }
                    deferreds.awaitAll()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice listing keys from Firebase shallow option: ${e.message}")
        }
        list.toList()
    }

    /**
     * Fetch all registered users
     */
    suspend fun fetchAllRegisteredUsers(): List<User> {
        return fetchAllRegisteredPayloads().map { it.user }
    }

    /**
     * Delete user payload from Firebase
     */
    suspend fun deletePayload(email: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val key = getSanitizedKey(email)
            val url = "${getBaseUrl()}users/$key.json"
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice deleting sync payload from Firebase: ${e.message}")
            false
        }
    }

    /**
     * Upload an individual image to its own key on Firebase to avoid bloating the main payload
     */
    suspend fun uploadIndividualImage(imageKey: String, base64Data: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (imageKey.isBlank() || base64Data.isBlank()) return@withContext false
        val url = "${getBaseUrl()}images/$imageKey.json"
        try {
            val escaped = base64Data
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            val jsonString = "\"$escaped\""
            
            val requestBody = jsonString.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .put(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Uploaded individual image successfully to Firebase for key: $imageKey")
                    true
                } else {
                    Log.d(TAG, "Notice upload individual image to Firebase code: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice uploading individual image to Firebase: $imageKey: ${e.message}")
            false
        }
    }

    /**
     * Download an individual image base64 resource from its own key on Firebase
     */
    suspend fun downloadIndividualImage(imageKey: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (imageKey.isBlank()) return@withContext null
        val url = "${getBaseUrl()}images/$imageKey.json"
        try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawStr = response.body?.string()?.trim() ?: return@withContext null
                    if (rawStr == "null" || rawStr.isEmpty()) return@withContext null
                    if (rawStr.startsWith("\"") && rawStr.endsWith("\"") && rawStr.length >= 2) {
                        rawStr.substring(1, rawStr.length - 1)
                            .replace("\\\\", "\\")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                    } else {
                        rawStr
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice downloading individual image from Firebase: $imageKey: ${e.message}")
            null
        }
    }

    /**
     * Delete user mapping redirect for clean update of logins
     */
    suspend fun deleteRedirect(id: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (id.isBlank()) return@withContext false
        val redirectKey = getSanitizedRedirectKey(id)
        val url = "${getBaseUrl()}users/$redirectKey.json"
        try {
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Deleted user mapping redirect for $id")
                    true
                } else {
                    Log.d(TAG, "Notice delete redirect for $id, code ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Notice deleting user mapping redirect for $id: ${e.message}")
            false
        }
    }
}
