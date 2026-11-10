package com.cadence.di

import com.cadence.domain.device.DeviceStateReader
import com.cadence.domain.engine.EngineDiagnostics
import com.cadence.domain.engine.EngineHost
import com.cadence.domain.engine.MacroEngine
import com.cadence.domain.permission.PermissionStatusProvider
import com.cadence.domain.plugin.ActionHandler
import com.cadence.domain.plugin.ActionRegistry
import com.cadence.domain.plugin.ConstraintEvaluator
import com.cadence.domain.plugin.ConstraintRegistry
import com.cadence.domain.plugin.RequirementResolver
import com.cadence.domain.plugin.TriggerHandler
import com.cadence.domain.plugin.TriggerRegistry
import com.cadence.domain.repository.ExecutionLogRepository
import com.cadence.domain.model.ExecutionStatus
import com.cadence.domain.repository.MacroRepository
import kotlinx.coroutines.flow.first
import com.cadence.domain.variable.GlobalVariableRepository
import com.cadence.domain.variable.SystemVariableSource
import com.cadence.domain.variable.VariableResolver
import com.cadence.platform.variable.AndroidSystemVariableSource
import com.cadence.platform.device.AndroidDeviceStateReader
import com.cadence.platform.diagnostics.LogcatEngineDiagnostics
import com.cadence.platform.permission.AndroidPermissionChecker
import com.cadence.platform.service.EngineServiceController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/** The macro engine, its registries, and the platform services it depends on. */
@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    abstract fun bindPermissionStatus(impl: AndroidPermissionChecker): PermissionStatusProvider

    @Binds
    abstract fun bindDeviceState(impl: AndroidDeviceStateReader): DeviceStateReader

    @Binds
    abstract fun bindDiagnostics(impl: LogcatEngineDiagnostics): EngineDiagnostics

    @Binds
    abstract fun bindEngineHost(impl: EngineServiceController): EngineHost

    @Binds
    abstract fun bindSystemVariables(impl: AndroidSystemVariableSource): SystemVariableSource

    companion object {
        @Provides
        @Singleton
        fun provideTriggerRegistry(handlers: Set<@JvmSuppressWildcards TriggerHandler<*>>) = TriggerRegistry(handlers)

        @Provides
        @Singleton
        fun provideActionRegistry(handlers: Set<@JvmSuppressWildcards ActionHandler<*>>) = ActionRegistry(handlers)

        @Provides
        @Singleton
        fun provideConstraintRegistry(evaluators: Set<@JvmSuppressWildcards ConstraintEvaluator<*>>) =
            ConstraintRegistry(evaluators)

        @Provides
        @Singleton
        fun provideVariableResolver(
            globals: GlobalVariableRepository,
            system: SystemVariableSource,
            log: ExecutionLogRepository,
        ) = VariableResolver(globals, system) { macroId ->
            log.observeExecutionsForMacro(macroId, limit = 50).first()
                .firstOrNull { it.status != ExecutionStatus.CONSTRAINTS_NOT_MET }
                ?.startedAt
        }

        @Provides
        @Singleton
        fun provideRequirementResolver(
            triggerRegistry: TriggerRegistry,
            actionRegistry: ActionRegistry,
            constraintRegistry: ConstraintRegistry,
        ) = RequirementResolver(triggerRegistry, actionRegistry, constraintRegistry)

        @Provides
        @Singleton
        fun provideMacroEngine(
            macroRepository: MacroRepository,
            executionLogRepository: ExecutionLogRepository,
            triggerRegistry: TriggerRegistry,
            actionRegistry: ActionRegistry,
            constraintRegistry: ConstraintRegistry,
            permissionStatus: PermissionStatusProvider,
            clock: Clock,
            diagnostics: EngineDiagnostics,
            variables: VariableResolver,
        ) = MacroEngine(
            macroRepository = macroRepository,
            executionLogRepository = executionLogRepository,
            triggerRegistry = triggerRegistry,
            actionRegistry = actionRegistry,
            constraintRegistry = constraintRegistry,
            permissionStatus = permissionStatus,
            clock = clock,
            diagnostics = diagnostics,
            variables = variables,
        )
    }
}
