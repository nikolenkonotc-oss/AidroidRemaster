package app.aidroid.remaster.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class AgentSafetyPolicyTest {
    @Test
    fun allowsOrdinaryTechnicalSearchAndCode() {
        AgentSafetyPolicy.validateSearchQuery("Kotlin Compose animation documentation")
        AgentSafetyPolicy.validateCode("print(2 + 2)")
    }

    @Test
    fun blocksRestrictedSearchCategoriesBeforeNetworkAccess() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentSafetyPolicy.validateSearchQuery("online casino")
        }
    }

    @Test
    fun blocksRestrictedCodeBeforeItIsSentToRemoteRunner() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentSafetyPolicy.validateCode("const topic = 'weapon construction';")
        }
    }
}
