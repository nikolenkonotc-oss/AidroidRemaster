package app.aidroid.remaster.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCommandToolsTest {

    @Test
    fun safeReadOnlyCommandsCanRunWithoutApproval() {
        assertTrue(TermuxCommandTools.isReadOnlyCommand("pwd"))
        assertTrue(TermuxCommandTools.isReadOnlyCommand("ls -la"))
        assertTrue(TermuxCommandTools.isReadOnlyCommand("df -h"))
        assertTrue(TermuxCommandTools.isReadOnlyCommand("pkg list-installed"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("pkg update"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("git branch -D main"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("git diff --output=patch.txt"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("date -s 2026-01-01"))
    }

    @Test
    fun shellOperatorsAndUnapprovedExecutablesAreRejected() {
        listOf(
            "pwd && id",
            "ls | grep dcim",
            "cat file > copy",
            "rm -rf something",
            "sh -c id",
            "su -c id",
            "python script.py"
        ).forEach { command ->
            assertFalse("Expected rejection: $command", runCatching {
                TermuxCommandTools.validateAndTokenize(command)
            }.isSuccess)
        }
    }

    @Test
    fun mutatingFindOptionsAreRejected() {
        listOf("find . -delete", "find . -exec rm file \\;", "find . -fprint output.txt").forEach { command ->
            assertFalse("Expected rejection: $command", runCatching {
                TermuxCommandTools.validateAndTokenize(command)
            }.isSuccess)
        }
    }

    @Test
    fun rootConfigPathsAreRestrictedToApprovedNonCriticalFiles() {
        assertEquals(
            "/system/etc/example.conf",
            TermuxCommandTools.validateConfigPath("/system/etc/example.conf", write = false)
        )
        assertEquals(
            "/vendor/etc/device.xml",
            TermuxCommandTools.validateConfigPath("/vendor/etc/device.xml", write = true)
        )
        listOf(
            "/system/etc/../build.prop",
            "/system/build.prop",
            "/system/etc/security/ca.xml",
            "/vendor/etc/init.rc",
            "/odm/etc/keys/device.xml",
            "/product/etc/private.bin"
        ).forEach { path ->
            assertFalse("Expected rejection: $path", runCatching {
                TermuxCommandTools.validateConfigPath(path, write = false)
            }.isSuccess)
        }
    }

    @Test
    fun rootDirectoryListingIsLimitedToSystemConfigFolders() {
        assertEquals("/system/etc", TermuxCommandTools.validateConfigDirectory("/system/etc/"))
        assertEquals("/vendor/etc/hw", TermuxCommandTools.validateConfigDirectory("/vendor/etc/hw"))
        listOf(
            "/data",
            "/system",
            "/system/etc/security",
            "/product/etc/../etc",
            "/odm/etc/keys"
        ).forEach { path ->
            assertFalse("Expected rejection: $path", runCatching {
                TermuxCommandTools.validateConfigDirectory(path)
            }.isSuccess)
        }
    }

    @Test
    fun rootWriteAlwaysCreatesAnApprovalAction() {
        val result = TermuxCommandTools.prepareRootWrite(
            buildJsonObject {
                put("path", "/system/etc/example.conf")
                put("content", "mode=1")
            },
            chatId = 42L
        )
        assertEquals(AgentActionType.TERMUX_COMMAND, result.action?.type)
        assertEquals("root_write", result.action?.subject)
        assertTrue(result.action?.body?.contains("/system/etc/example.conf") == true)
    }

    @Test
    fun rootToolsAreOnlyExposedWhenBothTermuxAndRootAreEnabled() {
        assertTrue(TermuxCommandTools.definitions(emptySet(), rootAccessEnabled = true).isEmpty())
        val tools = TermuxCommandTools.definitions(setOf("termux_command"), rootAccessEnabled = false)
        assertEquals(listOf("termux_run_command"), tools.map { it.name })

        val rootTools = TermuxCommandTools.definitions(
            setOf("termux_command"),
            rootAccessEnabled = true
        )
        assertTrue(rootTools.any { it.name == "termux_root_list_config" })
        assertTrue(rootTools.any { it.name == "termux_root_read_config" })
        assertTrue(rootTools.any { it.name == "termux_root_write_config" })
    }
}
