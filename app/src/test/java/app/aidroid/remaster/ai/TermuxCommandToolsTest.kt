package app.aidroid.remaster.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TermuxCommandToolsTest {
    @Test
    fun safeReadOnlyCommandIsTokenizedWithoutShell() {
        assertEquals(
            listOf("ls", "-la", "My Files"),
            TermuxCommandTools.validateAndTokenize("ls -la \"My Files\"")
        )
    }

    @Test
    fun shellOperatorsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("ls; rm -rf")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("ls | grep secret")
        }
    }

    @Test
    fun rootAndDestructiveCommandsAreRejected() {
        listOf("su", "sudo", "rm", "mv", "sh", "bash", "python").forEach { command ->
            assertThrows(IllegalArgumentException::class.java) {
                TermuxCommandTools.validateAndTokenize(command)
            }
        }
    }

    @Test
    fun dangerousFindOptionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("find . -delete")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("find . -exec rm file")
        }
    }

    @Test
    fun packageAndGitCommandsAreRestrictedToReadOnlyOperations() {
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("pkg install something")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TermuxCommandTools.validateAndTokenize("git reset --hard")
        }
    }
}
