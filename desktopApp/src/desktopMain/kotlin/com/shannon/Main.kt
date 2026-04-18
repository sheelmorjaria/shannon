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
import com.shannon.db.DatabaseDriverFactory
import com.shannon.di.databaseModule
import com.shannon.di.messageRepositoryModule
import com.shannon.di.networkModule
import com.shannon.di.repositoryModule
import com.shannon.di.viewModelModule
import com.shannon.network.ReticulumClient
import com.shannon.viewmodel.ConnectivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() = application {
    println("Starting Shannon Desktop")

    startKoin {
        modules(
            module {
                single<DatabaseDriverFactory> { DatabaseDriverFactory() }
                single<CoroutineScope> { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
            },
            databaseModule,
            repositoryModule,
            messageRepositoryModule("local_identity_placeholder"),
            viewModelModule,
            networkModule()
        )
    }

    val connectivityViewModel: ConnectivityViewModel by org.koin.java.KoinJavaComponent.inject(
        ConnectivityViewModel::class.java
    )
    connectivityViewModel.startObserving()

    Window(
        onCloseRequest = {
            exitApplication()
        },
        title = "Shannon Desktop"
    ) {
        MaterialTheme {
            App(connectivityViewModel)
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
