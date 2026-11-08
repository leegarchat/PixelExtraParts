package org.pixel.extra.parts.overscroll

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Immutable snapshot of every tunable overscroll parameter. Field names are
// short on purpose; the Settings.Global key mapping lives in toMap/fromKeys.
data class OverscrollValues(
    val pull: Float = OverscrollKeys.Defaults.PULL,
    val stiffness: Float = OverscrollKeys.Defaults.STIFFNESS,
    val damping: Float = OverscrollKeys.Defaults.DAMPING,
    val fling: Float = OverscrollKeys.Defaults.FLING,
    val resistanceExponent: Float = OverscrollKeys.Defaults.RESISTANCE_EXPONENT,
    val animSpeed: Float = OverscrollKeys.Defaults.ANIM_SPEED,
    val scaleMode: Int = OverscrollKeys.Defaults.SCALE_MODE,
    val scaleIntensity: Float = OverscrollKeys.Defaults.SCALE_INTENSITY,
    val scaleIntensityHoriz: Float = OverscrollKeys.Defaults.SCALE_INTENSITY_HORIZ,
    val scaleLimitMin: Float = OverscrollKeys.Defaults.SCALE_LIMIT_MIN,
    val scaleAnchorX: Float = OverscrollKeys.Defaults.ANCHOR,
    val scaleAnchorY: Float = OverscrollKeys.Defaults.ANCHOR,
    val scaleAnchorXHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val scaleAnchorYHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val zoomMode: Int = OverscrollKeys.Defaults.ZOOM_MODE,
    val zoomIntensity: Float = OverscrollKeys.Defaults.ZOOM_INTENSITY,
    val zoomIntensityHoriz: Float = OverscrollKeys.Defaults.ZOOM_INTENSITY_HORIZ,
    val zoomLimitMin: Float = OverscrollKeys.Defaults.ZOOM_LIMIT_MIN,
    val zoomAnchorX: Float = OverscrollKeys.Defaults.ANCHOR,
    val zoomAnchorY: Float = OverscrollKeys.Defaults.ANCHOR,
    val zoomAnchorXHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val zoomAnchorYHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val hScaleMode: Int = OverscrollKeys.Defaults.H_SCALE_MODE,
    val hScaleIntensity: Float = OverscrollKeys.Defaults.H_SCALE_INTENSITY,
    val hScaleIntensityHoriz: Float = OverscrollKeys.Defaults.H_SCALE_INTENSITY_HORIZ,
    val hScaleLimitMin: Float = OverscrollKeys.Defaults.H_SCALE_LIMIT_MIN,
    val hScaleAnchorX: Float = OverscrollKeys.Defaults.ANCHOR,
    val hScaleAnchorY: Float = OverscrollKeys.Defaults.ANCHOR,
    val hScaleAnchorXHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val hScaleAnchorYHoriz: Float = OverscrollKeys.Defaults.ANCHOR,
    val inputSmooth: Float = OverscrollKeys.Defaults.INPUT_SMOOTH,
    val physicsMinVel: Float = OverscrollKeys.Defaults.PHYSICS_MIN_VEL,
    val physicsMinVal: Float = OverscrollKeys.Defaults.PHYSICS_MIN_VAL,
    val lerpMainIdle: Float = OverscrollKeys.Defaults.LERP_MAIN_IDLE,
    val lerpMainRun: Float = OverscrollKeys.Defaults.LERP_MAIN_RUN,
    val composeScale: Float = OverscrollKeys.Defaults.COMPOSE_SCALE,
    val invertAnchor: Boolean = OverscrollKeys.Defaults.INVERT_ANCHOR,
    val logging: Boolean = OverscrollKeys.Defaults.LOGGING,
    val normEnabled: Boolean = OverscrollKeys.Defaults.NORM_ENABLED,
    val normRefDelta: Float = OverscrollKeys.Defaults.NORM_REF_DELTA,
    val normDetectMul: Float = OverscrollKeys.Defaults.NORM_DETECT_MUL,
    val normFactor: Float = OverscrollKeys.Defaults.NORM_FACTOR,
    val normWindow: Float = OverscrollKeys.Defaults.NORM_WINDOW,
    val normRamp: Float = OverscrollKeys.Defaults.NORM_RAMP,
    val normDetectMode: Int = OverscrollKeys.Defaults.NORM_DETECT_MODE,
)

// A stored profile: display name plus the raw JSON payload.
// JSON keys match Settings.Global keys one to one;
// numbers as doubles, meta fields ignored.
data class OverscrollStoredProfile(
    val name: String,
    val json: JSONObject,
    val isPreset: Boolean = false,
)

// Persistence bridge between Settings.Global and the editor UI.
// Settings keys and JSON payload keys are identical (direct names).
object OverscrollStore {
    private const val PRESET_DIR = "overscroll_presets"

    private fun getFloat(cr: ContentResolver, key: String, def: Float): Float {
        return try {
            Settings.Global.getFloat(cr, key, def)
        } catch (_: Exception) {
            def
        }
    }

    private fun getInt(cr: ContentResolver, key: String, def: Int): Int {
        return try {
            Settings.Global.getInt(cr, key, def)
        } catch (_: Exception) {
            def
        }
    }

    private fun isTrue(cr: ContentResolver, key: String, def: Boolean): Boolean {
        return getInt(cr, key, if (def) 1 else 0) == 1
    }

    suspend fun loadValues(context: Context): OverscrollValues = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val d = OverscrollKeys.Defaults
        OverscrollValues(
            pull = getFloat(cr, OverscrollKeys.PULL, d.PULL),
            stiffness = getFloat(cr, OverscrollKeys.STIFFNESS, d.STIFFNESS),
            damping = getFloat(cr, OverscrollKeys.DAMPING, d.DAMPING),
            fling = getFloat(cr, OverscrollKeys.FLING, d.FLING),
            resistanceExponent = getFloat(cr, OverscrollKeys.RESISTANCE_EXPONENT, d.RESISTANCE_EXPONENT),
            animSpeed = getFloat(cr, OverscrollKeys.ANIM_SPEED, d.ANIM_SPEED),
            scaleMode = getInt(cr, OverscrollKeys.SCALE_MODE, d.SCALE_MODE),
            scaleIntensity = getFloat(cr, OverscrollKeys.SCALE_INTENSITY, d.SCALE_INTENSITY),
            scaleIntensityHoriz = getFloat(cr, OverscrollKeys.SCALE_INTENSITY_HORIZ, d.SCALE_INTENSITY_HORIZ),
            scaleLimitMin = getFloat(cr, OverscrollKeys.SCALE_LIMIT_MIN, d.SCALE_LIMIT_MIN),
            scaleAnchorX = getFloat(cr, OverscrollKeys.SCALE_ANCHOR_X, d.ANCHOR),
            scaleAnchorY = getFloat(cr, OverscrollKeys.SCALE_ANCHOR_Y, d.ANCHOR),
            scaleAnchorXHoriz = getFloat(cr, OverscrollKeys.SCALE_ANCHOR_X_HORIZ, d.ANCHOR),
            scaleAnchorYHoriz = getFloat(cr, OverscrollKeys.SCALE_ANCHOR_Y_HORIZ, d.ANCHOR),
            zoomMode = getInt(cr, OverscrollKeys.ZOOM_MODE, d.ZOOM_MODE),
            zoomIntensity = getFloat(cr, OverscrollKeys.ZOOM_INTENSITY, d.ZOOM_INTENSITY),
            zoomIntensityHoriz = getFloat(cr, OverscrollKeys.ZOOM_INTENSITY_HORIZ, d.ZOOM_INTENSITY_HORIZ),
            zoomLimitMin = getFloat(cr, OverscrollKeys.ZOOM_LIMIT_MIN, d.ZOOM_LIMIT_MIN),
            zoomAnchorX = getFloat(cr, OverscrollKeys.ZOOM_ANCHOR_X, d.ANCHOR),
            zoomAnchorY = getFloat(cr, OverscrollKeys.ZOOM_ANCHOR_Y, d.ANCHOR),
            zoomAnchorXHoriz = getFloat(cr, OverscrollKeys.ZOOM_ANCHOR_X_HORIZ, d.ANCHOR),
            zoomAnchorYHoriz = getFloat(cr, OverscrollKeys.ZOOM_ANCHOR_Y_HORIZ, d.ANCHOR),
            hScaleMode = getInt(cr, OverscrollKeys.H_SCALE_MODE, d.H_SCALE_MODE),
            hScaleIntensity = getFloat(cr, OverscrollKeys.H_SCALE_INTENSITY, d.H_SCALE_INTENSITY),
            hScaleIntensityHoriz = getFloat(cr, OverscrollKeys.H_SCALE_INTENSITY_HORIZ, d.H_SCALE_INTENSITY_HORIZ),
            hScaleLimitMin = getFloat(cr, OverscrollKeys.H_SCALE_LIMIT_MIN, d.H_SCALE_LIMIT_MIN),
            hScaleAnchorX = getFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_X, d.ANCHOR),
            hScaleAnchorY = getFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_Y, d.ANCHOR),
            hScaleAnchorXHoriz = getFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_X_HORIZ, d.ANCHOR),
            hScaleAnchorYHoriz = getFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_Y_HORIZ, d.ANCHOR),
            inputSmooth = getFloat(cr, OverscrollKeys.INPUT_SMOOTH, d.INPUT_SMOOTH),
            physicsMinVel = getFloat(cr, OverscrollKeys.PHYSICS_MIN_VEL, d.PHYSICS_MIN_VEL),
            physicsMinVal = getFloat(cr, OverscrollKeys.PHYSICS_MIN_VAL, d.PHYSICS_MIN_VAL),
            lerpMainIdle = getFloat(cr, OverscrollKeys.LERP_MAIN_IDLE, d.LERP_MAIN_IDLE),
            lerpMainRun = getFloat(cr, OverscrollKeys.LERP_MAIN_RUN, d.LERP_MAIN_RUN),
            composeScale = getFloat(cr, OverscrollKeys.COMPOSE_SCALE, d.COMPOSE_SCALE),
            invertAnchor = isTrue(cr, OverscrollKeys.INVERT_ANCHOR, d.INVERT_ANCHOR),
            logging = isTrue(cr, OverscrollKeys.LOGGING, d.LOGGING),
            normEnabled = isTrue(cr, OverscrollKeys.NORM_ENABLED, d.NORM_ENABLED),
            normRefDelta = getFloat(cr, OverscrollKeys.NORM_REF_DELTA, d.NORM_REF_DELTA),
            normDetectMul = getFloat(cr, OverscrollKeys.NORM_DETECT_MUL, d.NORM_DETECT_MUL),
            normFactor = getFloat(cr, OverscrollKeys.NORM_FACTOR, d.NORM_FACTOR),
            normWindow = getFloat(cr, OverscrollKeys.NORM_WINDOW, d.NORM_WINDOW),
            normRamp = getFloat(cr, OverscrollKeys.NORM_RAMP, d.NORM_RAMP),
            normDetectMode = getInt(cr, OverscrollKeys.NORM_DETECT_MODE, d.NORM_DETECT_MODE),
        )
    }

    suspend fun isMasterEnabled(context: Context): Boolean = withContext(Dispatchers.IO) {
        isTrue(context.contentResolver, OverscrollKeys.ENABLED, OverscrollKeys.Defaults.ENABLED)
    }

    suspend fun putMasterEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, OverscrollKeys.ENABLED, if (enabled) 1 else 0)
    }

    suspend fun putFloat(context: Context, key: String, value: Float) = withContext(Dispatchers.IO) {
        Settings.Global.putFloat(context.contentResolver, key, value)
    }

    suspend fun putInt(context: Context, key: String, value: Int) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, key, value)
    }

    // Collects the current Settings.Global state into the JSON
    // payload. The per-package config string and master
    // flags travel inside the payload untouched.
    suspend fun collectJson(context: Context): JSONObject = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val json = JSONObject()
        val values = loadSnapshot(cr)
        for ((key, number) in values) {
            json.put(key, number)
        }
        json.put(OverscrollKeys.ENABLED, getInt(cr, OverscrollKeys.ENABLED, 1))
        json.put(OverscrollKeys.LOGGING, getInt(cr, OverscrollKeys.LOGGING, 0))
        json.put(OverscrollKeys.INVERT_ANCHOR, getInt(cr, OverscrollKeys.INVERT_ANCHOR, 1))
        json.put(
            OverscrollKeys.NORM_ENABLED,
            getInt(cr, OverscrollKeys.NORM_ENABLED, 1),
        )
        json.put(
            OverscrollKeys.NORM_DETECT_MODE,
            getInt(cr, OverscrollKeys.NORM_DETECT_MODE, 1),
        )
        val packages = try {
            Settings.Global.getString(cr, OverscrollKeys.PACKAGES_CONFIG) ?: ""
        } catch (_: Exception) {
            ""
        }
        json.put(OverscrollKeys.PACKAGES_CONFIG, packages)
        json
    }

    // Reads every known float/int key with its factory default.
    private fun loadSnapshot(cr: ContentResolver): List<Pair<String, Double>> {
        val d = OverscrollKeys.Defaults
        val floats = mapOf(
            OverscrollKeys.PULL to d.PULL,
            OverscrollKeys.STIFFNESS to d.STIFFNESS,
            OverscrollKeys.DAMPING to d.DAMPING,
            OverscrollKeys.FLING to d.FLING,
            OverscrollKeys.RESISTANCE_EXPONENT to d.RESISTANCE_EXPONENT,
            OverscrollKeys.PHYSICS_MIN_VEL to d.PHYSICS_MIN_VEL,
            OverscrollKeys.PHYSICS_MIN_VAL to d.PHYSICS_MIN_VAL,
            OverscrollKeys.ANIM_SPEED to d.ANIM_SPEED,
            OverscrollKeys.INPUT_SMOOTH to d.INPUT_SMOOTH,
            OverscrollKeys.LERP_MAIN_IDLE to d.LERP_MAIN_IDLE,
            OverscrollKeys.LERP_MAIN_RUN to d.LERP_MAIN_RUN,
            OverscrollKeys.COMPOSE_SCALE to d.COMPOSE_SCALE,
            OverscrollKeys.NORM_REF_DELTA to d.NORM_REF_DELTA,
            OverscrollKeys.NORM_DETECT_MUL to d.NORM_DETECT_MUL,
            OverscrollKeys.NORM_FACTOR to d.NORM_FACTOR,
            OverscrollKeys.NORM_WINDOW to d.NORM_WINDOW,
            OverscrollKeys.NORM_RAMP to d.NORM_RAMP,
            OverscrollKeys.SCALE_INTENSITY to d.SCALE_INTENSITY,
            OverscrollKeys.SCALE_INTENSITY_HORIZ to d.SCALE_INTENSITY_HORIZ,
            OverscrollKeys.SCALE_LIMIT_MIN to d.SCALE_LIMIT_MIN,
            OverscrollKeys.SCALE_ANCHOR_X to d.ANCHOR,
            OverscrollKeys.SCALE_ANCHOR_Y to d.ANCHOR,
            OverscrollKeys.SCALE_ANCHOR_X_HORIZ to d.ANCHOR,
            OverscrollKeys.SCALE_ANCHOR_Y_HORIZ to d.ANCHOR,
            OverscrollKeys.ZOOM_INTENSITY to d.ZOOM_INTENSITY,
            OverscrollKeys.ZOOM_INTENSITY_HORIZ to d.ZOOM_INTENSITY_HORIZ,
            OverscrollKeys.ZOOM_LIMIT_MIN to d.ZOOM_LIMIT_MIN,
            OverscrollKeys.ZOOM_ANCHOR_X to d.ANCHOR,
            OverscrollKeys.ZOOM_ANCHOR_Y to d.ANCHOR,
            OverscrollKeys.ZOOM_ANCHOR_X_HORIZ to d.ANCHOR,
            OverscrollKeys.ZOOM_ANCHOR_Y_HORIZ to d.ANCHOR,
            OverscrollKeys.H_SCALE_INTENSITY to d.H_SCALE_INTENSITY,
            OverscrollKeys.H_SCALE_INTENSITY_HORIZ to d.H_SCALE_INTENSITY_HORIZ,
            OverscrollKeys.H_SCALE_LIMIT_MIN to d.H_SCALE_LIMIT_MIN,
            OverscrollKeys.H_SCALE_ANCHOR_X to d.ANCHOR,
            OverscrollKeys.H_SCALE_ANCHOR_Y to d.ANCHOR,
            OverscrollKeys.H_SCALE_ANCHOR_X_HORIZ to d.ANCHOR,
            OverscrollKeys.H_SCALE_ANCHOR_Y_HORIZ to d.ANCHOR,
        )
        val result = floats.map { (key, def) ->
            key to getFloat(cr, key, def).toDouble()
        }.toMutableList()
        val modes = listOf(
            OverscrollKeys.SCALE_MODE to d.SCALE_MODE,
            OverscrollKeys.ZOOM_MODE to d.ZOOM_MODE,
            OverscrollKeys.H_SCALE_MODE to d.H_SCALE_MODE,
        )
        for ((key, def) in modes) {
            result.add(key to getInt(cr, key, def).toDouble())
        }
        return result
    }

    // Applies a profile payload: every numeric entry is routed to the
    // Settings.Global key, strings pass through, meta fields are skipped.
    // Returns the reloaded snapshot so the UI can refresh in one pass.
    suspend fun applyJson(context: Context, json: JSONObject): OverscrollValues =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val keys = json.keys()
            while (keys.hasNext()) {
                val raw = keys.next()
                if (raw == OverscrollKeys.ACTIVE_PROFILE_NAME || raw == "name") continue
                var key = raw
                if (key.endsWith("_v2")) key = key.removeSuffix("_v2")
                try {
                    when (val v = json.get(raw)) {
                        is Number -> {
                            val isInt = key.endsWith("_mode") ||
                                key.contains("enabled") ||
                                key.contains("logging") ||
                                key.contains("invert_anchor") ||
                                key.contains("norm_detect_mode")
                            if (isInt) {
                                Settings.Global.putInt(cr, key, v.toInt())
                            } else {
                                Settings.Global.putFloat(cr, key, v.toFloat())
                            }
                        }
                        is String -> Settings.Global.putString(cr, key, v)
                        is Boolean -> Settings.Global.putInt(cr, key, if (v) 1 else 0)
                        else -> Unit
                    }
                } catch (_: Exception) {
                }
            }
            loadValues(context)
        }

    suspend fun resetAll(context: Context): OverscrollValues = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val d = OverscrollKeys.Defaults
        Settings.Global.putInt(cr, OverscrollKeys.ENABLED, 1)
        Settings.Global.putInt(cr, OverscrollKeys.LOGGING, 0)
        Settings.Global.putString(cr, OverscrollKeys.ACTIVE_PROFILE_NAME, null)
        Settings.Global.putInt(cr, OverscrollKeys.INVERT_ANCHOR, 1)
        Settings.Global.putFloat(cr, OverscrollKeys.PULL, d.PULL)
        Settings.Global.putFloat(cr, OverscrollKeys.STIFFNESS, d.STIFFNESS)
        Settings.Global.putFloat(cr, OverscrollKeys.DAMPING, d.DAMPING)
        Settings.Global.putFloat(cr, OverscrollKeys.FLING, d.FLING)
        Settings.Global.putFloat(cr, OverscrollKeys.RESISTANCE_EXPONENT, d.RESISTANCE_EXPONENT)
        Settings.Global.putFloat(cr, OverscrollKeys.PHYSICS_MIN_VEL, d.PHYSICS_MIN_VEL)
        Settings.Global.putFloat(cr, OverscrollKeys.PHYSICS_MIN_VAL, d.PHYSICS_MIN_VAL)
        Settings.Global.putFloat(cr, OverscrollKeys.ANIM_SPEED, d.ANIM_SPEED)
        Settings.Global.putFloat(cr, OverscrollKeys.INPUT_SMOOTH, d.INPUT_SMOOTH)
        Settings.Global.putFloat(cr, OverscrollKeys.LERP_MAIN_IDLE, d.LERP_MAIN_IDLE)
        Settings.Global.putFloat(cr, OverscrollKeys.LERP_MAIN_RUN, d.LERP_MAIN_RUN)
        Settings.Global.putFloat(cr, OverscrollKeys.COMPOSE_SCALE, d.COMPOSE_SCALE)
        Settings.Global.putInt(cr, OverscrollKeys.SCALE_MODE, d.SCALE_MODE)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_INTENSITY, d.SCALE_INTENSITY)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_INTENSITY_HORIZ, d.SCALE_INTENSITY_HORIZ)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_LIMIT_MIN, d.SCALE_LIMIT_MIN)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_ANCHOR_X, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_ANCHOR_Y, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_ANCHOR_X_HORIZ, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.SCALE_ANCHOR_Y_HORIZ, d.ANCHOR)
        Settings.Global.putInt(cr, OverscrollKeys.ZOOM_MODE, d.ZOOM_MODE)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_INTENSITY, d.ZOOM_INTENSITY)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_INTENSITY_HORIZ, d.ZOOM_INTENSITY_HORIZ)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_LIMIT_MIN, d.ZOOM_LIMIT_MIN)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_ANCHOR_X, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_ANCHOR_Y, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_ANCHOR_X_HORIZ, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.ZOOM_ANCHOR_Y_HORIZ, d.ANCHOR)
        Settings.Global.putInt(cr, OverscrollKeys.H_SCALE_MODE, d.H_SCALE_MODE)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_INTENSITY, d.H_SCALE_INTENSITY)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_INTENSITY_HORIZ, d.H_SCALE_INTENSITY_HORIZ)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_LIMIT_MIN, d.H_SCALE_LIMIT_MIN)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_X, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_Y, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_X_HORIZ, d.ANCHOR)
        Settings.Global.putFloat(cr, OverscrollKeys.H_SCALE_ANCHOR_Y_HORIZ, d.ANCHOR)
        Settings.Global.putInt(cr, OverscrollKeys.NORM_ENABLED, 1)
        Settings.Global.putFloat(cr, OverscrollKeys.NORM_REF_DELTA, d.NORM_REF_DELTA)
        Settings.Global.putFloat(cr, OverscrollKeys.NORM_DETECT_MUL, d.NORM_DETECT_MUL)
        Settings.Global.putFloat(cr, OverscrollKeys.NORM_FACTOR, d.NORM_FACTOR)
        Settings.Global.putFloat(cr, OverscrollKeys.NORM_WINDOW, d.NORM_WINDOW)
        Settings.Global.putFloat(cr, OverscrollKeys.NORM_RAMP, d.NORM_RAMP)
        Settings.Global.putInt(cr, OverscrollKeys.NORM_DETECT_MODE, d.NORM_DETECT_MODE)
        loadValues(context)
    }

    suspend fun getSavedProfiles(context: Context): List<OverscrollStoredProfile> =
        withContext(Dispatchers.IO) {
            val raw = try {
                Settings.Global.getString(context.contentResolver, OverscrollKeys.SAVED_PROFILES)
            } catch (_: Exception) {
                null
            } ?: return@withContext emptyList()
            val out = mutableListOf<OverscrollStoredProfile>()
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    out.add(OverscrollStoredProfile(obj.getString("name"), obj.getJSONObject("data")))
                }
            } catch (_: Exception) {
            }
            out
        }

    suspend fun getActiveProfileName(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            Settings.Global.getString(context.contentResolver, OverscrollKeys.ACTIVE_PROFILE_NAME)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearActiveProfile(context: Context) = withContext(Dispatchers.IO) {
        try {
            Settings.Global.putString(context.contentResolver, OverscrollKeys.ACTIVE_PROFILE_NAME, null)
        } catch (_: Exception) {
        }
    }

    suspend fun saveProfile(context: Context, name: String) = withContext(Dispatchers.IO) {
        val payload = collectJson(context)
        val kept = getSavedProfilesSync(context).toMutableList()
        kept.removeAll { it.name == name }
        kept.add(OverscrollStoredProfile(name, payload))
        writeProfiles(context, kept)
        Settings.Global.putString(context.contentResolver, OverscrollKeys.ACTIVE_PROFILE_NAME, name)
    }

    suspend fun deleteProfile(context: Context, profile: OverscrollStoredProfile) =
        withContext(Dispatchers.IO) {
            val kept = getSavedProfilesSync(context).toMutableList()
            kept.removeIf { it.name == profile.name }
            writeProfiles(context, kept)
            val active = try {
                Settings.Global.getString(context.contentResolver, OverscrollKeys.ACTIVE_PROFILE_NAME)
            } catch (_: Exception) {
                null
            }
            if (active == profile.name) {
                Settings.Global.putString(context.contentResolver, OverscrollKeys.ACTIVE_PROFILE_NAME, null)
            }
        }

    suspend fun applyProfile(context: Context, profile: OverscrollStoredProfile): OverscrollValues {
        val values = applyJson(context, profile.json)
        withContext(Dispatchers.IO) {
            Settings.Global.putString(
                context.contentResolver,
                OverscrollKeys.ACTIVE_PROFILE_NAME,
                profile.name,
            )
        }
        return values
    }

    // Built-in presets shipped as raw JSON assets; the files are kept
    // byte-identical to the legacy overscroll.configs catalog.
    suspend fun loadPresets(context: Context): List<OverscrollStoredProfile> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<OverscrollStoredProfile>()
            val names = try {
                context.assets.list(PRESET_DIR)?.sorted() ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            for (file in names) {
                if (!file.endsWith(".json", ignoreCase = true)) continue
                try {
                    val text = context.assets.open("$PRESET_DIR/$file")
                        .bufferedReader().use { it.readText() }
                    val name = file.removeSuffix(".json").removeSuffix(".JSON")
                    out.add(OverscrollStoredProfile(name, JSONObject(text), isPreset = true))
                } catch (_: Exception) {
                }
            }
            out
        }

    private fun getSavedProfilesSync(context: Context): List<OverscrollStoredProfile> {
        val raw = try {
            Settings.Global.getString(context.contentResolver, OverscrollKeys.SAVED_PROFILES)
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val out = mutableListOf<OverscrollStoredProfile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                out.add(OverscrollStoredProfile(obj.getString("name"), obj.getJSONObject("data")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeProfiles(context: Context, profiles: List<OverscrollStoredProfile>) {
        val arr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("data", p.json)
            arr.put(obj)
        }
        try {
            Settings.Global.putString(context.contentResolver, OverscrollKeys.SAVED_PROFILES, arr.toString())
        } catch (_: Exception) {
        }
    }
}
