package org.pixel.extra.parts.utils

import android.content.ContentResolver
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

// Reflection shims for platform-hidden APIs. The Gradle debug stand compiles
// against the public SDK, while the firmware (Soong) build sees the full
// framework; both reach the same runtime behavior through reflection with
// public-API fallbacks where one exists.
object HiddenApi {
    private const val TAG = "PepHiddenApi"

    fun getSystemProperty(name: String, def: String = ""): String {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            method.invoke(null, name, def) as? String ?: def
        }.getOrDefault(def)
    }

    fun getSystemPropertyBoolean(name: String, def: Boolean = false): Boolean {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            method.invoke(null, name, def) as? Boolean ?: def
        }.getOrDefault(def)
    }

    fun userCurrent(): Int {
        return runCatching {
            Class.forName("android.os.UserHandle").getField("USER_CURRENT").getInt(null)
        }.getOrDefault(-2)
    }

    fun putIntForUser(resolver: ContentResolver, name: String, value: Int): Boolean {
        return runCatching {
            val method = Settings.System::class.java.getMethod(
                "putIntForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(null, resolver, name, value, userCurrent()) as? Boolean ?: false
        }.getOrElse {
            runCatching { Settings.System.putInt(resolver, name, value) }.getOrDefault(false)
        }
    }

    fun getIntForUser(resolver: ContentResolver, name: String, def: Int): Int {
        return runCatching {
            val method = Settings.System::class.java.getMethod(
                "getIntForUser",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(null, resolver, name, def, userCurrent()) as? Int ?: def
        }.getOrElse {
            runCatching { Settings.System.getInt(resolver, name, def) }.getOrDefault(def)
        }
    }

    fun setTemporaryBrightness(displayManager: DisplayManager, displayId: Int, brightness: Float): Boolean {
        return runCatching {
            val method = DisplayManager::class.java.getMethod(
                "setTemporaryBrightness", Int::class.javaPrimitiveType, Float::class.javaPrimitiveType
            )
            method.invoke(displayManager, displayId, brightness)
            true
        }.onFailure { Log.w(TAG, "setTemporaryBrightness unavailable", it) }.getOrDefault(false)
    }

    fun goToSleep(powerManager: PowerManager, uptimeMillis: Long): Boolean {
        return runCatching {
            val cls = PowerManager::class.java
            val reason = runCatching {
                cls.getField("GO_TO_SLEEP_REASON_APPLICATION").getInt(null)
            }.getOrDefault(0)
            runCatching {
                cls.getMethod(
                    "goToSleep",
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(powerManager, uptimeMillis, reason, 0)
            }.getOrElse {
                cls.getMethod("goToSleep", Long::class.javaPrimitiveType)
                    .invoke(powerManager, uptimeMillis)
            }
            true
        }.onFailure { Log.w(TAG, "PowerManager.goToSleep unavailable", it) }.getOrDefault(false)
    }

    fun setBrightness(displayManager: DisplayManager, displayId: Int, brightness: Float): Boolean {
        return runCatching {
            val cls = DisplayManager::class.java
            runCatching {
                cls.getMethod(
                    "setBrightness",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).invoke(displayManager, displayId, brightness, 0)
            }.getOrElse {
                cls.getMethod(
                    "setBrightness",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                ).invoke(displayManager, displayId, brightness)
            }
            true
        }.onFailure { Log.w(TAG, "DisplayManager.setBrightness unavailable", it) }.getOrDefault(false)
    }

    fun forceStopPackage(activityManager: android.app.ActivityManager, packageName: String): Boolean {
        return runCatching {
            activityManager.javaClass.getMethod("forceStopPackage", String::class.java)
                .invoke(activityManager, packageName)
            true
        }.onFailure { Log.w(TAG, "ActivityManager.forceStopPackage unavailable", it) }.getOrDefault(false)
    }

    fun lockNow(): Boolean {
        return runCatching {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val service = wmg.getMethod("getWindowManagerService").invoke(null) ?: return false
            val lockNow = service.javaClass.getMethod("lockNow", Bundle::class.java)
            lockNow.invoke(service, null)
            true
        }.onFailure { Log.w(TAG, "WindowManager.lockNow() unavailable", it) }.getOrDefault(false)
    }

    fun getSandboxManager(context: Context): Any? {
        return runCatching {
            val cls = Class.forName("android.app.AxSandboxManager")
            context.getSystemService(cls)
        }.getOrNull()
    }

    fun contextUserId(context: Context): Int {
        return runCatching {
            context.javaClass.getMethod("getUserId").invoke(context) as? Int
        }.getOrNull() ?: runCatching {
            val uh = Class.forName("android.os.UserHandle")
            uh.getMethod("myUserId").invoke(null) as? Int
        }.getOrNull() ?: 0
    }

    fun getHiddenPackages(context: Context, manager: Any?): Set<String> {
        if (manager == null) return emptySet()
        val userId = contextUserId(context)
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (manager.javaClass.getMethod("getHiddenPackages", Int::class.javaPrimitiveType)
                .invoke(manager, userId) as? Collection<String>)?.toSet()
        }.getOrNull().orEmpty()
    }

    fun setPackageHidden(context: Context, manager: Any?, packageName: String, hidden: Boolean): Boolean {
        if (manager == null) return false
        val userId = contextUserId(context)
        return runCatching {
            manager.javaClass.getMethod(
                "setPackageHidden", String::class.java,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
            ).invoke(manager, packageName, hidden, userId)
            isPackageHidden(context, manager, packageName) == hidden
        }.getOrDefault(false)
    }

    fun isPackageHidden(context: Context, manager: Any?, packageName: String): Boolean {
        if (manager == null) return false
        val userId = contextUserId(context)
        return runCatching {
            manager.javaClass.getMethod(
                "isPackageHidden", String::class.java, Int::class.javaPrimitiveType
            ).invoke(manager, packageName, userId) as? Boolean ?: false
        }.getOrDefault(false)
    }

    fun getLockablePackages(context: Context, manager: Any?): List<String> {
        if (manager == null) return emptyList()
        val userId = contextUserId(context)
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (manager.javaClass.getMethod("getLockablePackages", Int::class.javaPrimitiveType)
                .invoke(manager, userId) as? Collection<String>)?.toList()
        }.getOrNull().orEmpty()
    }

    // ---- OverlayManager (android.content.om) reflection ----
    object Om {
        private const val USER_SYSTEM_ID = 0
        private const val USER_CURRENT_ID = -2
        private const val THEME_CUSTOMIZATION_KEY = "theme_customization_overlay_packages"

        private fun userSystemHandle(): Any? {
            return runCatching {
                val uh = Class.forName("android.os.UserHandle")
                for (name in listOf("USER_SYSTEM", "SYSTEM")) {
                    runCatching { uh.getField(name).get(null) }.getOrNull()?.let { return it }
                }
                null
            }.getOrNull()
        }

        fun service(): Any? {
            return runCatching {
                val binder = getService("overlay") ?: return null
                val stub = Class.forName("android.content.om.IOverlayManager\$Stub")
                stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }.getOrNull()
        }

        @Suppress("UNCHECKED_CAST")
        fun infosForTarget(service: Any?, targetPackage: String): List<Any> {
            if (service == null) return emptyList()
            return runCatching {
                val m = service.javaClass.getMethod(
                    "getOverlayInfosForTarget", String::class.java, Int::class.javaPrimitiveType
                )
                (m.invoke(service, targetPackage, USER_SYSTEM_ID) as? List<Any>).orEmpty()
            }.getOrDefault(emptyList())
        }

        fun infoString(info: Any, field: String): String? {
            return runCatching {
                val f = info.javaClass.getField(field)
                f.isAccessible = true
                f.get(info) as? String
            }.getOrNull()
        }

        fun infoBool(info: Any, method: String): Boolean {
            return runCatching {
                info.javaClass.getMethod(method).invoke(info) as? Boolean
            }.getOrNull() ?: runCatching {
                val f = info.javaClass.getField(method)
                f.isAccessible = true
                f.getBoolean(info)
            }.getOrDefault(false)
        }

        fun infoInt(info: Any, field: String): Int {
            return runCatching {
                val f = info.javaClass.getField(field)
                f.isAccessible = true
                f.getInt(info)
            }.getOrDefault(0)
        }

        fun setEnabled(service: Any?, packageName: String, enable: Boolean): Boolean {
            if (service == null) return false
            return runCatching {
                val m = service.javaClass.getMethod(
                    "setEnabled", String::class.java,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
                m.invoke(service, packageName, enable, USER_SYSTEM_ID) as? Boolean ?: false
            }.getOrDefault(false)
        }

        fun setEnabledExclusiveInCategory(service: Any?, packageName: String): Boolean {
            if (service == null) return false
            return runCatching {
                val m = service.javaClass.getMethod(
                    "setEnabledExclusiveInCategory", String::class.java, Int::class.javaPrimitiveType
                )
                m.invoke(service, packageName, USER_SYSTEM_ID) as? Boolean ?: false
            }.getOrDefault(false)
        }

        fun getOverlayInfo(service: Any?, packageName: String): Any? {
            if (service == null) return null
            return runCatching {
                val m = service.javaClass.getMethod(
                    "getOverlayInfo", String::class.java, Int::class.javaPrimitiveType
                )
                m.invoke(service, packageName, USER_SYSTEM_ID)
            }.getOrNull()
        }

        fun identifier(packageName: String, overlayName: String?): Any? {
            return runCatching {
                val cls = Class.forName("android.content.om.OverlayIdentifier")
                if (overlayName == null) {
                    cls.getDeclaredConstructor(String::class.java).newInstance(packageName)
                } else {
                    cls.getDeclaredConstructor(String::class.java, String::class.java)
                        .newInstance(packageName, overlayName)
                }
            }.getOrNull()
        }

        fun managerGetOverlayInfo(
            manager: android.content.om.OverlayManager,
            identifier: Any?,
            user: Any?
        ): Any? {
            if (identifier == null || user == null) return null
            return runCatching {
                val uh = Class.forName("android.os.UserHandle")
                val m = manager.javaClass.getMethod(
                    "getOverlayInfo",
                    Class.forName("android.content.om.OverlayIdentifier"), uh
                )
                m.invoke(manager, identifier, user)
            }.getOrNull()
        }

        fun systemUser(): Any? = userSystemHandle()

        fun transaction(): Any? {
            return runCatching {
                val cls = Class.forName("android.content.om.OverlayManagerTransaction")
                cls.getMethod("newInstance").invoke(null)
            }.getOrNull()
        }

        fun txSetEnabled(tx: Any?, identifier: Any?, enable: Boolean): Boolean {
            if (tx == null) return false
            if (identifier == null) return false
            return runCatching {
                val idCls = Class.forName("android.content.om.OverlayIdentifier")
                val m = tx.javaClass.getMethod(
                    "setEnabled", idCls,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
                m.invoke(tx, identifier, enable, USER_SYSTEM_ID)
                true
            }.getOrDefault(false)
        }

        fun commitSetEnabled(manager: android.content.om.OverlayManager, identifier: Any?, enable: Boolean): Boolean {
            if (identifier == null) return false
            return runCatching {
                val builderCls = Class.forName("android.content.om.OverlayManagerTransaction\$Builder")
                val idCls = Class.forName("android.content.om.OverlayIdentifier")
                val builder = builderCls.getDeclaredConstructor().newInstance()
                val setM = builderCls.getMethod(
                    "setEnabled", idCls,
                    Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType
                )
                setM.invoke(builder, identifier, enable, USER_SYSTEM_ID)
                val built = builderCls.getMethod("build").invoke(builder)
                val commitM = manager.javaClass.getMethod(
                    "commit", Class.forName("android.content.om.OverlayManagerTransaction")
                )
                commitM.invoke(manager, built)
                true
            }.getOrDefault(false)
        }

        fun txRegister(tx: Any?, overlay: Any?): Boolean {
            if (tx == null || overlay == null) return false
            return runCatching {
                tx.javaClass.getMethod(
                    "registerFabricatedOverlay",
                    Class.forName("android.content.om.FabricatedOverlay")
                ).invoke(tx, overlay)
                true
            }.getOrDefault(false)
        }

        fun txUnregister(tx: Any?, identifier: Any?): Boolean {
            if (tx == null) return false
            if (identifier == null) return false
            return runCatching {
                tx.javaClass.getMethod(
                    "unregisterFabricatedOverlay",
                    Class.forName("android.content.om.OverlayIdentifier")
                ).invoke(tx, identifier)
                true
            }.getOrDefault(false)
        }

        fun fabricatedBuilder(owner: String, name: String, target: String): Any? {
            return runCatching {
                val cls = Class.forName("android.content.om.FabricatedOverlay\$Builder")
                cls.getDeclaredConstructor(
                    String::class.java, String::class.java, String::class.java
                ).newInstance(owner, name, target)
            }.getOrNull()
        }

        fun fabricatedSetString(builder: Any?, resName: String, dataType: Int, value: String): Any? {
            if (builder == null) return null
            return runCatching {
                val m = builder.javaClass.getMethod(
                    "setResourceValue", String::class.java,
                    Int::class.javaPrimitiveType, String::class.java
                )
                m.invoke(builder, resName, dataType, value)
            }.getOrNull()
        }

        fun fabricatedSetInt(builder: Any?, resName: String, dataType: Int, value: Int): Any? {
            if (builder == null) return null
            return runCatching {
                val m = builder.javaClass.getMethod(
                    "setResourceValue", String::class.java,
                    Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
                )
                m.invoke(builder, resName, dataType, value, null)
            }.getOrNull()
        }

        fun fabricatedBuild(builder: Any?): Any? {
            if (builder == null) return null
            return runCatching {
                builder.javaClass.getMethod("build").invoke(builder)
            }.getOrNull()
        }

        fun secureGetString(cr: ContentResolver, name: String): String? {
            val key = if (name == "theme_customization") THEME_CUSTOMIZATION_KEY else name
            return runCatching {
                val m = Settings.Secure::class.java.getMethod(
                    "getStringForUser", ContentResolver::class.java,
                    String::class.java, Int::class.javaPrimitiveType
                )
                m.invoke(null, cr, key, USER_CURRENT_ID) as? String
            }.getOrElse {
                runCatching { Settings.Secure.getString(cr, key) }.getOrNull()
            }
        }

        fun securePutString(cr: ContentResolver, name: String, value: String?): Boolean {
            val key = if (name == "theme_customization") THEME_CUSTOMIZATION_KEY else name
            return runCatching {
                val m = Settings.Secure::class.java.getMethod(
                    "putStringForUser", ContentResolver::class.java,
                    String::class.java, String::class.java, Int::class.javaPrimitiveType
                )
                m.invoke(null, cr, key, value, USER_CURRENT_ID) as? Boolean ?: false
            }.getOrElse {
                runCatching { Settings.Secure.putString(cr, key, value) }.getOrDefault(false)
            }
        }
    }

    fun getService(name: String): IBinder? {
        return runCatching {
            val clazz = Class.forName("android.os.ServiceManager")
            clazz.getMethod("getService", String::class.java).invoke(null, name) as? IBinder
        }.getOrNull()
    }

    fun restartSystemUI(statusBarServiceName: String = "statusbar"): Boolean {
        return runCatching {
            val binder = getService(statusBarServiceName) ?: return false
            val stub = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return false
            service.javaClass.getMethod("restartSystemUI").invoke(service)
            true
        }.onFailure { Log.w(TAG, "IStatusBarService.restartSystemUI unavailable", it) }.getOrDefault(false)
    }
}
