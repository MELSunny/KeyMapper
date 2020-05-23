package io.github.sds100.keymapper.data.model

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioManager
import android.util.Log
import io.github.sds100.keymapper.R
import io.github.sds100.keymapper.util.ActionType
import io.github.sds100.keymapper.util.SystemAction
import io.github.sds100.keymapper.util.result.ExtraNotFound
import io.github.sds100.keymapper.util.result.Result
import io.github.sds100.keymapper.util.result.Success
import io.github.sds100.keymapper.util.result.onSuccess
import splitties.bitflags.hasFlag
import java.io.Serializable

/**
 * Created by sds100 on 16/07/2018.
 */

/**
 * @property [data] The information required to perform the action. E.g if the type is [ActionType.APP],
 * the data will be the package name of the application
 *
 * Different Types of actions:
 * - Apps
 * - App shortcuts
 * - Keycode
 * - Key
 * - Insert a block of text
 * - System actions/settings
 */
data class Action(
    val type: ActionType,

    /**
     * How each action type saves data:
     *
     * - Apps: package name
     * - App shortcuts: the intent for the shortcut as a parsed URI
     * - Keycode: the keycode
     * - Key: the keycode of the key
     * - Block of text: text to insert
     * - System action: the system action id
     */
    val data: String,
    val extras: MutableList<Extra> = mutableListOf(),
    var flags: Int = 0

) : Serializable {
    companion object {
        const val ACTION_FLAG_SHOW_VOLUME_UI = 1

        val ACTION_FLAG_LABEL_MAP = mapOf(
            ACTION_FLAG_SHOW_VOLUME_UI to R.string.flag_show_volume_dialog
        )

        //DON'T CHANGE THESE IDs!!!!
        const val EXTRA_SHORTCUT_TITLE = "extra_title"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_STREAM_TYPE = "extra_stream_type"
        const val EXTRA_LENS = "extra_flash"
        const val EXTRA_RINGER_MODE = "extra_ringer_mode"

        const val EXTRA_IME_ID = "extra_ime_id"
        const val EXTRA_IME_NAME = "extra_ime_name"

        fun appAction(packageName: String): Action {
            return Action(ActionType.APP, packageName)
        }

        fun appShortcutAction(model: AppShortcutModel): Action {
            val extras = mutableListOf(
                Extra(EXTRA_SHORTCUT_TITLE, model.name),
                Extra(EXTRA_PACKAGE_NAME, model.packageName)
            )

            return Action(ActionType.APP_SHORTCUT, data = model.uri, extras = extras)
        }

        fun keyAction(keyCode: Int): Action {
            return Action(ActionType.KEY, keyCode.toString())
        }

        fun keycodeAction(keyCode: Int): Action {
            return Action(ActionType.KEYCODE, keyCode.toString())
        }

        fun textBlockAction(text: String): Action {
            return Action(ActionType.TEXT_BLOCK, text)
        }

        fun urlAction(url: String): Action {
            return Action(ActionType.URL, url)
        }

        fun systemAction(ctx: Context, model: SelectedSystemActionModel): Action {
            val action = Action(ActionType.SYSTEM_ACTION, model.id)

            model.optionData?.let {
                action.extras.add(Extra(Option.getExtraIdForOption(model.id), it))

                if (model.id == SystemAction.SWITCH_KEYBOARD) {
                    Option.getOptionLabel(ctx, model.id, it).onSuccess { imeName ->
                        action.extras.add(Extra(EXTRA_IME_NAME, imeName))
                    }
                }
            }

            return action
        }
    }

    constructor(type: ActionType, data: String, extra: Extra) : this(type, data, mutableListOf(extra))

    /**
     * A unique identifier describing this action
     */
    val uniqueId: String
        get() = buildString {
            append(type)
            append(data)
            extras.forEach {
                append("${it.id}${it.data}")
            }
        }

    fun getExtraData(extraId: String): Result<String> {
        migrateExtra(extraId)

        return extras.find { it.id == extraId }.let {
            it ?: return@let ExtraNotFound(extraId)

            Success(it.data)
        }
    }

    private fun migrateExtra(extraId: String) {
        //migrate old system action options to new ones (SDK int to key mapper string id)
        extras.find { it.id == extraId }?.data?.toIntOrNull().let { extraValueInt ->
            when (data) {
                SystemAction.VOLUME_DECREASE_STREAM, SystemAction.VOLUME_INCREASE_STREAM ->
                    when (extraValueInt) {
                        AudioManager.STREAM_ALARM -> Option.STREAM_ALARM
                        AudioManager.STREAM_DTMF -> Option.STREAM_DTMF
                        AudioManager.STREAM_MUSIC -> Option.STREAM_MUSIC
                        AudioManager.STREAM_NOTIFICATION -> Option.STREAM_NOTIFICATION
                        AudioManager.STREAM_RING -> Option.STREAM_RING
                        AudioManager.STREAM_SYSTEM -> Option.STREAM_SYSTEM
                        AudioManager.STREAM_VOICE_CALL -> Option.STREAM_VOICE_CALL
                        AudioManager.STREAM_ACCESSIBILITY -> Option.STREAM_ACCESSIBILITY
                        else -> null
                    }

                SystemAction.TOGGLE_FLASHLIGHT, SystemAction.ENABLE_FLASHLIGHT, SystemAction.DISABLE_FLASHLIGHT ->
                    when (extraValueInt) {
                        CameraCharacteristics.LENS_FACING_FRONT -> Option.LENS_FRONT
                        CameraCharacteristics.LENS_FACING_BACK -> Option.LENS_BACK
                        else -> null
                    }

                else -> null

            }?.let { newExtraValue ->
                extras.removeAll { it.id == extraId }
                extras.add(Extra(extraId, newExtraValue))
            }
        }
    }

    override fun equals(other: Any?) = this.uniqueId == (other as Action?)?.uniqueId
}