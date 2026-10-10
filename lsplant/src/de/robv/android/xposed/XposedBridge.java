package de.robv.android.xposed;

import android.util.Log;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

import org.pixel.customparts.manager.lsplant.LsplantBridge;
import org.pixel.customparts.manager.lsplant.LsplantXposed;

/**
 * This class contains most of Xposed's central logic, such as initialization and callbacks used by
 * the native side. It also includes methods to add new hooks.
 */
public final class XposedBridge {
	// LSPlant backend: previous engine calls removed.
	/*package*/ static final Object[] EMPTY_OBJECT_ARRAY = {};

	// LSPlant added: New API for querying supported features
	private static String[] sSupportedFeatures = new String[0];

	/**
	 * The system class loader which can be used to locate Android framework classes.
	 * Application classes cannot be retrieved from it.
	 *
	 * @see ClassLoader#getSystemClassLoader
	 */
	public static final ClassLoader BOOTCLASSLOADER = ClassLoader.getSystemClassLoader();

	/** @hide */
	public static final String TAG = LsplantXposed.TAG;

	/** @deprecated Use {@link #getXposedVersion()} instead. */
	@Deprecated
	public static int XPOSED_BRIDGE_VERSION = 90;

	// built-in handlers
	private static final Map<Member, CopyOnWriteSortedSet<XC_MethodHook>> sHookedMethodCallbacks = new HashMap<>();

	// LSPlant changed: Move sLoadedPackageCallbacks to LsplantXposed.
	// /*package*/ static final CopyOnWriteSortedSet<XC_LoadPackage> sLoadedPackageCallbacks = new CopyOnWriteSortedSet<>();

	private static HookProvider hookProvider = HookProvider.LSPLANT;

	public interface HookProvider {
		/** Routes to the LSPlant backend. */
		HookProvider LSPLANT = new HookProvider() {
			@Override
			public void hook(Member method, CopyOnWriteSortedSet<XC_MethodHook> callbacks) {
				LSPlantDispatcher dispatcher = new LSPlantDispatcher(method, callbacks);
				if (!LsplantBridge.hookMember(method, dispatcher)) {
					XposedBridge.log("LSPlant hook failed for " + method);
				}
			}

			@Override
			public Object invokeOriginal(Member method, Object thisObject, Object[] args) throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
				try {
					return LsplantBridge.invokeBackup(method, thisObject, args);
				} catch (NullPointerException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					throw e;
				} catch (Throwable t) {
					throw new InvocationTargetException(t);
				}
			}
		};
		void hook(Member method, CopyOnWriteSortedSet<XC_MethodHook> callbacks);
		Object invokeOriginal(Member method, Object thisObject, Object[] args) throws
				NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException;
	}

	private XposedBridge() {}

	/**
	 * Returns the currently installed version of the Xposed framework.
	 */
	public static int getXposedVersion() {
		return XPOSED_BRIDGE_VERSION;
	}

	// Added
	public static void setXposedVersion(int version) {
		XPOSED_BRIDGE_VERSION = version;
	}

	public static HookProvider getHookProvider() {
		return hookProvider;
	}

	public static void setHookProvider(HookProvider provider) {
		hookProvider = provider;
	}

	// Added: New API for querying supported features
	public static boolean isFeatureSupported(String featureName) {
		for (String f : sSupportedFeatures) {
			if (f.equalsIgnoreCase(featureName)) return true;
		}
		return false;
	}

	public static String[] getSupportedFeatures() {
		return sSupportedFeatures;
	}

	public static void setSupportedFeatures(String[] features) {
		sSupportedFeatures = features;
	}

	/**
	 * Writes a message to the Xposed error log.
	 *
	 * <p class="warning"><b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging.
	 * If you want to write information/debug messages, use logcat.
	 *
	 * @param text The log message.
	 */
	public static synchronized void log(String text) {
		Log.i(TAG, text);
	}

	/**
	 * Logs a stack trace to the Xposed error log.
	 *
	 * <p class="warning"><b>DON'T FLOOD THE LOG!!!</b> This is only meant for error logging.
	 * If you want to write information/debug messages, use logcat.
	 *
	 * @param t The Throwable object for the stack trace.
	 */
	public static synchronized void log(Throwable t) {
		Log.e(TAG, Log.getStackTraceString(t));
	}

	/**
	 * Deoptimize a method to avoid callee being inlined.
	 *
	 * @param method The method to deoptmize. Generally it should be a caller of a method that is inlined.
	 */
	public static void deoptimizeMethod(Member method) {
		LsplantBridge.deoptimize(method);
	}

	/**
	 * Hook any method (or constructor) with the specified callback. See below for some wrappers
	 * that make it easier to find a method/constructor in one step.
	 *
	 * @param hookMethod The method to be hooked.
	 * @param callback The callback to be executed when the hooked method is called.
	 * @return An object that can be used to remove the hook.
	 *
	 * @see XposedHelpers#findAndHookMethod(String, ClassLoader, String, Object...)
	 * @see XposedHelpers#findAndHookMethod(Class, String, Object...)
	 * @see #hookAllMethods
	 * @see XposedHelpers#findAndHookConstructor(String, ClassLoader, Object...)
	 * @see XposedHelpers#findAndHookConstructor(Class, Object...)
	 * @see #hookAllConstructors
	 */
	public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
		if (!(hookMethod instanceof Method) && !(hookMethod instanceof Constructor<?>)) {
			throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod.toString());
		}
		// LSPlant changed: We can hook interfaces' non-abstract methods
		/*else if (hookMethod.getDeclaringClass().isInterface()) {
			throw new IllegalArgumentException("Cannot hook interfaces: " + hookMethod.toString());
		}*/ else if (Modifier.isAbstract(hookMethod.getModifiers())) {
			throw new IllegalArgumentException("Cannot hook abstract methods: " + hookMethod.toString());
		}

		boolean newMethod = false;
		CopyOnWriteSortedSet<XC_MethodHook> callbacks;
		synchronized (sHookedMethodCallbacks) {
			callbacks = sHookedMethodCallbacks.get(hookMethod);
			if (callbacks == null) {
				callbacks = new CopyOnWriteSortedSet<>();
				sHookedMethodCallbacks.put(hookMethod, callbacks);
				newMethod = true;
			}
		}
		callbacks.add(callback);

		if (newMethod) {
			hookProvider.hook(hookMethod, callbacks);
		}

		return callback.new Unhook(hookMethod);
	}

	/**
	 * Removes the callback for a hooked method/constructor.
	 *
	 * @deprecated Use {@link XC_MethodHook.Unhook#unhook} instead. An instance of the {@code Unhook}
	 * class is returned when you hook the method.
	 *
	 * @param hookMethod The method for which the callback should be removed.
	 * @param callback The reference to the callback as specified in {@link #hookMethod}.
	 */
	@Deprecated
	public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
		CopyOnWriteSortedSet<XC_MethodHook> callbacks;
		synchronized (sHookedMethodCallbacks) {
			callbacks = sHookedMethodCallbacks.get(hookMethod);
			if (callbacks == null)
				return;
		}
		callbacks.remove(callback);
	}

	/**
	 * Hooks all methods with a certain name that were declared in the specified class. Inherited
	 * methods and constructors are not considered. For constructors, use
	 * {@link #hookAllConstructors} instead.
	 *
	 * @param hookClass The class to check for declared methods.
	 * @param methodName The name of the method(s) to hook.
	 * @param callback The callback to be executed when the hooked methods are called.
	 * @return A set containing one object for each found method which can be used to unhook it.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
		Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
		for (Member method : hookClass.getDeclaredMethods())
			if (method.getName().equals(methodName))
				unhooks.add(hookMethod(method, callback));
		return unhooks;
	}

	/**
	 * Hook all constructors of the specified class.
	 *
	 * @param hookClass The class to check for constructors.
	 * @param callback The callback to be executed when the hooked constructors are called.
	 * @return A set containing one object for each found constructor which can be used to unhook it.
	 */
	@SuppressWarnings("UnusedReturnValue")
	public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
		Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
		for (Member constructor : hookClass.getDeclaredConstructors())
			unhooks.add(hookMethod(constructor, callback));
		return unhooks;
	}

	// LSPlant changed: removed handleHookedMethod(), it be implemented in Handler.class
	// LSPlant changed: removed hookXxx(), it is implemented in LsplantXposed.class

	/**
	 * Basically the same as {@link Method#invoke}, but calls the original method
	 * as it was before the interception by Xposed. Also, access permissions are not checked.
	 * If the given method is not hooked, the behavior is undefined and not guaranteed
	 * will always work and may crash on other Xposed framework implementations.
	 *
	 * <p class="caution">There are very few cases where this method is needed. A common mistake is
	 * to replace a method and then invoke the original one based on dynamic conditions. This
	 * creates overhead and skips further hooks by other modules. Instead, just hook (don't replace)
	 * the method and call {@code param.setResult(null)} in {@link XC_MethodHook#beforeHookedMethod}
	 * if the original method should be skipped.
	 *
	 * @param method The method to be called.
	 * @param thisObject For non-static calls, the "this" pointer, otherwise {@code null}.
	 * @param args Arguments for the method call as Object[] array.
	 * @return The result returned from the invoked method.
	 * @throws NullPointerException
	 *             if {@code receiver == null} for a non-static method
	 * @throws IllegalAccessException
	 *             if this method is not accessible (see {@link AccessibleObject})
	 * @throws IllegalArgumentException
	 *             if the number of arguments doesn't match the number of parameters, the receiver
	 *             is incompatible with the declaring class, or an argument could not be unboxed
	 *             or converted by a widening conversion to the corresponding parameter type
	 * @throws InvocationTargetException
	 *             if an exception was thrown by the invoked method
	 */
	public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
			throws NullPointerException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		return hookProvider.invokeOriginal(method, thisObject, args);
	}

	// LSPlant changed: per-method dispatch lives here (single native
	// replacement runs the full before/original/after chain inline, so no
	// CallFrame pairing needed). Same package => same access rights the
	// the old engine Handler had.
	/** @hide */
	public static final class LSPlantDispatcher {
		private final Member method;
		private final boolean isStatic;
		private final CopyOnWriteSortedSet<XC_MethodHook> callbacks;
		private volatile Object backup;
		private final Method callbackMethod;

		public LSPlantDispatcher(Member method, CopyOnWriteSortedSet<XC_MethodHook> callbacks) {
			this.method = method;
			boolean stat;
			try {
				stat = Modifier.isStatic(method.getModifiers());
			} catch (Throwable t) {
				stat = false;
			}
			this.isStatic = stat;
			this.callbacks = callbacks;
			Method cb;
			try {
				cb = LSPlantDispatcher.class.getDeclaredMethod("callback", Object[].class);
			} catch (Throwable t) {
				throw new IllegalStateException(t);
			}
			this.callbackMethod = cb;
		}

		public Method getCallbackMethod() {
			return callbackMethod;
		}

		public void attachBackup(Object backup) {
			this.backup = backup;
		}

		/** LSPlant replacement entry point. Signature must stay exactly this. */
		public Object callback(Object[] args) throws Throwable {
			Object[] callbacksSnapshot = callbacks.getSnapshot();
			final int callbacksLength = callbacksSnapshot.length;

			MethodHookParam param = new MethodHookParam();
			param.method = method;
			Object[] raw = args != null ? args : new Object[0];
			if (isStatic) {
				param.thisObject = null;
				param.args = raw;
			} else {
				param.thisObject = raw.length > 0 ? raw[0] : null;
				Object[] params = new Object[Math.max(0, raw.length - 1)];
				if (raw.length > 1) {
					System.arraycopy(raw, 1, params, 0, raw.length - 1);
				}
				param.args = params;
			}

			int beforeIdx = 0;
			if (callbacksLength != 0) {
				do {
					try {
						((XC_MethodHook) callbacksSnapshot[beforeIdx]).beforeHookedMethod(param);
					} catch (Throwable t) {
						XposedBridge.log(t);
						param.setResult(null);
						param.returnEarly = false;
						continue;
					}
					if (param.returnEarly) {
						beforeIdx++;
						break;
					}
				} while (++beforeIdx < callbacksLength);
			}

			if (!param.returnEarly) {
				try {
					Object result = invokeOriginalNow(param);
					param.setResult(result);
					param.returnEarly = false;
				} catch (Throwable t) {
					param.setThrowable(t);
				}
			}

			for (int afterIdx = beforeIdx - 1; afterIdx >= 0; afterIdx--) {
				Object lastResult = param.getResult();
				Throwable lastThrowable = param.getThrowable();
				try {
					((XC_MethodHook) callbacksSnapshot[afterIdx]).afterHookedMethod(param);
				} catch (Throwable t) {
					XposedBridge.log(t);
					if (lastThrowable == null) {
						param.setResult(lastResult);
					} else {
						param.setThrowable(lastThrowable);
					}
				}
			}

			if (param.hasThrowable()) {
				throw param.getThrowable();
			}
			return param.getResult();
		}

		private Object invokeOriginalNow(MethodHookParam param) throws Throwable {
			Object b = backup;
			if (b == null) {
				throw new IllegalStateException("LSPlant backup missing for " + method);
			}
			try {
				if (b instanceof Constructor) {
					return ((Constructor<?>) b).newInstance(param.args);
				}
				return ((Method) b).invoke(param.thisObject, param.args);
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw e.getCause() != null ? e.getCause() : e;
			}
		}
	}

	/** @hide */
	public static final class CopyOnWriteSortedSet<E> {
		// LSPlant changed: Use EMPTY_OBJECT_ARRAY
		private transient volatile Object[] elements = XposedBridge.EMPTY_OBJECT_ARRAY;

		@SuppressWarnings("UnusedReturnValue")
		public synchronized boolean add(E e) {
			int index = indexOf(e);
			if (index >= 0)
				return false;

			Object[] newElements = new Object[elements.length + 1];
			System.arraycopy(elements, 0, newElements, 0, elements.length);
			newElements[elements.length] = e;
			Arrays.sort(newElements);
			elements = newElements;
			return true;
		}

		@SuppressWarnings("UnusedReturnValue")
		public synchronized boolean remove(E e) {
			int index = indexOf(e);
			if (index == -1)
				return false;

			Object[] newElements = new Object[elements.length - 1];
			System.arraycopy(elements, 0, newElements, 0, index);
			System.arraycopy(elements, index + 1, newElements, index, elements.length - index - 1);
			elements = newElements;
			return true;
		}

		private int indexOf(Object o) {
			for (int i = 0; i < elements.length; i++) {
				if (o.equals(elements[i]))
					return i;
			}
			return -1;
		}

		public Object[] getSnapshot() {
			return elements;
		}
	}
}
