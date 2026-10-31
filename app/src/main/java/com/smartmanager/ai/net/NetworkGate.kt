package com.smartmanager.ai.net

import com.smartmanager.ai.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

class NetworkDisabledException(what: String) : Exception("Network access is disabled in settings — $what")

/** Every network use in the app goes through this gate, so "disable all online connectivity" is enforced in one place. */
@Singleton
class NetworkGate @Inject constructor(private val settings: SettingsStore) {
    suspend fun allowed(): Boolean = settings.data.first().networkAllowed
    suspend fun require(what: String) { if (!allowed()) throw NetworkDisabledException(what) }
    fun requireBlocking(what: String) = runBlocking { require(what) }
}
