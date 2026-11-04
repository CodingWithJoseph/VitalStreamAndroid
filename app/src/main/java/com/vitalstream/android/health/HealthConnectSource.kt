package com.vitalstream.android.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.vitalstream.android.data.Observation
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class HealthConnectSource(private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
    fun available() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun snapshot(owner: String): List<Observation> {
        check(available()) { "Health Connect is unavailable; use a compatible device or emulator" }
        val client = client()
        check(client.permissionController.getGrantedPermissions().containsAll(permissions)) { "Grant resting heart rate access first" }
        val end = Instant.now()
        var page: String? = null
        val records = mutableListOf<Observation>()
        do {
            val response = client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class,
                TimeRangeFilter.between(end.minus(28, ChronoUnit.DAYS), end), pageToken = page))
            response.records.forEach { record ->
                val source = "health_connect:${record.metadata.dataOrigin.packageName}"
                val stableId = UUID.nameUUIDFromBytes("$source:${record.metadata.id}".toByteArray(Charsets.UTF_8)).toString()
                records += Observation(owner, stableId, source, record.metadata.id,
                    record.metadata.lastModifiedTime.toEpochMilli().coerceAtLeast(1),
                    record.beatsPerMinute.toDouble(), record.time.toString(), false)
            }
            page = response.pageToken
        } while (page != null)
        // JOSEPH-M2 (intermediate): replace repeated snapshots with Changes tokens. Persist a
        // token PER account and record type only after that page is committed to Room. Handle
        // UpsertionChange, DeletionChange and expired tokens; add an outbox operation for deletes.
        // Tests: restart mid-page; replay a page; remove permission; expire a token; delete a record.
        // Until then, this feature is an explicit foreground import, not continuous device sync.
        return records
    }
}
