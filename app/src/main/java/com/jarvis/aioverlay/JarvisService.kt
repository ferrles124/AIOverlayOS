package com.jarvis.aioverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class JarvisService : Service() {

    private val TAG = "JARVIS"
    private val CHANNEL_ID = "jarvis_service_channel"
    private val NOTIF_ID = 1

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var groqClient: GroqClient
    private lateinit var commandProcessor: CommandProcessor
    private lateinit var taskManager: TaskManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isListening = false
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Başlatılıyor..."))

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val groqKey = prefs.getString("groq_api_key", "") ?: ""
        val weatherKey = prefs.getString("weather_api_key", "") ?: ""
        val userName = prefs.getString("user_name", "Efendim") ?: "Efendim"
        val city = prefs.getString("user_city", "Istanbul") ?: "Istanbul"

        groqClient = GroqClient(groqKey)
        taskManager = TaskManager(this)
        commandProcessor = CommandProcessor(this, groqClient, taskManager, weatherKey, city, userName) { response ->
            speak(response)
        }

        initTTS(userName)
    }

    private fun initTTS(userName: String) {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("tr", "TR")
                tts.setSpeechRate(0.9f)
                tts.setPitch(0.85f)
                ttsReady = true
                Log.d(TAG, "TTS hazır")

                Handler(Looper.getMainLooper()).postDelayed({
                    speak("Sistemler aktif. Merhaba $userName. Emrinize amadeyim.")
                    Handler(Looper.getMainLooper()).postDelayed({
                        startListening()
                    }, 3000)
                }, 500)
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) return
        Log.d(TAG, "JARVIS: $text")
        updateNotification(text.take(60))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utterance")
        } else {
            @Suppress("DEPRECATION")
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }

        // Konuşma bitince tekrar dinle
        val estimatedDuration = (text.length * 70L) + 1500L
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isListening) startListening()
        }, estimatedDuration)
    }

    private fun startListening() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer mevcut değil")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                updateNotification("Dinliyorum...")
                Log.d(TAG, "Dinlemeye başlandı")
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                Log.d(TAG, "Duyulan: $text")
                updateNotification("Anlaşıldı: $text")
                processCommand(text)
            }
            override fun onError(error: Int) {
                isListening = false
                Log.d(TAG, "Dinleme hatası: $error")
                // Sessizlik veya timeout hatalarında tekrar dene
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_CLIENT) {
                    Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 1000)
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 3000)
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processCommand(text: String) {
        serviceScope.launch {
            commandProcessor.process(text)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "JARVIS Servisi", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "JARVIS arka plan servisi"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("J.A.R.V.I.S.")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
        isListening = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
