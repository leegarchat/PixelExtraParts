package io.github.libxposed.api;

import java.lang.reflect.Executable;

import org.pixel.customparts.manager.lsplant.LsplantBridge;

/**
 * Modern host-capability surface (API 101 subset).
 */
public class XposedInterfaceWrapper {

    public boolean deoptimize(Executable executable) {
        try {
            LsplantBridge.deoptimize((java.lang.reflect.Member) executable);
            return true;
        } catch (Throwable t) {
            android.util.Log.w("XposedInterfaceWrapper", "deoptimize failed", t);
            return false;
        }
    }
}
