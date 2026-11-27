package app.aidroid.remaster.data.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

class AttachmentStorage(context: Context) {
    private val appContext = context.applicationContext
    private val attachmentDirectory = File(appContext.filesDir, "attachments")

    suspend fun importUri(uri: Uri): StoredAttachment = withContext(Dispatchers.IO) {
        if (!attachmentDirectory.exists() && !attachmentDirectory.mkdirs()) {
            throw IOException("Не удалось создать папку вложений.")
        }

        val displayName = queryDisplayName(uri).ifBlank { "Вложение" }.take(140)
        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
        val fileName = UUID.randomUUID().toString() + (extension?.let { ".$it" } ?: "")
        val destination = File(attachmentDirectory, fileName)
        val mimeType = appContext.contentResolver.getType(uri)
            ?: extension?.lowercase()?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
            ?: "application/octet-stream"

        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Не удалось прочитать выбранный файл.")
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_SINGLE_ATTACHMENT_BYTES) {
                            throw IOException("Файл слишком большой. Максимальный размер — 25 МБ.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            StoredAttachment(
                id = UUID.randomUUID().toString(),
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = destination.length(),
                relativePath = "attachments/${destination.name}"
            )
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    fun resolveFile(attachment: StoredAttachment): File {
        val base = attachmentDirectory.canonicalFile
        val target = File(appContext.filesDir, attachment.relativePath).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Недопустимый путь к вложению." }
        require(target.isFile) { "Файл вложения больше не найден." }
        return target
    }

    suspend fun readBytes(attachment: StoredAttachment, maxBytes: Long = MAX_SINGLE_ATTACHMENT_BYTES): ByteArray =
        withContext(Dispatchers.IO) {
            val file = resolveFile(attachment)
            require(file.length() <= maxBytes) { "Файл слишком большой для обработки агентом." }
            file.readBytes()
        }

    suspend fun saveGeneratedImage(bytes: ByteArray, displayName: String): StoredAttachment =
        withContext(Dispatchers.IO) {
            require(bytes.isNotEmpty() && bytes.size <= MAX_GENERATED_IMAGE_BYTES) {
                "Сгенерированное изображение пустое или превышает лимит 20 МБ."
            }
            if (!attachmentDirectory.exists() && !attachmentDirectory.mkdirs()) {
                throw IOException("Не удалось создать папку вложений.")
            }
            val safeName = displayName
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .take(48)
                .ifBlank { "generated_image" }
            val destination = File.createTempFile("${safeName}_", ".png", attachmentDirectory)
            try {
                FileOutputStream(destination).use { it.write(bytes) }
                StoredAttachment(
                    id = UUID.randomUUID().toString(),
                    displayName = "$safeName.png",
                    mimeType = "image/png",
                    sizeBytes = destination.length(),
                    relativePath = "attachments/${destination.name}"
                )
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }

    fun createCameraOutput(): File {
        if (!attachmentDirectory.exists() && !attachmentDirectory.mkdirs()) {
            throw IOException("Не удалось создать папку для фото.")
        }
        return File.createTempFile("camera_", ".jpg", attachmentDirectory)
    }

    fun fromCameraFile(file: File): StoredAttachment {
        require(file.isFile && file.length() > 0L) { "Камера не сохранила снимок." }
        return StoredAttachment(
            id = UUID.randomUUID().toString(),
            displayName = "Фото_${System.currentTimeMillis()}.jpg",
            mimeType = "image/jpeg",
            sizeBytes = file.length(),
            relativePath = "attachments/${file.name}"
        )
    }

    fun delete(attachment: StoredAttachment) {
        val base = attachmentDirectory.canonicalFile
        val target = File(appContext.filesDir, attachment.relativePath).canonicalFile
        if (target.path.startsWith(base.path + File.separator)) target.delete()
    }

    private fun queryDisplayName(uri: Uri): String {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index).orEmpty()
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Вложение"
    }

    companion object {
        private const val MAX_SINGLE_ATTACHMENT_BYTES = 25L * 1024L * 1024L
        private const val MAX_GENERATED_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
