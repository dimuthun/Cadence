package com.cadence.data.backup

import com.cadence.data.serialization.CadenceJson
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.model.Weekday
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testMacro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroBackupCodecTest {

    private val codec = MacroBackupCodec(CadenceJson.instance)

    private val macros = listOf(
        testMacro(
            id = 5,
            name = "Morning",
            triggers = listOf(TriggerConfig.TimeOfDay(7, 0, setOf(Weekday.MONDAY))),
            actions = listOf(ActionConfig.SpeakText("Good morning")),
            constraints = listOf(ConstraintConfig.DaysOfWeek(setOf(Weekday.MONDAY))),
        ).copy(category = "Routines"),
        testMacro(id = 9, name = "Night", enabled = false, actions = listOf(ActionConfig.Vibrate())),
    )

    @Test
    fun `macros round trip without their ids`() {
        val decoded = codec.decode(codec.encode(macros, TEST_INSTANT)) as MacroBackupCodec.DecodeResult.Decoded

        assertEquals(macros.map { it.copy(id = 0) }, decoded.macros)
        assertEquals(0, decoded.skipped)
    }

    @Test
    fun `macro with an unknown config type is skipped and others are kept`() {
        val document = codec.encode(macros, TEST_INSTANT)
            .replace("\"action.vibrate\"", "\"action.from_the_future\"")

        val decoded = codec.decode(document) as MacroBackupCodec.DecodeResult.Decoded

        assertEquals(listOf("Morning"), decoded.macros.map { it.name })
        assertEquals(1, decoded.skipped)
    }

    @Test
    fun `non-backup documents are rejected`() {
        assertTrue(codec.decode("hello") is MacroBackupCodec.DecodeResult.Invalid)
        assertTrue(codec.decode("""{"some":"json"}""") is MacroBackupCodec.DecodeResult.Invalid)
        assertTrue(
            codec.decode("""{"format":"other.app","version":1,"exportedAtMillis":0,"macros":[]}""")
                is MacroBackupCodec.DecodeResult.Invalid,
        )
    }

    @Test
    fun `backups from a newer format version are rejected with a clear reason`() {
        val result = codec.decode("""{"format":"cadence.macros","version":99,"exportedAtMillis":0,"macros":[]}""")

        assertTrue((result as MacroBackupCodec.DecodeResult.Invalid).reason.contains("newer version"))
    }
}
