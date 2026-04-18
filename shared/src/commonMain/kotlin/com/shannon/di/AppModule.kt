package com.shannon.di

import com.shannon.db.DatabaseDriverFactory
import com.shannon.db.ShannonDatabase
import com.shannon.db.SqlDelightConfigRepository
import com.shannon.db.SqlDelightContactRepository
import com.shannon.db.SqlDelightMessageRepository
import com.shannon.domain.repository.ConfigRepository
import com.shannon.domain.repository.ContactRepository
import com.shannon.domain.repository.MessageRepository
import com.shannon.network.ReticulumClient
import com.shannon.network.ReticulumClientImpl
import com.shannon.network.ReticulumConfig
import com.shannon.network.VoiceCallManagerIntegrated
import com.shannon.viewmodel.ConnectivityViewModel
import com.shannon.viewmodel.ConversationViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module

val databaseModule = module {
    single { ShannonDatabase(get<DatabaseDriverFactory>().createDriver()) }
}

val repositoryModule = module {
    single<ContactRepository> { SqlDelightContactRepository(get()) }
    single<ConfigRepository> { SqlDelightConfigRepository(get()) }
}

fun messageRepositoryModule(localHash: String) = module {
    single<MessageRepository> { SqlDelightMessageRepository(get(), localHash, get()) }
}

val viewModelModule = module {
    factory { (contactHash: String) ->
        ConversationViewModel(
            repository = get(),
            contactHash = contactHash,
            observeScope = get<CoroutineScope>()
        )
    }
    factory {
        ConnectivityViewModel(
            client = get(),
            configRepository = get(),
            observeScope = get<CoroutineScope>()
        )
    }
}

fun networkModule() = module {
    single<ReticulumClient> {
        ReticulumClientImpl(
            config = ReticulumConfig(),
            scope = get<CoroutineScope>(),
            messageRepository = getOrNull(), // Break circular dependency
            audioEngine = getOrNull() // Optional audio engine for voice calls
        )
    }
}

/**
 * Voice call module for real-time communication functionality.
 * Provides audio engine and voice call management.
 */
fun voiceCallModule() = module {
    // Audio Engine (platform-specific implementations provided per platform)
    single { com.shannon.audio.AudioEngine(
        recorder = get(),
        player = get(),
        codec = getOrNull(),
        packetCollector = getOrNull()
    )}

    // Voice Call Manager with audio integration
    single { VoiceCallManagerIntegrated(
        client = get(),
        localHash = get(), // Local identity hash
        audioEngine = get(),
        scope = get<CoroutineScope>()
    )}
}
