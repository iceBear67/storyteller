package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.session.DialogFrame
import io.ib67.mods.storyteller.util.TypedKey

object KnownKeys {

    /**
     * 玩家的对话 frame 列表。
     */
    val DIALOG_STACK: TypedKey<List<DialogFrame>> = TypedKey.of("dialogStack")

    /**
     * 玩家状态对应的脚本校验和。
     */
    val DIALOG_SCRIPT_CHECKSUM: TypedKey<String> = TypedKey.of("dialogScriptChecksum")

    /**
     * 全局状态中的动作间隔，单位为游戏刻。
     */
    val DIALOG_INTERVAL_TICKS: TypedKey<Number> = TypedKey.of("dialogIntervalTicks")

    /**
     * 玩家对话的挂起标记。
     */
    val DIALOG_SUSPENDED: TypedKey<Boolean> = TypedKey.of("dialogSuspended")

    /**
     * 条件变量：顶层 frame 的脚本 ID，不写入状态。
     */
    const val CURRENT_SCRIPT_ID: String = "currentScriptId"

    /**
     * 条件变量：顶层 frame 的对话 ID，不写入状态。
     */
    const val CURRENT_DIALOG_ID: String = "currentDialogId"

    /**
     * 条件变量：顶层 frame 当前动作的下标，不写入状态。
     */
    const val ACTION_INDEX: String = "actionIndex"

    /**
     * 条件变量：顶层 frame 当前动作已经过的游戏刻，动作开始前不存在。
     */
    const val ACTION_TICKS: String = "actionTicks"

    /**
     * 条件变量：顶层 frame 的动作间隔，单位为游戏刻，不写入状态。
     */
    const val SESSION_INTERVAL_TICKS: String = "sessionIntervalTicks"

    /**
     * 条件变量：顶层 frame 下一个动作允许开始的游戏刻，不写入状态。
     */
    const val SESSION_NEXT_ACTION_TICK: String = "sessionNextActionTick"

    /**
     * 条件变量：值为全局状态映射的变量名。
     */
    const val GLOBAL_SCOPE: String = "global"
}
