package com.cadence.domain.variable

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.HttpHeader
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.constraint.ConstraintConfig.VariableCompare.Operator
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import com.cadence.testutil.FakeGlobalVariableRepository
import com.cadence.testutil.TEST_INSTANT
import com.cadence.testutil.testMacro
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariablesTest {

    private val globals = FakeGlobalVariableRepository()

    /** Fake device values; counts calls to check per-run caching. */
    private var systemCalls = 0
    private val system = object : SystemVariableSource {
        override suspend fun value(key: String, context: ExecutionContext): String? {
            systemCalls++
            return when (key) {
                "battery.level" -> "42"
                "location.latitude" -> "6.927079"
                else -> null
            }
        }
    }
    private val resolver = VariableResolver(globals, system)

    private fun context(data: Map<String, String> = emptyMap()) = ExecutionContext(
        macro = testMacro(name = "Morning"),
        trigger = TriggerEvent(TriggerConfig.Manual, TEST_INSTANT, data),
        startedAt = TEST_INSTANT,
        variables = resolver,
    )

    // region Resolver

    @Test
    fun `placeholders resolve from every source`() = runTest {
        globals.values["city"] = "Colombo"
        val ctx = context(mapOf("package" to "com.example.maps"))
        ctx.locals["http.status"] = "200"

        val text = ctx.render("{macro.name}: {battery.level}% in {city}, app {trigger.package}, HTTP {http.status}")

        assertEquals("Morning: 42% in Colombo, app com.example.maps, HTTP 200", text)
    }

    @Test
    fun `unknown placeholders and stray braces are left as written`() = runTest {
        assertEquals("Hi {nobody} and {not valid} and {", context().render("Hi {nobody} and {not valid} and {"))
    }

    @Test
    fun `locals shadow globals`() = runTest {
        globals.values["mode"] = "global"
        val ctx = context()
        ctx.locals["mode"] = "local"

        assertEquals("local", ctx.render("{mode}"))
    }

    @Test
    fun `system values are read once per run`() = runTest {
        val ctx = context()
        ctx.render("{battery.level} {battery.level} {battery.level}")

        assertEquals(1, systemCalls)
        assertEquals("42", resolver.lookup("battery.level", ctx))
        assertEquals(1, systemCalls)
    }

    @Test
    fun `run values come from the macro, the trigger and the last-run lookup`() = runTest {
        val lastRun = TEST_INSTANT.minusSeconds(3_600)
        val withHistory = VariableResolver(globals, system) { id -> if (id == 1L) lastRun else null }
        val ctx = context().copy(variables = withHistory, macro = testMacro(id = 1).copy(category = "Home"))

        assertEquals("Home", ctx.render("{macro.category}"))
        assertEquals(TEST_INSTANT.toString(), ctx.render("{trigger.time}"))
        assertEquals(lastRun.toString(), ctx.render("{macro.last_run}"))
        assertEquals("", context().render("{macro.last_run}")) // no lookup wired: empty, not literal
    }

    @Test
    fun `every built-in name is a valid placeholder and unique`() {
        val keys = SystemVariables.all.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assertTrue("invalid: $it", VariableNames.isValidReference(it)) }
    }

    @Test
    fun `forgetting cached values makes the next read fresh`() = runTest {
        val ctx = context()
        ctx.render("{location.latitude} {battery.level}")
        assertEquals(2, systemCalls)

        resolver.forgetCached(ctx, "location.")
        ctx.render("{location.latitude} {battery.level}")

        assertEquals(3, systemCalls) // only the location value was read again
    }

    @Test
    fun `placeholder scanning finds valid names only`() {
        assertEquals(setOf("a", "location.latitude"), Templates.placeholders("{a} {location.latitude} {9bad} {}"))
    }

    // endregion

    // region Set variable

    private val setVariable = SetVariableActionHandler(globals)

    @Test
    fun `set stores rendered text globally or locally`() = runTest {
        val ctx = context()

        setVariable.execute(ActionConfig.SetVariable("note", "Battery {battery.level}"), ctx)
        setVariable.execute(ActionConfig.SetVariable("temp", "x", scope = VariableScope.LOCAL), ctx)

        assertEquals("Battery 42", globals.values["note"])
        assertEquals("x", ctx.locals["temp"])
        assertNull(globals.values["temp"])
    }

    @Test
    fun `add treats missing values as zero and keeps numbers tidy`() = runTest {
        val ctx = context()
        val add = { amount: String -> ActionConfig.SetVariable("counter", amount, operation = ActionConfig.SetVariable.Operation.ADD) }

        setVariable.execute(add("1"), ctx)
        setVariable.execute(add("2.5"), ctx)
        setVariable.execute(add("-0.5"), ctx)

        assertEquals("3", globals.values["counter"])
    }

    @Test
    fun `add rejects non-numbers without changing the value`() = runTest {
        globals.values["counter"] = "5"

        val result = setVariable.execute(
            ActionConfig.SetVariable("counter", "five", operation = ActionConfig.SetVariable.Operation.ADD),
            context(),
        )

        assertTrue(result is ActionResult.Failure)
        assertEquals("5", globals.values["counter"])
    }

    @Test
    fun `append concatenates`() = runTest {
        val ctx = context()
        val append = ActionConfig.SetVariable("log", "{battery.level};", operation = ActionConfig.SetVariable.Operation.APPEND)

        setVariable.execute(append, ctx)
        setVariable.execute(append, ctx)

        assertEquals("42;42;", globals.values["log"])
    }

    @Test
    fun `set variable without a name fails`() = runTest {
        assertTrue(setVariable.execute(ActionConfig.SetVariable("", "x"), context()) is ActionResult.Failure)
    }

    // endregion

    // region Variable constraint

    private val compare = VariableCompareEvaluator()

    private suspend fun holds(name: String, operator: Operator, value: String = ""): Boolean =
        compare.evaluate(ConstraintConfig.VariableCompare(name, operator, value), context())

    @Test
    fun `numbers compare numerically`() = runTest {
        globals.values["count"] = "9"

        assertTrue(holds("count", Operator.LESS_THAN, "10"))
        assertFalse(holds("count", Operator.GREATER_THAN, "10"))
        assertTrue(holds("count", Operator.EQUALS, "9.0"))
        assertTrue(holds("battery.level", Operator.LESS_THAN, "50"))
    }

    @Test
    fun `text compares case-insensitively`() = runTest {
        globals.values["mode"] = "Work"

        assertTrue(holds("mode", Operator.EQUALS, "work"))
        assertTrue(holds("mode", Operator.CONTAINS, "OR"))
        assertTrue(holds("mode", Operator.NOT_EQUALS, "home"))
        assertFalse(holds("mode", Operator.GREATER_THAN, "a")) // not numbers
    }

    @Test
    fun `set and empty checks`() = runTest {
        globals.values["blank"] = ""
        globals.values["full"] = "x"

        assertTrue(holds("full", Operator.IS_SET))
        assertTrue(holds("blank", Operator.IS_NOT_SET))
        assertTrue(holds("missing", Operator.IS_NOT_SET))
        assertFalse(holds("missing", Operator.EQUALS, ""))
        assertTrue(holds("missing", Operator.NOT_EQUALS, "x"))
    }

    @Test
    fun `compared value may contain placeholders`() = runTest {
        globals.values["limit"] = "42"

        assertTrue(holds("limit", Operator.EQUALS, "{battery.level}"))
    }

    // endregion

    // region JSON path

    private val json = """{"data":{"items":[{"name":"first","n":1},{"name":"second"}],"ok":true,"none":null}}"""

    @Test
    fun `json path picks values`() {
        assertEquals("second", JsonPath.extract(json, "data.items[1].name"))
        assertEquals("first", JsonPath.extract(json, "data.items.0.name"))
        assertEquals("1", JsonPath.extract(json, "data.items[0].n"))
        assertEquals("true", JsonPath.extract(json, "data.ok"))
        assertEquals("", JsonPath.extract(json, "data.none"))
        assertEquals("""{"name":"second"}""", JsonPath.extract(json, "data.items[1]"))
    }

    @Test
    fun `json path returns null when missing or not json`() {
        assertNull(JsonPath.extract(json, "data.items[5].name"))
        assertNull(JsonPath.extract(json, "data.missing"))
        assertNull(JsonPath.extract("<html>", "data"))
    }

    // endregion

    // region Requirements

    @Test
    fun `location placeholders require location access`() {
        val requirements = RequirementResolver(TriggerRegistry(emptySet()), ActionRegistry(emptySet()), ConstraintRegistry(emptySet()))
        val location = setOf(PermissionRequirement.FINE_LOCATION, PermissionRequirement.BACKGROUND_LOCATION)

        assertEquals(location, requirements.forConfig(ActionConfig.SpeakText("I'm at {location.latitude}")))
        assertEquals(
            location,
            requirements.forConfig(ActionConfig.HttpRequest("https://x.test", headers = listOf(HttpHeader("X-Speed", "{location.speed}")))),
        )
        assertEquals(location, requirements.forConfig(ConstraintConfig.VariableCompare("location.speed_kmh", Operator.GREATER_THAN, "80")))
        assertTrue(requirements.forConfig(ActionConfig.SpeakText("Battery {battery.level}")).isEmpty())
    }

    // endregion
}
