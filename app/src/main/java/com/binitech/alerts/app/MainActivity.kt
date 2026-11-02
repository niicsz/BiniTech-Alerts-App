package com.binitech.alerts.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val settings = Settings(this)
        if (settings.monitoring && settings.configured) {
            AlertMonitorService.start(this)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFC2410C))) {
                MainScreen(settings)
            }
        }
    }

    fun batteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    fun fullScreenAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun requestBatteryExemption() {
        startActivity(
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
        )
    }

    fun requestFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")),
            )
        }
    }
}

@Composable
private fun MainScreen(settings: Settings) {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(settings.serverUrl) }
    var token by remember { mutableStateOf(settings.token) }
    var monitoring by remember { mutableStateOf(settings.monitoring) }
    var message by remember { mutableStateOf("") }
    var targets by remember { mutableStateOf<List<TargetItem>>(emptyList()) }
    val alerts by AlertBus.alerts.collectAsState()
    val connection by AlertBus.connection.collectAsState()
    val ringing by AlertBus.ringing.collectAsState()

    fun api() = AlertsApi(url.trim().trimEnd('/'), token.trim())

    suspend fun refresh() {
        if (!settings.configured) return
        val result = withContext(Dispatchers.IO) { runCatching { api().recent() to api().targets() } }
        result.onSuccess { (recent, status) ->
            AlertBus.replace(recent)
            targets = status
        }.onFailure { message = "Falha ao consultar: ${it.message}" }
    }

    LaunchedEffect(Unit) { refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF6F4EF)).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("BiniTech Alertas", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when (connection) {
                    ConnectionState.CONNECTED -> Color(0xFF15803D)
                    ConnectionState.STOPPED -> Color.Gray
                    else -> Color(0xFFA16207)
                }
                Spacer(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.size(8.dp))
                Text(
                    when (connection) {
                        ConnectionState.CONNECTED -> "Conectado à produção"
                        ConnectionState.CONNECTING -> "Conectando..."
                        ConnectionState.RETRYING -> "Sem conexão, tentando de novo"
                        ConnectionState.STOPPED -> "Monitoramento desligado"
                    },
                    color = Color.DarkGray,
                )
            }
        }

        ringing?.let { alert ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFB91C1C))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(alert.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(alert.message, color = Color(0xFFFEE2E2))
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                AlertMonitorService.silence(activity)
                                acknowledgeInBackground(activity, alert.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB91C1C)),
                        ) { Text("Reconhecer e silenciar") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Servidor (https://...)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Monitorar produção", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = monitoring,
                            onCheckedChange = { enabled ->
                                settings.serverUrl = url
                                settings.token = token
                                if (enabled && !settings.configured) {
                                    message = "Informe a URL https e o token (32+ caracteres)."
                                    return@Switch
                                }
                                settings.monitoring = enabled
                                monitoring = enabled
                                if (enabled) AlertMonitorService.start(activity) else AlertMonitorService.stop(activity)
                                scope.launch { refresh() }
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            settings.serverUrl = url
                            settings.token = token
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { api().sendTest() } }
                                message = result.fold({ "Alerta de teste enviado. O alarme deve tocar." }, { "Falhou: ${it.message}" })
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Testar alarme") }
                    if (!activity.batteryUnrestricted()) {
                        OutlinedButton(onClick = { activity.requestBatteryExemption() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Permitir rodar em segundo plano")
                        }
                    }
                    if (!activity.fullScreenAllowed()) {
                        OutlinedButton(onClick = { activity.requestFullScreen() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Permitir alarme em tela cheia")
                        }
                    }
                    if (message.isNotEmpty()) Text(message, color = Color.DarkGray, fontSize = 13.sp)
                }
            }
        }

        if (targets.isNotEmpty()) {
            item { Text("Serviços", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            items(targets, key = { "t-" + it.name }) { target ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        Modifier.size(10.dp).clip(CircleShape)
                            .background(if (target.healthy) Color(0xFF15803D) else Color(0xFFB91C1C)),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(target.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(target.lastError ?: target.lastDurationMs?.let { "$it ms" } ?: "—", color = Color.DarkGray)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alertas recentes", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { scope.launch { refresh() } }) { Text("Atualizar") }
            }
        }
        if (alerts.isEmpty()) {
            item { Text("Nenhum alerta ainda.", color = Color.Gray) }
        }
        items(alerts, key = { it.id }) { alert ->
            val accent = when (alert.severity) {
                "CRITICAL" -> Color(0xFFB91C1C)
                "WARNING" -> Color(0xFFC2410C)
                else -> Color(0xFF15803D)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text(alert.title, fontWeight = FontWeight.Bold, color = accent)
                    Text(alert.message, fontSize = 14.sp)
                    Text(
                        listOfNotNull(
                            alert.lastSeenAt.replace('T', ' ').take(19),
                            if (alert.occurrences > 1) "${alert.occurrences} ocorrências" else null,
                            if (alert.acknowledged) "reconhecido" else null,
                        ).joinToString(" · "),
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    if (!alert.acknowledged && alert.noisy) {
                        OutlinedButton(onClick = {
                            AlertMonitorService.silence(activity)
                            acknowledgeInBackground(activity, alert.id)
                        }) { Text("Reconhecer") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
