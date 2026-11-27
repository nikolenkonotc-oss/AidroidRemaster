package app.aidroid.remaster.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE isArchived = 0 AND deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActiveChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE isArchived = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeArchivedChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChat(chatId: Long): ChatEntity?

    @Query("SELECT * FROM chats WHERE syncId = :syncId LIMIT 1")
    suspend fun getChatBySyncId(syncId: String): ChatEntity?

    @Query("SELECT * FROM chats")
    suspend fun getAllChats(): List<ChatEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY createdAt ASC, id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND deletedAt IS NULL")
    suspend fun countMessages(chatId: Long): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessages(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesForSync(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getMessagesForInference(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE syncId = :syncId LIMIT 1")
    suspend fun getMessageBySyncId(syncId: String): MessageEntity?

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Insert
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun renameChat(chatId: Long, title: String, updatedAt: Long)

    @Query("UPDATE chats SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun setPinned(chatId: Long, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE chats SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun setArchived(chatId: Long, archived: Boolean, updatedAt: Long)

    @Query("UPDATE chats SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :chatId")
    suspend fun softDeleteChat(chatId: Long, deletedAt: Long)

    @Query("UPDATE messages SET deletedAt = :deletedAt WHERE id = :messageId")
    suspend fun softDeleteMessage(messageId: Long, deletedAt: Long)

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE id = :chatId")
    suspend fun touchChat(chatId: Long, updatedAt: Long)
}
