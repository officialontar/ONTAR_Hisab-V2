package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val shopName: String,
    val phone: String,
    val passwordHash: String,
    val profilePicture: String? = null,
    val ownerName: String? = null,
    val shopPicture: String? = null,
    val ipAddress: String? = null,
    val registerLocation: String? = null,
    val registerDevice: String? = null,
    val activeDevicesJson: String? = null,
    val blockedDevicesJson: String? = null,
    val isBlocked: Boolean = false,
    val registrationTimestamp: Long? = null
) : Serializable {
    fun getLocalizedShopName(isBn: Boolean): String {
        if (shopName.isNotBlank()) return shopName
        return if (isBn) "আমার দোকান" else "My Shop"
    }

    fun getLocalizedOwnerName(isBn: Boolean): String {
        if (!ownerName.isNullOrBlank()) return ownerName
        return if (isBn) "দোকানের মালিক" else "Shop Owner"
    }
}

@Entity(tableName = "stock_items")
data class StockItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val purchasePrice: Double,
    val salesPrice: Double,
    val stockCount: Int,
    val category: String,
    val imageResName: String? = null,
    val unit: String = "পিস"
) : Serializable

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val phone: String,
    val address: String? = null,
    val totalDue: Double = 0.0,
    val photoUri: String? = null,
    val initialDetails: String? = null,
    val orderIndex: Int = 0
) : Serializable

@Entity(tableName = "dealers")
data class Dealer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val name: String,
    val phone: String,
    val company: String? = null,
    val totalOwed: Double = 0.0,
    val photoUri: String? = null,
    val initialDetails: String? = null,
    val orderIndex: Int = 0
) : Serializable

@Entity(tableName = "transactions")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val type: String, // "SALE", "EXPENSE", "CUSTOMER_DUE", "CUSTOMER_PAYMENT", "DEALER_PAYMENT"
    val amount: Double,
    val profit: Double = 0.0,
    val title: String,
    val description: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Int? = null,
    val dealerId: Int? = null
) : Serializable

data class OwnerInfo(
    val name: String,
    val phone: String,
    val email: String
) : Serializable

object MasterCustomerRegistry {
    // 59 customers exactly in the user's master Excel sheet order
    val masterList = listOf(
        "মাসুম বাবু" to "01785923475",
        "ফরহাদ হাসান" to "01785923476",
        "স্বপন ভাই" to "01749295808",
        "রুহুল আমিন" to "01719288751",
        "এফাজ চাচা" to "01721172117",
        "জামিরুল ভাই" to "01781204787",
        "শাহজালাল বন্ধু" to "01354842813",
        "মানিক চা" to "20",
        "ভিক্ষু ভাগ্নে" to "01319689115",
        "জাকির ভাই" to "01734366184",
        "মর্জিনা বেবি" to "01345026184",
        "জাহিদ বন্ধু হোস্টেল" to "01796803448",
        "নয়ন ভাই" to "01780871833",
        "রোকসানা ভাবি" to "01763143597",
        "সফিকুল ইসলাম" to "01756142983",
        "পল্টু ভাই" to "01763054818",
        "সুমন ভাই এফাজর ভাইস্তা" to "01761210179",
        "জানারুল চাচা" to "01798975717",
        "হামিদুল" to "01315347207",
        "বিটুল" to "01961310948",
        "হারুন মামা" to "01318130912",
        "হাসান ভাই" to "01753809894",
        "রাসেল" to "01301584848",
        "সনি ভাই" to "01738989931",
        "টিপু বিদেশি" to "01353966553",
        "ইসলাম মিয়া" to "0172",
        "জাহিদুল চা" to "30",
        "আপেল মেকার" to "01742337459",
        "শাহিন দক্ষিণপাড়া" to "60",
        "নজুরুল চাচা" to "66",
        "তোতা মাস্টার" to "90",
        "শিবলু হোস্টেল" to "33",
        "টিপু চাচা" to "88",
        "মোল্লা" to "00",
        "রফিকুল ভাই (জেটাই)" to "01111",
        "বাদল কেরানি" to "01725351325",
        "সাজু বন্ধু" to "01754161186",
        "মিঠু সরকার" to "01536",
        "জনি ভাই" to "01704018939",
        "জিহাদ পাটা" to "01783321692",
        "শাহিন দিঘাপাড়া গহীন বাধ" to "111111",
        "শিপলু হোটেল" to "5555",
        "রুমি আপা" to "1111111",
        "আশিদুল ভাই" to "6666666",
        "আব্দুল হাই গোইং" to "55555555",
        "শফিকুল আর্মি" to "523",
        "সাইদুল ইসলাম" to "",
        "আব্দুল্লাহ মায়ের বাকি" to "0253",
        "সিকান্দার হালেল" to "0624",
        "হেলাল ডাঃ" to "0178888",
        "শিপু মামা" to "09632",
        "মুঞ্জু ভাই" to "831",
        "মিল্লাত বাবু" to "01761173255",
        "মিঠু চা" to "0852",
        "যেটাই ঝলো পাগলী" to "",
        "ভুট্টা চা" to "",
        "কলু হাসান" to "",
        "খায়রুল ভাই কাশের মালা" to "",
        "বাইদের ব্যাটা মামা" to "",
        "লতিব  ( কুদ্দুস মেম্বারের ভাই )" to "",
        "বাবুদা মোহনের  ভাই" to "",
        "জামিরুল চর নৌকা" to ""
    )

    fun normalizeName(raw: String): String {
        return raw.trim().lowercase()
            .replace("\t", " ")
            .replace("  ", " ")
            .replace("  ", " ")
            .replace("(", "").replace(")", "")
            .replace("-", " ").replace("_", " ")
            .replace("  ", " ")
            .trim()
    }

    fun isSameCustomer(c1Name: String, c1Phone: String, c2Name: String, c2Phone: String): Boolean {
        val n1 = normalizeName(c1Name)
        val n2 = normalizeName(c2Name)
        if (n1.isNotEmpty() && n2.isNotEmpty() && n1 == n2) {
            return true
        }

        val cleanP1 = c1Phone.filter { it.isDigit() }
        val cleanP2 = c2Phone.filter { it.isDigit() }
        if (cleanP1.length >= 10 && cleanP2.length >= 10) {
            val phone1 = cleanP1.removePrefix("88")
            val phone2 = cleanP2.removePrefix("88")
            if (phone1 == phone2) {
                return true
            }
        }
        return false
    }

    fun isSameDealer(d1Name: String, d1Phone: String, d2Name: String, d2Phone: String): Boolean {
        val n1 = normalizeName(d1Name)
        val n2 = normalizeName(d2Name)
        if (n1.isNotEmpty() && n2.isNotEmpty() && n1 == n2) {
            return true
        }

        val cleanP1 = d1Phone.filter { it.isDigit() }
        val cleanP2 = d2Phone.filter { it.isDigit() }
        if (cleanP1.length >= 10 && cleanP2.length >= 10) {
            val phone1 = cleanP1.removePrefix("88")
            val phone2 = cleanP2.removePrefix("88")
            if (phone1 == phone2) {
                return true
            }
        }
        return false
    }

    fun getMasterOrder(name: String, phone: String = ""): Int? {
        val cleanName = name.trim().lowercase()
        val normName = normalizeName(name)
        val cleanPhone = phone.trim().replace("-", "").replace(" ", "").replace(".", "")
        
        // 1. Try matching exact name or normalized name
        val nameIdx = masterList.indexOfFirst {
            val mExact = it.first.trim().lowercase()
            val mNorm = normalizeName(it.first)
            mExact == cleanName || mNorm == normName
        }
        if (nameIdx != -1) return nameIdx + 1
        
        // 2. Try matching phone if phone is non-trivial (at least 6 chars)
        if (cleanPhone.length >= 6) {
            val phoneIdx = masterList.indexOfFirst {
                val masterPhone = it.second.trim().replace("-", "").replace(" ", "").replace(".", "")
                masterPhone.length >= 6 && (masterPhone == cleanPhone || cleanPhone.endsWith(masterPhone) || masterPhone.endsWith(cleanPhone))
            }
            if (phoneIdx != -1) return phoneIdx + 1
        }
        
        return null
    }
}

object OwnerParser {
    fun serialize(owners: List<OwnerInfo>): String {
        val array = org.json.JSONArray()
        owners.forEach { o ->
            val obj = org.json.JSONObject()
            obj.put("name", o.name)
            obj.put("phone", o.phone)
            obj.put("email", o.email)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserialize(ownerNameStr: String?, defaultPhone: String = "", defaultEmail: String = ""): List<OwnerInfo> {
        if (ownerNameStr != null && ownerNameStr.trim().startsWith("[") && ownerNameStr.trim().endsWith("]")) {
            try {
                val list = mutableListOf<OwnerInfo>()
                val array = org.json.JSONArray(ownerNameStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(OwnerInfo(
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        email = obj.optString("email", "")
                    ))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // fallback
            }
        }
        val safeName = ownerNameStr ?: ""
        return listOf(OwnerInfo(name = safeName, phone = defaultPhone, email = defaultEmail))
    }

    fun getFirstOwnerName(ownerNameStr: String?, defaultVal: String = ""): String {
        val list = deserialize(ownerNameStr)
        return list.firstOrNull()?.name?.ifBlank { defaultVal } ?: defaultVal
    }

    fun getFirstOwnerPhone(ownerNameStr: String?, defaultPhone: String = ""): String {
        val list = deserialize(ownerNameStr, defaultPhone)
        return list.firstOrNull()?.phone?.ifBlank { defaultPhone } ?: defaultPhone
    }

    fun getFormattedOwnersDetails(ownerNameStr: String?, defaultPhone: String = "", defaultEmail: String = ""): String {
        val list = deserialize(ownerNameStr, defaultPhone, defaultEmail)
        val sb = StringBuilder()
        list.forEachIndexed { index, owner ->
            if (index > 0) {
                sb.append("\n\n")
            }
            if (owner.name.isNotBlank()) {
                sb.append(owner.name)
            }
            if (owner.phone.isNotBlank()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                sb.append(owner.phone)
            }
            if (owner.email.isNotBlank()) {
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                sb.append(owner.email)
            }
        }
        return sb.toString()
    }
}

data class OtaConfig(
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0",
    val updateDownloadUrl: String = "https://ais-pre-wolkhdsxahnvgjlshvncw2-122144077257.asia-southeast1.run.app",
    val bengaliMessage: String = "আপনাদের সকল ডাটা ও ইমেজ লাইফটাইম ব্যাকআপ সম্পন্ন করা হয়েছে!",
    val englishMessage: String = "All your data and images are successfully backed up for lifetime!",
    val forceUpdateEnabled: Boolean = false,
    val freePremiumActive: Boolean = true
) : Serializable



