package com.binitech.alerts.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AlertItem(
    val id: String,
    val kind: String,
    val severity: String,
    val service: String,
    val title: String,
    val message: String,
    val durationMs: Long?,
    val occurrences: Int,
    val lastSeenAt: String,
    val acknowledged: Boolean,
    val noisy: Boolean,
) {
    companion object {
        fun from(json: JSONObject) = AlertItem(
            id = json.optString("id"),
            kind = json.optString("kind"),
            severity = json.optString("severity"),
            service = json.optString("service"),
            title = json.optString("title"),
            message = json.optString("message"),
            durationMs = if (json.isNull("durationMs")) null else json.optLong("durationMs"),
            occurrences = json.optInt("occurrences", 1),
            lastSeenAt = json.optString("lastSeenAt"),
            acknowledged = json.optBoolean("acknowledged"),
            noisy = json.optBoolean("noisy", true),
        )
    }
}

data class TargetItem(
    val name: String,
    val healthy: Boolean,
    val lastDurationMs: Long?,
    val lastStatus: Int?,
    val lastError: String?,
) {
    companion object {
        fun from(json: JSONObject) = TargetItem(
            name = json.optString("name"),
            healthy = json.optBoolean("healthy", true),
            lastDurationMs = if (json.isNull("lastDurationMs")) null else json.optLong("lastDurationMs"),
            lastStatus = if (json.isNull("lastStatus")) null else json.optInt("lastStatus"),
            lastError = if (json.isNull("lastError")) null else json.optString("lastError"),
        )
    }
}

enum class ConnectionState { STOPPED, CONNECTING, CONNECTED, RETRYING }

object AlertBus {
    private val _alerts = MutableStateFlow<List<AlertItem>>(emptyList())
    val alerts: StateFlow<List<AlertItem>> = _alerts.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.STOPPED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _ringing = MutableStateFlow<AlertItem?>(null)
    val ringing: StateFlow<AlertItem?> = _ringing.asStateFlow()

    fun replace(items: List<AlertItem>) {
        _alerts.value = items
    }

    fun upsert(item: AlertItem) {
        val others = _alerts.value.filterNot { it.id == item.id }
        _alerts.value = (listOf(item) + others).take(MAX_ITEMS)
        if (item.acknowledged && _ringing.value?.id == item.id) {
            _ringing.value = null
        }
    }

    fun connection(state: ConnectionState) {
        _connection.value = state
    }

    fun ring(item: AlertItem?) {
        _ringing.value = item
    }

    private const val MAX_ITEMS = 100
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("serverUrl", null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString("serverUrl", value.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    var monitoring: Boolean
        get() = prefs.getBoolean("monitoring", false)
        set(value) = prefs.edit().putBoolean("monitoring", value).apply()

    val configured: Boolean
        get() = serverUrl.startsWith("https://") && token.length >= MIN_TOKEN

    companion object {
        private const val MIN_TOKEN = 32
    }
}
