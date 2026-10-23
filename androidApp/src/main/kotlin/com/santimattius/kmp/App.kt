package com.santimattius.kmp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.santimattius.persistent.cache.sample.PersistenceCheckResult
import io.github.santimattius.persistent.cache.sample.PersistentCacheSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun App(cacheRoot: String) {
    val sample = remember(cacheRoot) { PersistentCacheSample(cacheRoot) }
    val scope = rememberCoroutineScope()
    var okioState by remember { mutableStateOf<BackendCheckState>(BackendCheckState.Idle) }
    var kotlinxIoState by remember { mutableStateOf<BackendCheckState>(BackendCheckState.Idle) }

    BackendChecksScreen(
        okioState = okioState,
        kotlinxIoState = kotlinxIoState,
        onRunOkio = {
            okioState = BackendCheckState.Running
            scope.launch {
                okioState = runCheck { sample.verifyOkio() }
            }
        },
        onRunKotlinxIo = {
            kotlinxIoState = BackendCheckState.Running
            scope.launch {
                kotlinxIoState = runCheck { sample.verifyKotlinxIo() }
            }
        },
    )
}

private suspend fun runCheck(check: suspend () -> PersistenceCheckResult): BackendCheckState =
    try {
        val result = withContext(Dispatchers.Default) { check() }
        if (result.secondClientReadPersistedData) {
            BackendCheckState.Passed(result)
        } else {
            BackendCheckState.Failed(
                result = result,
                message = "The second client made an engine request instead of reading persisted data.",
            )
        }
    } catch (error: Throwable) {
        BackendCheckState.Failed(message = error.message ?: error::class.simpleName.orEmpty())
    }

@Composable
private fun BackendChecksScreen(
    okioState: BackendCheckState,
    kotlinxIoState: BackendCheckState,
    onRunOkio: () -> Unit,
    onRunKotlinxIo: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .safeContentPadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Persistent cache backend sample", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Each check uses an offline MockEngine and a fresh client to verify persisted data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                BackendCheckCard("Okio", okioState, onRunOkio)
                BackendCheckCard("kotlinx-io", kotlinxIoState, onRunKotlinxIo)
            }
        }
    }
}

@Composable
private fun BackendCheckCard(
    backend: String,
    state: BackendCheckState,
    onRun: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(backend, style = MaterialTheme.typography.titleLarge)
            Text(state.status, color = state.statusColor)
            state.result?.let { result ->
                Text("First client engine requests: ${result.firstClientEngineRequests}")
                Text("Second client engine requests: ${result.secondClientEngineRequests}")
                Text("Second client read persisted data: ${result.secondClientReadPersistedData}")
                Text("Response: ${result.responseBody}")
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onRun, enabled = state !is BackendCheckState.Running) {
                Text("Run $backend check")
            }
        }
    }
}

private sealed interface BackendCheckState {
    data object Idle : BackendCheckState

    data object Running : BackendCheckState

    data class Passed(
        val result: PersistenceCheckResult,
    ) : BackendCheckState

    data class Failed(
        val result: PersistenceCheckResult? = null,
        val message: String,
    ) : BackendCheckState
}

private val BackendCheckState.status: String
    get() =
        when (this) {
            BackendCheckState.Idle -> "Idle"
            BackendCheckState.Running -> "Running"
            is BackendCheckState.Passed -> "Pass"
            is BackendCheckState.Failed -> "Fail"
        }

private val BackendCheckState.result: PersistenceCheckResult?
    get() =
        when (this) {
            is BackendCheckState.Passed -> result

            is BackendCheckState.Failed -> result

            BackendCheckState.Idle,
            BackendCheckState.Running,
            -> null
        }

private val BackendCheckState.message: String?
    get() =
        when (this) {
            is BackendCheckState.Failed -> message
            else -> null
        }

private val BackendCheckState.statusColor: Color
    @Composable
    get() =
        when (this) {
            BackendCheckState.Idle,
            BackendCheckState.Running,
            -> MaterialTheme.colorScheme.onSurface

            is BackendCheckState.Passed -> Color(0xFF1B5E20)

            is BackendCheckState.Failed -> MaterialTheme.colorScheme.error
        }

@Preview(showBackground = true)
@Composable
fun BackendChecksScreenPreview() {
    BackendChecksScreen(
        okioState = BackendCheckState.Idle,
        kotlinxIoState = BackendCheckState.Idle,
        onRunOkio = {},
        onRunKotlinxIo = {},
    )
}
