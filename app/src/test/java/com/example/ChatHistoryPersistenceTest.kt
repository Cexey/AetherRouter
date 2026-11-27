package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.LiveClockProvider
import com.example.data.local.AetherDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatHistoryPersistenceTest {

    private lateinit var db: AetherDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AetherDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `switching models and updating conversation via upsert never cascade-deletes messages`() = runTest {
        val convDao = db.conversationDao()
        val msgDao = db.messageDao()

        val conv = ConversationEntity(
            id = "conv-1",
            title = "Android Debugging",
            modelId = "anthropic/claude-sonnet-4",
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )
        convDao.upsertConversation(conv)

        msgDao.upsertMessage(
            MessageEntity(
                id = "msg-1",
                conversationId = conv.id,
                role = "user",
                content = "Why did my messages disappear?",
                createdAt = 1_100L,
            ),
        )
        msgDao.upsertMessage(
            MessageEntity(
                id = "msg-2",
                conversationId = conv.id,
                role = "assistant",
                content = "Because REPLACE triggers ON DELETE CASCADE in SQLite.",
                modelUsed = "anthropic/claude-sonnet-4",
                createdAt = 1_200L,
            ),
        )

        // Simulate switching models mid-conversation, toggling web search, pinning, and renaming
        convDao.upsertConversation(
            conv.copy(
                modelId = "google/gemini-2.5-pro",
                webSearchEnabled = false,
                isPinned = true,
                updatedAt = 2_000L,
            ),
        )

        val remainingMessages = msgDao.getMessagesOnce(conv.id)
        assertEquals(2, remainingMessages.size)
        assertEquals("msg-1", remainingMessages[0].id)
        assertEquals("msg-2", remainingMessages[1].id)

        val rowsWithCounts = convDao.observeAllConversationsWithCounts().first()
        assertEquals(1, rowsWithCounts.size)
        assertEquals("google/gemini-2.5-pro", rowsWithCounts[0].conversation.modelId)
        assertEquals(2, rowsWithCounts[0].messageCount)

        // Explicit deletion MUST still cascade-delete child messages cleanly
        convDao.deleteConversationById(conv.id)
        assertTrue(msgDao.getMessagesOnce(conv.id).isEmpty())
    }

    @Test
    fun `pruneEmptyUnpinnedConversations removes ghost drafts while preserving real and pinned chats`() = runTest {
        val convDao = db.conversationDao()
        val msgDao = db.messageDao()

        // 1. Empty unpinned ghost draft -> should be pruned
        convDao.upsertConversation(
            ConversationEntity(id = "ghost-1", title = "New Conversation", modelId = "openai/gpt-4.1"),
        )
        // 2. Real chat with 1 message -> must stay
        convDao.upsertConversation(
            ConversationEntity(id = "real-1", title = "Saved Chat", modelId = "openai/gpt-4.1"),
        )
        msgDao.upsertMessage(
            MessageEntity(id = "m1", conversationId = "real-1", role = "user", content = "Hello"),
        )
        // 3. Pinned empty chat -> must stay
        convDao.upsertConversation(
            ConversationEntity(id = "pinned-1", title = "Pinned Workspace", modelId = "openai/gpt-4.1", isPinned = true),
        )

        val deletedCount = convDao.pruneEmptyUnpinnedConversations()
        assertEquals(1, deletedCount)

        val remainingIds = convDao.observeAllConversationsWithCounts().first().map { it.conversation.id }.toSet()
        assertEquals(setOf("real-1", "pinned-1"), remainingIds)
    }

    @Test
    fun `LiveClockProvider formatHistoryTimestamp formats relative and calendar labels cleanly`() {
        val now = 1_764_172_800_000L // Fixed reference time
        assertEquals("Just now", LiveClockProvider.formatHistoryTimestamp(now - 15_000L, nowMillis = now))
        assertEquals("12m ago", LiveClockProvider.formatHistoryTimestamp(now - 12 * 60_000L, nowMillis = now))
        assertEquals("Yesterday", LiveClockProvider.formatHistoryTimestamp(now - 24 * 3_600_000L, nowMillis = now))
    }
}
