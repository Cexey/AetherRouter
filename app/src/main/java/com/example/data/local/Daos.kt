package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY updatedAt DESC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllConversationsOnce(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    suspend fun getAllMessagesOnce(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt > :afterEpochMillis")
    suspend fun deleteMessagesAfter(conversationId: String, afterEpochMillis: Long)
}

@Dao
interface CachedModelDao {
    @Query("SELECT * FROM cached_models ORDER BY isFavorite DESC, createdEpochSeconds DESC, name ASC")
    fun observeAllModels(): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): CachedModelEntity?

    @Query("SELECT id FROM cached_models WHERE isFavorite = 1")
    suspend fun getFavoriteModelIds(): List<String>

    @Query("SELECT COUNT(*) FROM cached_models")
    suspend fun getModelCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(models: List<CachedModelEntity>)

    @Query("UPDATE cached_models SET isFavorite = :isFavorite WHERE id = :modelId")
    suspend fun setFavorite(modelId: String, isFavorite: Boolean)
}

@Dao
interface CustomStickerDao {
    @Query("SELECT * FROM custom_stickers ORDER BY createdAt DESC")
    fun observeStickers(): Flow<List<CustomStickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSticker(sticker: CustomStickerEntity)

    @Query("DELETE FROM custom_stickers WHERE id = :id")
    suspend fun deleteStickerById(id: String)
}

@Dao
interface PersonaCardDao {
    @Query("SELECT * FROM persona_cards ORDER BY updatedAt DESC")
    fun observeAllPersonas(): Flow<List<PersonaCardEntity>>

    @Query("SELECT * FROM persona_cards WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): PersonaCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersona(persona: PersonaCardEntity)

    @Query("DELETE FROM persona_cards WHERE id = :id")
    suspend fun deletePersonaById(id: String)
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY lastSyncedAt DESC, id ASC")
    fun observeAllServers(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers")
    suspend fun getAllServersOnce(): List<McpServerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertServer(server: McpServerEntity)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}
