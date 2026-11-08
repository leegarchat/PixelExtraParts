package com.android.server.policy;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.view.KeyEvent;

/**
 * PixelExtraParts hardware-input broadcast helper (DELEGATE target).
 * Called from PhoneWindowManager.interceptKeyBeforeQueueing via a single
 * static call; all logic lives here so the framework diff stays minimal.
 */
public final class PixelPartsHardwareInput {
    private PixelPartsHardwareInput() {}

    private static final String PIXELPARTS_PACKAGE = "org.pixel.customparts";
    private static final String ACTION_PIXELPARTS_HARDWARE_INPUT =
            "org.pixel.customparts.action.HARDWARE_INPUT";

    public static void notifyHardwareInput(Handler handler, Context context,
            KeyEvent event, boolean down, int policyFlags) {
        if (!down || (policyFlags & WindowManagerPolicy.FLAG_INJECTED) != 0) {
            return;
        }
        final KeyEvent keyEvent = KeyEvent.obtain(event);
        handler.post(() -> {
            try {
                context.sendBroadcastAsUser(new Intent(ACTION_PIXELPARTS_HARDWARE_INPUT)
                        .setPackage(PIXELPARTS_PACKAGE)
                        .putExtra(Intent.EXTRA_KEY_EVENT, keyEvent), UserHandle.ALL);
            } finally {
                keyEvent.recycle();
            }
        });
    }
}
