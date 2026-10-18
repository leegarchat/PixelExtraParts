package org.pixel.customparts.addon.yandexmaps;

import android.content.Context;

import org.pixel.customparts.core.IAddonHook;

import java.util.Collections;
import java.util.Set;

import org.pixel.customparts.addon.yandexmaps.hooks.FeedHook;
import org.pixel.customparts.addon.yandexmaps.hooks.SearchHistoryHook;
import org.pixel.customparts.addon.yandexmaps.hooks.TaxiButtonHook;
import org.pixel.customparts.addon.yandexmaps.hooks.VulkanHook;

/** Entry point: installs every Yandex Maps tweak. Each hook is self-guarded by its own settings key. */
public final class YandexMapsHook implements IAddonHook {
    private static final String TARGET = "ru.yandex.yandexmaps";

    @Override public String getId() { return "yandexmaps_fast"; }
    @Override public String getName() { return "Yandex Maps Fast"; }
    @Override public String getAuthor() { return "PixelExtraParts"; }
    @Override public String getDescription() { return "Vulkan render and declutter tweaks for Yandex Maps."; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton(TARGET); }
    @Override public int getPriority() { return 900; }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET.equals(packageName)) return;
        Settings.markProcessStart();
        VulkanHook.install(context, classLoader);
        TaxiButtonHook.install(context, classLoader);
        FeedHook.install(context, classLoader);
        SearchHistoryHook.install(context, classLoader);
    }
}
