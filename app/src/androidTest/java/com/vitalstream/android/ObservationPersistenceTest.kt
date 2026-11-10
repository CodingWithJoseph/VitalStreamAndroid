package com.vitalstream.android

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.vitalstream.android.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ObservationPersistenceTest {
    @Test fun restartRetryAccountIsolationAndDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, ObservationDatabase::class.java, name).build()
        var db = open()
        try {
            val records = syntheticHistory("alice")
            db.observations().merge(records)
            db.close(); db = open()
            val dao = db.observations()
            dao.merge(records)
            assertEquals(14, dao.pending("alice").size)
            assertTrue(dao.observe("bob").first().isEmpty())
            val old = records.first()
            dao.merge(listOf(old.copy(revision = 2, value = 80.0)))
            dao.acknowledge("alice", old.id, 1, "synced")
            assertEquals("pending", dao.find("alice", old.id)?.syncState)
            dao.acknowledge("alice", old.id, 2, "synced")
            dao.reconcile("alice", emptyList())
            dao.merge(listOf(old.copy(revision = 3)))
            assertEquals("deleted", dao.find("alice", old.id)?.syncState)
            assertEquals(13, dao.pending("alice").size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
