package app.aidroid.remaster.data

import app.aidroid.remaster.data.local.ChatDao
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import app.aidroid.remaster.data.attachments.AttachmentJson
import app.aidroid.remaster.data.attachments.AttachmentStorage
import app.aidroid.remaster.data.attachments.StoredAttachment
import app.aidroid.remaster.sync.FirebaseCloudSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChatRepository(
    private val dao: ChatDao,
    private val attachmentStorage: AttachmentStorage,
    private val cloudSync: FirebaseCloudSync? = null
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val syncMutex = Mutex()

    init {
        syncScope.launch {
            for (ignored in syncRequests) {
                // Coalesce rapid local edits into one cloud sync instead of syncing every message.
                delay(CLOUD_SYNC_DEBOUNCE_MS)
                while (syncRequests.tryReceive().isSuccess) Unit
                syncMutex.withLock {
                    runCatching { cloudSync?.takeIf { it.isSignedIn }?.syncChats(dao) }
                }
            }
        }
    }

    fun activeChats(): Flow<List<ChatEntity>> = dao.observeActiveChats()
    fun archivedChats(): Flow<List<ChatEntity>> = dao.observeArchivedChats()
    fun messages(chatId: Long): Flow<List<MessageEntity>> = dao.observeMessages(chatId)

    suspend fun createChat(): Long {
        val now = System.currentTimeMillis()
        // Persist locally first; cloud sync is queued independently.
        val id = dao.insertChat(ChatEntity(createdAt = now, updatedAt = now))
        requestCloudSync()
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
        requestCloudSync()
    }

    suspend fun addAssistantMessage(chatId: Long, content: String, attachments: List<StoredAttachment> = emptyList()) {
        val clean = content.trim()
        if (clean.isBlank()) return
        val now = System.currentTimeMillis()
        dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = "assistant",
                content = clean,
                createdAt = now,
                attachmentsJson = AttachmentJson.encode(attachments)
            )
        )
        dao.touchChat(chatId, now)
        requestCloudSync()
    }

    suspend fun rename(chatId: Long, title: String) {
        val clean = title.trim()
        if (clean.isNotEmpty()) {
            dao.renameChat(chatId, clean.take(64), System.currentTimeMillis())
            requestCloudSync()
        }
    }

    suspend fun setPinned(chat: ChatEntity, pinned: Boolean) {
        dao.setPinned(chat.id, pinned, System.currentTimeMillis())
        requestCloudSync()
    }

    suspend fun setArchived(chat: ChatEntity, archived: Boolean) {
        dao.setArchived(chat.id, archived, System.currentTimeMillis())
        requestCloudSync()
    }

    suspend fun delete(chat: ChatEntity) {
        // Keep a tombstone so a deletion is propagated to the user's other devices.
        val storedAttachments = dao.getMessages(chat.id)
            .flatMap { AttachmentJson.decode(it.attachmentsJson) }
        dao.softDeleteChat(chat.id, System.currentTimeMillis())
        requestCloudSync()
        withContext(Dispatchers.IO) {
            storedAttachments.forEach(attachmentStorage::delete)
        }
    }

    suspend fun deleteMessage(message: MessageEntity) {
        // Message tombstones prevent deleted messages from reappearing during cloud merge.
        val storedAttachments = AttachmentJson.decode(message.attachmentsJson)
        dao.softDeleteMessage(message.id, System.currentTimeMillis())
        requestCloudSync()
        withContext(Dispatchers.IO) {
            storedAttachments.forEach(attachmentStorage::delete)
        }
    }

    suspend fun getChat(chatId: Long): ChatEntity? = dao.getChat(chatId)

    suspend fun syncNow() {
        syncMutex.withLock { cloudSync?.syncChats(dao) }
    }

    private fun requestCloudSync() {
        if (cloudSync?.isSignedIn == true) syncRequests.trySend(Unit)
    }

    private companion object {
        const val CLOUD_SYNC_DEBOUNCE_MS = 1_500L
    }
}
