package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidFileToolsTest {
    @Test
    fun fileToolsAreHiddenUntilEnabled() {
        assertTrue(AndroidFileTools.definitions(emptySet(), 1).isEmpty())
    }

    @Test
    fun fileToolsExposeOnlyWhenEnabled() {
        val names = AndroidFileTools.definitions(setOf("android_file_access"), 2).map { it.name }
        assertEquals(
            listOf(
                "android_file_list",
                "android_file_read",
                "android_file_create_folder",
                "android_file_copy",
                "android_file_move",
                "android_file_delete"
            ),
            names
        )
    }

    @Test
    fun pathNormalizationKeepsRelativePaths() {
        assertEquals("Pictures/Camera", AndroidFileTools.safePath("/Pictures/Camera/"))
        assertEquals("", AndroidFileTools.safePath(""))
    }

    @Test
    fun pathTraversalIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidFileTools.safePath("../private")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidFileTools.safePath("folder/../../private")
        }
    }

    @Test
    fun createFolderAlwaysProducesAFileOperationApproval() {
        val result = AndroidFileTools.prepare(
            "android_file_create_folder",
            buildJsonObject {
                put("root_index", 0)
                put("parent", "Documents")
                put("name", "Notes")
            },
            chatId = 5L
        )
        assertEquals(AgentActionType.FILE_OPERATION, result.action?.type)
        assertEquals("create_folder", result.action?.subject)
        assertTrue(result.action?.title?.contains("Notes") == true)
    }

    @Test
    fun deleteRequiresAnExactRelativePath() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidFileTools.prepare(
                "android_file_delete",
                buildJsonObject {
                    put("root_index", 0)
                    put("path", "../secrets.txt")
                },
                chatId = 5L
            )
        }
    }
}
