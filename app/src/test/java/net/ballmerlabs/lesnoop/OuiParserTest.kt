package net.ballmerlabs.lesnoop

import net.ballmerlabs.lesnoop.db.OuiParser
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OuiParserTest {
    @Test
    fun loadResource() {
        val context = RuntimeEnvironment.getApplication()
        val parser = OuiParser(context)
        val oui = parser.oui.blockingFirst()
        assert(oui.isNotEmpty())
    }
}