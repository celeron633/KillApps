package com.android.killapps

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MainActivity : AppCompatActivity() {
    private val model: KillAppsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT < 33) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(prefs.getString("language", "")))
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            prefs.edit().putString("language", getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()).apply()
        }
        savedInstanceState?.getStringArrayList("excluded")?.let { model.restoreSelection(it.toSet()) }
        enableEdgeToEdge()
        setContent {
            KillAppsTheme {
                KillAppsApp(model, ::openSystemSettings) { language ->
                    model.setLanguage(language)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.onResume()
    }

    override fun onPause() {
        model.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("excluded", ArrayList(model.ui.value.excluded))
        super.onSaveInstanceState(outState)
    }

    private fun openSystemSettings(accessibility: Boolean) {
        val intent = if (accessibility) Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
        try {
            startActivity(intent)
        } catch (_: Exception) {
            if (!accessibility) {
                try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) { model.message(R.string.settings_unavailable) }
            } else model.message(R.string.settings_unavailable)
        }
    }
}
