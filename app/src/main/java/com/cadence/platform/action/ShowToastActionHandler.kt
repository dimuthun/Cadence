package com.cadence.platform.action

import android.content.Context
import android.widget.Toast
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.plugin.ActionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ActionConfig.ShowToast]. Plain-text toasts are still allowed from the background. */
class ShowToastActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : ActionHandler<ActionConfig.ShowToast> {

    override val configType: KClass<ActionConfig.ShowToast> = ActionConfig.ShowToast::class

    override suspend fun execute(config: ActionConfig.ShowToast, context: ExecutionContext): ActionResult {
        val text = context.render(config.text)
        if (text.isBlank()) return ActionResult.Failure("There's no text to show")
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, text, if (config.long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
        return ActionResult.Success()
    }
}
