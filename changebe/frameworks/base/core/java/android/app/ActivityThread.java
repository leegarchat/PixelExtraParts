




    @UnsupportedAppUsage
    private void handleBindApplication(AppBindData data) {
        
        long timestampApplicationOnCreateNs = 0;
        try {
            
            
            app = data.info.makeApplicationInner(data.restrictedBackupMode, null);

            
            app.setAutofillOptions(data.autofillOptions);

            
            app.setContentCaptureOptions(data.contentCaptureOptions);
            if (android.view.contentcapture.flags.Flags.warmUpBackgroundThreadForContentCapture()
                    && data.contentCaptureOptions != null) {
                if (data.contentCaptureOptions.enableReceiver
                        && !data.contentCaptureOptions.lite) {
                    
                    
                    
                    BackgroundThread.startIfNeeded();
                }
            }
            sendMessage(H.SET_CONTENT_CAPTURE_OPTIONS_CALLBACK, data.appInfo.packageName);

            mInitialApplication = app;
            final boolean updateHttpProxy;
            synchronized (this) {
                updateHttpProxy = mUpdateHttpProxyOnBind;
                
                
            }
            if (updateHttpProxy) {
                ActivityThread.updateHttpProxy(app);
            }
            
            if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                    installContentProviders(app, data.providers);
                }
            }
            try {
                mInstrumentation.onCreate(data.instrumentationArgs);
            }
            catch (Exception e) {
                throw new RuntimeException(
                    "Exception thrown in onCreate() of " + data.instrumentationName, e);
            }

            // --- [PixelParts] INJECTION START ---
            if (data.appInfo.packageName != null) {
                try {
                    final String jarPath = "/system/framework/PineInject.jar";
                    final java.io.File jarFile = new java.io.File(jarPath);

                    
                    if (jarFile.exists()) {
                        final ClassLoader appCl = data.info.getClassLoader(); 

                        if (appCl instanceof dalvik.system.BaseDexClassLoader) {
                            dalvik.system.BaseDexClassLoader dexLoader = 
                                    (dalvik.system.BaseDexClassLoader) appCl;
                            
                            
                            dexLoader.addDexPath(jarPath);

                            
                            
                            Class<?> entry = appCl.loadClass("org.pixel.customparts.pineinject.ModEntry");
                            
                            
                            java.lang.reflect.Method m = entry.getDeclaredMethod("init");
                            m.invoke(null);
                            
                            
                            android.util.Log.i("PixelParts", "Injected into: " + data.appInfo.packageName);
                        }
                    }
                } catch (Throwable t) {
                    
                    android.util.Log.e("PixelParts", "Injection failed for " + data.appInfo.packageName, t);
                }
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
            
            
            if (data.appInfo.targetSdkVersion < Build.VERSION_CODES.O_MR1
                    || StrictMode.getThreadPolicy().equals(writesAllowedPolicy)) {
                StrictMode.setThreadPolicy(savedPolicy);
            }
        }

        
    }