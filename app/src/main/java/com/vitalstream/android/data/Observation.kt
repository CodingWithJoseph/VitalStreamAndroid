package com.vitalstream.android.data

import androidx.room.Entity
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Entity(tableName = "observations", primaryKeys = ["owner", "id"])
data class Observation(
    val owner: String,
    val id: String,
    val source: String,
    val sourceRecordId: String,
    val revision: Long,
    val value: Double,
    val observedAt: String,
    val synthetic: Boolean,
    val syncState: String = "pending",
)

fun syntheticHistory(owner: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<Observation> =
    (1..14).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        val sourceId = "demo-rhr-v1-$date"
        Observation(owner, UUID.nameUUIDFromBytes(sourceId.toByteArray(Charsets.UTF_8)).toString(),
            "synthetic", sourceId, 1, if (daysAgo == 1) 72.0 else 60.0 + daysAgo % 4,
            date.atTime(8, 0).toInstant(ZoneOffset.UTC).toString(), true)
    }
