package com.vitalstream.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitalstream.android.ui.MainViewModel
import com.vitalstream.android.ui.theme.VitalStreamAndroidTheme

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val authorization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.let { model.finishLogin(it) }
    }
    private val permissions = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        model.message.value = if (granted.containsAll(model.graph.health.permissions)) "Access granted. You can import resting heart rate now." else "Permission not granted. Demo data remains available."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VitalStreamAndroidTheme {
            Dashboard(model, onLogin = { model.action { authorization.launch(model.graph.auth.loginIntent()) } },
                onPermissions = { permissions.launch(model.graph.health.permissions) })
        } }
    }
}

@Composable
private fun Dashboard(model: MainViewModel, onLogin: () -> Unit, onPermissions: () -> Unit) {
    val owner by model.owner.collectAsStateWithLifecycle()
    val storedRecords by model.observations.collectAsStateWithLifecycle()
    val records = storedRecords.filter { it.owner == (owner ?: "preview") }
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val insight by model.insight.collectAsStateWithLifecycle()
    val source by model.selectedSource.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Overview") }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("VitalStream", style = MaterialTheme.typography.headlineLarge)
                Text(if (owner == null) "Offline preview" else "Signed in · personal health trends")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (owner == null) Button(onClick = onLogin, enabled = !busy) { Text("Sign in / register") }
                    else OutlinedButton(onClick = { model.signOut() }, enabled = !busy) { Text("Sign out") }
                    if (owner != null) Button(onClick = { model.sync() }, enabled = !busy) { Text("Sync now") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Overview", "Sources", "Insights").forEach { name ->
                        FilterChip(selected = tab == name, onClick = { tab = name }, label = { Text(name) })
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            when (tab) {
                "Overview" -> {
                    item {
                        Text("Resting heart rate", style = MaterialTheme.typography.titleLarge)
                        Text("${records.size} readings · ${records.count { it.syncState == "pending" }} pending")
                        if (records.isEmpty()) Text("No readings yet. Load a simulated history in Sources.")
                        // JOSEPH-M1 (intermediate): build a 7/28-day chart here from a pure mapper.
                        // Group by an explicit timezone, preserve missing days as gaps, and never
                        // mix source methods. Add TalkBack summaries and tests for DST and no data.
                        // Keep the list below as the accessible detailed view of the chart's inputs.
                    }
                    items(records, key = { "${it.owner}:${it.id}" }) { record ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${record.value} bpm", style = MaterialTheme.typography.titleMedium)
                                Text(record.observedAt)
                                Text("${if (record.synthetic) "SIMULATED · " else ""}${record.source}")
                                Text("Sync: ${record.syncState}")
                            }
                        }
                    }
                }
                "Sources" -> item {
                    Text("Data sources", style = MaterialTheme.typography.titleLarge)
                    Text("Synthetic data is for development and is not medical evidence.")
                    Button(onClick = { model.demo() }, enabled = !busy) { Text("Load demo history") }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                    Text("Explicit foreground import of resting heart rate from the last 28 days. Companion apps must share this metric. Continuous updates and deletion sync are not implemented yet.")
                    val available = model.graph.health.available()
                    Text(if (available) "Health Connect available" else "Health Connect unavailable on this device")
                    OutlinedButton(onClick = onPermissions, enabled = !busy && available && owner != null) { Text("Grant read access") }
                    Button(onClick = { model.importHealth() }, enabled = !busy && available && owner != null) { Text("Import resting heart rate") }
                }
                "Insights" -> item {
                    Text("Personal baseline", style = MaterialTheme.typography.titleLarge)
                    Text("Choose a source, then Sync now. Sources are evaluated separately.")
                    records.map { it.source }.distinct().forEach { name ->
                        FilterChip(selected = source == name, onClick = { model.selectedSource.value = name; model.insight.value = null }, label = { Text(name) })
                    }
                    insight?.let { result ->
                        Text(result.status.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                        Text(result.explanation)
                        Text("Baseline: ${result.baseline ?: "—"} bpm · Change: ${result.deviation ?: "—"} bpm")
                        Text("${result.historyDays} earlier days · ${result.generatedAt}")
                    } ?: Text("No insight loaded. Sign in and synchronize at least 8 days of observations.")
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
