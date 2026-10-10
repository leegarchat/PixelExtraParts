package org.pixel.customparts.manager.lsplant;

/**
 * Minimal compatibility stub kept for source compatibility.
 *
 * <p>The previous native engine is gone (replaced by LSPlant, see
 * {@link LsplantBridge}). Only the logging tag and the global kill-switch
 * survive here because {@code XposedBridge} references them.
 */
public final class LsplantXposed {
    public static final String TAG = "LsplantXposed";
    public static boolean disableHooks = false;

    private LsplantXposed() {
    }
}
