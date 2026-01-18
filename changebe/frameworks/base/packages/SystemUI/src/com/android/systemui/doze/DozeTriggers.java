@DozeScope
public class DozeTriggers implements DozeMachine.Part {
    // Inject custom code start calss
    private final com.android.systemui.customNativeExtractParts.DozeTapHandler mDt2wHandler;
    // Inject custom code end

    @Inject
    public DozeTriggers(Context context, DozeHost dozeHost
            // .... 
            // ....
        ) {
        // Inject custom code start in end method
        mDt2wHandler = new com.android.systemui.customNativeExtractParts.DozeTapHandler(mContext);
        // Inject custom code end
    }
    @VisibleForTesting
    void onSensor(int pulseReason, boolean sensorPerformedProxCheck,
            float screenX, float screenY, float[] rawValues) {
        // Inject custom code start in start method
        if (pulseReason == DozeLog.REASON_SENSOR_TAP) {
            // Передаем логику в хендлер. Лямбда вызывает ре-регистрацию сенсора.
            if (mDt2wHandler.processTap(screenX, screenY, () -> mDozeSensors.reregisterTapSensor())) {
                return;
            }
        }
        // Inject custom code end
    }
}
