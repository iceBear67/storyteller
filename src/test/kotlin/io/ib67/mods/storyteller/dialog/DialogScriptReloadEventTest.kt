package io.ib67.mods.storyteller.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DialogScriptReloadEventTest {

    @Test
    fun `event forwards previous and current checksums`() {
        val event = DialogScriptReloadEvent()
        var observed: Pair<String, String>? = null
        event.register { previous, current -> observed = previous to current }
        event.fire("old-checksum", "new-checksum")
        assertEquals("old-checksum" to "new-checksum", observed)
    }
}
