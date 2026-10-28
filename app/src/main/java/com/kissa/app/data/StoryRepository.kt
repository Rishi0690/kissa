package com.kissa.app.data

import android.content.Context
import com.kissa.app.content.SeedContent
import com.kissa.app.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class StoryRepository(private val ctx: Context, val db: AppDatabase) {
    val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val allStories: Flow<List<Story>> = dao.customStories().map { list ->
        list.mapNotNull { decode(it.json) } + SeedContent.stories
    }

    fun decode(s: String): Story? = runCatching { json.decodeFromString<Story>(s) }.getOrNull()

    suspend fun story(id: String): Story? = SeedContent.byId(id) ?: dao.customStory(id)?.let { decode(it.json) }

    suspend fun saveCustom(story: Story) {
        val now = System.currentTimeMillis()
        val existing = dao.customStory(story.id)
        dao.upsertCustom(CustomStoryEntity(story.id, json.encodeToString(story.copy(isCustom = true)), existing?.createdAt ?: now, now))
    }

    suspend fun deleteCustom(id: String) {
        resetProgress(id)
        dao.removeFavorite(id)
        dao.deleteCustom(id)
    }

    suspend fun ensurePlaythrough(story: Story): PlaythroughEntity =
        dao.playthrough(story.id) ?: PlaythroughEntity(story.id, story.startingMood, story.startingRelationship, 0).also { dao.upsertPlaythrough(it) }

    suspend fun toggleFavorite(id: String, fav: Boolean) = if (fav) dao.addFavorite(FavoriteEntity(id)) else dao.removeFavorite(id)

    fun audioDir(): File = File(ctx.filesDir, "voice").apply { mkdirs() }

    suspend fun resetProgress(id: String) = withContext(Dispatchers.IO) {
        dao.audioPathsAfter(id, 0).forEach { runCatching { File(it).delete() } }
        dao.deleteMessages(id); dao.deleteNotes(id); dao.deleteCheckpoints(id); dao.deletePlaythrough(id)
    }

    suspend fun createCheckpoint(storyId: String, label: String): Long? {
        val p = dao.playthrough(storyId) ?: return null
        val msgs = dao.messages(storyId)
        return dao.insertCheckpoint(
            CheckpointEntity(
                storyId = storyId, label = label, chapter = p.chapter, mood = p.mood, relationship = p.relationship,
                summary = p.summary, summarizedUpToId = p.summarizedUpToId, lastMessageId = msgs.lastOrNull()?.id ?: 0,
                turnCount = p.turnCount, noteCount = dao.notes(storyId).size,
            )
        )
    }

    /** Branch from a checkpoint: drops everything after it and restores the story state. */
    suspend fun restoreCheckpoint(cid: Long): String? = withContext(Dispatchers.IO) {
        val c = dao.checkpoint(cid) ?: return@withContext null
        dao.audioPathsAfter(c.storyId, c.lastMessageId).forEach { runCatching { File(it).delete() } }
        dao.deleteMessagesAfter(c.storyId, c.lastMessageId)
        dao.trimNotes(c.storyId, c.noteCount)
        dao.deleteCheckpointsAfter(c.storyId, c.lastMessageId)
        val p = dao.playthrough(c.storyId)
        dao.upsertPlaythrough(
            (p ?: PlaythroughEntity(c.storyId, c.mood, c.relationship, c.chapter)).copy(
                mood = c.mood, relationship = c.relationship, chapter = c.chapter, summary = c.summary,
                summarizedUpToId = c.summarizedUpToId, turnCount = c.turnCount, ended = false, endingTitle = null,
                lastPlayed = System.currentTimeMillis(),
            )
        )
        c.storyId
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        dao.wipeNotes()
        // keep transcripts but forget summaries
        audioDir().listFiles()?.forEach { it.delete() }
        dao.wipeMessages(); dao.wipeCheckpoints(); dao.wipePlaythroughs()
    }

    suspend fun clearEverything() = withContext(Dispatchers.IO) {
        clearAllMemories(); dao.wipeFavorites(); dao.wipeCustom(); dao.wipeReports()
    }
}
