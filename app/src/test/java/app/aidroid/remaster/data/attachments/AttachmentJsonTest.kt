package app.aidroid.remaster.data.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentJsonTest {
    @Test
    fun attachmentsRoundTrip() {
        val expected = listOf(
            StoredAttachment(
                id = "file-1",
                displayName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 2048L,
                relativePath = "attachments/photo.jpg"
            )
        )

        assertEquals(expected, AttachmentJson.decode(AttachmentJson.encode(expected)))
    }

    @Test
    fun emptyAndMalformedValuesDecodeSafely() {
        assertTrue(AttachmentJson.decode("[]").isEmpty())
        assertTrue(AttachmentJson.decode("not-json").isEmpty())
    }
}
