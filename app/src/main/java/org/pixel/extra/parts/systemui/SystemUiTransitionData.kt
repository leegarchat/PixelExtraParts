package org.pixel.extra.parts.systemui

import android.content.Context
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import org.pixel.extra.parts.R

// Built-in activity-transition catalog. Mode IDs are firmware-stable: the
// framework hooks persist the raw int, so IDs must never be renumbered.
internal const val TRANSITION_THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim."
internal const val TRANSITION_MODE_DISABLED = 0
internal const val TRANSITION_MODE_CUSTOM = -1
internal const val TRANSITION_MODE_NO_ANIMATION = -2

internal data class TransitionAnimMode(
    val modeId: Int,
    @StringRes val groupNameRes: Int,
    @StringRes val dirNameRes: Int? = null,
    @AnimRes val openEnterAnim: Int,
    @AnimRes val openExitAnim: Int,
    @AnimRes val closeEnterAnim: Int,
    @AnimRes val closeExitAnim: Int,
)

internal val TRANSITION_BUILTIN_MODES: List<TransitionAnimMode> by lazy {
    listOf(
        TransitionAnimMode(10, R.string.sysui_trans_group_slide, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_right),
        TransitionAnimMode(11, R.string.sysui_trans_group_slide, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.no_anim, R.anim.no_anim, R.anim.train_exit),
        TransitionAnimMode(12, R.string.sysui_trans_group_slide, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_top),
        TransitionAnimMode(13, R.string.sysui_trans_group_slide, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.no_anim, R.anim.no_anim, R.anim.slide_down_exit),
        TransitionAnimMode(20, R.string.sysui_trans_group_card_stack, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.card_stack_exit, R.anim.card_stack_enter, R.anim.slide_out_right),
        TransitionAnimMode(21, R.string.sysui_trans_group_card_stack, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.card_stack_exit_left, R.anim.card_stack_enter_left, R.anim.train_exit),
        TransitionAnimMode(22, R.string.sysui_trans_group_card_stack, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.card_stack_exit_top, R.anim.card_stack_enter_top, R.anim.slide_out_top),
        TransitionAnimMode(23, R.string.sysui_trans_group_card_stack, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.card_stack_exit_bottom, R.anim.card_stack_enter_bottom, R.anim.slide_down_exit),
        TransitionAnimMode(30, R.string.sysui_trans_group_train, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.train_exit, R.anim.slide_in_left, R.anim.slide_out_right),
        TransitionAnimMode(31, R.string.sysui_trans_group_train, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.train_exit),
        TransitionAnimMode(32, R.string.sysui_trans_group_train, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.slide_down_exit, R.anim.slide_up_enter, R.anim.slide_out_top),
        TransitionAnimMode(33, R.string.sysui_trans_group_train, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_out_top, R.anim.slide_in_top, R.anim.slide_down_exit),
        TransitionAnimMode(40, R.string.sysui_trans_group_ios, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.ios_open_exit, R.anim.ios_close_enter, R.anim.slide_out_right),
        TransitionAnimMode(41, R.string.sysui_trans_group_ios, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.ios_open_exit_left, R.anim.ios_close_enter_left, R.anim.train_exit),
        TransitionAnimMode(42, R.string.sysui_trans_group_ios, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.ios_open_exit_top, R.anim.ios_close_enter_top, R.anim.slide_out_top),
        TransitionAnimMode(43, R.string.sysui_trans_group_ios, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.ios_open_exit_bottom, R.anim.ios_close_enter_bottom, R.anim.slide_down_exit),
        TransitionAnimMode(50, R.string.sysui_trans_group_fade, null,
            R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out),
        TransitionAnimMode(60, R.string.sysui_trans_group_zoom, null,
            R.anim.zoom_enter, R.anim.zoom_exit, R.anim.zoom_close_enter, R.anim.zoom_close_exit),
        TransitionAnimMode(70, R.string.sysui_trans_group_modal, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.slide_up_exit_right, R.anim.slide_up_close_enter_right, R.anim.slide_out_right),
        TransitionAnimMode(71, R.string.sysui_trans_group_modal, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.slide_up_exit_left, R.anim.slide_up_close_enter_left, R.anim.train_exit),
        TransitionAnimMode(72, R.string.sysui_trans_group_modal, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.slide_up_exit_top, R.anim.slide_up_close_enter_top, R.anim.slide_out_top),
        TransitionAnimMode(73, R.string.sysui_trans_group_modal, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_up_exit, R.anim.slide_up_close_enter, R.anim.slide_down_exit),
        TransitionAnimMode(80, R.string.sysui_trans_group_depth, R.string.sysui_trans_dir_right,
            R.anim.slide_in_right, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_right),
        TransitionAnimMode(81, R.string.sysui_trans_group_depth, R.string.sysui_trans_dir_left,
            R.anim.slide_in_left, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.train_exit),
        TransitionAnimMode(82, R.string.sysui_trans_group_depth, R.string.sysui_trans_dir_top,
            R.anim.slide_in_top, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_top),
        TransitionAnimMode(83, R.string.sysui_trans_group_depth, R.string.sysui_trans_dir_bottom,
            R.anim.slide_up_enter, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_down_exit),
        TransitionAnimMode(90, R.string.sysui_trans_group_pivot, R.string.sysui_trans_dir_right,
            R.anim.pivot_enter, R.anim.pivot_exit, R.anim.pivot_close_enter, R.anim.pivot_close_exit),
        TransitionAnimMode(91, R.string.sysui_trans_group_pivot, R.string.sysui_trans_dir_left,
            R.anim.pivot_enter_left, R.anim.pivot_exit_left, R.anim.pivot_close_enter_left, R.anim.pivot_close_exit_left),
        TransitionAnimMode(92, R.string.sysui_trans_group_pivot, R.string.sysui_trans_dir_top,
            R.anim.pivot_enter_top, R.anim.pivot_exit_top, R.anim.pivot_close_enter_top, R.anim.pivot_close_exit_top),
        TransitionAnimMode(93, R.string.sysui_trans_group_pivot, R.string.sysui_trans_dir_bottom,
            R.anim.pivot_enter_bottom, R.anim.pivot_exit_bottom, R.anim.pivot_close_enter_bottom, R.anim.pivot_close_exit_bottom),
    )
}

internal fun transitionModeDisplayName(context: Context, modeId: Int): String {
    if (modeId == TRANSITION_MODE_DISABLED) return context.getString(R.string.sysui_trans_builtin_disabled)
    if (modeId == TRANSITION_MODE_CUSTOM) return context.getString(R.string.sysui_trans_custom)
    if (modeId == TRANSITION_MODE_NO_ANIMATION) return context.getString(R.string.sysui_trans_no_anim_title)
    val mode = TRANSITION_BUILTIN_MODES.find { it.modeId == modeId } ?: return "Mode $modeId"
    val group = context.getString(mode.groupNameRes)
    return if (mode.dirNameRes != null) "$group — ${context.getString(mode.dirNameRes)}" else group
}

internal data class InstalledTransitionTheme(
    val packageName: String,
    val styleName: String,
    val isActiveOpen: Boolean,
    val isActiveClose: Boolean,
)

// Theme discovery mirrors the legacy scan: any installed package under the
// prefix is a candidate; the per-side custom package keys decide activeness.
internal fun loadInstalledTransitionThemes(
    context: Context,
    currentOpenMode: Int,
    currentCloseMode: Int,
): List<InstalledTransitionTheme> {
    val activeOpenPkg = readGlobalString(context, SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
        ?: readGlobalString(context, SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
    val activeClosePkg = readGlobalString(context, SystemUiKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE)
        ?: readGlobalString(context, SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
    return runCatching {
        context.packageManager.getInstalledPackages(0)
            .filter { it.packageName.startsWith(TRANSITION_THEME_PACKAGE_PREFIX) }
            .map { info ->
                InstalledTransitionTheme(
                    packageName = info.packageName,
                    styleName = info.packageName.removePrefix(TRANSITION_THEME_PACKAGE_PREFIX),
                    isActiveOpen = currentOpenMode == TRANSITION_MODE_CUSTOM && info.packageName == activeOpenPkg,
                    isActiveClose = currentCloseMode == TRANSITION_MODE_CUSTOM && info.packageName == activeClosePkg,
                )
            }
            .sortedBy { it.styleName }
    }.getOrDefault(emptyList())
}

internal fun applyTransitionOpen(context: Context, mode: Int) {
    writeGlobalInt(context, SystemUiKeys.ACTIVITY_OPEN_TRANSITION, mode)
}

internal fun applyTransitionClose(context: Context, mode: Int) {
    writeGlobalInt(context, SystemUiKeys.ACTIVITY_CLOSE_TRANSITION, mode)
}

internal fun activateTransitionTheme(context: Context, packageName: String) {
    writeGlobalString(context, SystemUiKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE, packageName)
    writeGlobalString(context, SystemUiKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
    writeGlobalString(context, SystemUiKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
}
