package org.pixel.extra.parts.addons

import org.pixel.extra.parts.R

// Built-in declarative stub for the systemui_hooks pack: rotation, HBM and
// AOD pages. Values persist under AddonKeys; no hook reads them yet, so the
// pages only demonstrate the engine until the host binds the keys.
internal object SystemUiHooksAddon {
    private val rotationPage = AddonPage(
        id = "rotation",
        titleRes = R.string.addon_rotation_title,
        descriptionRes = R.string.addon_rotation_desc,
        nodes = listOf(
            AddonNode.Segment(
                key = AddonKeys.ROTATION_MODE,
                titleRes = R.string.addon_rotation_style_title,
                default = AddonKeys.ROTATION_MODE_DEFAULT,
                options = listOf(
                    SegmentChoice(0, R.string.sysui_rot_default),
                    SegmentChoice(1, R.string.sysui_rot_zoom),
                    SegmentChoice(2, R.string.sysui_rot_slide),
                    SegmentChoice(3, R.string.sysui_rot_fade),
                ),
            ),
            AddonNode.Toggle(
                key = AddonKeys.ROTATION_SMOOTH,
                titleRes = R.string.addon_rotation_smooth_title,
                summaryRes = R.string.addon_rotation_smooth_desc,
                default = true,
            ),
        ),
    )

    private val hbmPage = AddonPage(
        id = "hbm",
        titleRes = R.string.addon_hbm_title,
        descriptionRes = R.string.addon_hbm_desc,
        nodes = listOf(
            AddonNode.Toggle(
                key = AddonKeys.HBM_LOCK,
                titleRes = R.string.addon_hbm_lock_title,
                summaryRes = R.string.addon_hbm_lock_desc,
                default = false,
            ),
            AddonNode.Slider(
                key = AddonKeys.HBM_WINDOW_S,
                titleRes = R.string.addon_hbm_window_title,
                min = AddonKeys.HBM_WINDOW_MIN_S,
                max = AddonKeys.HBM_WINDOW_MAX_S,
                step = AddonKeys.HBM_WINDOW_STEP_S,
                default = AddonKeys.HBM_WINDOW_DEFAULT_S,
                unit = AddonSliderUnit.SEC,
            ),
        ),
    )

    private val aodPage = AddonPage(
        id = "aod",
        titleRes = R.string.addon_aod_title,
        descriptionRes = R.string.addon_aod_desc,
        nodes = listOf(
            AddonNode.Group(
                titleRes = R.string.addon_aod_group_title,
                subtitleRes = R.string.addon_aod_group_desc,
                collapsible = true,
                children = listOf(
                    AddonNode.Toggle(
                        key = AddonKeys.AOD_SHIFT,
                        titleRes = R.string.addon_aod_shift_title,
                        summaryRes = R.string.addon_aod_shift_desc,
                        default = true,
                    ),
                    AddonNode.Toggle(
                        key = AddonKeys.AOD_FULL_COLOR,
                        titleRes = R.string.addon_aod_color_title,
                        summaryRes = R.string.addon_aod_color_desc,
                        default = false,
                    ),
                ),
            ),
        ),
    )

    val descriptor = AddonDescriptor(
        id = "systemui_hooks",
        nameRes = R.string.addon_systemui_name,
        descriptionRes = R.string.addon_systemui_desc,
        pages = listOf(rotationPage, hbmPage, aodPage),
    )
}
