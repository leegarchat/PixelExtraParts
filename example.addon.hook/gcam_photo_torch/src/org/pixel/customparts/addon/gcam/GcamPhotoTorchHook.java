package org.pixel.customparts.addon.gcam;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.hardware.camera2.CaptureRequest;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IAddonHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class GcamPhotoTorchHook implements IAddonHook {
    private static final String TAG = "PixelPartsGcamTorch";
    private static final String TARGET_PACKAGE = "com.google.android.GoogleCamera";
    private static final String SETTING_ENABLED = "gcam_photo_torch_enabled";
    private static final String TORCH_VALUE = "torch";
    private static final String METADATA_NATIVE = "android.hardware.camera2.impl.CameraMetadataNative";

    private static final Set<Object> PATCHED_BUILDERS = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static final Set<Object> PHOTO_MAPPERS = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static final Set<Method> HOOKED_METHODS = Collections.newSetFromMap(new IdentityHashMap<Method, Boolean>());

    private static volatile boolean photoTorchRequested;
    private static volatile boolean resubmittingRepeating;
    private static volatile long lastLogTime;
    private static volatile long lastResubmitTime;
    private static volatile Object lastRepeatingSession;
    private static volatile Method lastRepeatingMethod;
    private static volatile Object[] lastRepeatingArgs;
    private static volatile List<String> runtimeClassNames;
    private static volatile Field[] captureRequestFields;
    private static volatile Method metadataSetMethod;

    @Override public String getId() { return "gcam_photo_torch"; }
    @Override public String getName() { return "GCam Photo Torch"; }
    @Override public String getAuthor() { return "LeeGarChat"; }
    @Override public String getDescription() { return "Adds an always-on torch option to Google Camera photo flash controls."; }
    @Override public String getVersion() { return "1.0"; }
    @Override public Set<String> getTargetPackages() { return Collections.singleton(TARGET_PACKAGE); }
    @Override public int getPriority() { return 700; }
    @Override public boolean isEnabled(Context context) { return enabled(context); }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        if (!TARGET_PACKAGE.equals(packageName) || !enabled(context)) {
            return;
        }
        Log.d(TAG, "Initializing hook for " + packageName);
        hookFlashMenu(context, classLoader);
        hookCameraRequests(context);
    }

    private static void hookFlashMenu(final Context context, final ClassLoader classLoader) {
        try {
            final Class<?> optionEnum = resolveEnum(context, classLoader, "flash option enum", "PHOTO_FLASH_ON", "PHOTO_FLASH_OFF", "VIDEO_FLASH_ON");
            final Class<?> targetEnum = resolveEnum(context, classLoader, "flash target enum", "BACK_PHOTO_FLASH", "FRONT_PHOTO_FLASH");
            final Class<?> builderClass = resolveBuilder(context, classLoader, optionEnum, targetEnum);
            hookOnce(findBuildMethod(builderClass), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    patchFlashBuilder(context, param.thisObject, builderClass, optionEnum, targetEnum);
                }
            }, "Photo flash builder");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook GCam flash menu", t);
        }
    }

    private static void patchFlashBuilder(Context context, Object builder, Class<?> builderClass, Class<?> optionEnum, Class<?> targetEnum) {
        try {
            if (builder == null || !builderClass.isInstance(builder) || !isEnumNamed(readFieldByType(builder, targetEnum), "BACK_PHOTO_FLASH")) {
                return;
            }
            hookPhotoMapper(builder, optionEnum);
            synchronized (PATCHED_BUILDERS) {
                if (!PATCHED_BUILDERS.add(builder)) {
                    return;
                }
            }

            int icon = resourceId(context, "drawable", "ic_lightbulb_on", "quantum_gm_ic_flash_on_white_24");
            int title = resourceId(context, "string", "illumination_on_desc", "cam_flash_on");
            int description = resourceId(context, "string", "illumination_on_desc", "flash_on_desc");
            if (icon == 0 || title == 0 || description == 0) {
                Log.w(TAG, "Torch menu resources are missing: icon=" + icon + " title=" + title + " desc=" + description);
                return;
            }

            findAppendOptionMethod(builderClass, optionEnum).invoke(builder, enumValue(optionEnum, "VIDEO_FLASH_ON"), icon, title, description);
            Log.d(TAG, "Always-on torch option appended to rear photo flash menu");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to patch rear photo flash builder", t);
        }
    }

    private static void hookPhotoMapper(Object builder, Class<?> optionEnum) {
        for (Field field : builder.getClass().getDeclaredFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object mapper = field.get(builder);
                int direction = detectMapperDirection(mapper, optionEnum);
                if (direction == 0) {
                    continue;
                }
                synchronized (PHOTO_MAPPERS) {
                    PHOTO_MAPPERS.add(mapper);
                }
                hookMapperMethods(mapper.getClass(), optionEnum, direction);
                hookSelectionSetter(mapper.getClass(), optionEnum);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to inspect flash mapper field", t);
            }
        }
    }

    private static int detectMapperDirection(Object mapper, Class<?> optionEnum) {
        if (mapper == null) {
            return 0;
        }
        int direction = 0;
        Object photoOn = enumValue(optionEnum, "PHOTO_FLASH_ON");
        for (Method method : oneArgReturnMethods(mapper.getClass())) {
            Class<?> paramType = method.getParameterTypes()[0];
            try {
                if (canPass(paramType, "on") && isEnumNamed(method.invoke(mapper, "on"), "PHOTO_FLASH_ON")) {
                    direction |= 1;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (canPass(paramType, photoOn) && "on".equals(method.invoke(mapper, photoOn))) {
                    direction |= 2;
                }
            } catch (Throwable ignored) {
            }
        }
        return direction;
    }

    private static void hookMapperMethods(Class<?> mapperClass, final Class<?> optionEnum, final int direction) {
        for (final Method method : oneArgReturnMethods(mapperClass)) {
            hookOnce(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!photoMapper(param.thisObject)) {
                        return;
                    }
                    Object input = param.args[0];
                    if ((direction & 1) != 0 && TORCH_VALUE.equals(input)) {
                        param.setResult(enumValue(optionEnum, "VIDEO_FLASH_ON"));
                        setTorchRequested(true, "stored torch");
                    } else if ((direction & 2) != 0 && isEnumNamed(input, "VIDEO_FLASH_ON")) {
                        param.setResult(TORCH_VALUE);
                    }
                }
            }, "Rear photo flash mapper");
        }
    }

    private static void hookSelectionSetter(Class<?> mapperClass, final Class<?> optionEnum) {
        Method setter = firstVoidOneArgMethod(mapperClass);
        if (setter == null) {
            return;
        }
        hookOnce(setter, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (photoMapper(param.thisObject)) {
                    updateTorchState(optionEnum, param.args[0]);
                }
            }
        }, "Rear photo flash selection setter");
    }

    static void updateTorchState(Class<?> optionEnum, Object option) {
        if (isEnumNamed(option, "VIDEO_FLASH_ON")) {
            setTorchRequested(true, "selection=" + option);
        } else if (TORCH_VALUE.equals(option) || option == null || optionEnum.isInstance(option)) {
            setTorchRequested(false, "selection=" + option);
        }
    }

    private static void setTorchRequested(boolean requested, String reason) {
        if (photoTorchRequested == requested) {
            return;
        }
        photoTorchRequested = requested;
        Log.d(TAG, (requested ? "Rear photo torch requested by " : "Rear photo torch cleared by ") + reason);
        resubmitRepeatingRequest(requested);
    }

    static boolean photoMapper(Object mapper) {
        synchronized (PHOTO_MAPPERS) {
            return PHOTO_MAPPERS.contains(mapper);
        }
    }

    private static void hookCameraRequests(final Context context) {
        hookSubmitClass(context, "android.hardware.camera2.impl.CameraCaptureSessionImpl");
        hookSubmitClass(context, "android.hardware.camera2.impl.CameraConstrainedHighSpeedCaptureSessionImpl");
    }

    private static void hookSubmitClass(final Context context, String className) {
        try {
            for (final Method method : Class.forName(className).getDeclaredMethods()) {
                if (!cameraSubmitMethod(method)) {
                    continue;
                }
                hookOnce(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (method.getName().contains("setRepeating")) {
                            rememberRepeating(param.thisObject, method, param.args);
                        }
                        if (photoTorchRequested && enabled(context) && patchRequestArgs(param.args, true)) {
                            throttledLog("Camera2 submit forced to FLASH_MODE_TORCH via " + method.getDeclaringClass().getName() + "#" + method.getName());
                        }
                    }
                }, "Camera2 submit torch");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Skipping Camera2 submit hooks for " + className, t);
        }
    }

    private static boolean cameraSubmitMethod(Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)) {
            return false;
        }
        if (!method.getName().contains("setRepeating")) {
            return false;
        }
        for (Class<?> type : method.getParameterTypes()) {
            if (type == CaptureRequest.class || List.class.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    static void rememberRepeating(Object session, Method method, Object[] args) {
        if (resubmittingRepeating || session == null || args == null) {
            return;
        }
        lastRepeatingSession = session;
        lastRepeatingMethod = method;
        lastRepeatingArgs = args.clone();
    }

    private static void resubmitRepeatingRequest(boolean torch) {
        Object session = lastRepeatingSession;
        Method method = lastRepeatingMethod;
        Object[] args = lastRepeatingArgs;
        long now = System.currentTimeMillis();
        if (session == null || method == null || args == null || now - lastResubmitTime < 250L) {
            return;
        }
        Object[] invokeArgs = args.clone();
        if (!patchRequestArgs(invokeArgs, torch)) {
            return;
        }
        try {
            resubmittingRepeating = true;
            lastResubmitTime = now;
            method.setAccessible(true);
            method.invoke(session, invokeArgs);
            Log.d(TAG, "Camera2 repeating request resubmitted with torch=" + torch);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to resubmit Camera2 repeating request with torch=" + torch, t);
        } finally {
            resubmittingRepeating = false;
        }
    }

    private static boolean patchRequestArgs(Object[] args, boolean torch) {
        boolean patched = false;
        if (args == null) {
            return false;
        }
        for (Object arg : args) {
            if (arg instanceof CaptureRequest) {
                patched |= patchCaptureRequest((CaptureRequest) arg, torch);
            } else if (arg instanceof List) {
                for (Object item : (List<?>) arg) {
                    if (item instanceof CaptureRequest) {
                        patched |= patchCaptureRequest((CaptureRequest) item, torch);
                    }
                }
            }
        }
        return patched;
    }

    private static boolean patchCaptureRequest(CaptureRequest request, boolean torch) {
        boolean patched = false;
        Integer aeMode = Integer.valueOf(CaptureRequest.CONTROL_AE_MODE_ON);
        Integer flashMode = Integer.valueOf(torch ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        for (Field field : captureRequestFields()) {
            try {
                Object value = field.get(request);
                if (metadataNative(value)) {
                    patched |= writeMetadata(value, aeMode, flashMode);
                } else if (value instanceof Map) {
                    for (Object metadata : ((Map<?, ?>) value).values()) {
                        if (metadataNative(metadata)) {
                            patched |= writeMetadata(metadata, aeMode, flashMode);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return patched;
    }

    private static Field[] captureRequestFields() {
        Field[] fields = captureRequestFields;
        if (fields != null) {
            return fields;
        }
        fields = CaptureRequest.class.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
        }
        captureRequestFields = fields;
        return fields;
    }

    private static boolean metadataNative(Object value) {
        return value != null && METADATA_NATIVE.equals(value.getClass().getName());
    }

    private static boolean writeMetadata(Object metadata, Integer aeMode, Integer flashMode) {
        try {
            Method method = metadataSetMethod;
            if (method == null) {
                method = metadata.getClass().getDeclaredMethod("set", CaptureRequest.Key.class, Object.class);
                method.setAccessible(true);
                metadataSetMethod = method;
            }
            method.invoke(metadata, CaptureRequest.CONTROL_AE_MODE, aeMode);
            method.invoke(metadata, CaptureRequest.FLASH_MODE, flashMode);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to patch CameraMetadataNative flash keys", t);
            return false;
        }
    }

    private static Class<?> resolveBuilder(Context context, ClassLoader classLoader, final Class<?> optionEnum, final Class<?> targetEnum) throws Exception {
        for (String name : runtimeClassNames(context)) {
            Class<?> clazz = loadClass(name, classLoader);
            if (clazz != null && !clazz.isEnum() && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())
                    && findAppendOptionMethodOrNull(clazz, optionEnum) != null
                    && findField(clazz, targetEnum) != null
                    && findBuildMethodOrNull(clazz) != null) {
                Log.d(TAG, "Resolved flash builder: " + clazz.getName());
                return clazz;
            }
        }
        throw new ClassNotFoundException("No runtime class matched flash builder");
    }

    private static Class<?> resolveEnum(Context context, ClassLoader classLoader, String label, String... constants) throws Exception {
        for (String name : runtimeClassNames(context)) {
            Class<?> clazz = loadClass(name, classLoader);
            if (enumHas(clazz, constants)) {
                Log.d(TAG, "Resolved " + label + ": " + clazz.getName());
                return clazz;
            }
        }
        throw new ClassNotFoundException("No runtime class matched " + label);
    }

    private static List<String> runtimeClassNames(Context context) {
        List<String> cached = runtimeClassNames;
        if (cached != null) {
            return cached;
        }
        synchronized (GcamPhotoTorchHook.class) {
            if (runtimeClassNames != null) {
                return runtimeClassNames;
            }
            Set<String> names = new HashSet<>();
            ApplicationInfo info = context.getApplicationInfo();
            List<String> paths = new ArrayList<>();
            if (info.sourceDir != null) {
                paths.add(info.sourceDir);
            }
            if (info.splitSourceDirs != null) {
                Collections.addAll(paths, info.splitSourceDirs);
            }
            for (String path : paths) {
                addDexNames(path, names);
            }
            runtimeClassNames = Collections.unmodifiableList(new ArrayList<>(names));
            Log.d(TAG, "Runtime dex scan indexed " + runtimeClassNames.size() + " short class names");
            return runtimeClassNames;
        }
    }

    private static void addDexNames(String path, Set<String> names) {
        DexFile dexFile = null;
        try {
            if (!hasClassesDex(path)) {
                return;
            }
            dexFile = new DexFile(path);
            Enumeration<String> entries = dexFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement();
                if (shortDefaultPackageName(name)) {
                    names.add(name);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to enumerate dex classes from " + path, t);
        } finally {
            if (dexFile != null) {
                try {
                    dexFile.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean hasClassesDex(String path) {
        try (ZipFile zipFile = new ZipFile(path)) {
            return zipFile.getEntry("classes.dex") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean shortDefaultPackageName(String name) {
        return name != null && name.length() > 0 && name.length() <= 5 && name.indexOf('.') < 0 && name.charAt(0) >= 'a' && name.charAt(0) <= 'z';
    }

    private static Class<?> loadClass(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static boolean enumHas(Class<?> enumClass, String... names) {
        if (enumClass == null || !enumClass.isEnum()) {
            return false;
        }
        for (String name : names) {
            boolean found = false;
            for (Object value : enumClass.getEnumConstants()) {
                if (isEnumNamed(value, name)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEnumNamed(Object value, String name) {
        return value instanceof Enum && name.equals(((Enum<?>) value).name());
    }

    private static Object readFieldByType(Object receiver, Class<?> type) throws IllegalAccessException {
        Field field = findField(receiver.getClass(), type);
        return field == null ? null : field.get(receiver);
    }

    private static Field findField(Class<?> owner, Class<?> type) {
        for (Class<?> clazz = owner; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Method findAppendOptionMethodOrNull(Class<?> owner, Class<?> optionEnum) {
        try {
            return findAppendOptionMethod(owner, optionEnum);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findAppendOptionMethod(Class<?> owner, Class<?> optionEnum) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == Void.TYPE
                    && params.length == 4
                    && params[0] == optionEnum
                    && params[1] == Integer.TYPE
                    && params[2] == Integer.TYPE
                    && params[3] == Integer.TYPE) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("No flash option append method in " + owner.getName());
    }

    private static Method findBuildMethodOrNull(Class<?> owner) {
        try {
            return findBuildMethod(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findBuildMethod(Class<?> owner) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    && method.getParameterTypes().length == 0
                    && method.getReturnType() != Void.TYPE
                    && !method.getReturnType().isPrimitive()) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException("No builder build method in " + owner.getName());
    }

    private static List<Method> oneArgReturnMethods(Class<?> owner) {
        List<Method> result = new ArrayList<>();
        for (Class<?> clazz = owner; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        && !Modifier.isAbstract(method.getModifiers())
                        && method.getParameterTypes().length == 1
                        && method.getReturnType() != Void.TYPE) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
        }
        return result;
    }

    private static Method firstVoidOneArgMethod(Class<?> owner) {
        for (Class<?> clazz = owner; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        && !Modifier.isAbstract(method.getModifiers())
                        && method.getParameterTypes().length == 1
                        && method.getReturnType() == Void.TYPE) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean canPass(Class<?> paramType, Object value) {
        return value != null && !paramType.isPrimitive() && paramType.isAssignableFrom(value.getClass());
    }

    private static void hookOnce(Method method, XC_MethodHook hook, String label) {
        synchronized (HOOKED_METHODS) {
            if (!HOOKED_METHODS.add(method)) {
                return;
            }
        }
        method.setAccessible(true);
        XposedBridge.hookMethod(method, hook);
        Log.d(TAG, label + " hook installed: " + method.getDeclaringClass().getName() + "#" + method.getName());
    }

    private static int resourceId(Context context, String type, String preferred, String fallback) {
        int id = context.getResources().getIdentifier(preferred, type, TARGET_PACKAGE);
        return id != 0 ? id : context.getResources().getIdentifier(fallback, type, TARGET_PACKAGE);
    }

    private static void throttledLog(String message) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 2000L) {
            lastLogTime = now;
            Log.d(TAG, message);
        }
    }

    private static boolean enabled(Context context) {
        if (context == null) {
            return true;
        }
        try {
            return Settings.Global.getInt(context.getContentResolver(), SETTING_ENABLED, 1) != 0;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
