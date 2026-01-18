




    @UnsupportedAppUsage
    private void handleBindApplication(AppBindData data) {
        // Original code
        long timestampApplicationOnCreateNs = 0;
        try {
            // If the app is being launched for full backup or restore, bring it up in
            // a restricted environment with the base application class.
            app = data.info.makeApplicationInner(data.restrictedBackupMode, null);

            // Propagate autofill compat state
            app.setAutofillOptions(data.autofillOptions);

            // Propagate Content Capture options
            app.setContentCaptureOptions(data.contentCaptureOptions);
            if (android.view.contentcapture.flags.Flags.warmUpBackgroundThreadForContentCapture()
                    && data.contentCaptureOptions != null) {
                if (data.contentCaptureOptions.enableReceiver
                        && !data.contentCaptureOptions.lite) {
                    // Warm up the background thread when:
                    // 1) app is launched with content capture enabled, and
                    // 2) the app is NOT launched with content capture lite enabled.
                    BackgroundThread.startIfNeeded();
                }
            }
            sendMessage(H.SET_CONTENT_CAPTURE_OPTIONS_CALLBACK, data.appInfo.packageName);

            mInitialApplication = app;
            final boolean updateHttpProxy;
            synchronized (this) {
                updateHttpProxy = mUpdateHttpProxyOnBind;
                // This synchronized block ensures that any subsequent call to updateHttpProxy()
                // will see a non-null mInitialApplication.
            }
            if (updateHttpProxy) {
                ActivityThread.updateHttpProxy(app);
            }

            // don't bring up providers in restricted mode; they may depend on the
            // app's custom Application class
            if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                    installContentProviders(app, data.providers);
                }
            }

            // Do this after providers, since instrumentation tests generally start their
            // test thread at this point, and we don't want that racing.
            try {
                mInstrumentation.onCreate(data.instrumentationArgs);
            }
            catch (Exception e) {
                throw new RuntimeException(
                    "Exception thrown in onCreate() of " + data.instrumentationName, e);
            }

            // --- [PixelParts] INJECTION START ---
            if (data.appInfo.packageName != null) {
                // Делегируем всё менеджеру, он сам разберется какой пакет ему нужен
                com.android.internal.customNativeExtractParts.NativePartsManager.init(app);
            }
            // --- [PixelParts] INJECTION END ---

            try {
                timestampApplicationOnCreateNs = SystemClock.uptimeNanos();
                mInstrumentation.callApplicationOnCreate(app);
            } catch (Exception e) {
                timestampApplicationOnCreateNs = 0;
                if (!mInstrumentation.onException(app, e)) {
                    throw new RuntimeException(
                      "Unable to create application " + app.getClass().getName(), e);
                }
            }
        } finally {
            // If the app targets < O-MR1, or doesn't change the thread policy
            // during startup, clobber the policy to maintain behavior of b/36951662
            if (data.appInfo.targetSdkVersion < Build.VERSION_CODES.O_MR1
                    || StrictMode.getThreadPolicy().equals(writesAllowedPolicy)) {
                StrictMode.setThreadPolicy(savedPolicy);
            }
        }

        // Original code 
    }