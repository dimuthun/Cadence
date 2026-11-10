package com.cadence.data.serialization

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.trigger.TriggerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigJsonCodecTest {

    private val codec = ConfigJsonCodec(CadenceJson.instance)

    @Test
    fun `unknown item is skipped and counted while the rest decode`() {
        val raw = """[
            {"type":"trigger.boot_completed"},
            {"type":"trigger.from_the_future","foo":1},
            {"type":"trigger.screen_state","event":"ON"}
        ]"""

        val decoded = codec.decodeTriggers(raw)

        assertEquals(
            listOf(TriggerConfig.BootCompleted, TriggerConfig.ScreenState(TriggerConfig.ScreenState.Event.ON)),
            decoded.items,
        )
        assertEquals(1, decoded.failures)
    }

    @Test
    fun `item failing validation is counted as a failure`() {
        val decoded = codec.decodeActions("""[{"type":"action.vibrate","durationMillis":0}]""")

        assertEquals(emptyList<ActionConfig>(), decoded.items)
        assertEquals(1, decoded.failures)
    }

    @Test
    fun `malformed json counts as one failure`() {
        val decoded = codec.decodeTriggers("not json")

        assertEquals(emptyList<TriggerConfig>(), decoded.items)
        assertEquals(1, decoded.failures)
    }

    @Test
    fun `json that isn't an array counts as one failure`() {
        assertEquals(1, codec.decodeTriggers("""{"type":"trigger.boot_completed"}""").failures)
    }

    @Test
    fun `lists round trip`() {
        val actions = listOf(ActionConfig.Wait(100), ActionConfig.OpenUrl("https://example.com"))

        val decoded = codec.decodeActions(codec.encodeActions(actions))

        assertEquals(actions, decoded.items)
        assertEquals(0, decoded.failures)
    }

    @Test
    fun `single values return null instead of throwing`() {
        assertNull(codec.decodeTrigger("""{"type":"nope"}"""))
        assertNull(codec.decodeAction("garbage"))
    }

    @Test
    fun `string maps round trip and tolerate garbage`() {
        val map = mapOf("ssid" to "HomeNet", "package" to "com.example")

        assertEquals(map, codec.decodeStringMap(codec.encodeStringMap(map)))
        assertEquals(emptyMap<String, String>(), codec.decodeStringMap("[1,2]"))
    }
}
