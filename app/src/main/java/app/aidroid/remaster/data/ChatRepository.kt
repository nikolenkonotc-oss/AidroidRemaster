package app.aidroid.remaster.data

import app.aidroid.remaster.data.local.ChatDao
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import app.aidroid.remaster.data.attachments.AttachmentJson
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val dao: ChatDao,
    private val attachmentStorage: AttachmentStorage
) {
    fun activeChats(): Flow<List<ChatEntity>> = dao.observeActiveChats()
    fun archivedChats(): Flow<List<ChatEntity>> = dao.observeArchivedChats()
    fun messages(chatId: Long): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    suspend fun createChat(): Long {
        val now = System.currentTimeMillis()
        return dao.insertChat(ChatEntity(createdAt = now, updatedAt = now))
    }

    suspend fun sendUserMessage(chatId: Long, text: String, attachments: List<StoredAttachment> = emptyList()) {
        val cleanText = text.trim()
        if (cleanText.isEmpty() && attachments.isEmpty()) return
        val now = System.currentTimeMillis()
        val isFirstMessage = dao.countMessages(chatId) == 0
        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = "user",
                content = cleanText,
                createdAt = now,
                attachmentsJson = AttachmentJson.encode(attachments)
            )
        )
        if (isFirstMessage) {
            val titleSource = cleanText.ifBlank { attachments.firstOrNull()?.displayName ?: "Вложение" }
            val title = titleSource.replace("\\s+".toRegex(), " ").take(42)
                .let { if (titleSource.length > 42) "$it…" else it }
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

    suspend fun delete(chat: ChatEntity) {
        val storedAttachments = dao.getMessages(chat.id)
            .flatMap { AttachmentJson.decode(it.attachmentsJson) }
        withContext(Dispatchers.IO) {
            storedAttachments.forEach(attachmentStorage::delete)
        }
        dao.deleteChat(chat)
    }

    suspend fun deleteMessage(message: MessageEntity) {
        dao.deleteMessage(message)
        withContext(Dispatchers.IO) {
            AttachmentJson.decode(message.attachmentsJson).forEach(attachmentStorage::delete)
        }
    }

    suspend fun getChat(chatId: Long): ChatEntity? = dao.getChat(chatId)
}
