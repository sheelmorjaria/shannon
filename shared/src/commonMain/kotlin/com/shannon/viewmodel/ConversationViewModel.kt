package com.shannon.viewmodel

import com.shannon.domain.model.Message
import com.shannon.domain.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the conversation screen.
 */
sealed class ConversationUiState {
    /** Initial loading state. */
    data object Loading : ConversationUiState()

    /** Loaded state with messages and input field. */
    data class Success(
        val messages: List<Message>,
        val inputText: String = ""
    ) : ConversationUiState()

    /** Error state. */
    data class Error(val message: String) : ConversationUiState()
}

/**
 * ViewModel for a single conversation with a contact.
 * Manages message list, user input, and send actions.
 *
 * @param observeScope Scope used for long-lived observation (collecting flows).
 *   In tests, pass `TestScope.backgroundScope` so collectors are auto-cancelled.
 * @param actionScope Scope used for one-shot actions like sending. Defaults to observeScope.
 */
class ConversationViewModel(
    private val repository: MessageRepository,
    private val contactHash: String,
    private val observeScope: CoroutineScope,
    private val actionScope: CoroutineScope = observeScope
) {
    private val _uiState = MutableStateFlow<ConversationUiState>(ConversationUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var observeJob: Job? = null

    /** Start observing messages. Call after construction. */
    fun startObserving() {
        observeJob = observeScope.launch {
            repository.observeMessages(contactHash).collect { messages ->
                val current = _uiState.value
                val currentInput = if (current is ConversationUiState.Success) current.inputText else ""
                _uiState.value = ConversationUiState.Success(
                    messages = messages,
                    inputText = currentInput
                )
            }
        }
    }

    /** Update the text input field. */
    fun onInputChanged(text: String) {
        val current = _uiState.value
        if (current is ConversationUiState.Success) {
            _uiState.value = current.copy(inputText = text)
        }
    }

    /** Send the current input text as a message. */
    fun send() {
        val current = _uiState.value
        if (current !is ConversationUiState.Success) return
        val text = current.inputText.trim()
        if (text.isBlank()) return

        actionScope.launch {
            val message = Message(
                destinationHash = contactHash,
                content = text,
                timestamp = System.currentTimeMillis()
            )
            repository.send(message)
        }

        // Clear input immediately (optimistic UI update)
        _uiState.value = current.copy(inputText = "")
    }
}
