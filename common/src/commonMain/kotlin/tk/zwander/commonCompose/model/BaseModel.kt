package tk.zwander.commonCompose.model

import androidx.compose.runtime.*
import io.ktor.client.utils.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tk.zwander.common.data.imei.IMEIDatabase
import tk.zwander.common.data.imei.IMEIGenerator
import tk.zwander.common.util.BifrostSettings

/**
 * A model class to hold information for the various views.
 */
abstract class BaseModel(
    private val modelKey: String,
) {
    companion object {
        private const val MODEL_KEY = "field_model"
        private const val REGION_KEY = "field_region"
        private const val FIRMWARE_KEY = "field_firmware"
        private const val IMEI_SERIAL_KEY = "field_imei_serial"
    }

    /**
     * Device model.
     */
    val model = MutableStateFlow(BifrostSettings.settings.getString(MODEL_KEY.fullKey, ""))

    /**
     * Device region.
     */
    val region = MutableStateFlow(BifrostSettings.settings.getString(REGION_KEY.fullKey, ""))

    /**
     * Firmware string, if available.
     */
    val fw = MutableStateFlow(BifrostSettings.settings.getString(FIRMWARE_KEY.fullKey, ""))

    val imeiSerial = MutableStateFlow(BifrostSettings.settings.getString(IMEI_SERIAL_KEY.fullKey, ""))

    /**
     * Current status, if available.
     */
    val statusText = MutableStateFlow("")

    /**
     * The current speed of the operation.
     */
    val speed = MutableStateFlow(0L)

    /**
     * The current progress of the operation,
     * based on Pair(current, max).
     */
    val progress = MutableStateFlow(0L to 0L)

    private val _jobs = MutableStateFlow(listOf<Job>())

    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    val hasRunningJobs: Flow<Boolean>
        get() = jobs.map { it.any { j -> j.isActive } }

    /**
     * A coroutine scope.
     */
    @OptIn(InternalAPI::class)
    private val scope = CoroutineScope(Dispatchers.clientDispatcher(5, "Background${this::class.simpleName}"))

    protected val String.fullKey: String
        get() = "${modelKey}_$this"

            /**
     * Called when a Job should be ended.
     * @param text the text to show in the status message.
     */
    open val endJob = { text: String ->
        _jobs.value.forEach {
            it.cancelChildren()
            it.cancel()
        }
        _jobs.value = listOf()

        progress.value = 0L to 0L
        speed.value = 0L
        statusText.value = text

        onEnd(text)
    }

    fun launchJob(block: suspend CoroutineScope.() -> Unit) {
        _jobs.value += scope.launch(block = block)
    }

    /**
     * Sub-classes can override this to perform
     * extra operations when a Job ends.
     */
    protected open fun onEnd(text: String) {}

    suspend fun onCreate() = coroutineScope {
        launch(Dispatchers.Unconfined) {
            model.collect {
                BifrostSettings.settings.putString(MODEL_KEY.fullKey, it)
            }
        }

        launch(Dispatchers.Unconfined) {
            model.combine(IMEIDatabase.imeis) { model, imeis ->
                model to imeis
            }.collect { (model, imeis) ->
                imeiSerial.value = IMEIGenerator.makeImeisForModel(model, imeis).joinToString("\n")
            }
        }

        launch(Dispatchers.Unconfined) {
            region.collect {
                BifrostSettings.settings.putString(REGION_KEY.fullKey, it)
            }
        }

        launch(Dispatchers.Unconfined) {
            fw.collect {
                BifrostSettings.settings.putString(FIRMWARE_KEY.fullKey, it)
            }
        }

        launch(Dispatchers.Unconfined) {
            imeiSerial.collect {
                BifrostSettings.settings.putString(IMEI_SERIAL_KEY.fullKey, it)
            }
        }

        createExtra()
    }

    protected open suspend fun createExtra() {}
}
