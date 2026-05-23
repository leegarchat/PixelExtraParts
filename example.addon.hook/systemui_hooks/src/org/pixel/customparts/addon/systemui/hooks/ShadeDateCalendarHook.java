package org.pixel.customparts.addon.systemui.hooks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ShadeDateCalendarHook extends BaseSystemUIHook {
    private static final String KEY_DATE_OPENS_CALENDAR = "shade_date_opens_calendar";

    @Override
    public String getHookId() {
        return "ShadeDateCalendarHook";
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        hookShadeHeaderController(classLoader);
    }

    private void hookShadeHeaderController(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = XposedHelpers.findClass(
                    "com.android.systemui.shade.ShadeHeaderController",
                    classLoader);
            XposedBridge.hookAllMethods(controllerClass, "onViewAttached", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    installDateClickListener(param.thisObject);
                }
            });
            XposedBridge.hookAllMethods(controllerClass, "onViewDetached", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    clearDateClickListener(param.thisObject);
                }
            });
            log("Shade header date click hook installed");
        } catch (Throwable throwable) {
            logError("Failed to hook ShadeHeaderController date click", throwable);
        }
    }

    private void installDateClickListener(final Object controller) {
        View dateView = getDateView(controller);
        if (dateView == null) return;

        dateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view != null ? view.getContext() : null;
                if (isSettingEnabled(context, KEY_DATE_OPENS_CALENDAR, false)) {
                    launchCalendar(controller, context);
                } else {
                    launchClock(controller, context);
                }
            }
        });
    }

    private void clearDateClickListener(Object controller) {
        View dateView = getDateView(controller);
        if (dateView != null) {
            dateView.setOnClickListener(null);
        }
    }

    private View getDateView(Object controller) {
        try {
            Object date = XposedHelpers.getObjectField(controller, "date");
            return date instanceof View ? (View) date : null;
        } catch (Throwable throwable) {
            logError("Failed to resolve shade date view", throwable);
            return null;
        }
    }

    private void launchCalendar(Object controller, Context context) {
        Intent intent = buildCalendarIntent(context);
        if (!startThroughActivityStarter(controller, intent)) {
            startFromContext(context, intent);
        }
    }

    private Intent buildCalendarIntent(Context context) {
        Uri dateUri = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .appendPath(Long.toString(System.currentTimeMillis()))
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, dateUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (context == null || context.getPackageManager().resolveActivity(intent, 0) != null) {
            return intent;
        }
        Intent fallback = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_CALENDAR);
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return fallback;
    }

    private void launchClock(Object controller, Context context) {
        Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!startThroughActivityStarter(controller, intent)) {
            startFromContext(context, intent);
        }
    }

    private boolean startThroughActivityStarter(Object controller, Intent intent) {
        if (controller == null || intent == null) return false;
        try {
            Object starter = XposedHelpers.getObjectField(controller, "activityStarter");
            if (starter == null) return false;
            XposedHelpers.callMethod(starter, "postStartActivityDismissingKeyguard", intent, 0);
            return true;
        } catch (Throwable throwable) {
            logError("Failed to launch date action via ActivityStarter", throwable);
            return false;
        }
    }

    private void startFromContext(Context context, Intent intent) {
        if (context == null || intent == null) return;
        try {
            context.startActivity(intent);
        } catch (Throwable throwable) {
            logError("Failed to launch date action from context", throwable);
        }
    }
}