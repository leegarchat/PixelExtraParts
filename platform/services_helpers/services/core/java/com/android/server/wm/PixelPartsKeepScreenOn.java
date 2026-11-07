package com.android.server.wm;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;

/**
 * PixelExtraParts keep-screen-on broadcast helper (DELEGATE target).
 * Called from DisplayContent.finishHoldScreenUpdate via a single static
 * call; all broadcast logic lives here so the framework diff stays minimal.
 */
public final class PixelPartsKeepScreenOn {
    private PixelPartsKeepScreenOn() {}

    private static final String PIXELPARTS_PACKAGE = "org.pixel.customparts";
    private static final String ACTION_PIXELPARTS_KEEP_SCREEN_ON =
            "org.pixel.customparts.action.KEEP_SCREEN_ON_CHANGED";
    private static final String EXTRA_PIXELPARTS_KEEP_SCREEN_ON = "active";
    private static final String EXTRA_PIXELPARTS_KEEP_SCREEN_ON_PACKAGE = "package";

    public static void notifyChanged(Handler handler, Context context,
            boolean hold, boolean wasHolding, String holderPackage) {
        if (hold == wasHolding) {
            return;
        }
        handler.post(() -> context.sendBroadcastAsUser(
                new Intent(ACTION_PIXELPARTS_KEEP_SCREEN_ON)
                        .setPackage(PIXELPARTS_PACKAGE)
                        .putExtra(EXTRA_PIXELPARTS_KEEP_SCREEN_ON, hold)
                        .putExtra(EXTRA_PIXELPARTS_KEEP_SCREEN_ON_PACKAGE, holderPackage),
                UserHandle.ALL));
    }
}
