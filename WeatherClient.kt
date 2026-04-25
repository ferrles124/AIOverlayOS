package com.jarvis.aioverlay

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WeatherClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getWeather(city: String, apiKey: String): String {
        val url = "https://api.openweathermap.org/data/2.5/weather" +
                "?q=${city}&appid=${apiKey}&units=metric&lang=tr"

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Yanıt yok")

        if (!response.isSuccessful) throw Exception("Hava durumu alınamadı")

        val json = JSONObject(body)
        val temp = json.getJSONObject("main").getDouble("temp").toInt()
        val feels = json.getJSONObject("main").getDouble("feels_like").toInt()
        val desc = json.getJSONArray("weather").getJSONObject(0).getString("description")
        val humidity = json.getJSONObject("main").getInt("humidity")

        return "$desc, $temp derece, hissedilen $feels derece, nem yüzde $humidity"
    }
}
