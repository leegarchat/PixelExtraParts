package com.example.addon.test;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.pixel.customparts.core.IAddonHook;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class SimpleTestHook implements IAddonHook {

    private static final String TAG = "PineAddonTest";
    private static final String TARGET_PACKAGE = "com.android.settings";

    @Override
    public String getId() {
        return "test_visual_hook";
    }

    @Override
    public String getName() {
        return "Settings Button Test";
    }

    @Override
    public String getAuthor() {
        return "LeeGarBook";
    }

    @Override
    public String getDescription() {
        return "Внедряет кастомную кнопку в главное меню Настроек.";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<String> getTargetPackages() {
        Set<String> targets = new HashSet<>();
        targets.add(TARGET_PACKAGE);
        return targets;
    }

    @Override
    public int getPriority() {
        return 999;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    // Хукаем родительский класс всех экранов настроек
    private static class DashboardResumeHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            // Проверяем, что текущий объект - это именно главное меню (TopLevelSettings)
            String className = param.thisObject.getClass().getName();
            if (!className.contains("TopLevelSettings")) {
                return; // Игнорируем другие подменю настроек
            }

            ClassLoader classLoader = param.thisObject.getClass().getClassLoader();

            // 1. Получаем PreferenceScreen (экран со списком)
            Object prefScreen = XposedHelpers.callMethod(param.thisObject, "getPreferenceScreen");
            if (prefScreen == null) return;

            // 2. Защита от дубликатов (onResume вызывается каждый раз при сворачивании/разворачивании)
            Object existing = XposedHelpers.callMethod(prefScreen, "findPreference", "pine_test_pref");
            if (existing != null) return;

            // 3. Получаем Context
            Context context = (Context) XposedHelpers.callMethod(param.thisObject, "getContext");
            if (context == null) return;

            // 4. Создаем нашу кнопку (androidx.preference.Preference)
            Class<?> prefClass = XposedHelpers.findClass("androidx.preference.Preference", classLoader);
            Object myPref = XposedHelpers.newInstance(prefClass, context);

            // 5. Настраиваем кнопку
            XposedHelpers.callMethod(myPref, "setKey", "pine_test_pref");
            XposedHelpers.callMethod(myPref, "setTitle", "🔥 Pine Addon Test 🔥");
            XposedHelpers.callMethod(myPref, "setSummary", "Инъекция работает. Кнопка добавлена кодом!");
            
            // Ставим order -150, чтобы кнопка была прямо над твоей "Pixel Extra Parts"
            XposedHelpers.callMethod(myPref, "setOrder", -150);

            // 6. Добавляем кнопку на экран
            XposedHelpers.callMethod(prefScreen, "addPreference", myPref);
            
            Log.d(TAG, "Успех! Кастомная кнопка добавлена в TopLevelSettings.");
            
            // Бонусом кидаем Toast
            Toast.makeText(context, "Хук меню Настроек сработал!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET_PACKAGE.equals(packageName)) {
            return;
        }

        Log.d(TAG, "Внедрение в процесс Настроек началось...");

        try {
            // Хукаем метод onResume в родительском классе DashboardFragment
            XposedHelpers.findAndHookMethod(
                "com.android.settings.dashboard.DashboardFragment",
                classLoader,
                "onResume",
                new DashboardResumeHook()
            );
            Log.d(TAG, "Хук на DashboardFragment.onResume установлен!");
        } catch (Throwable t) {
            Log.e(TAG, "Ошибка при хуке DashboardFragment", t);
        }
    }
}