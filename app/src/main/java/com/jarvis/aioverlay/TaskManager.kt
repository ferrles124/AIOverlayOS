package com.jarvis.aioverlay

import android.content.Context

class TaskManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_tasks", Context.MODE_PRIVATE)
    private val KEY = "task_list"

    fun addTask(task: String) {
        val tasks = getTasks().toMutableList()
        tasks.add(task.trim())
        saveTasks(tasks)
    }

    fun getTasks(): List<String> {
        val raw = prefs.getString(KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList()
        else raw.split("||").filter { it.isNotEmpty() }
    }

    fun removeTask(keyword: String): Boolean {
        val tasks = getTasks().toMutableList()
        val toRemove = tasks.find { it.lowercase().contains(keyword.lowercase()) }
        return if (toRemove != null) {
            tasks.remove(toRemove)
            saveTasks(tasks)
            true
        } else false
    }

    fun clearAll() {
        prefs.edit().remove(KEY).apply()
    }

    private fun saveTasks(tasks: List<String>) {
        prefs.edit().putString(KEY, tasks.joinToString("||")).apply()
    }
}
