package com.cadence.domain.variable

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.HttpParameter
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.model.Macro
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.platform.http.HttpUrls
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the placeholder and URL code on Android's own regex/encoding implementations (ICU),
 * which differ from the JVM's: the JVM-only unit tests missed an ICU regex syntax error.
 */
@RunWith(AndroidJUnit4::class)
class TemplatesOnDeviceTest {

    private val now = Instant.parse("2026-11-11T09:00:00Z")
    private val macro = Macro(name = "Device test", triggers = emptyList(), actions = emptyList(), createdAt = now, updatedAt = now)

    @Test
    fun placeholdersRenderOnAndroid() = runTest {
        val context = ExecutionContext(macro, TriggerEvent(TriggerConfig.Manual, now), now)
        context.locals["greeting"] = "hello"

        assertEquals("hello from Device test {missing}", context.render("{greeting} from {macro.name} {missing}"))
        assertEquals(setOf("a", "b.c"), Templates.placeholders("{a} and {b.c}"))
    }

    @Test
    fun requirementScanningOfHttpQueryParametersWorksOnAndroid() {
        val resolver = RequirementResolver(TriggerRegistry(emptySet()), ActionRegistry(emptySet()), ConstraintRegistry(emptySet()))
        val request = ActionConfig.HttpRequest(
            url = "https://example.com",
            queryParameters = listOf(HttpParameter("lat", "{location.latitude}")),
        )

        assertTrue(resolver.referencedVariables(request).contains("location.latitude"))
    }

    @Test
    fun queryParametersEncodeOnAndroid() {
        assertEquals(
            "https://x.test/?q=a%20b%26c",
            HttpUrls.withQueryParameters("https://x.test/", listOf("q" to "a b&c")),
        )
    }
}
