package com.jarvis.aioverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.view.animation.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.aioverlay.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST = 100
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            binding.tvClock.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 1000)
        }
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.SEND_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tam ekran, koyu status bar
        window.statusBarColor = android.graphics.Color.parseColor("#020b14")
        window.navigationBarColor = android.graphics.Color.parseColor("#020b14")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runBootAnimation()
        setupUI()
        startClock()
        checkPermissions()
        loadSavedSettings()
    }

    // ── Boot animasyonu ──────────────────────────────────────────────────────
    private fun runBootAnimation() {
        val views = listOf(
            binding.tvJarvisTitle,
            binding.tvStatus,
            binding.btnStart,
            binding.btnStop,
            binding.btnSave
        )

        views.forEachIndexed { i, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 120 + 300).toLong())
                .setDuration(600)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ── UI setup ─────────────────────────────────────────────────────────────
    private fun setupUI() {
        // KAYDET
        binding.btnSave.setOnClickListener {
            val groqKey = binding.etGroqKey.text.toString().trim()
            if (groqKey.isEmpty()) {
                shakeView(binding.etGroqKey)
                showStatus("⚠  GROQ KEY GEREKLİ — KAYIT REDDEDİLDİ", "#ff4444")
                return@setOnClickListener
            }

            getSharedPreferences("jarvis_prefs", MODE_PRIVATE).edit()
                .putString("groq_api_key", groqKey)
                .putString("weather_api_key", binding.etWeatherKey.text.toString().trim())
                .putString("user_name", binding.etUserName.text.toString().trim().ifEmpty { "Efendim" })
                .putString("user_city", binding.etCity.text.toString().trim().ifEmpty { "Istanbul" })
                .apply()

            showStatus("✓  KONFİGÜRASYON KAYDEDILDI — BELLEGE YAZILDI", "#00cc88")
            pulseView(binding.btnSave)
        }

        // BAŞLAT
        binding.btnStart.setOnClickListener {
            val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            if (prefs.getString("groq_api_key", "").isNullOrEmpty()) {
                shakeView(binding.btnStart)
                showStatus("⚠  ÖNCE API KEY GİRİN", "#ff4444")
                return@setOnClickListener
            }
            startJarvisService()
            showStatus("⬡  SİSTEMLER AKTİF — DİNLİYOR...", "#00cc88")
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            binding.statusDot.background = ContextCompat.getDrawable(this, R.drawable.status_dot_active)
            pulseView(binding.reactorRing)
        }

        // DURDUR
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, JarvisService::class.java))
            showStatus("◼  SİSTEMLER DEVRE DIŞI", "#ff4444")
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            binding.statusDot.background = ContextCompat.getDrawable(this, R.drawable.status_dot_inactive)
        }

        binding.btnStop.isEnabled = false
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
        binding.etGroqKey.setText(prefs.getString("groq_api_key", ""))
        binding.etWeatherKey.setText(prefs.getString("weather_api_key", ""))
        binding.etUserName.setText(prefs.getString("user_name", ""))
        binding.etCity.setText(prefs.getString("user_city", ""))
    }

    private fun startClock() {
        clockHandler.post(clockRunnable)
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun showStatus(text: String, colorHex: String) {
        binding.tvStatus.text = text
        binding.tvStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
    }

    // ── Animasyon yardımcıları ──────────────────────────────────────────────
    private fun shakeView(view: View) {
        val shake = TranslateAnimation(-12f, 12f, 0f, 0f).apply {
            duration = 60
            repeatCount = 5
            repeatMode = Animation.REVERSE
        }
        view.startAnimation(shake)
    }

    private fun pulseView(view: View) {
        val scaleUp = ScaleAnimation(
            1f, 1.04f, 1f, 1.04f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 150 }
        val scaleDown = ScaleAnimation(
            1.04f, 1f, 1.04f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 150
            startOffset = 150
        }
        val set = AnimationSet(true).apply {
            addAnimation(scaleUp)
            addAnimation(scaleDown)
        }
        view.startAnimation(set)
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val denied = permissions.filterIndexed { i, _ ->
            grantResults[i] != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            showStatus("⚠  BAZI İZİNLER VERİLMEDİ", "#ffaa00")
        }
    }
}
