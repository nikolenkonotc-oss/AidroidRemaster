package app.aidroid.remaster.data

import app.aidroid.remaster.data.local.ChatDao
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val dao: ChatDao) {
    fun activeChats(): Flow<List<ChatEntity>> = dao.observeActiveChats()
    fun archivedChats(): Flow<List<ChatEntity>> = dao.observeArchivedChats()
    fun messages(chatId: Long): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    suspend fun createChat(): Long {
        val now = System.currentTimeMillis()
        return dao.insertChat(ChatEntity(createdAt = now, updatedAt = now))
    }

    suspend fun sendUserMessage(chatId: Long, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val now = System.currentTimeMillis()
        val isFirstMessage = dao.countMessages(chatId) == 0
        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = "user",
                content = cleanText,
                createdAt = now
            )
        )
        if (isFirstMessage) {
            val title = cleanText.replace("\\s+".toRegex(), " ").take(42)
                .let { if (cleanText.length > 42) "$it…" else it }
            dao.renameChat(chatId, title, now)
        } else {
            dao.touchChat(chatId, now)
        }
    }

    suspend fun rename(chatId: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) dao.renameChat(chatId, clean.take(64), System.currentTimeMillis())
    }

    suspend fun setPinned(chat: ChatEntity, pinned: Boolean) {
        dao.setPinned(chat.id, pinned)
    }

    suspend fun setArchived(chat: ChatEntity, archived: Boolean) {
        dao.setArchived(chat.id, archived)
    }

    suspend fun delete(chat: ChatEntity) = dao.deleteChat(chat)
    suspend fun getChat(chatId: Long): ChatEntity? = dao.getChat(chatId)
}
