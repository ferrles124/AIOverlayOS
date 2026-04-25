package com.jarvis.aioverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.aioverlay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST = 100

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // API Key kaydet
        binding.btnSave.setOnClickListener {
            val groqKey = binding.etGroqKey.text.toString().trim()
            val weatherKey = binding.etWeatherKey.text.toString().trim()

            if (groqKey.isEmpty()) {
                Toast.makeText(this, "Groq API Key gerekli!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            prefs.edit()
                .putString("groq_api_key", groqKey)
                .putString("weather_api_key", weatherKey)
                .putString("user_name", binding.etUserName.text.toString().trim().ifEmpty { "Efendim" })
                .putString("user_city", binding.etCity.text.toString().trim().ifEmpty { "Istanbul" })
                .apply()

            Toast.makeText(this, "Ayarlar kaydedildi!", Toast.LENGTH_SHORT).show()
            binding.tvStatus.text = "✅ Ayarlar kaydedildi"
        }

        // JARVIS başlat
        binding.btnStart.setOnClickListener {
            val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            if (prefs.getString("groq_api_key", "").isNullOrEmpty()) {
                Toast.makeText(this, "Önce API key girin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startJarvisService()
            binding.tvStatus.text = "🟢 JARVIS aktif — dinliyor..."
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
        }

        // JARVIS durdur
        binding.btnStop.setOnClickListener {
            stopService(Intent(this, JarvisService::class.java))
            binding.tvStatus.text = "🔴 JARVIS durduruldu"
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
        }

        // Mevcut ayarları yükle
        val prefs = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
        binding.etGroqKey.setText(prefs.getString("groq_api_key", ""))
        binding.etWeatherKey.setText(prefs.getString("weather_api_key", ""))
        binding.etUserName.setText(prefs.getString("user_name", ""))
        binding.etCity.setText(prefs.getString("user_city", ""))

        binding.btnStop.isEnabled = false
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            val denied = permissions.filterIndexed { i, _ ->
                grantResults[i] != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "Bazı izinler verilmedi: ${denied.joinToString()}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
