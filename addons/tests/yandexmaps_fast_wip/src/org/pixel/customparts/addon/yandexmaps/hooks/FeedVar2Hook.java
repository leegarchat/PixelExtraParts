package org.pixel.customparts.addon.yandexmaps.hooks;

import android.content.Context;

import org.pixel.customparts.addon.yandexmaps.Settings;

/**
 * Author-style feed suppression (mode var2, analogous to the reference
 * patcher's discovery-shutter hooks): hides the discovery feed content
 * instead of driving anchors. Filled in during the port merge.
 */
public final class FeedVar2Hook {
    private FeedVar2Hook() {}

    /**
     * Author-style content suppression entry point. The actual hooks live in
     * {@link DiscoveryHooks} and are wired separately from
     * {@link org.pixel.customparts.addon.yandexmaps.YandexMapsHook}, so this
     * only verifies the mode (avoids double-hooking the same methods).
     */
    public static void install(final Context context, final ClassLoader cl) {
        if (!Settings.FEED_VAR2.equals(Settings.feedMode(context))) return;
        android.util.Log.i("PixelPartsYandexFast", "feed var2 active (DiscoveryHooks wired separately)");
    }
}
