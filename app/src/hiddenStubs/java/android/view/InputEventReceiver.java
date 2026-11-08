package android.view;

import android.os.Looper;

// Compile-only stub for the platform-hidden input API (Gradle debug stand).
// This source set is NOT part of the Soong build (platform_apis: true there)
// and is inert at runtime: parent-first delegation always loads the framework
// class, whose binary signatures match these declarations exactly.
public class InputEventReceiver {
    public InputEventReceiver(InputChannel inputChannel, Looper looper) {
    }

    public void onInputEvent(InputEvent event) {
    }

    public final void finishInputEvent(InputEvent event, boolean handled) {
    }

    public void dispose() {
    }
}
