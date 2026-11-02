package com.binitech.alerts.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Settings(context).monitoring) {
            AlertMonitorService.start(context)
        }
    }
}

class AckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertMonitorService.silence(context)
        val id = intent.getStringExtra(AlarmActivity.EXTRA_ID) ?: return
        acknowledgeInBackground(context, id)
    }
}

fun acknowledgeInBackground(context: Context, id: String) {
    val settings = Settings(context.applicationContext)
    thread(name = "ack-$id") {
        runCatching { AlertsApi(settings.serverUrl, settings.token).acknowledge(id) }
            .onSuccess(AlertBus::upsert)
    }
}
