package com.smartmanager.ai.core.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.smartmanager.ai.core.conversation.ConvState.*

enum class ConvState { STANDBY, WAKE_DETECTED, LISTENING, PROCESSING, THINKING, SPEAKING, EXECUTING, WAITING_CONFIRMATION, ERROR, QUIET, SHUTDOWN }

/** Validates conversation transitions; illegal ones are recorded in [violations] and rejected. */
class ConversationStateMachine(initial: ConvState = ConvState.STANDBY) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<ConvState> = _state
    val violations = mutableListOf<Pair<ConvState, ConvState>>()

    fun transition(to: ConvState): Boolean {
        val from = _state.value
        if (from == to) return true
        if (!canTransition(from, to)) { violations += from to to; return false }
        _state.value = to
        return true
    }

    companion object {
        private val table: Map<ConvState, Set<ConvState>> = mapOf(
            STANDBY to setOf(WAKE_DETECTED, LISTENING, QUIET),
            WAKE_DETECTED to setOf(LISTENING, SPEAKING, ERROR),
            LISTENING to setOf(PROCESSING, SPEAKING, ERROR, QUIET),
            PROCESSING to setOf(THINKING, EXECUTING, SPEAKING, WAITING_CONFIRMATION, LISTENING, ERROR),
            THINKING to setOf(EXECUTING, SPEAKING, ERROR, LISTENING, WAITING_CONFIRMATION),
            EXECUTING to setOf(SPEAKING, WAITING_CONFIRMATION, ERROR, LISTENING, EXECUTING),
            SPEAKING to setOf(LISTENING, EXECUTING, WAITING_CONFIRMATION, QUIET, ERROR, THINKING, PROCESSING, SPEAKING),
            WAITING_CONFIRMATION to setOf(LISTENING, SPEAKING, PROCESSING, EXECUTING, ERROR),
            ERROR to setOf(LISTENING, SPEAKING),
            QUIET to emptySet(),
            SHUTDOWN to emptySet(),
        )
        /** Any state may fall back to STANDBY, QUIET or SHUTDOWN (emergency/timeout paths); SHUTDOWN/QUIET can restart only via STANDBY. */
        fun canTransition(from: ConvState, to: ConvState): Boolean {
            if (to == SHUTDOWN || to == STANDBY) return true
            if (from == SHUTDOWN || from == QUIET) return to == STANDBY
            return to in table.getValue(from)
        }
    }
}
