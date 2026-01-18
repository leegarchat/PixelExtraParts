
public class DozeSensors {
    // --- [CustomNativeParts] START inject in end ---
    public void reregisterTapSensor() {
        for (TriggerSensor s : mTriggerSensors) {
            if (s.mPulseReason == com.android.systemui.doze.DozeLog.REASON_SENSOR_TAP) {
                s.setListening(false);
                s.setListening(true);
                break;
            }
        }
    }
    // --- [CustomNativeParts] END ---
}
