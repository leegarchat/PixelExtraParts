package org.pixel.extra.parts.systemui

// Settings.Global keys shared by the SystemUI section (text magnifier,
// activity transitions, rotation style). The string values are the legacy
// platform names and must stay byte-identical: the framework hooks read
// these exact names. Only this object holds them; a later pass renames it.
object SystemUiKeys {
    // Text magnifier (magnifier_custom_*_pine).
    const val MAGNIFIER_ENABLED = "magnifier_custom_enabled_pine"
    const val MAGNIFIER_ZOOM = "magnifier_custom_zoom_pine"
    const val MAGNIFIER_SIZE = "magnifier_custom_size_pine"
    const val MAGNIFIER_SHAPE = "magnifier_custom_shape_pine"
    const val MAGNIFIER_OFFSET_Y = "magnifier_custom_offset_y_pine"

    // Activity transitions (activity_*_pine).
    const val ACTIVITY_OPEN_TRANSITION = "activity_open_transition_pine"
    const val ACTIVITY_CLOSE_TRANSITION = "activity_close_transition_pine"
    const val ACTIVITY_TRANSITION_CUSTOM_PACKAGE = "activity_transition_custom_package_pine"
    const val ACTIVITY_OPEN_CUSTOM_PACKAGE = "activity_open_custom_package_pine"
    const val ACTIVITY_CLOSE_CUSTOM_PACKAGE = "activity_close_custom_package_pine"
    const val DISABLE_PREDICTIVE_BACK = "disable_predictive_back_anim_pine"

    // Rotation animation (no _pine suffix on the legacy names).
    const val ROTATION_STYLE = "rotation_animation_style"
    const val ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package"
}
