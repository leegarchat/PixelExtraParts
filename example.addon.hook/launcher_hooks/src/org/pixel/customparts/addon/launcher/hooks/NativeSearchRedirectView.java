package org.pixel.customparts.addon.launcher.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

/** Keeps the stock QSB UI but routes its tap to Launcher app search. */
final class NativeSearchRedirectView extends FrameLayout {
    private static final String GOOGLE_SEARCH_PACKAGE =
            "com.google.android.googlequicksearchbox";
    private final View original;
    private final int googleLogoId;
    private final int lensButtonId;
    private final int oneRightIconId;
    private final int shortcutButtonId;
    private final int twoRightIconsId;
    private final int voiceButtonId;
    private final int thirdPartyVoiceButtonId;

    NativeSearchRedirectView(Context context, View original) {
        super(context);
        this.original = original;
        googleLogoId = widgetId(context, "googleapp_search_widget_google_logo");
        lensButtonId = widgetId(context, "googleapp_search_widget_lens_btn");
        oneRightIconId = widgetId(context, "googleapp_search_widget_one_right_icon");
        shortcutButtonId = widgetId(context, "googleapp_search_widget_shortcut_btn");
        twoRightIconsId = widgetId(context, "googleapp_search_widget_two_right_icons");
        voiceButtonId = widgetId(context, "googleapp_search_widget_voice_btn");
        thirdPartyVoiceButtonId = widgetId(
                context, "googleapp_search_widget_third_party_voice_btn");
        setClipChildren(false);
        setClipToPadding(false);

        ViewGroup.LayoutParams originalParams = original.getLayoutParams();
        if (originalParams != null) {
            setLayoutParams(originalParams);
        }
        addView(this.original, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View clickInterceptor = new FieldClickInterceptor(context);
        addView(clickInterceptor, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private final class FieldClickInterceptor extends View {
        private boolean fieldTouch;

        FieldClickInterceptor(Context context) {
            super(context);
            setClickable(true);
            setOnClickListener(v -> openLauncherSearch());
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fieldTouch = isFieldPoint(event.getX(), event.getY());
                    return fieldTouch;
                case MotionEvent.ACTION_MOVE:
                    return fieldTouch;
                case MotionEvent.ACTION_UP:
                    boolean click = fieldTouch && isFieldPoint(event.getX(), event.getY());
                    fieldTouch = false;
                    if (click) performClick();
                    return click;
                case MotionEvent.ACTION_CANCEL:
                    fieldTouch = false;
                    return false;
                default:
                    return fieldTouch;
            }
        }
    }

    private boolean isFieldPoint(float x, float y) {
        Rect wrapper = new Rect();
        getGlobalVisibleRect(wrapper);
        int pointX = wrapper.left + Math.round(x);
        int pointY = wrapper.top + Math.round(y);
        int left = wrapper.left;
        int right = wrapper.right;

        View logo = findOriginalView(googleLogoId);
        if (logo != null) {
            Rect bounds = new Rect();
            if (logo.getGlobalVisibleRect(bounds)) left = Math.max(left, bounds.right);
        }

        int[] rightIconIds = {
                lensButtonId,
                oneRightIconId,
                shortcutButtonId,
                twoRightIconsId,
                voiceButtonId,
                thirdPartyVoiceButtonId
        };
        for (int id : rightIconIds) {
            View icon = findOriginalView(id);
            if (icon == null || icon.getVisibility() != View.VISIBLE) continue;
            Rect bounds = new Rect();
            if (icon.getGlobalVisibleRect(bounds)) right = Math.min(right, bounds.left);
        }
        right = findAdditionalRightControl(original, wrapper, right);

        if (right <= left) {
            left = wrapper.left + wrapper.width() / 5;
            right = wrapper.right - wrapper.width() / 5;
        }
        return pointX >= left && pointX < right
                && pointY >= wrapper.top && pointY < wrapper.bottom;
    }

    private int findAdditionalRightControl(View view, Rect wrapper, int currentRight) {
        if (view != original && view.getVisibility() == View.VISIBLE && view.isClickable()) {
            Rect bounds = new Rect();
            int rightHalf = wrapper.left + wrapper.width() / 2;
            int maxControlWidth = Math.max(1, wrapper.width() / 3);
            if (view.getGlobalVisibleRect(bounds)
                    && bounds.left >= rightHalf
                    && bounds.width() <= maxControlWidth) {
                currentRight = Math.min(currentRight, bounds.left);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                currentRight = findAdditionalRightControl(
                        group.getChildAt(i), wrapper, currentRight);
            }
        }
        return currentRight;
    }

    private View findOriginalView(int id) {
        return id == 0 ? null : original.findViewById(id);
    }

    private static int widgetId(Context context, String name) {
        try {
            Context googleContext = context.createPackageContext(
                    GOOGLE_SEARCH_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            return googleContext.getResources().getIdentifier(
                    name, "id", GOOGLE_SEARCH_PACKAGE);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void openLauncherSearch() {
        Activity activity = findActivity(getContext());
        if (activity == null) return;
        try {
            Method toggleAllApps = activity.getClass().getMethod(
                    "toggleAllApps", boolean.class, boolean.class);
            toggleAllApps.invoke(activity, true, true);
        } catch (Throwable ignored) {
            // The stock widget remains visible if this launcher build has no toggle method.
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
