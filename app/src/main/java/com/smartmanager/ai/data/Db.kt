package com.smartmanager.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memory", indices = [Index(value = ["category", "key"], unique = true)])
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** PROFILE, LANGUAGE, PREFERENCE, IMPORTANT, TASK */
    val category: String,
    val key: String,
    /** Plain text, or Base64(AES-GCM) when [encrypted]. */
    val value: String,
    val encrypted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "contact_alias")
data class ContactAlias(@PrimaryKey val alias: String, val contactName: String, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "turns")
data class TurnEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val timeMs: Long, val fromUser: Boolean, val text: String, val lang: String)

@Entity(tableName = "audit")
data class AuditEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val timeMs: Long, val action: String, val risk: String, val outcome: String, val summary: String, val confirmed: Boolean)

@Entity(tableName = "reminders")
data class ReminderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val triggerAtMs: Long, val text: String, val done: Boolean = false)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory ORDER BY category, key") fun observeAll(): Flow<List<MemoryItem>>
    @Query("SELECT * FROM memory WHERE category = :c AND key = :k LIMIT 1") suspend fun find(c: String, k: String): MemoryItem?
    @Query("SELECT * FROM memory WHERE category = :c") suspend fun byCategory(c: String): List<MemoryItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(i: MemoryItem)
    @Query("DELETE FROM memory WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM memory") suspend fun clear()

    @Query("SELECT * FROM contact_alias ORDER BY alias") fun observeAliases(): Flow<List<ContactAlias>>
    @Query("SELECT * FROM contact_alias") suspend fun aliases(): List<ContactAlias>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAlias(a: ContactAlias)
    @Query("DELETE FROM contact_alias WHERE alias = :alias") suspend fun deleteAlias(alias: String)
    @Query("DELETE FROM contact_alias") suspend fun clearAliases()

    @Insert suspend fun addTurn(t: TurnEntity)
    @Query("SELECT * FROM turns ORDER BY timeMs DESC LIMIT :n") fun observeTurns(n: Int): Flow<List<TurnEntity>>
    @Query("DELETE FROM turns WHERE timeMs < :before") suspend fun purgeTurns(before: Long): Int
    @Query("DELETE FROM turns") suspend fun clearTurns()

    @Insert suspend fun addAudit(a: AuditEntity)
    @Query("SELECT * FROM audit ORDER BY timeMs DESC LIMIT :n") fun observeAudit(n: Int): Flow<List<AuditEntity>>
    @Query("DELETE FROM audit") suspend fun clearAudit()

    @Insert suspend fun addReminder(r: ReminderEntity): Long
    @Query("SELECT * FROM reminders WHERE done = 0 AND triggerAtMs > :now") suspend fun pendingReminders(now: Long): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: Long): ReminderEntity?
    @Query("UPDATE reminders SET done = 1 WHERE id = :id") suspend fun markDone(id: Long)
    @Query("DELETE FROM reminders") suspend fun clearReminders()
}

@Database(entities = [MemoryItem::class, ContactAlias::class, TurnEntity::class, AuditEntity::class, ReminderEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): MemoryDao }
