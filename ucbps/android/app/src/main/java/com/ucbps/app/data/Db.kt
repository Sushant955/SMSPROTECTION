package com.ucbps.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events")
data class SecurityEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val kind: String,      // SMS | CALL
    val source: String,    // number / sender (stays on the device)
    val risk: Double,
    val action: String,
    val reason: String,
    val simulated: Boolean = false,
)

@Entity(tableName = "blocked")
data class BlockedNumber(@PrimaryKey val number: String, val addedAt: Long)

@Dao
interface UcbpsDao {
    @Insert fun insertEvent(e: SecurityEvent)
    @Query("SELECT * FROM events ORDER BY ts DESC LIMIT 300") fun events(): Flow<List<SecurityEvent>>
    @Query("SELECT * FROM events WHERE action != 'ALLOW' ORDER BY ts DESC LIMIT 100") fun threats(): Flow<List<SecurityEvent>>
    @Query("DELETE FROM events") fun clearEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun addBlocked(b: BlockedNumber)
    @Query("DELETE FROM blocked WHERE number = :n") fun removeBlocked(n: String)
    @Query("SELECT * FROM blocked ORDER BY addedAt DESC") fun blocked(): Flow<List<BlockedNumber>>
    @Query("SELECT * FROM blocked") fun blockedNow(): List<BlockedNumber>
}

@Database(entities = [SecurityEvent::class, BlockedNumber::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): UcbpsDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "ucbps.db").build().also { inst = it }
        }
    }
}
