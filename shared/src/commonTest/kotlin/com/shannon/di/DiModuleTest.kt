package com.shannon.di

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.shannon.db.ShannonDatabase
import com.shannon.db.SqlDelightContactRepository
import com.shannon.db.SqlDelightConfigRepository
import com.shannon.domain.repository.ConfigRepository
import com.shannon.domain.repository.ContactRepository
import com.shannon.domain.repository.MessageRepository
import com.shannon.network.FakeReticulumClient
import com.shannon.network.ReticulumClient
import com.shannon.viewmodel.ConnectivityViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiModuleTest : KoinTest {

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single {
                        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                        ShannonDatabase.Schema.create(driver)
                        ShannonDatabase(driver)
                    }
                    single<ReticulumClient> { FakeReticulumClient() }
                    single<CoroutineScope> { CoroutineScope(Dispatchers.Default) }
                },
                repositoryModule,
                messageRepositoryModule("test_local_hash_1234567890abcdef1234567890abcdef12"),
                viewModelModule
            )
        }
    }

    @AfterTest
    fun teardown() {
        stopKoin()
    }

    @Test
    fun `database is provided`() {
        val db: ShannonDatabase by inject()
        assertNotNull(db)
    }

    @Test
    fun `contact repository is provided`() {
        val repo: ContactRepository by inject()
        assertNotNull(repo)
        assertTrue(repo is SqlDelightContactRepository)
    }

    @Test
    fun `config repository is provided`() {
        val repo: ConfigRepository by inject()
        assertNotNull(repo)
        assertTrue(repo is SqlDelightConfigRepository)
    }

    @Test
    fun `message repository is provided`() {
        val repo: MessageRepository by inject()
        assertNotNull(repo)
    }

    @Test
    fun `connectivity view model is provided`() {
        val vm: ConnectivityViewModel by inject()
        assertNotNull(vm)
    }
}
