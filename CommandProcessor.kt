package com.jarvis.aioverlay

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CommandProcessor(
    private val context: Context,
    private val groqClient: GroqClient,
    private val taskManager: TaskManager,
    private val weatherApiKey: String,
    private val city: String,
    private val userName: String,
    private val onResponse: (String) -> Unit
) {

    private val conversationHistory = mutableListOf<Map<String, String>>()

    suspend fun process(input: String) {
        val lower = input.lowercase(Locale("tr"))

        when {
            // UYAN komutu - tam durum raporu
            lower.contains("uyan") || lower.contains("dur raporu") || lower.contains("durum raporu") -> {
                handleWakeUp()
            }

            // SAAT
            lower.contains("saat kaç") || lower.contains("saat") && lower.contains("kaç") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                onResponse("Saat $time, $userName.")
            }

            // TARİH
            lower.contains("tarih") || lower.contains("bugün ne") || lower.contains("hangi gün") -> {
                val date = SimpleDateFormat("d MMMM yyyy, EEEE", Locale("tr")).format(Date())
                onResponse("Bugün $date.")
            }

            // HAVA DURUMU
            lower.contains("hava") -> {
                handleWeather()
            }

            // PİL
            lower.contains("pil") || lower.contains("batarya") || lower.contains("şarj") -> {
                val battery = getBatteryLevel()
                onResponse("Pil durumu yüzde $battery, $userName.")
            }

            // GÖREV EKLE
            lower.contains("görev ekle") || lower.contains("not al") || lower.contains("kaydet") -> {
                val task = extractAfterKeyword(input, listOf("görev ekle", "not al", "kaydet"))
                if (task.isNotEmpty()) {
                    taskManager.addTask(task)
                    onResponse("Görev kaydedildi: $task")
                } else {
                    onResponse("Hangi görevi ekleyeyim, $userName?")
                }
            }

            // GÖREVLERİ LİSTELE
            lower.contains("görev") && (lower.contains("listele") || lower.contains("oku") || lower.contains("neler") || lower.contains("var mı")) -> {
                val tasks = taskManager.getTasks()
                if (tasks.isEmpty()) {
                    onResponse("Görev listeniz boş, $userName.")
                } else {
                    onResponse("Görevleriniz: ${tasks.joinToString(". ")}. Toplam ${tasks.size} görev.")
                }
            }

            // GÖREV SİL
            lower.contains("görevi sil") || lower.contains("görevi tamamla") || lower.contains("görevi kaldır") -> {
                val task = extractAfterKeyword(input, listOf("görevi sil", "görevi tamamla", "görevi kaldır"))
                if (task.isNotEmpty()) {
                    val removed = taskManager.removeTask(task)
                    onResponse(if (removed) "Görev silindi: $task" else "O görevi bulamadım, $userName.")
                } else {
                    onResponse("Hangi görevi silmemi istiyorsunuz?")
                }
            }

            // HATIRLATICI
            lower.contains("hatırlat") || lower.contains("alarm") || lower.contains("bildir") -> {
                handleReminder(input, lower)
            }

            // MESAJ GÖNDER
            lower.contains("mesaj gönder") || lower.contains("sms gönder") -> {
                handleSms(input)
            }

            // JARVIS KAPAT
            lower.contains("kapat") || lower.contains("dur") || lower.contains("devre dışı") -> {
                onResponse("Sistemler kapatılıyor. Güle güle $userName.")
                context.stopService(Intent(context, JarvisService::class.java))
            }

            // DİĞER HER ŞEY → GROQ AI
            else -> {
                handleAI(input)
            }
        }
    }

    private suspend fun handleWakeUp() {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("d MMMM, EEEE", Locale("tr")).format(Date())
        val battery = getBatteryLevel()
        val tasks = taskManager.getTasks()
        val taskInfo = if (tasks.isEmpty()) "Görev listeniz temiz." else "${tasks.size} bekleyen göreviniz var."

        var weatherInfo = ""
        if (weatherApiKey.isNotEmpty()) {
            weatherInfo = try {
                val weather = WeatherClient.getWeather(city, weatherApiKey)
                "Hava durumu: $weather. "
            } catch (e: Exception) { "" }
        }

        val report = "Günaydın $userName. Saat $time, $date. " +
                "${weatherInfo}" +
                "Pil durumu yüzde $battery. " +
                "$taskInfo " +
                "Sistemler nominal. Emrinize amadeyim."
        onResponse(report)
    }

    private suspend fun handleWeather() {
        if (weatherApiKey.isEmpty()) {
            onResponse("Hava durumu için API key girilmemiş, $userName. Lütfen ayarlardan OpenWeatherMap key ekleyin.")
            return
        }
        onResponse("Hava durumu kontrol ediliyor...")
        val weather = withContext(Dispatchers.IO) {
            try { WeatherClient.getWeather(city, weatherApiKey) }
            catch (e: Exception) { null }
        }
        if (weather != null) {
            onResponse("$city için hava durumu: $weather")
        } else {
            onResponse("Hava durumu alınamadı, $userName. İnternet bağlantısını kontrol edin.")
        }
    }

    private fun handleReminder(input: String, lower: String) {
        // "10 dakika sonra hatırlat: toplantı" gibi komutlar
        val minuteRegex = Regex("(\\d+)\\s*dakika")
        val match = minuteRegex.find(lower)
        val task = extractAfterKeyword(input, listOf("hatırlat:", "hatırlatıcı:", "sonra"))
            .trim().trimStart(':').trim()

        if (match != null && task.isNotEmpty()) {
            val minutes = match.groupValues[1].toLong()
            scheduleReminder(task, minutes)
            onResponse("${minutes} dakika sonra hatırlatacağım: $task")
        } else {
            onResponse("Kaç dakika sonra ne hatırlatmamı istiyorsunuz, $userName?")
        }
    }

    private fun scheduleReminder(task: String, minutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("task", task)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, task.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = SystemClock.elapsedRealtime() + minutes * 60 * 1000
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pendingIntent)
    }

    private fun handleSms(input: String) {
        // "Ahmet'e mesaj gönder: geç kalıyorum" formatı
        val toRegex = Regex("(.+?)['e]?[']?e mesaj gönder", RegexOption.IGNORE_CASE)
        val match = toRegex.find(input)
        val recipient = match?.groupValues?.get(1)?.trim() ?: ""
        val message = extractAfterKeyword(input, listOf(":", "mesaj gönder")).trim()

        if (recipient.isNotEmpty() && message.isNotEmpty()) {
            // SMS uygulamasını aç (direkt gönderim için numara gerekir)
            val smsIntent = Intent(Intent.ACTION_VIEW).apply {
                putExtra("address", recipient)
                putExtra("sms_body", message)
                type = "vnd.android-dir/mms-sms"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(smsIntent)
            onResponse("$recipient için mesaj hazırlandı: $message")
        } else {
            onResponse("Kime ve ne mesajı göndereceğimi anlayamadım, $userName.")
        }
    }

    private suspend fun handleAI(input: String) {
        onResponse("Anlıyorum...")
        val response = withContext(Dispatchers.IO) {
            try {
                conversationHistory.add(mapOf("role" to "user", "content" to input))
                val reply = groqClient.chat(conversationHistory, userName)
                conversationHistory.add(mapOf("role" to "assistant", "content" to reply))
                // Hafızayı sınırla (son 10 mesaj)
                if (conversationHistory.size > 20) {
                    conversationHistory.subList(0, conversationHistory.size - 20).clear()
                }
                reply
            } catch (e: Exception) {
                "Bağlantı kurulamadı, $userName. ${e.message}"
            }
        }
        onResponse(response)
    }

    private fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun extractAfterKeyword(input: String, keywords: List<String>): String {
        for (keyword in keywords) {
            val idx = input.lowercase().indexOf(keyword.lowercase())
            if (idx != -1) {
                return input.substring(idx + keyword.length).trim().trimStart(':').trim()
            }
        }
        return ""
    }
}
