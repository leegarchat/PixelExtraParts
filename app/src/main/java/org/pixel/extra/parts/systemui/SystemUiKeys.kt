package org.pixel.extra.parts.systemui

// Settings.Global keys shared by the SystemUI section (text magnifier,
// activity transitions, rotation style). The framework hooks read
// these exact names. Only this object holds them.
object SystemUiKeys {
    // Text magnifier.
    const val MAGNIFIER_ENABLED = "magnifier_custom_enabled"
    const val MAGNIFIER_ZOOM = "magnifier_custom_zoom"
    const val MAGNIFIER_SIZE = "magnifier_custom_size"
    const val MAGNIFIER_SHAPE = "magnifier_custom_shape"
    const val MAGNIFIER_OFFSET_Y = "magnifier_custom_offset_y"

    // Activity transitions.
    const val ACTIVITY_OPEN_TRANSITION = "activity_open_transition"
    const val ACTIVITY_CLOSE_TRANSITION = "activity_close_transition"
    const val ACTIVITY_TRANSITION_CUSTOM_PACKAGE = "activity_transition_custom_package"
    const val ACTIVITY_OPEN_CUSTOM_PACKAGE = "activity_open_custom_package"
    const val ACTIVITY_CLOSE_CUSTOM_PACKAGE = "activity_close_custom_package"
    const val DISABLE_PREDICTIVE_BACK = "disable_predictive_back_anim"

    // Rotation animation.
    const val ROTATION_STYLE = "rotation_animation_style"
    const val ROTATION_CUSTOM_PACKAGE = "rotation_animation_custom_package"
}
