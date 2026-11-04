package com.vitalstream.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiFailure(val status: Int) : IOException("Server request failed ($status)")
data class Insight(val status: String, val explanation: String, val baseline: Double?, val deviation: Double?, val historyDays: Int, val generatedAt: String)

class VitalStreamApi(private val baseUrl: String) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    private suspend fun request(path: String, token: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path).header("Authorization", "Bearer $token")
        if (body != null) request.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiFailure(response.code)
            JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        }
    }
    suspend fun me(token: String): String = request("/v1/me", token).getString("id")
    suspend fun upload(records: List<Observation>, token: String): Map<String, String> {
        val response = request("/v1/me/observations:batch", token,
            JSONObject().put("records", JSONArray(records.map { it.toWire() })))
        val results = response.getJSONArray("results")
        val mapped = (0 until results.length()).associate {
            results.getJSONObject(it).let { result -> result.getString("id") to result.getString("status") }
        }
        check(mapped.keys == records.map { it.id }.toSet()) { "Incomplete server acknowledgement" }
        return mapped
    }
    suspend fun readAll(owner: String, token: String): List<Observation> {
        val records = mutableListOf<Observation>()
        var cursor = 0L
        do {
            val response = request("/v1/me/observations?cursor=$cursor", token)
            val items = response.getJSONArray("items")
            for (index in 0 until items.length()) records += items.getJSONObject(index).toObservation(owner)
            if (response.isNull("next_cursor")) break
            val next = response.getLong("next_cursor")
            check(next > cursor && records.size <= 100_000) { "Invalid pagination response" }
            cursor = next
        } while (true)
        return records
    }
    suspend fun insight(source: String, token: String): Insight {
        val json = request("/v1/me/insights?source=${URLEncoder.encode(source, "UTF-8")}", token)
        return Insight(json.getString("status"), json.getString("explanation"),
            if (json.isNull("baseline")) null else json.getDouble("baseline"),
            if (json.isNull("deviation")) null else json.getDouble("deviation"),
            json.getInt("history_days"), json.getString("generated_at"))
    }
}

fun Observation.toWire(): JSONObject = JSONObject()
    .put("id", id).put("source", source).put("source_record_id", sourceRecordId)
    .put("revision", revision).put("metric", "resting_heart_rate").put("value", value)
    .put("unit", "bpm").put("observed_at", observedAt).put("synthetic", synthetic)

private fun JSONObject.toObservation(owner: String) = Observation(
    owner, getString("id"), getString("source"), getString("source_record_id"),
    getLong("revision"), getDouble("value"), getString("observed_at"), getBoolean("synthetic"), "synced")
