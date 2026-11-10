package com.cadence.platform.action

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cadence.domain.action.ActionConfig
import com.cadence.domain.action.ActionResult
import com.cadence.domain.action.ToggleState
import com.cadence.domain.engine.ExecutionContext
import com.cadence.domain.engine.render
import com.cadence.domain.plugin.ActionHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** [ActionConfig.Vibrate]. */
class VibrateActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : ActionHandler<ActionConfig.Vibrate> {
    override val configType: KClass<ActionConfig.Vibrate> = ActionConfig.Vibrate::class

    override suspend fun execute(config: ActionConfig.Vibrate, context: ExecutionContext): ActionResult {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            appContext.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return ActionResult.Failure("This device can't vibrate")
        vibrator.vibrate(VibrationEffect.createOneShot(config.durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
        return ActionResult.Success()
    }
}

/** [ActionConfig.SetFlashlight]. Uses torch mode, so no camera permission is needed. */
class SetFlashlightActionHandler @Inject constructor(
    @ApplicationContext appContext: Context,
) : ActionHandler<ActionConfig.SetFlashlight> {
    override val configType: KClass<ActionConfig.SetFlashlight> = ActionConfig.SetFlashlight::class

    private val cameraManager = appContext.getSystemService(CameraManager::class.java)

    override suspend fun execute(config: ActionConfig.SetFlashlight, context: ExecutionContext): ActionResult {
        val cameraId = torchCameraId() ?: return ActionResult.Failure("This device has no flashlight")
        val target = when (config.state) {
            ToggleState.ON -> true
            ToggleState.OFF -> false
            ToggleState.TOGGLE -> !(currentTorchState(cameraId) ?: false)
        }
        return try {
            cameraManager.setTorchMode(cameraId, target)
            ActionResult.Success()
        } catch (e: CameraAccessException) {
            ActionResult.Failure("The camera is in use by another app", e)
        }
    }

    /** Prefers the back camera's flash. */
    private fun torchCameraId(): String? {
        val withFlash = cameraManager.cameraIdList.filter { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        return withFlash.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }

    /** The torch callback reports the current state right after registering. */
    private suspend fun currentTorchState(cameraId: String): Boolean? = withTimeoutOrNull(1.seconds) {
        callbackFlow {
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) trySend(enabled)
                }

                override fun onTorchModeUnavailable(id: String) {
                    if (id == cameraId) trySend(false)
                }
            }
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            awaitClose { cameraManager.unregisterTorchCallback(callback) }
        }.first()
    }
}

/**
 * [ActionConfig.SpeakText]. Creates a TextToSpeech instance per run and shuts it down afterwards,
 * so no engine stays bound between runs.
 */
class SpeakTextActionHandler @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) : ActionHandler<ActionConfig.SpeakText> {
    override val configType: KClass<ActionConfig.SpeakText> = ActionConfig.SpeakText::class

    override suspend fun execute(config: ActionConfig.SpeakText, context: ExecutionContext): ActionResult {
        val text = context.render(config.text)
        if (text.isBlank()) return ActionResult.Failure("There's no text to speak")
        if (text.length > TextToSpeech.getMaxSpeechInputLength()) {
            return ActionResult.Failure("The text is too long to speak")
        }
        return withContext(Dispatchers.Main) {
            val tts = initialize() ?: return@withContext ActionResult.Failure("No text-to-speech engine is available")
            try {
                config.languageTag?.let { tag ->
                    val result = tts.setLanguage(Locale.forLanguageTag(tag))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        return@withContext ActionResult.Failure("The language $tag isn't available for speech")
                    }
                }
                speak(tts, text)
            } finally {
                tts.shutdown()
            }
        }
    }

    private suspend fun initialize(): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                continuation.resume(tts)
            } else {
                tts.shutdown()
                continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { tts.shutdown() }
    }

    private suspend fun speak(tts: TextToSpeech, text: String): ActionResult = suspendCancellableCoroutine { continuation ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) {
                if (id == utteranceId && continuation.isActive) continuation.resume(ActionResult.Success())
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resume(ActionResult.Failure("Text-to-speech failed"))
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resume(ActionResult.Failure("Text-to-speech failed (error $errorCode)"))
                }
            }
        })
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
            continuation.resume(ActionResult.Failure("Text-to-speech refused the text"))
        }
        continuation.invokeOnCancellation { tts.stop() }
    }
}
