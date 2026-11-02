package com.binitech.alerts.app

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject

class AlertsApi(private val baseUrl: String, private val token: String) {

    fun recent(limit: Int = 50): List<AlertItem> {
        val body = execute(request("/api/alerts?limit=$limit").get().build())
        val array = JSONArray(body)
        return (0 until array.length()).map { AlertItem.from(array.getJSONObject(it)) }
    }

    fun targets(): List<TargetItem> {
        val array = JSONArray(execute(request("/api/alerts/targets").get().build()))
        return (0 until array.length()).map { TargetItem.from(array.getJSONObject(it)) }
    }

    fun acknowledge(id: String): AlertItem =
        AlertItem.from(JSONObject(execute(request("/api/alerts/$id/ack").post(EMPTY).build())))

    fun sendTest(): AlertItem =
        AlertItem.from(JSONObject(execute(request("/api/alerts/test").post(EMPTY).build())))

    fun stream(listener: EventSourceListener): EventSource {
        val request = request("/api/alerts/stream").header("Accept", "text/event-stream").get().build()
        return EventSources.createFactory(streamClient).newEventSource(request, listener)
    }

    private fun request(path: String) = Request.Builder()
        .url(baseUrl + path)
        .header("Authorization", "Bearer $token")

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}")
            }
            return body
        }
    }

    companion object {
        private val EMPTY = ByteArray(0).toRequestBody("application/json".toMediaType())

        private val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        private val streamClient: OkHttpClient = client.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
