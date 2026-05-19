# Example: Java Hook Recipes

## Hook One Method By Name

```java
XposedHelpers.findAndHookMethod(
        "com.example.TargetClass",
        classLoader,
        "targetMethod",
        String.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String argument = (String) param.args[0];
                // Inspect or replace arguments here.
            }
        }
);
```

## Hook All Methods With A Name

```java
Class<?> targetClass = XposedHelpers.findClass("com.example.TargetClass", classLoader);
XposedBridge.hookAllMethods(targetClass, "targetMethod", new XC_MethodHook() {
    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        // Runs after every overload.
    }
});
```

## Replace A Result

```java
XposedHelpers.findAndHookMethod(
        "com.example.Flags",
        classLoader,
        "isFeatureEnabled",
        new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                param.setResult(Boolean.TRUE);
            }
        }
);
```

## Read A Global Setting

```java
private static int getInt(Context context, String key, int defaultValue) {
    return Settings.Global.getInt(context.getContentResolver(), key, defaultValue);
}
```

## Defensive Class Lookup

```java
private static Class<?> findClassOrNull(String name, ClassLoader classLoader) {
    try {
        return XposedHelpers.findClass(name, classLoader);
    } catch (Throwable ignored) {
        return null;
    }
}
```

## Hook Once Registry

```java
private static final Set<Method> HOOKED = Collections.newSetFromMap(new IdentityHashMap<Method, Boolean>());

private static void hookOnce(Method method, XC_MethodHook callback) {
    if (method == null) return;
    synchronized (HOOKED) {
        if (!HOOKED.add(method)) return;
    }
    XposedBridge.hookMethod(method, callback);
}
```

## Main Thread Work

```java
Handler mainHandler = new Handler(Looper.getMainLooper());
mainHandler.post(new Runnable() {
    @Override
    public void run() {
        // Touch UI or window state here.
    }
});
```
