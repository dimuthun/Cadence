package com.cadence.testutil

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.constraint.ConstraintConfig
import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.engine.EngineDiagnostics
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.model.Macro
import com.cadence.domain.model.MacroExecution
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.plugin.ConstraintEvaluator
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.repository.MacroRepository
import com.cadence.domain.trigger.TriggerConfig
import com.cadence.domain.trigger.TriggerEvent
import java.io.IOException
import java.time.Instant
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

val TEST_INSTANT: Instant = Instant.parse("2026-11-10T09:00:00Z")

fun testMacro(
    id: Long = 1,
    name: String = "Test macro $id",
    enabled: Boolean = true,
    triggers: List<TriggerConfig> = listOf(TriggerConfig.Manual),
    actions: List<ActionConfig> = emptyList(),
    constraints: List<ConstraintConfig> = emptyList(),
) = Macro(
    id = id,
    name = name,
    enabled = enabled,
    triggers = triggers,
    actions = actions,
    constraints = constraints,
    createdAt = TEST_INSTANT,
    updatedAt = TEST_INSTANT,
)

fun testContext(macro: Macro = testMacro()) = ExecutionContext(
    macro = macro,
    trigger = TriggerEvent(TriggerConfig.Manual, TEST_INSTANT),
    startedAt = TEST_INSTANT,
)

class FakeMacroRepository : MacroRepository {
    private val state = MutableStateFlow<List<Macro>>(emptyList())

    fun set(vararg macros: Macro) {
        state.value = macros.toList()
    }

    override fun observeMacros(): Flow<List<Macro>> = state

    override fun observeEnabledMacros(): Flow<List<Macro>> = state.map { list -> list.filter { it.enabled } }

    override fun observeCategories(): Flow<List<String>> =
        state.map { list -> list.map { it.category }.distinct().sorted() }

    override suspend fun getMacro(id: Long): Macro? = state.value.firstOrNull { it.id == id }

    override suspend fun saveMacro(macro: Macro): Long {
        val id = if (macro.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1 else macro.id
        state.update { list -> list.filterNot { it.id == id } + macro.copy(id = id) }
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        state.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    override suspend fun deleteMacro(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeExecutionLogRepository : ExecutionLogRepository {
    private val state = MutableStateFlow<List<MacroExecution>>(emptyList())
    private var nextId = 1L

    /** When `true`, [record] throws to simulate a storage failure. */
    var failWrites = false

    val recorded: List<MacroExecution> get() = state.value

    override fun observeExecutions(limit: Int): Flow<List<MacroExecution>> =
        state.map { it.asReversed().take(limit) }

    override fun observeExecutionsForMacro(macroId: Long, limit: Int): Flow<List<MacroExecution>> =
        state.map { list -> list.filter { it.macroId == macroId }.asReversed().take(limit) }

    override suspend fun record(execution: MacroExecution): Long {
        if (failWrites) throw IOException("disk full")
        val id = nextId++
        state.update { it + execution.copy(id = id) }
        return id
    }

    override suspend fun deleteOlderThan(cutoff: Instant): Int {
        val before = state.value.size
        state.update { list -> list.filterNot { it.startedAt < cutoff } }
        return before - state.value.size
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** Trigger handler that tracks live registrations and emits events on demand. */
class FakeTriggerHandler<C : TriggerConfig>(
    override val configType: KClass<C>,
    private val permissions: Set<PermissionRequirement> = emptySet(),
) : TriggerHandler<C> {
    private val events = MutableSharedFlow<TriggerEvent>(extraBufferCapacity = 64)

    /** Number of currently collected [observe] flows, i.e. registered listeners. */
    var activeRegistrations = 0
        private set

    override fun requiredPermissions(config: C): Set<PermissionRequirement> = permissions

    override fun observe(config: C): Flow<TriggerEvent> = flow {
        activeRegistrations++
        try {
            emitAll(events.filter { it.source == config })
        } finally {
            activeRegistrations--
        }
    }

    fun fire(config: C, at: Instant = TEST_INSTANT, data: Map<String, String> = emptyMap(), id: String? = null) {
        check(events.tryEmit(TriggerEvent(config, at, data, id)))
    }
}

/** Trigger handler whose registration always fails. */
class FailingTriggerHandler<C : TriggerConfig>(override val configType: KClass<C>) : TriggerHandler<C> {
    override fun observe(config: C): Flow<TriggerEvent> = flow {
        throw IOException("receiver registration failed")
    }
}

/** Action handler that records every execution into [executed] and then runs [behavior]. */
class RecordingActionHandler<C : ActionConfig>(
    override val configType: KClass<C>,
    private val executed: MutableList<ActionConfig>,
    private val permissions: Set<PermissionRequirement> = emptySet(),
) : ActionHandler<C> {
    var behavior: suspend (C) -> ActionResult = { ActionResult.Success() }

    override fun requiredPermissions(config: C): Set<PermissionRequirement> = permissions

    override suspend fun execute(config: C, context: ExecutionContext): ActionResult {
        executed += config
        return behavior(config)
    }
}

class FakeConstraintEvaluator<C : ConstraintConfig>(
    override val configType: KClass<C>,
) : ConstraintEvaluator<C> {
    var result = true
    var error: Exception? = null

    override suspend fun evaluate(config: C, context: ExecutionContext): Boolean {
        error?.let { throw it }
        return result
    }
}

class RecordingDiagnostics : EngineDiagnostics {
    val errors = mutableListOf<String>()

    override fun onError(macroId: Long?, message: String, cause: Throwable?) {
        errors += message
    }
}

class FakeGlobalVariableRepository : com.cadence.domain.variable.GlobalVariableRepository {
    val values = linkedMapOf<String, String>()

    override fun observeAll(): Flow<List<com.cadence.domain.variable.GlobalVariable>> = flow {
        emit(values.map { (name, value) -> com.cadence.domain.variable.GlobalVariable(name, value, TEST_INSTANT) })
    }

    override suspend fun get(name: String): String? = values[name]

    override suspend fun set(name: String, value: String) {
        values[name] = value
    }

    override suspend fun delete(name: String) {
        values.remove(name)
    }
}

class FakeDeviceStateReader(
    var batteryLevel: Int? = 50,
    var charging: Boolean = false,
    var wifiConnected: Boolean = false,
    val switches: MutableMap<com.cadence.domain.device.DeviceSwitch, Boolean> = mutableMapOf(),
    var ringer: com.cadence.domain.action.RingerMode = com.cadence.domain.action.RingerMode.NORMAL,
    var network: com.cadence.domain.device.NetworkType = com.cadence.domain.device.NetworkType.WIFI,
) : DeviceStateReader {
    override suspend fun batteryLevelPercent(): Int? = batteryLevel
    override suspend fun isCharging(): Boolean = charging
    override suspend fun isWifiConnected(): Boolean = wifiConnected
    override suspend fun switchState(switch: com.cadence.domain.device.DeviceSwitch): Boolean? = switches[switch]
    override suspend fun ringerMode() = ringer
    override suspend fun networkType() = network
}
