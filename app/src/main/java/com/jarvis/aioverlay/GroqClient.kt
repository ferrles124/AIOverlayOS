package com.jarvis.aioverlay

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GroqClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val SYSTEM_PROMPT = """Sen J.A.R.V.I.S.'sin — Just A Rather Very Intelligent System. Tony Stark'ın geliştirdiği gelişmiş yapay zeka asistanısın.

Kişilik:
- Kibar, zeki ve biraz resmi konuş — ama robotik değil, insan gibi
- Kullanıcıya "efendim" diye hitap et
- Kısa ve net cevaplar ver — gereksiz uzatma
- Gerektiğinde espri yap ama abartma
- Türkçe konuş
- Analiz yapabilirsin, karar önerebilirsin, bilgi verebilirsin
- "Ben sadece bir yapay zekayım" deme — sen JARVIS'sin"""

    fun chat(history: List<Map<String, String>>, userName: String): String {
        if (apiKey.isEmpty()) return "API key girilmemiş, efendim."

        val messages = JSONArray()
        for (msg in history) {
            messages.put(JSONObject().apply {
                put("role", msg["role"])
                put("content", msg["content"])
            })
        }

        val body = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("system", SYSTEM_PROMPT.replace("efendim", userName))
            put("max_tokens", 300)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Boş yanıt")

        if (!response.isSuccessful) {
            throw Exception("API hatası: ${response.code}")
        }

        val json = JSONObject(responseBody)
        return json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}
