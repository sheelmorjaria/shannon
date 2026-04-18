package com.shannon.viewmodel

import app.cash.turbine.test
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.domain.repository.MessageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fake MessageRepository for ViewModel tests.
 */
class FakeMessageRepository : MessageRepository {

    private val _messagesByContact = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    var sendCallCount = 0
        private set
    var lastSentMessage: Message? = null
        private set

    override fun observeMessages(contactHash: String): Flow<List<Message>> {
        return getFlow(contactHash)
    }

    override suspend fun getMessages(contactHash: String, limit: Long, offset: Long): List<Message> {
        return getFlow(contactHash).value.drop(offset.toInt()).take(limit.toInt())
    }

    override suspend fun saveMessage(message: Message) {
        val flow = getFlow(message.destinationHash)
        flow.value = listOf(message) + flow.value
    }

    override suspend fun updateMessage(message: Message) {
        val flow = getFlow(message.destinationHash)
        val updated = flow.value.map { if (it.id == message.id) message else it }
        flow.value = updated
    }

    override suspend fun deleteMessage(id: String) {
        _messagesByContact.forEach { (_, flow) ->
            flow.value = flow.value.filter { it.id != id }
        }
    }

    override suspend fun send(message: Message): Message {
        sendCallCount++
        lastSentMessage = message
        return message.transitionTo(MessageState.QUEUED)
            .transitionTo(MessageState.SENDING)
            .transitionTo(MessageState.SENT)
    }

    /** Simulate a message appearing in the contact's message list. */
    fun addMessage(contactHash: String, message: Message) {
        val flow = getFlow(contactHash)
        flow.value = listOf(message) + flow.value
    }

    private fun getFlow(contactHash: String): MutableStateFlow<List<Message>> {
        return _messagesByContact.getOrPut(contactHash) { MutableStateFlow(emptyList()) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val contactHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    private fun createVm(
        repo: FakeMessageRepository = FakeMessageRepository(),
        testScope: TestScope
    ): Pair<FakeMessageRepository, ConversationViewModel> {
        val vm = ConversationViewModel(
            repository = repo,
            contactHash = contactHash,
            observeScope = testScope.backgroundScope,
            actionScope = testScope
        )
        vm.startObserving()
        return repo to vm
    }

    @Test
    fun `loading state emits Loading then Success with messages`() = runTest {
        val repo = FakeMessageRepository()
        val message = Message(
            destinationHash = contactHash,
            content = "Hello",
            timestamp = System.currentTimeMillis(),
            state = MessageState.SENT
        )
        repo.addMessage(contactHash, message)

        val (_, vm) = createVm(repo, this)

        vm.uiState.test {
            // First emission should be Loading
            val loading = awaitItem()
            assertTrue(loading is ConversationUiState.Loading)

            // Then Success with messages
            val success = awaitItem()
            assertTrue(success is ConversationUiState.Success)
            assertEquals(1, success.messages.size)
            assertEquals("Hello", success.messages[0].content)
        }
    }

    @Test
    fun `empty state shows no messages`() = runTest {
        val (_, vm) = createVm(testScope = this)

        vm.uiState.test {
            // Skip Loading
            awaitItem()
            val state = awaitItem()
            assertTrue(state is ConversationUiState.Success)
            assertTrue(state.messages.isEmpty())
        }
    }

    @Test
    fun `user input updates text field`() = runTest {
        val (_, vm) = createVm(testScope = this)

        vm.uiState.test {
            // Skip to Success state
            awaitItem() // Loading
            awaitItem() // Success (empty)

            vm.onInputChanged("Hello world")
            val updated = awaitItem()
            assertTrue(updated is ConversationUiState.Success)
            assertEquals("Hello world", updated.inputText)
        }
    }

    @Test
    fun `send action calls repository and clears input`() = runTest {
        val (repo, vm) = createVm(testScope = this)

        vm.uiState.test {
            // Skip to Success state
            awaitItem() // Loading
            awaitItem() // Success

            // Type a message
            vm.onInputChanged("Test message")
            awaitItem() // input updated

            // Send
            vm.send()
            testScheduler.advanceUntilIdle()

            val sentState = awaitItem()
            assertTrue(sentState is ConversationUiState.Success)
            assertEquals("", sentState.inputText, "Input should be cleared after send")

            // Verify repository was called exactly once
            assertEquals(1, repo.sendCallCount)
            assertEquals("Test message", repo.lastSentMessage?.content)
        }
    }

    @Test
    fun `send does nothing when input is blank`() = runTest {
        val (repo, vm) = createVm(testScope = this)

        vm.uiState.test {
            awaitItem() // Loading
            awaitItem() // Success

            vm.onInputChanged("   ")
            awaitItem() // whitespace input

            vm.send()
            testScheduler.advanceUntilIdle()

            assertEquals(0, repo.sendCallCount, "Send should not be called for blank input")
        }
    }
}
