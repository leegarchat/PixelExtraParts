# Double Tap to Wake (DTW) Architecture — Pixel 8 (zuma)

## Status
INVESTIGATION COMPLETE — native HAL approach infeasible (no APK override of vendor HAL).
**Feasible** as privileged system_app using `SensorManager` + `PowerManager.wakeUp()`.

## Problem
Pixel 8 (shiba/husky/akita = zuma) only supports **Single Tap** on dark
screen. There is no `SENS_TYPE_DOUBLE_TAP` wired to wakeup in firmware.
`SINGLE_TAP` is **proprietary** — not defined in AOSP context hub, emitted
by Google's closed contexthub binary.

## Key Findings (from source analysis)

### Sensor discovery (framework_overlay)
`device/google/zuma/overlay/FrameworkResOverlayVendorZuma/res/values/config.xml:363`:
```xml
<string name="config_dozeTapSensorType">com.google.sensor.single_touch</string>
```
This is the **custom sensor type string** Pixel uses for Single Tap.
DozeTriggers / Keyguard doze logic subscribes to this string to detect taps.

### Context hub side
`device/google/contexthub/firmware/os/inc/sensType.h:49`:
```c
#define SENS_TYPE_DOUBLE_TAP 27
```
No `SINGLE_TAP` in AOSP. The single_tap path is proprietary firmware only.
However, `hubconnection.cpp` shows how the generic bridge works:
contexthub → ring buffer → Sensor HAL → SensorService.

### Wake mechanism
`hardware/libhardware/include/hardware/power.h:72`:
```c
POWER_FEATURE_DOUBLE_TAP_TO_WAKE = 0x00000001
```
Android's built-in DTW feature — controlled via
`Settings.Secure.DOUBLE_TAP_TO_WAKE` flag +
`PowerManagerService.nativeSetPowerMode(Mode.DOUBLE_TAP_TO_WAKE, ...)`.

## Current Implementation
Xposed/Pine hook on `DozeTriggers.onSensor()`:
- `DozeTapDozeHook.java` — intercepts `onSensor(pulseReason, ...)`
- `DozeTapManager.java` — DTW algorithm (counts 2 events in timeout window)
- `DozeTapShadeHook.java` — hooks `onSingleTapUp` in Keyguard

### Limitations
- Requires Xposed/Pine framework (not always available).
- Hook is fragile — `DozeTriggers` internals may change.

## Feasible Native Alternative (privileged app)

Since **PixelExtraParts** is built with:
- `privileged: true`
- `system_ext_specific: true`
- `certificate: "platform"`

It **can**:

1. Enumerate sensors via `SensorManager.getSensorList(Sensor.TYPE_ALL)`.
   Look for sensor with name/type matching `com.google.sensor.single_touch`.

2. If sensor has a `requiredPermission`, add it to the privileged app
   allowlist in `privapp-permissions-pixelparts.xml`:
   ```xml
   <permission name="android.permission.<SENSOR_PERMISSION>"/>
   ```

3. Register `SensorEventListener` for that sensor.

4. In `onSensorChanged()`, implement DTW logic:
   - Count 2 `single_touch` events within configurable timeout (e.g. 600ms).
   - On double-tap → `PowerManager.wakeUp(now, WAKE_REASON_GESTURE, "dtw")`

5. Permissions needed:
   - `android.permission.DEVICE_POWER`
   - `android.permission.WAKE_LOCK`

6. SELinux: privileged apps already in `priv_app` domain can access
   `sensor_service` and `power_service` binder interfaces by default.

## Implementation Plan

### Step 1 — Sensor subscription
Create `sensorhal/src/org/pixel/customparts/sensor/DtwSensorService.kt` (or JNI):
```kotlin
val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
val sensor = sm.getSensors().find { it.stringType == "com.google.sensor.single_touch" }
sensor?.let {
    sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_FASTEST)
}
```

### Step 2 — Double-tap detection logic
Reuse `DozeTapManager.processTap()` logic for X/Y window matching
(the X/Y from `com.google.sensor.single_touch` events correspond to tap location).

### Step 3 — Waking the device
Use `PowerManager.wakeUp(SystemClock.uptimeMillis(),
                          PowerManager.WAKE_REASON_GESTURE,
                          "PixelExtraParts:dtw")`.

### Step 4 — SELinux & permissions
Add to `device/google/zuma/pixelparts/permissions.xml` (or privapp-permissions):
```xml
<permission name="android.permission.DEVICE_POWER"/>
```

### Why not JNI / binder Sensor HAL?
Sensor HAL `ISensors` is a **vendor** HIDL/AIDL interface accessible only
to `sensorservice` and the HAL process. An APK cannot bind to it directly.
Java `SensorManager` is the supported path for privileged apps.

## Files referenced
- `device/google/zuma/overlay/.../config.xml:363` — `config_dozeTapSensorType`
- `device/google/contexthub/firmware/os/inc/sensType.h:49` — `SENS_TYPE_DOUBLE_TAP=27`
- `device/google/contexthub/sensorhal/hubconnection.cpp` — HAL-to-SensorService bridge reference
- `frameworks/base/services/core/java/com/android/server/power/PowerManagerService.java` — DTW mode handling
- `hardware/libhardware/include/hardware/power.h:72` — `POWER_FEATURE_DOUBLE_TAP_TO_WAKE`
- `packages/apps/PixelExtraParts/example.addon.hook/systemui_hooks/.../DozeTapManager.java` — current DTW algorithm

## Next action
Implement `DtwSensorService` as a standalone foreground service inside PixelExtraParts,
subscribe to `com.google.sensor.single_touch`, port `DozeTapManager.processTap()` logic,
and call `PowerManager.wakeUp()`.
