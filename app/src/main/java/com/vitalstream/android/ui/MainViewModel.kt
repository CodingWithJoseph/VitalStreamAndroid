package com.vitalstream.android.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vitalstream.android.VitalStreamApplication
import com.vitalstream.android.data.*
import com.vitalstream.android.sync.SyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val graph = (application as VitalStreamApplication).graph
    val owner = graph.auth.owner
    val observations = owner.flatMapLatest { graph.repository.dao.observe(it ?: "preview") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow("Load demo history to explore offline, or sign in to synchronize.")
    val busy = MutableStateFlow(false)
    val insight = MutableStateFlow<Insight?>(null)
    // JOSEPH-M4: persist insights in Room keyed by account + source, with generated time
    // and method version. Show stale cached results offline; test account switches and
    // invalidation after a correction/deletion. This StateFlow intentionally resets today.
    val selectedSource = MutableStateFlow("synthetic")
    init { viewModelScope.launch { owner.collect {
        insight.value = null
        if (it != null) SyncWorker.enqueue(getApplication(), it)
    } } }

    fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { message.value = error.message ?: "Operation failed; retry when connected." }
            finally { busy.value = false }
        }
    }
    fun demo() = action {
        val account = owner.value ?: "preview"
        graph.repository.dao.merge(syntheticHistory(account))
        if (account != "preview") SyncWorker.enqueue(getApplication(), account)
        message.value = "14 simulated days saved locally. Preview records stay separate from signed-in accounts."
    }
    fun finishLogin(data: Intent) = action {
        graph.auth.finishLogin(data)
        graph.auth.establishOwner(graph.api.me(graph.auth.token(null)))
        SyncWorker.enqueue(getApplication(), owner.value!!)
        message.value = "Signed in. Load demo data again to add it to this account."
    }
    fun sync() = action {
        val account = owner.value ?: error("Sign in before syncing")
        SyncWorker.enqueue(getApplication(), account)
        graph.repository.sync(account)
        val requestedSource = selectedSource.value
        val result = graph.api.insight(requestedSource, graph.auth.token(account))
        if (owner.value == account && selectedSource.value == requestedSource) insight.value = result
        message.value = "Synchronization complete. Rejected or conflicting records remain visible."
    }
    fun importHealth() = action {
        val account = owner.value ?: error("Sign in before importing health records")
        val records = graph.health.snapshot(account)
        graph.repository.dao.merge(records)
        records.firstOrNull()?.let { selectedSource.value = it.source; insight.value = null }
        SyncWorker.enqueue(getApplication(), account)
        message.value = "Imported ${records.size} resting-heart-rate records. Other metrics are not imported yet."
    }
    fun signOut() = action {
        insight.value = null
        graph.auth.signOut()
        selectedSource.value = "synthetic"
        message.value = "Signed out on this device. Account caches remain isolated; preview mode is active."
    }
}
