package io.github.libxposed.api;

import java.util.List;

/**
 * Modern (API 101) hooking surface, backed by our LSPlant engine.
 * Only the subset actually used by modules is implemented; anything
 * else throws UnsupportedOperationException with a log.
 */
public interface XposedInterface {

    /** Single-method interceptor (replaces before/after pair). */
    interface Hooker {
        Object intercept(Chain chain) throws Throwable;
    }

    /** Invocation context handed to {@link Hooker}. */
    interface Chain {
        Object getThisObject();

        List<Object> getArgs();

        Object getArg(int index);

        Object proceed() throws Throwable;

        Object proceed(Object[] args) throws Throwable;
    }

    /** Binds a {@link Hooker} to a hooked executable. */
    interface HookBuilder {
        HookHandle intercept(Hooker hooker);

        HookBuilder setExceptionMode(ExceptionMode mode);
    }

    /** Opaque handle for a single interception. */
    interface HookHandle {
        void unhook();
    }
}
