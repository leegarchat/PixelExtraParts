
@SysUISingleton
class PulsingGestureListener
@Inject
constructor(
    private val notificationShadeWindowView: NotificationShadeWindowView,
    private val falsingManager: FalsingManager,
    private val dockManager: DockManager,
    private val powerInteractor: PowerInteractor,
    private val ambientDisplayConfiguration: AmbientDisplayConfiguration,
    private val statusBarStateController: StatusBarStateController,
    private val shadeLogger: ShadeLogger,
    private val dozeInteractor: DozeInteractor,
    userTracker: UserTracker,
    tunerService: TunerService,
    dumpManager: DumpManager
) : GestureDetector.SimpleOnGestureListener(), Dumpable {
    // ....
    // --- [CustomNativeParts] START ---
    private val dt2wHandler = com.android.systemui.customNativeExtractParts.DozeTapHandler(notificationShadeWindowView.context)
    // --- [CustomNativeParts] END --
    init {
	vibrator = notificationShadeWindowView.getContext().getSystemService(
                Context.VIBRATOR_SERVICE) as Vibrator
        // ....
    }
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // --- [CustomNativeParts] START ---
        if (statusBarStateController.isDozing) {
            if (dt2wHandler.processTap(e.x, e.y, null)) {
                shadeLogger.d("DT2W: Pulsing tap consumed")
                return true
            }
        }
        // --- [CustomNativeParts] END ---
        return onSingleTapUp(e.x, e.y)
    }
}
