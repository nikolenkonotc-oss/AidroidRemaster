package app.aidroid.remaster.sync

import android.content.Context
import android.net.Uri
import app.aidroid.remaster.R
import app.aidroid.remaster.data.AccountProfile
import app.aidroid.remaster.data.local.ChatDao
import app.aidroid.remaster.data.local.ChatEntity
import app.aidroid.remaster.data.local.MessageEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

data class CloudAccount(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?
)

/**
 * Firebase is optional at build time. Empty configuration keeps local-only mode working;
 * a configured Firebase project enables Google identity, profile storage and chat sync.
 */
class FirebaseCloudSync(context: Context) {
    private val appContext = context.applicationContext

    private val firebaseApp: FirebaseApp? = runCatching {
        FirebaseApp.getApps(appContext).firstOrNull() ?: run {
            val apiKey = appContext.getString(R.string.firebase_api_key).trim()
            val applicationId = appContext.getString(R.string.firebase_application_id).trim()
            val projectId = appContext.getString(R.string.firebase_project_id).trim()
            val storageBucket = appContext.getString(R.string.firebase_storage_bucket).trim()
            if (apiKey.isBlank() || applicationId.isBlank() || projectId.isBlank()) {
                null
            } else {
                FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setApplicationId(applicationId)
                        .setProjectId(projectId)
                        .apply { if (storageBucket.isNotBlank()) setStorageBucket(storageBucket) }
                        .build()
                )
            }
        }
    }.getOrNull()

    private val auth: FirebaseAuth? = firebaseApp?.let(FirebaseAuth::getInstance)
    private val firestore: FirebaseFirestore? = firebaseApp?.let(FirebaseFirestore::getInstance)
    private val storage: FirebaseStorage? = firebaseApp?.let(FirebaseStorage::getInstance)

    val isConfigured: Boolean get() = firebaseApp != null
    val isSignedIn: Boolean get() = auth?.currentUser != null

    suspend fun signInWithGoogle(idToken: String): CloudAccount {
        require(idToken.isNotBlank()) { "Google не вернул токен авторизации." }
        val firebaseAuth = auth ?: throw IllegalStateException(
            "Облачный сервис не настроен. Заполни Firebase-параметры в strings.xml по docs/GOOGLE_AUTH_SETUP.md."
        )
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user ?: throw IllegalStateException("Firebase не вернул пользователя.")
        return user.toCloudAccount()
    }

    suspend fun signOut() {
        auth?.signOut()
    }

    suspend fun loadProfile(): AccountProfile? {
        val user = requireUser()
        val snapshot = firestore!!
            .collection(USERS_COLLECTION)
            .document(user.uid)
            .collection(PROFILE_COLLECTION)
            .document(PROFILE_DOCUMENT)
            .get()
            .await()
        if (!snapshot.exists()) return null

        return AccountProfile(
            accountType = "google",
            displayName = snapshot.getString("displayName")
                ?.takeIf { it.isNotBlank() } ?: user.displayName.orEmpty(),
            username = snapshot.getString("username").orEmpty(),
            bio = snapshot.getString("bio").orEmpty(),
            status = snapshot.getString("status") ?: "На связи",
            email = user.email.orEmpty(),
            googlePhotoUrl = user.photoUrl?.toString().orEmpty(),
            cloudAvatarUrl = snapshot.getString("avatarUrl").orEmpty()
        )
    }

    suspend fun syncProfile(profile: AccountProfile): AccountProfile {
        val user = requireUser()
        var cloudAvatarUrl = profile.cloudAvatarUrl
        var syncedAvatarUri = profile.syncedAvatarUri

        if (profile.avatarUri.isNotBlank() && profile.avatarUri != profile.syncedAvatarUri) {
            val uri = Uri.parse(profile.avatarUri)
            if (uri.scheme == "https" || uri.scheme == "http") {
                cloudAvatarUrl = profile.avatarUri
            } else {
                val bucket = storage ?: throw IllegalStateException(
                    "Firebase Storage не настроен. Укажи Storage bucket в Firebase-параметрах."
                )
                val avatarRef = bucket.reference.child("users/${user.uid}/profile/avatar")
                avatarRef.putFile(uri).await()
                cloudAvatarUrl = avatarRef.downloadUrl.await().toString()
            }
            syncedAvatarUri = profile.avatarUri
        }

        val updated = profile.copy(
            accountType = "google",
            email = user.email.orEmpty(),
            cloudAvatarUrl = cloudAvatarUrl,
            syncedAvatarUri = syncedAvatarUri
        )
        val data = mapOf(
            "displayName" to updated.displayName,
            "username" to updated.username,
            "bio" to updated.bio,
            "status" to updated.status,
            "email" to user.email.orEmpty(),
            "googlePhotoUrl" to user.photoUrl?.toString().orEmpty(),
            "avatarUrl" to cloudAvatarUrl,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore!!
            .collection(USERS_COLLECTION)
            .document(user.uid)
            .collection(PROFILE_COLLECTION)
            .document(PROFILE_DOCUMENT)
            .set(data)
            .await()
        return updated
    }

    /**
     * Two-way merge keyed by stable UUIDs. Deletion timestamps are retained as tombstones
     * so a deleted chat/message cannot silently reappear from another device.
     */
    suspend fun syncChats(dao: ChatDao) {
        val user = auth?.currentUser ?: return
        val chatCollection = firestore!!
            .collection(USERS_COLLECTION)
            .document(user.uid)
            .collection(CHATS_COLLECTION)
        val cloudChats = chatCollection.get().await()

        for (local in dao.getAllChats()) {
            val remote = cloudChats.documents.firstOrNull { it.id == local.syncId }
            if (remote == null || localWins(local, remote)) {
                chatCollection.document(local.syncId).set(local.toCloudMap()).await()
            } else {
                saveCloudChat(dao, remote)
            }
        }

        val refreshedChats = chatCollection.get().await()
        for (remote in refreshedChats.documents) {
            if (dao.getChatBySyncId(remote.id) == null) saveCloudChat(dao, remote)
        }

        for (chat in dao.getAllChats()) {
            syncMessages(dao, chat, chatCollection.document(chat.syncId))
        }
    }

    private suspend fun syncMessages(
        dao: ChatDao,
        chat: ChatEntity,
        cloudChat: com.google.firebase.firestore.DocumentReference
    ) {
        val messageCollection = cloudChat.collection(MESSAGES_COLLECTION)
        val cloudMessages = messageCollection.get().await()
        val localMessages = dao.getMessagesForSync(chat.id)

        for (local in localMessages) {
            val remote = cloudMessages.documents.firstOrNull { it.id == local.syncId }
            if (remote == null || localWins(local, remote)) {
                // Binary attachment files are intentionally not uploaded in this first sync layer.
                messageCollection.document(local.syncId).set(local.toCloudMap()).await()
            } else {
                saveCloudMessage(dao, chat, remote)
            }
        }

        val refreshedMessages = messageCollection.get().await()
        for (remote in refreshedMessages.documents) {
            if (dao.getMessageBySyncId(remote.id) == null) saveCloudMessage(dao, chat, remote)
        }
    }

    private suspend fun saveCloudChat(dao: ChatDao, document: DocumentSnapshot) {
        val current = dao.getChatBySyncId(document.id)
        val entity = ChatEntity(
            id = current?.id ?: 0,
            title = document.getString("title") ?: "Новый чат",
            createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = document.getLong("updatedAt") ?: System.currentTimeMillis(),
            isPinned = document.getBoolean("isPinned") ?: false,
            isArchived = document.getBoolean("isArchived") ?: false,
            syncId = document.id,
            deletedAt = document.getLong("deletedAt")
        )
        if (current == null) dao.insertChat(entity) else dao.updateChat(entity)
    }

    private suspend fun saveCloudMessage(
        dao: ChatDao,
        chat: ChatEntity,
        document: DocumentSnapshot
    ) {
        val current = dao.getMessageBySyncId(document.id)
        val entity = MessageEntity(
            id = current?.id ?: 0,
            chatId = chat.id,
            role = document.getString("role") ?: "user",
            content = document.getString("content").orEmpty(),
            createdAt = document.getLong("createdAt") ?: System.currentTimeMillis(),
            attachmentsJson = "[]",
            syncId = document.id,
            deletedAt = document.getLong("deletedAt")
        )
        if (current == null) dao.insertMessage(entity) else dao.upsertMessage(entity)
    }

    private fun localWins(local: ChatEntity, remote: DocumentSnapshot): Boolean {
        val remoteUpdated = remote.getLong("updatedAt") ?: 0L
        val remoteDeleted = remote.getLong("deletedAt") ?: 0L
        return local.updatedAt > remoteUpdated || (local.deletedAt ?: 0L) > remoteDeleted
    }

    private fun localWins(local: MessageEntity, remote: DocumentSnapshot): Boolean {
        val remoteCreated = remote.getLong("createdAt") ?: 0L
        val remoteDeleted = remote.getLong("deletedAt") ?: 0L
        return local.createdAt > remoteCreated || (local.deletedAt ?: 0L) > remoteDeleted
    }

    private fun ChatEntity.toCloudMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isPinned" to isPinned,
        "isArchived" to isArchived,
        "deletedAt" to deletedAt
    )

    private fun MessageEntity.toCloudMap(): Map<String, Any?> = mapOf(
        "role" to role,
        "content" to content,
        "createdAt" to createdAt,
        "deletedAt" to deletedAt,
        "attachmentsJson" to "[]"
    )

    private fun requireUser(): FirebaseUser = auth?.currentUser
        ?: throw IllegalStateException("Сначала войди через Google.")

    private fun FirebaseUser.toCloudAccount() = CloudAccount(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        photoUrl = photoUrl?.toString()
    )

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val PROFILE_COLLECTION = "profile"
        private const val PROFILE_DOCUMENT = "main"
        private const val CHATS_COLLECTION = "chats"
        private const val MESSAGES_COLLECTION = "messages"
    }
}
