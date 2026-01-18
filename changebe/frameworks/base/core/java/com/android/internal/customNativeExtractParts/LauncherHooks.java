package com.android.internal.customNativeExtractParts;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class LauncherHooks {

    public static void init(Application app) {
        LauncherDT2S.init();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                LauncherDT2S.attach(activity);
                LauncherClearAll.attach(activity);
                LauncherGridSize.attach(activity);
                SearchWidgetHook.attach(activity);
                LauncherFeedDisabler.checkAndDisableFeed(activity);
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
}