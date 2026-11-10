package com.cadence.platform.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.cadence.R
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.plugin.ActionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.reflect.KClass

private const val BACKGROUND_PROMPT_MESSAGE =
    "Android blocks opening apps from the background, so a notification was posted to open it. " +
        "Allow \"Display over other apps\" for Cadence to open it directly."

private fun ActivityLauncher.Outcome.toLaunchResult(): ActionResult = when (this) {
    ActivityLauncher.Outcome.Started -> ActionResult.Success()
    ActivityLauncher.Outcome.PromptPosted -> ActionResult.Fallback(BACKGROUND_PROMPT_MESSAGE)
    is ActivityLauncher.Outcome.Failed -> ActionResult.Failure(reason)
}

/** [ActionConfig.LaunchApp]. Visible apps come from the manifest's launcher `<queries>`. */
class LaunchAppActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionConfig.LaunchApp> {
    override val configType: KClass<ActionConfig.LaunchApp> = ActionConfig.LaunchApp::class

    override suspend fun execute(config: ActionConfig.LaunchApp, context: ExecutionContext): ActionResult {
        val packageManager = appContext.packageManager
        val intent = packageManager.getLaunchIntentForPackage(config.packageName)
            ?: return ActionResult.Failure("${config.packageName} isn't installed or has no launcher screen")
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(config.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            config.packageName
        }
        return launcher.launch(
            intent,
            promptTitle = appContext.getString(R.string.prompt_open_app, label),
            promptText = appContext.getString(R.string.prompt_text, context.macro.name),
        ).toLaunchResult()
    }
}

/** [ActionConfig.OpenUrl]. Only http and https links are allowed. */
class OpenUrlActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val launcher: ActivityLauncher,
) : ActionHandler<ActionConfig.OpenUrl> {
    override val configType: KClass<ActionConfig.OpenUrl> = ActionConfig.OpenUrl::class

    override suspend fun execute(config: ActionConfig.OpenUrl, context: ExecutionContext): ActionResult {
        val uri = Uri.parse(context.render(config.url).trim())
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES || uri.host.isNullOrBlank()) {
            return ActionResult.Failure("Only http:// and https:// links can be opened")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return launcher.launch(
            intent,
            promptTitle = appContext.getString(R.string.prompt_open_url),
            promptText = appContext.getString(R.string.prompt_text, context.macro.name),
        ).toLaunchResult()
    }

    private companion object {
        val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
