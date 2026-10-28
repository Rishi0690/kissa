package com.kissa.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_stories")
data class CustomStoryEntity(@PrimaryKey val id: String, val json: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "playthroughs")
data class PlaythroughEntity(
    @PrimaryKey val storyId: String,
    val mood: Int,
    val relationship: Int,
    val chapter: Int,
    val summary: String = "",
    val summarizedUpToId: Long = 0,
    val turnCount: Int = 0,
    val lastPlayed: Long = System.currentTimeMillis(),
    val ended: Boolean = false,
    val endingTitle: String? = null,
    val language: String? = null,
)

@Entity(tableName = "messages", indices = [Index("storyId")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val role: String, // PLAYER / CHARACTER / NARRATOR
    val speaker: String,
    val text: String,
    val action: String? = null,
    val emotion: String? = null,
    val gesture: String? = null,
    val audioPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val hidden: Boolean = false,
)

@Entity(tableName = "memory_notes", indices = [Index("storyId")])
data class MemoryNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "checkpoints", indices = [Index("storyId")])
data class CheckpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val label: String,
    val chapter: Int,
    val mood: Int,
    val relationship: Int,
    val summary: String,
    val summarizedUpToId: Long,
    val lastMessageId: Long,
    val turnCount: Int,
    val noteCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val storyId: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storyId: String,
    val messageId: Long?,
    val reason: String,
    val excerpt: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface StoryDao {
    // custom stories
    @Query("SELECT * FROM custom_stories ORDER BY updatedAt DESC") fun customStories(): Flow<List<CustomStoryEntity>>
    @Query("SELECT * FROM custom_stories WHERE id = :id") suspend fun customStory(id: String): CustomStoryEntity?
    @Upsert suspend fun upsertCustom(e: CustomStoryEntity)
    @Query("DELETE FROM custom_stories WHERE id = :id") suspend fun deleteCustom(id: String)

    // playthroughs
    @Query("SELECT * FROM playthroughs WHERE storyId = :id") suspend fun playthrough(id: String): PlaythroughEntity?
    @Query("SELECT * FROM playthroughs WHERE storyId = :id") fun playthroughFlow(id: String): Flow<PlaythroughEntity?>
    @Query("SELECT * FROM playthroughs ORDER BY lastPlayed DESC") fun playthroughs(): Flow<List<PlaythroughEntity>>
    @Upsert suspend fun upsertPlaythrough(e: PlaythroughEntity)
    @Query("DELETE FROM playthroughs WHERE storyId = :id") suspend fun deletePlaythrough(id: String)

    // messages
    @Insert suspend fun insertMessage(m: MessageEntity): Long
    @Query("SELECT * FROM messages WHERE storyId = :id AND hidden = 0 ORDER BY id ASC") suspend fun messages(id: String): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE storyId = :id ORDER BY id ASC") fun messagesFlow(id: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE storyId = :id AND id > :afterId AND hidden = 0 ORDER BY id ASC") suspend fun messagesAfter(id: String, afterId: Long): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE storyId = :id AND id > :fromId AND id <= :toId AND hidden = 0 ORDER BY id ASC") suspend fun messagesBetween(id: String, fromId: Long, toId: Long): List<MessageEntity>
    @Query("SELECT audioPath FROM messages WHERE storyId = :id AND id > :afterId AND audioPath IS NOT NULL") suspend fun audioPathsAfter(id: String, afterId: Long): List<String>
    @Query("DELETE FROM messages WHERE storyId = :id AND id > :afterId") suspend fun deleteMessagesAfter(id: String, afterId: Long)
    @Query("DELETE FROM messages WHERE storyId = :id") suspend fun deleteMessages(id: String)
    @Query("UPDATE messages SET hidden = 1 WHERE id = :messageId") suspend fun hideMessage(messageId: Long)
    @Query("SELECT COUNT(*) FROM messages WHERE storyId = :id AND hidden = 0") suspend fun messageCount(id: String): Int

    // notes
    @Insert suspend fun insertNote(n: MemoryNoteEntity): Long
    @Query("SELECT * FROM memory_notes WHERE storyId = :id ORDER BY id ASC") suspend fun notes(id: String): List<MemoryNoteEntity>
    @Query("SELECT * FROM memory_notes WHERE storyId = :id ORDER BY id ASC") fun notesFlow(id: String): Flow<List<MemoryNoteEntity>>
    @Query("DELETE FROM memory_notes WHERE id = :noteId") suspend fun deleteNote(noteId: Long)
    @Query("DELETE FROM memory_notes WHERE storyId = :id") suspend fun deleteNotes(id: String)
    @Query("DELETE FROM memory_notes WHERE storyId = :id AND id NOT IN (SELECT id FROM memory_notes WHERE storyId = :id ORDER BY id ASC LIMIT :keep)")
    suspend fun trimNotes(id: String, keep: Int)

    // checkpoints
    @Insert suspend fun insertCheckpoint(c: CheckpointEntity): Long
    @Query("SELECT * FROM checkpoints WHERE storyId = :id ORDER BY id DESC") fun checkpointsFlow(id: String): Flow<List<CheckpointEntity>>
    @Query("SELECT * FROM checkpoints WHERE id = :cid") suspend fun checkpoint(cid: Long): CheckpointEntity?
    @Query("SELECT * FROM checkpoints ORDER BY id DESC") fun allCheckpoints(): Flow<List<CheckpointEntity>>
    @Query("DELETE FROM checkpoints WHERE storyId = :id AND lastMessageId > :afterId") suspend fun deleteCheckpointsAfter(id: String, afterId: Long)
    @Query("DELETE FROM checkpoints WHERE storyId = :id") suspend fun deleteCheckpoints(id: String)
    @Query("DELETE FROM checkpoints WHERE id = :cid") suspend fun deleteCheckpoint(cid: Long)

    // favorites
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC") fun favorites(): Flow<List<FavoriteEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE storyId = :id") suspend fun removeFavorite(id: String)

    // reports
    @Insert suspend fun insertReport(r: ReportEntity): Long
    @Query("SELECT COUNT(*) FROM reports") fun reportCount(): Flow<Int>

    @Query("DELETE FROM messages") suspend fun wipeMessages()
    @Query("DELETE FROM memory_notes") suspend fun wipeNotes()
    @Query("DELETE FROM checkpoints") suspend fun wipeCheckpoints()
    @Query("DELETE FROM playthroughs") suspend fun wipePlaythroughs()
    @Query("DELETE FROM favorites") suspend fun wipeFavorites()
    @Query("DELETE FROM custom_stories") suspend fun wipeCustom()
    @Query("DELETE FROM reports") suspend fun wipeReports()
}

@Database(
    entities = [CustomStoryEntity::class, PlaythroughEntity::class, MessageEntity::class, MemoryNoteEntity::class,
        CheckpointEntity::class, FavoriteEntity::class, ReportEntity::class],
    version = 1, exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): StoryDao

    companion object {
        fun create(ctx: Context): AppDatabase =
            Room.databaseBuilder(ctx, AppDatabase::class.java, "kissa.db").fallbackToDestructiveMigration().build()
    }
}
