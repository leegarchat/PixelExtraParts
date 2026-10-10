package top.canyie.pine.xposed;

/**
 * Minimal compatibility stub kept for source compatibility.
 *
 * <p>The Pine native engine is gone (replaced by LSPlant, see
 * {@code org.pixel.customparts.manager.lsplant.LsplantBridge}). Only the
 * logging tag and the global kill-switch survive here because
 * {@code XposedBridge} references them.
 */
public final class PineXposed {
    public static final String TAG = "PineXposed";
    public static boolean disableHooks = false;

    private PineXposed() {
    }
}
