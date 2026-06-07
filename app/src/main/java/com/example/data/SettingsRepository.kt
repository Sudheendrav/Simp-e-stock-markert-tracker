package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("stock_settings", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow<Boolean?>(
        if (prefs.contains("dark_mode")) prefs.getBoolean("dark_mode", false) else null
    )
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode

    private val _refreshInterval = MutableStateFlow(
        prefs.getInt("refresh_interval", 15) // default 15 seconds
    )
    val refreshInterval: StateFlow<Int> = _refreshInterval

    private val _isDataSaver = MutableStateFlow(
        prefs.getBoolean("data_saver", false)
    )
    val isDataSaver: StateFlow<Boolean> = _isDataSaver

    fun setDarkMode(dark: Boolean?) {
        _isDarkMode.value = dark
        prefs.edit().apply {
            if (dark == null) {
                remove("dark_mode")
            } else {
                putBoolean("dark_mode", dark)
            }
            apply()
        }
    }

    fun setRefreshInterval(seconds: Int) {
        _refreshInterval.value = seconds
        prefs.edit().putInt("refresh_interval", seconds).apply()
    }

    fun setDataSaver(enabled: Boolean) {
        _isDataSaver.value = enabled
        prefs.edit().putBoolean("data_saver", enabled).apply()
    }
}
