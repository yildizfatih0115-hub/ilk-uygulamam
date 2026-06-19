package com.jarvis.ai.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.jarvis.ai.utils.AppLauncher
import com.jarvis.ai.utils.ContactHelper

class CommandProcessor(private val context: Context) {

    private val appLauncher = AppLauncher(context)
    private val contactHelper = ContactHelper(context)

    // Bilinen komut kalıpları
    private val appKeywords = mapOf(
        listOf("youtube", "yt") to "com.google.android.youtube",
        listOf("whatsapp", "watsap") to "com.whatsapp",
        listOf("instagram", "ınstagram") to "com.instagram.android",
        listOf("spotify", "müzik") to "com.spotify.music",
        listOf("tarayıcı", "chrome", "internet") to "com.android.chrome",
        listOf("kamera") to "android.media.action.IMAGE_CAPTURE",
        listOf("galeri", "fotoğraflar") to "com.google.android.apps.photos",
        listOf("harita", "maps", "yol tarifi") to "com.google.android.apps.maps",
        listOf("ayarlar") to "com.android.settings",
        listOf("hesap makinesi") to "com.google.android.calculator",
        listOf("takvim") to "com.google.android.calendar",
        listOf("gmail", "e-posta") to "com.google.android.gm",
        listOf("telegram") to "org.telegram.messenger",
        listOf("netflix") to "com.netflix.mediaclient",
        listOf("twitter", "x") to "com.twitter.android",
        listOf("tiktok", "tik tok") to "com.zhiliaoapp.musically"
    )

    /**
     * Komutu işle — yerel çözüm varsa döndür, yoksa null (Gemini'ye git)
     */
    fun process(command: String): String? {
        val lower = command.lowercase().trim()

        // ─── UYGULAMA AÇ ───
        if (lower.contains("aç") || lower.contains("başlat") || lower.contains("open")) {
            val app = findAppInCommand(lower)
            if (app != null) {
                return appLauncher.launch(app.first, app.second)
            }
        }

        // ─── ARA / CALL ───
        if (lower.contains("ara") || lower.contains("ara ") || lower.contains("çağır")) {
            // "anneyi ara", "ahmet'i ara", "05.. numarasını ara"
            val number = extractPhoneNumber(lower)
            if (number != null) {
                return makeCall(number)
            }
            val contactName = extractContactName(lower)
            if (contactName != null) {
                val number2 = contactHelper.findNumber(contactName)
                return if (number2 != null) makeCall(number2)
                       else "\"$contactName\" rehberde bulunamadı efendim."
            }
        }

        // ─── WHATSAPP MESAJ ───
        if (lower.contains("whatsapp") && (lower.contains("mesaj") || lower.contains("yaz") || lower.contains("gönder"))) {
            return handleWhatsAppMessage(lower)
        }

        // ─── ZAMAN / SAAT ───
        if (lower.contains("saat kaç") || lower.contains("saat nedir") || lower.contains("zaman")) {
            val now = java.util.Calendar.getInstance()
            val h = now.get(java.util.Calendar.HOUR_OF_DAY)
            val m = now.get(java.util.Calendar.MINUTE)
            return "Şu an saat $h:${String.format("%02d", m)} efendim."
        }

        // ─── TARİH ───
        if (lower.contains("bugün") && (lower.contains("tarih") || lower.contains("gün") || lower.contains("ne zaman"))) {
            val sdf = java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale("tr", "TR"))
            return "Bugün ${sdf.format(java.util.Date())} efendim."
        }

        // ─── MERHABA ───
        if (lower == "merhaba" || lower == "hey jarvis" || lower == "selam") {
            val greetings = listOf(
                "Merhaba efendim. Nasıl yardımcı olabilirim?",
                "İyi günler efendim. Emirlerinizi bekliyorum.",
                "Sizi bekliyordum efendim. Nasıl yardımcı olayım?"
            )
            return greetings.random()
        }

        // ─── KAPAT ───
        if (lower.contains("güle güle") || lower.contains("kapat") || lower.contains("hoşça kal")) {
            return "İyi günler efendim. Gerektiğinde buradayım."
        }

        // ─── GİBBERLİNK ───
        if (lower.contains("gibberlink") || lower.contains("şifreli mod") || lower.contains("güvenli iletişim")) {
            return "GibberLink modu başlatılıyor efendim. Frekans şifreleme protokolü hazır."
        }

        // ─── PİL ───
        if (lower.contains("pil") || lower.contains("batarya") || lower.contains("şarj")) {
            val batteryLevel = getBatteryLevel()
            return "Pil seviyesi %$batteryLevel efendim."
        }

        return null // Gemini'ye gönder
    }

    private fun findAppInCommand(command: String): Pair<String, String>? {
        for ((keywords, packageName) in appKeywords) {
            for (keyword in keywords) {
                if (command.contains(keyword)) {
                    return Pair(packageName, keyword)
                }
            }
        }
        return null
    }

    private fun extractPhoneNumber(command: String): String? {
        val phoneRegex = Regex("\\b(0?[5][0-9]{9}|\\+90[5][0-9]{9})\\b")
        return phoneRegex.find(command)?.value
    }

    private fun extractContactName(command: String): String? {
        // "ahmet'i ara", "ayşe'yi ara" kalıpları
        val patterns = listOf(
            Regex("([a-züğışöçÜĞİŞÖÇ]+)[''`]?[ıiuü] ara"),
            Regex("([a-züğışöçÜĞİŞÖÇ]+)[''`]?[yı] ara"),
            Regex("ara ([a-züğışöçÜĞİŞÖÇ]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(command)
            if (match != null) return match.groupValues[1].replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun makeCall(number: String): String {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return "$number aranıyor efendim."
    }

    private fun handleWhatsAppMessage(command: String): String {
        // "ahmet'e whatsapp mesajı gönder: merhaba"
        val namePattern = Regex("([a-züğışöçÜĞİŞÖÇ]+)[''`]?[ae] ")
        val msgPattern = Regex("[:;] (.+)$")

        val name = namePattern.find(command)?.groupValues?.get(1)
        val message = msgPattern.find(command)?.groupValues?.get(1) ?: ""

        return if (name != null) {
            val number = contactHelper.findNumber(name)
            if (number != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(message)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                "$name'e WhatsApp mesajı gönderiliyor efendim."
            } else {
                "$name rehberde bulunamadı efendim."
            }
        } else {
            "Kime mesaj gönderilecek efendim?"
        }
    }

    private fun getBatteryLevel(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        return manager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
