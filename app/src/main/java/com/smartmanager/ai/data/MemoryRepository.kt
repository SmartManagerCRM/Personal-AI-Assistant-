package com.smartmanager.ai.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartmanager.ai.security.SecretStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

object MemoryCategory { const val PROFILE = "PROFILE"; const val LANGUAGE = "LANGUAGE"; const val PREFERENCE = "PREFERENCE"; const val IMPORTANT = "IMPORTANT"; const val TASK = "TASK"
    val all = listOf(PROFILE, LANGUAGE, PREFERENCE, IMPORTANT, TASK) }

/** Persistent memory on Room; PROFILE / IMPORTANT values are encrypted at rest with the Keystore key. */
@Singleton
class MemoryRepository @Inject constructor(private val db: AppDatabase, private val secrets: SecretStore, private val settings: SettingsStore) {
    private val dao get() = db.dao()
    private val encryptedCategories = setOf(MemoryCategory.PROFILE, MemoryCategory.IMPORTANT)

    data class Entry(val id: Long, val category: String, val key: String, val value: String, val updatedAt: Long)

    fun observe(): Flow<List<MemoryItem>> = dao.observeAll()
    fun decode(i: MemoryItem): String = if (i.encrypted) runCatching { secrets.decrypt(i.value) }.getOrDefault("⚠ unreadable") else i.value

    suspend fun put(category: String, key: String, value: String) {
        val enc = category in encryptedCategories
        dao.upsert(MemoryItem(category = category, key = key, value = if (enc) secrets.encrypt(value) else value, encrypted = enc))
    }
    suspend fun get(category: String, key: String): String? = dao.find(category, key)?.let { decode(it) }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clearAll() { dao.clear(); dao.clearAliases(); dao.clearTurns() }

    fun aliases() = dao.observeAliases()
    suspend fun addAlias(alias: String, contact: String) = dao.upsertAlias(ContactAlias(alias.trim().lowercase(), contact.trim()))
    suspend fun deleteAlias(alias: String) = dao.deleteAlias(alias)
    suspend fun aliasMap(): Map<String, String> = dao.aliases().associate { it.alias to it.contactName }

    suspend fun recordTurn(fromUser: Boolean, text: String, lang: String, timeMs: Long) = dao.addTurn(TurnEntity(timeMs = timeMs, fromUser = fromUser, text = text, lang = lang))
    fun turns(n: Int = 200) = dao.observeTurns(n)
    suspend fun purgeOld(): Int {
        val days = settings.data.first().retentionDays
        return if (days <= 0) 0 else dao.purgeTurns(System.currentTimeMillis() - days * 86_400_000L)
    }

    @HiltWorker
    class RetentionWorker @AssistedInject constructor(@Assisted ctx: Context, @Assisted p: WorkerParameters, private val repo: MemoryRepository) : CoroutineWorker(ctx, p) {
        override suspend fun doWork(): Result { repo.purgeOld(); return Result.success() }
    }
}
