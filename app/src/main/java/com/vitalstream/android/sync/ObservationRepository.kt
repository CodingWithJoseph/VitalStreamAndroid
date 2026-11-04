package com.vitalstream.android.sync

import com.vitalstream.android.auth.AuthManager
import com.vitalstream.android.data.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ObservationRepository(val dao: ObservationDao, private val auth: AuthManager, val api: VitalStreamApi) {
    private val syncLock = Mutex()
    suspend fun sync(owner: String) = syncLock.withLock {
        do {
            val batch = dao.pending(owner)
            if (batch.isEmpty()) break
            val results = api.upload(batch, auth.token(owner))
            for (record in batch) {
                val state = when (val result = results.getValue(record.id)) {
                    "created", "updated", "unchanged" -> "synced"
                    "conflict", "stale", "deleted", "rejected" -> result
                    else -> error("Unknown server acknowledgement")
                }
                dao.acknowledge(owner, record.id, record.revision, state)
            }
        } while (true)
        dao.reconcile(owner, api.readAll(owner, auth.token(owner)))
    }
}
