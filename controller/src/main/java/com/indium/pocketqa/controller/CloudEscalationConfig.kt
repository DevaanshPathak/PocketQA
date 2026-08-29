package com.indium.pocketqa.controller

import android.content.Context

/** Demo-only BYOK setting. The key is stored locally and is never sent to local models. */
data class CloudEscalationConfig(val enabled: Boolean, val apiKey: String, val model: String) {
    val ready: Boolean get() = enabled && apiKey.isNotBlank()

    companion object {
        private const val PREFS = "cloud_escalation"
        private const val ENABLED = "enabled"
        private const val KEY = "openrouter_key"
        private const val MODEL = "openrouter_model"
        fun load(context: Context): CloudEscalationConfig {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return CloudEscalationConfig(p.getBoolean(ENABLED, false), p.getString(KEY, "").orEmpty(), p.getString(MODEL, "openai/gpt-4.1-mini").orEmpty())
        }
        fun save(context: Context, config: CloudEscalationConfig) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, config.enabled).putString(KEY, config.apiKey).putString(MODEL, config.model).apply()
        }
    }
}
