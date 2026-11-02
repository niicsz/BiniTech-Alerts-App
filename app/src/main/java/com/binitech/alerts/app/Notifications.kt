package com.binitech.alerts.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color

object Notifications {
    const val CHANNEL_MONITOR = "monitor"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_INFO = "info"
    const val MONITOR_ID = 1
    private const val ALERT_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, "Monitoramento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra que o app está conectado ao servidor de alertas"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alertas de produção", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lentidão ou queda em produção (toca alarme)"
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
                lightColor = Color.RED
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INFO, "Normalizações", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avisos de que um serviço voltou ao normal"
            },
        )
    }

    fun monitor(context: Context, state: ConnectionState): Notification {
        val text = when (state) {
            ConnectionState.CONNECTED -> "Conectado: aguardando alertas de produção"
            ConnectionState.CONNECTING -> "Conectando ao servidor de alertas..."
            ConnectionState.RETRYING -> "Sem conexão com o servidor. Tentando de novo..."
            ConnectionState.STOPPED -> "Monitoramento parado"
        }
        return Notification.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("BiniTech Alertas")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()
    }

    fun alarm(context: Context, alert: AlertItem) {
        val fullScreen = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            Intent(context, AlarmActivity::class.java)
                .putExtra(AlarmActivity.EXTRA_ID, alert.id)
                .putExtra(AlarmActivity.EXTRA_TITLE, alert.title)
                .putExtra(AlarmActivity.EXTRA_MESSAGE, alert.message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ack = PendingIntent.getBroadcast(
            context,
            alert.id.hashCode(),
            Intent(context, AckReceiver::class.java).putExtra(AlarmActivity.EXTRA_ID, alert.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(Notification.BigTextStyle().bigText(alert.message))
            .setCategory(Notification.CATEGORY_ALARM)
            .setColor(Color.RED)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "Reconhecer", ack).build())
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
    }

    fun info(context: Context, alert: AlertItem) {
        val notification = Notification.Builder(context, CHANNEL_INFO)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(alert.id.hashCode(), notification)
    }

    fun clearAlarm(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
