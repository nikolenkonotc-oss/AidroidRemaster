package app.aidroid.remaster

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun projectIdentity_isStable() {
        assertEquals("app.aidroid.remaster", BuildIdentity.applicationId)
    }
}

internal object BuildIdentity {
    const val applicationId = "app.aidroid.remaster"
}
