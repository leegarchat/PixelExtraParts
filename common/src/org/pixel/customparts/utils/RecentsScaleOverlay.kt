package org.pixel.customparts.utils

import android.content.Context
import android.content.om.OverlayManager
import android.os.UserHandle

object RecentsScaleOverlay {
    private val scaleSteps = listOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    private fun overlayPackageFor(percent: Int): String? {
        return if (scaleSteps.contains(percent)) {
            "customparts_recents_scale_${percent}_overlay"
        } else {
            null
        }
    }

    fun apply(context: Context, enabled: Boolean, percent: Int) {
        val overlayManager = context.getSystemService(OverlayManager::class.java) ?: return
        val userHandle = UserHandle.of(UserHandle.myUserId())

        for (step in scaleSteps) {
            val pkg = overlayPackageFor(step) ?: continue
            try {
                overlayManager.setEnabled(pkg, false, userHandle)
            } catch (_: Throwable) {
                
            }
        }

        if (!enabled) return

        val targetPackage = overlayPackageFor(percent) ?: return
        try {
            overlayManager.setEnabled(targetPackage, true, userHandle)
        } catch (_: Throwable) {
            
        }
    }
}
