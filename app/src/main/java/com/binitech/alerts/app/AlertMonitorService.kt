package com.binitech.alerts.app

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONObject

class AlertMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var source: EventSource? = null
    private var retryDelayMs = INITIAL_RETRY_MS
    private var lastEventAt = 0L
    private var running = false

    private val watchdog = object : Runnable {
        override fun run() {
            if (running && AlertBus.connection.value == ConnectionState.CONNECTED &&
                SystemClock.elapsedRealtime() - lastEventAt > STALE_MS
            ) {
                reconnect()
            }
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        val notification = Notifications.monitor(this, ConnectionState.CONNECTING)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifications.MONITOR_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.MONITOR_ID, notification)
        }
        if (!running) {
            running = true
            connect()
            handler.postDelayed(watchdog, WATCHDOG_MS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        source?.cancel()
        AlertBus.connection(ConnectionState.STOPPED)
        super.onDestroy()
    }

    private fun connect() {
        val settings = Settings(this)
        if (!settings.configured) {
            updateState(ConnectionState.RETRYING)
            return
        }
        updateState(ConnectionState.CONNECTING)
        source = AlertsApi(settings.serverUrl, settings.token).stream(listener)
    }

    private fun reconnect() {
        source?.cancel()
        source = null
        if (!running) {
            return
        }
        updateState(ConnectionState.RETRYING)
        handler.postDelayed({ if (running) connect() }, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
    }

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            handler.post {
                lastEventAt = SystemClock.elapsedRealtime()
                retryDelayMs = INITIAL_RETRY_MS
                updateState(ConnectionState.CONNECTED)
            }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            handler.post {
                lastEventAt = SystemClock.elapsedRealtime()
                if (type == "alert" || type == "update") {
                    handle(type, AlertItem.from(JSONObject(data)))
                }
            }
        }

        override fun onClosed(eventSource: EventSource) {
            handler.post { if (source === eventSource) reconnect() }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            handler.post { if (source === eventSource) reconnect() }
        }
    }

    private fun handle(type: String, alert: AlertItem) {
        AlertBus.upsert(alert)
        if (type == "update") {
            if (alert.acknowledged && AlertBus.ringing.value?.id == alert.id) {
                silence(this)
            }
            return
        }
        if (alert.noisy && !alert.acknowledged) {
            AlertBus.ring(alert)
            AlarmPlayer.start(this)
            Notifications.alarm(this, alert)
        } else {
            Notifications.info(this, alert)
        }
    }

    private fun updateState(state: ConnectionState) {
        AlertBus.connection(state)
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.MONITOR_ID, Notifications.monitor(this, state))
    }

    private fun stopMonitoring() {
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.binitech.alerts.app.STOP"
        private const val INITIAL_RETRY_MS = 2_000L
        private const val MAX_RETRY_MS = 60_000L
        private const val WATCHDOG_MS = 30_000L
        private const val STALE_MS = 75_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AlertMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlertMonitorService::class.java).setAction(ACTION_STOP))
        }

        fun silence(context: Context) {
            AlarmPlayer.stop(context)
            Notifications.clearAlarm(context)
            AlertBus.ring(null)
        }
    }
}
