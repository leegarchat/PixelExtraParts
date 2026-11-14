package android.content.res;

/**
 * Xposed-compatible resource forwarder (classic
 * {@code de.robv.android.xposed} API surface).
 *
 * <p>Pairs a {@link Resources} with a resource id so hooks can redirect
 * lookups to a replacement package. Exists so modules (e.g. YukiHookAPI)
 * can link against the classic type; forwarding itself is implemented
 * by the module.
 */
public class XResForwarder {
    private final Resources res;
    private final int id;

    public XResForwarder(Resources res, int id) {
        this.res = res;
        this.id = id;
    }

    public Resources getResources() {
        return res;
    }

    public int getId() {
        return id;
    }
}
