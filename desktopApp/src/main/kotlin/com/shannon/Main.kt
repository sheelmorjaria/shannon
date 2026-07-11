package com.shannon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.shannon.audio.AudioPlayer
import com.shannon.audio.AudioRecorder
import com.shannon.audio.StubAudioPlayer
import com.shannon.audio.StubAudioRecorder
import com.shannon.bridge.BridgeServer
import com.shannon.bridge.DefaultBridgeBackend
import com.shannon.db.DatabaseDriverFactory
import com.shannon.di.captionModule
import com.shannon.di.databaseModule
import com.shannon.di.messageRepositoryModule
import com.shannon.di.networkModule
import com.shannon.di.repositoryModule
import com.shannon.di.viewModelModule
import com.shannon.di.voiceCallModule
import com.shannon.network.ReticulumClient
import com.shannon.ui.ReactWebView
import com.shannon.viewmodel.ConnectivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module

private const val LOCAL_HASH = "local_identity_placeholder"

fun main() = application {
    println("Starting Shannon Desktop")

    val koin = startKoin {
        modules(
            module {
                single<DatabaseDriverFactory> { DatabaseDriverFactory() }
                single<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
                single<String> { LOCAL_HASH }
                single<AudioRecorder> { StubAudioRecorder() }
                single<AudioPlayer> { StubAudioPlayer() }
            },
            databaseModule,
            repositoryModule,
            messageRepositoryModule(LOCAL_HASH),
            viewModelModule,
            networkModule(),
            voiceCallModule(),
            captionModule(),
            module {
                // Override the stub SpeechEngine with real Vosk STT (§2.2). Falls back gracefully
                // if the model isn't found (isAvailable = false → captions disabled).
                single<com.shannon.speech.SpeechEngine> {
                    com.shannon.speech.VoskSpeechEngine(
                        modelPath = System.getProperty("shannon.vosk.model") ?: "vosk-model-en",
                    )
                }
            },
        )
    }.koin

    val connectivityViewModel: ConnectivityViewModel by org.koin.java.KoinJavaComponent.inject(
        ConnectivityViewModel::class.java
    )
    connectivityViewModel.startObserving()

    // Launch the localhost JSON-RPC bridge so the React UI (webview) can talk to the Kotlin core.
    val voskCacheDir = java.io.File(System.getProperty("user.home"), ".shannon/vosk")
    val voskEngine = com.shannon.speech.VoskSpeechEngine(
        modelPath = System.getProperty("shannon.vosk.model")
            ?: com.shannon.speech.VoskModelManager.ensureModel(
                com.shannon.speech.VoskModelManager.DEFAULT_LANG, voskCacheDir,
            ) ?: "vosk-model-small-en-us-0.15",
    )
    val scope = koin.get<CoroutineScope>()
    val backend = DefaultBridgeBackend(
        messages = koin.get(),
        client = koin.get(),
        calls = koin.get(),
        captions = koin.get(),
        speech = voskEngine,
        localHash = LOCAL_HASH,
        onLanguageChanged = { lang ->
            scope.launch {
                val path = com.shannon.speech.VoskModelManager.ensureModel(lang, voskCacheDir)
                if (path != null) voskEngine.switchLanguage(lang, path)
            }
        },
    )
    val bridge = BridgeServer(backend)
    bridge.start()
    println("Shannon bridge listening on ws://127.0.0.1:${BridgeServer.DEFAULT_PORT}/bridge")

    val webUrl = System.getProperty("shannon.web.url")
        ?: "http://127.0.0.1:${BridgeServer.DEFAULT_PORT}/"
    println("Loading Shannon UI from $webUrl")

    Window(
        onCloseRequest = {
            bridge.stop()
            exitApplication()
        },
        title = "Shannon Desktop"
    ) {
        MaterialTheme {
            ReactWebView(webUrl, Modifier.fillMaxSize())
        }
    }
}

@Composable
fun App(connectivityViewModel: ConnectivityViewModel) {
    val uiState by connectivityViewModel.connectivityState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Shannon Desktop",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Network Status: ${uiState.statusText}",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // TODO: Add connection controls
        }) {
            Text("Connect")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            // TODO: Add disconnect controls
        }) {
            Text("Disconnect")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
