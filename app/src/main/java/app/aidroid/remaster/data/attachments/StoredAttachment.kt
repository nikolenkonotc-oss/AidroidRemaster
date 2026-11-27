package app.aidroid.remaster.data.attachments

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String
)

object AttachmentJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(attachments: List<StoredAttachment>): String = json.encodeToString(attachments)

    fun decode(value: String): List<StoredAttachment> = runCatching {
        json.decodeFromString<List<StoredAttachment>>(value)
    }.getOrDefault(emptyList())
}
