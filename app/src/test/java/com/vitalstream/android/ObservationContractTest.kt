package com.vitalstream.android

import com.vitalstream.android.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ObservationContractTest {
    @Test fun fixturesAreStableAndNeverUploadOwner() {
        val day = LocalDate.of(2026, 11, 1)
        val a = syntheticHistory("alice", day)
        val b = syntheticHistory("bob", day)
        assertEquals(a.map { it.id }, b.map { it.id })
        assertEquals(14, a.map { it.id }.distinct().size)
        assertFalse(a.first().toWire().has("owner"))
        assertEquals("bpm", a.first().toWire().getString("unit"))
        assertEquals("resting_heart_rate", a.first().toWire().getString("metric"))
    }
    @Test fun apiSendsBearerAndRejectsIncompleteAcknowledgements() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"results\":[]}"))
            val api = VitalStreamApi(server.url("/").toString())
            try { api.upload(syntheticHistory("alice"), "test-token"); fail("Must reject incomplete ACK") }
            catch (_: IllegalStateException) { /* pending records must not be silently lost */ }
            val request = server.takeRequest()
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("/v1/me/observations:batch", request.path)
            assertFalse(request.body.readUtf8().contains("alice"))
        }
    }
}
