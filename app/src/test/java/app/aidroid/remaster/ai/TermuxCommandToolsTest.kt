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
        assertTrue(TermuxCommandTools.isReadOnlyCommand("ls -la /storage/emulated/0"))
        assertTrue(TermuxCommandTools.isReadOnlyCommand("df -h"))
        assertTrue(TermuxCommandTools.isReadOnlyCommand("pkg list-installed"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("adb devices"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("adb shell getprop"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("adb shell settings get global airplane_mode_on"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("su -c 'id'"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("su -c 'ls /system/etc'"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("su -c 'settings put global airplane_mode_on 1'"))
        assertFalse(TermuxCommandTools.isReadOnlyCommand("su -c 'rm /data/local/tmp/test'"))
    }

    @Test
    fun modifyingAndPrivilegedCommandsAlwaysRequireApproval() {
        listOf(
            "pkg install android-tools",
            "pkg update",
            "git branch -D main",
            "date -s 2026-01-01",
            "su -c id",
            "adb shell settings put global airplane_mode_on 1",
            "adb shell pm clear com.example.app",
            "rm -f test.txt",
            "ls -la | grep dcim"
        ).forEach { command ->
            assertFalse("Expected approval: $command", TermuxCommandTools.isReadOnlyCommand(command))
        }
    }

    @Test
    fun rootAndAdbCommandsCreateExplicitApprovalActions() {
        val root = TermuxCommandTools.prepare(
            buildJsonObject { put("command", "su -c 'id'") },
            chatId = 42L
        )
        assertEquals(AgentActionType.TERMUX_COMMAND, root.action?.type)
        assertEquals("root", root.action?.subject)
        assertEquals("su -c 'id'", root.action?.body)

        val adb = TermuxCommandTools.prepare(
            buildJsonObject { put("command", "adb shell getprop ro.build.version.release") },
            chatId = 42L
        )
        assertEquals("adb", adb.action?.subject)
    }

    @Test
    fun shellCommandsThatNeedApprovalAreDisplayedVerbatim() {
        val result = TermuxCommandTools.prepare(
            buildJsonObject { put("command", "pwd && ls -la | head -20") },
            chatId = 7L
        )
        assertEquals("shell", result.action?.subject)
        assertEquals("pwd && ls -la | head -20", result.action?.body)
    }

    @Test
    fun deviceWipingAndPartitionFlashingCommandsAreBlocked() {
        listOf(
            "rm -rf /",
            "dd if=/dev/zero of=/dev/block/by-name/system",
            "mkfs.ext4 /dev/block/by-name/userdata",
            "fastboot flash boot boot.img"
        ).forEach { command ->
            assertFalse("Expected block: $command", runCatching {
                TermuxCommandTools.prepare(
                    buildJsonObject { put("command", command) },
                    chatId = 5L
                )
            }.isSuccess)
        }
    }

    @Test
    fun termuxToolIsOnlyExposedWhenEnabled() {
        assertTrue(TermuxCommandTools.definitions(emptySet()).isEmpty())
        assertEquals(
            listOf("termux_run_command"),
            TermuxCommandTools.definitions(setOf("termux_command")).map { it.name }
        )
    }
}
