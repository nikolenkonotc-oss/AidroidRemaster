package app.aidroid.remaster.data

import app.aidroid.remaster.data.local.ChatDao
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import app.aidroid.remaster.data.attachments.AttachmentJson
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import app.aidroid.remaster.sync.FirebaseCloudSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(
    private val dao: ChatDao,
    private val attachmentStorage: AttachmentStorage,
    private val cloudSync: FirebaseCloudSync? = null
) {
    fun activeChats(): Flow<List<ChatEntity>> = dao.observeActiveChats()
    fun archivedChats(): Flow<List<ChatEntity>> = dao.observeArchivedChats()
    fun messages(chatId: Long): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    suspend fun createChat(): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertChat(ChatEntity(createdAt = now, updatedAt = now))
        syncCloudSafely()
        return id
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
        syncCloudSafely()
    }

    suspend fun rename(chatId: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            dao.renameChat(chatId, clean.take(64), System.currentTimeMillis())
            syncCloudSafely()
        }
    }

    suspend fun setPinned(chat: ChatEntity, pinned: Boolean) {
        dao.setPinned(chat.id, pinned, System.currentTimeMillis())
        syncCloudSafely()
    }

    suspend fun setArchived(chat: ChatEntity, archived: Boolean) {
        dao.setArchived(chat.id, archived, System.currentTimeMillis())
        syncCloudSafely()
    }

    suspend fun delete(chat: ChatEntity) {
        // Keep a tombstone so a deletion is propagated to the user's other devices.
        dao.softDeleteChat(chat.id, System.currentTimeMillis())
        syncCloudSafely()
    }

    suspend fun deleteMessage(message: MessageEntity) {
        // Message tombstones prevent deleted messages from reappearing during cloud merge.
        dao.softDeleteMessage(message.id, System.currentTimeMillis())
        syncCloudSafely()
    }

    suspend fun getChat(chatId: Long): ChatEntity? = dao.getChat(chatId)

    suspend fun syncNow() {
        cloudSync?.syncChats(dao)
    }

    private suspend fun syncCloudSafely() {
        if (cloudSync?.isSignedIn == true) {
            runCatching { cloudSync.syncChats(dao) }
        }
    }
}
