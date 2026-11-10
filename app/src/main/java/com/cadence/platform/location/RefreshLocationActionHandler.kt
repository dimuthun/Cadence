package com.cadence.platform.location

import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.domain.plugin.ActionHandler
import com.cadence.platform.variable.AndroidSystemVariableSource
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ActionConfig.RefreshLocation]: gets a brand-new fix and makes it this run's location, so
 * later `{location.*}` placeholders use it. A run that never refreshes keeps using the last
 * known location.
 */
class RefreshLocationActionHandler @Inject constructor(
    private val locations: FusedLocationSource,
    private val playServices: PlayServicesAvailability,
) : ActionHandler<ActionConfig.RefreshLocation> {

    override val configType: KClass<ActionConfig.RefreshLocation> = ActionConfig.RefreshLocation::class

    override fun requiredPermissions(config: ActionConfig.RefreshLocation) =
        setOf(PermissionRequirement.FINE_LOCATION, PermissionRequirement.BACKGROUND_LOCATION)

    override fun timeout(config: ActionConfig.RefreshLocation): Duration = (config.timeoutSeconds + 5).seconds

    override suspend fun execute(config: ActionConfig.RefreshLocation, context: ExecutionContext): ActionResult {
        if (!playServices.isAvailable()) return ActionResult.Failure("Google Play services isn't available on this device")
        val snapshot = locations.freshSnapshot(config.highAccuracy, config.timeoutSeconds * 1_000L)
            ?: return ActionResult.Failure(
                "No location within ${config.timeoutSeconds} s. Is location turned on? " +
                    "Indoors, try turning off high accuracy.",
            )
        context.runCache[AndroidSystemVariableSource.LOCATION_CACHE_KEY] = snapshot
        context.variables.forgetCached(context, "location.")
        val accuracy = snapshot.accuracyMeters?.let { String.format(Locale.ROOT, " ±%.0f m", it) }.orEmpty()
        return ActionResult.Success("Location updated$accuracy")
    }
}
