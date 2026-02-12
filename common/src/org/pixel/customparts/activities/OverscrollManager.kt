package org.pixel.customparts.activities

import android.content.Context
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import org.pixel.customparts.AppConfig

data class SavedProfile(val name: String, val jsonData: JSONObject)
data class AppConfigItem(val pkg: String, var filter: Boolean, var scale: Float, var ignore: Boolean) {
    override fun toString(): String = "$pkg:${if(filter) 1 else 0}:$scale:${if(ignore) 1 else 0}"
}

object OverscrollManager {
    val KEY_ENABLED: String
        get() = if (AppConfig.IS_XPOSED) "overscroll_enabled_xposed" else "overscroll_enabled_pine"
    private const val KEY_DT2W_TIMEOUT = "doze_double_tap_timeout"
    
    const val KEY_SAVED_PROFILES = "overscroll_saved_profiles"
    const val KEY_ACTIVE_PROFILE = "overscroll_active_profile_name"
    const val KEY_PACKAGES_CONFIG = "overscroll_packages_config"
    const val KEY_LOGGING = "overscroll_logging"
    const val KEY_COMPOSE_SCALE = "overscroll_compose_scale"
    const val KEY_INVERT_ANCHOR = "overscroll_invert_anchor"
    const val KEY_PULL_COEFF = "overscroll_pull"
    const val KEY_STIFFNESS = "overscroll_stiffness"
    const val KEY_DAMPING = "overscroll_damping"
    const val KEY_FLING = "overscroll_fling"
    const val KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent"
    const val KEY_SCALE_MODE = "overscroll_scale_mode"
    const val KEY_SCALE_INTENSITY = "overscroll_scale_intensity"
    const val KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz"
    const val KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min"
    const val KEY_SCALE_ANCHOR_X = "overscroll_scale_anchor_x"
    const val KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y"
    const val KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz"
    const val KEY_SCALE_ANCHOR_Y_HORIZ = "overscroll_scale_anchor_y_horiz"
    const val KEY_ZOOM_MODE = "overscroll_zoom_mode"
    const val KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity"
    const val KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz"
    const val KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min"
    const val KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x"
    const val KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y"
    const val KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz"
    const val KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz"
    const val KEY_H_SCALE_MODE = "overscroll_h_scale_mode"
    const val KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity"
    const val KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz"
    const val KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min"
    const val KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x"
    const val KEY_H_SCALE_ANCHOR_Y = "overscroll_h_scale_anchor_y"
    const val KEY_H_SCALE_ANCHOR_X_HORIZ = "overscroll_h_scale_anchor_x_horiz"
    const val KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz"
    const val KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth"
    const val KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel_v2"
    const val KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val_v2"
    const val KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle"
    const val KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run"

    fun isMasterEnabled(context: Context) = Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 1) == 1
    
    suspend fun setMasterEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_ENABLED, if (enabled) 1 else 0)
    }

    fun getSavedProfiles(context: Context): List<SavedProfile> {
        val raw = Settings.Global.getString(context.contentResolver, KEY_SAVED_PROFILES) ?: return emptyList()
        val list = mutableListOf<SavedProfile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(SavedProfile(obj.getString("name"), obj.getJSONObject("data")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun getActiveProfileName(context: Context): String? {
        return Settings.Global.getString(context.contentResolver, KEY_ACTIVE_PROFILE)
    }

    fun clearActiveProfile(context: Context) {
        if (Settings.Global.getString(context.contentResolver, KEY_ACTIVE_PROFILE) != null) {
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
        }
    }

    suspend fun saveProfile(context: Context, name: String) = withContext(Dispatchers.IO) {
        val currentJson = collectCurrentSettingsJson(context)
        val profiles = getSavedProfiles(context).toMutableList()
        profiles.removeAll { it.name == name }
        profiles.add(SavedProfile(name, currentJson))
        saveProfilesToGlobal(context, profiles)
        Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, name)
    }

    suspend fun deleteProfile(context: Context, profile: SavedProfile) = withContext(Dispatchers.IO) {
        val profiles = getSavedProfiles(context).toMutableList()
        profiles.removeIf { it.name == profile.name }
        saveProfilesToGlobal(context, profiles)
        
        val active = getActiveProfileName(context)
        if (active == profile.name) {
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
        }
    }

    suspend fun loadProfile(context: Context, profile: SavedProfile) = withContext(Dispatchers.IO) {
        applySettingsFromJson(context, profile.jsonData)
        Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, profile.name)
    }

    private fun saveProfilesToGlobal(context: Context, profiles: List<SavedProfile>) {
        val arr = JSONArray()
        profiles.forEach { 
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("data", it.jsonData)
            arr.put(obj)
        }
        Settings.Global.putString(context.contentResolver, KEY_SAVED_PROFILES, arr.toString())
    }

    fun getAppConfigs(context: Context): List<AppConfigItem> {
        val raw = Settings.Global.getString(context.contentResolver, KEY_PACKAGES_CONFIG) ?: return emptyList()
        val list = mutableListOf<AppConfigItem>()
        if (raw.isBlank()) return list
        
        raw.split(" ").forEach {
            try {
                val parts = it.split(":")
                if (parts.size >= 3) {
                    list.add(AppConfigItem(parts[0], parts[1] == "1", parts[2].toFloat(), if (parts.size >= 4) parts[3] == "1" else false))
                }
            } catch (_: Exception) {}
        }
        return list
    }

    suspend fun saveAppConfig(context: Context, list: List<AppConfigItem>) = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        list.forEachIndexed { index, item ->
            sb.append(item.toString())
            if (index < list.size - 1) sb.append(" ")
        }
        Settings.Global.putString(context.contentResolver, KEY_PACKAGES_CONFIG, sb.toString())
    }

    private fun collectCurrentSettingsJson(context: Context): JSONObject {
        val json = JSONObject()
        json.put(KEY_ENABLED, Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 1))
        json.put(KEY_LOGGING, Settings.Global.getInt(context.contentResolver, KEY_LOGGING, 0))
        json.put(KEY_INVERT_ANCHOR, Settings.Global.getInt(context.contentResolver, KEY_INVERT_ANCHOR, 1))
        json.put(KEY_PACKAGES_CONFIG, Settings.Global.getString(context.contentResolver, KEY_PACKAGES_CONFIG))

        val floatKeys = listOf(
            KEY_PULL_COEFF, KEY_STIFFNESS, KEY_DAMPING, KEY_FLING, KEY_RESISTANCE_EXPONENT,
            KEY_PHYSICS_MIN_VEL, KEY_PHYSICS_MIN_VAL, KEY_INPUT_SMOOTH_FACTOR,
            KEY_LERP_MAIN_IDLE, KEY_LERP_MAIN_RUN, KEY_COMPOSE_SCALE 
        )
        for (k in floatKeys) json.put(k, Settings.Global.getFloat(context.contentResolver, k, 0f).toDouble())

        val prefixes = listOf("overscroll_scale", "overscroll_zoom", "overscroll_h_scale")
        for (pre in prefixes) {
            json.put(pre + "_mode", Settings.Global.getInt(context.contentResolver, pre + "_mode", 0))
            json.put(pre + "_intensity", Settings.Global.getFloat(context.contentResolver, pre + "_intensity", 0f).toDouble())
            json.put(pre + "_intensity_horiz", Settings.Global.getFloat(context.contentResolver, pre + "_intensity_horiz", 0f).toDouble())
            json.put(pre + "_limit_min", Settings.Global.getFloat(context.contentResolver, pre + "_limit_min", 0f).toDouble())
            json.put(pre + "_anchor_x", Settings.Global.getFloat(context.contentResolver, pre + "_anchor_x", 0f).toDouble())
            json.put(pre + "_anchor_y", Settings.Global.getFloat(context.contentResolver, pre + "_anchor_y", 0f).toDouble())
            json.put(pre + "_anchor_x_horiz", Settings.Global.getFloat(context.contentResolver, pre + "_anchor_x_horiz", 0f).toDouble())
            json.put(pre + "_anchor_y_horiz", Settings.Global.getFloat(context.contentResolver, pre + "_anchor_y_horiz", 0f).toDouble())
        }
        return json
    }

    private fun applySettingsFromJson(context: Context, json: JSONObject) {
        val iter = json.keys()
        while(iter.hasNext()) {
            val key = iter.next()
            if (key == KEY_ACTIVE_PROFILE) continue 

            val valObj = json.get(key)
            if (valObj is Number) { 
                if (key.endsWith("_mode") || key == KEY_ENABLED || key == KEY_LOGGING || key == KEY_INVERT_ANCHOR) {
                    Settings.Global.putInt(context.contentResolver, key, valObj.toInt())
                } else {
                    Settings.Global.putFloat(context.contentResolver, key, valObj.toFloat())
                }
            } else if (valObj is String) {
                Settings.Global.putString(context.contentResolver, key, valObj)
            }
        }
    }

    suspend fun resetAll(context: Context) = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        Settings.Global.putInt(cr, KEY_ENABLED, 1)
        Settings.Global.putInt(cr, KEY_LOGGING, 0)
        Settings.Global.putString(cr, KEY_ACTIVE_PROFILE, null)
        Settings.Global.putInt(cr, KEY_INVERT_ANCHOR, 1)
        Settings.Global.putFloat(cr, KEY_PULL_COEFF, 0.5f)
        Settings.Global.putFloat(cr, KEY_STIFFNESS, 450f)
        Settings.Global.putFloat(cr, KEY_DAMPING, 0.7f)
        Settings.Global.putFloat(cr, KEY_FLING, 0.6f)
        Settings.Global.putFloat(cr, KEY_RESISTANCE_EXPONENT, 4.0f)
        Settings.Global.putFloat(cr, KEY_PHYSICS_MIN_VEL, 80.0f)
        Settings.Global.putFloat(cr, KEY_PHYSICS_MIN_VAL, 4.0f)
        Settings.Global.putFloat(cr, KEY_INPUT_SMOOTH_FACTOR, 0.5f)
        Settings.Global.putFloat(cr, KEY_LERP_MAIN_IDLE, 0.4f)
        Settings.Global.putFloat(cr, KEY_LERP_MAIN_RUN, 0.7f)
        Settings.Global.putFloat(cr, KEY_COMPOSE_SCALE, 3.33f)
        Settings.Global.putInt(cr, KEY_SCALE_MODE, 0)
        Settings.Global.putFloat(cr, KEY_SCALE_INTENSITY, 0.0f)
        Settings.Global.putFloat(cr, KEY_SCALE_INTENSITY_HORIZ, 0.0f)
        Settings.Global.putFloat(cr, KEY_SCALE_LIMIT_MIN, 0.3f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_Y_HORIZ, 0.5f)
        Settings.Global.putInt(cr, KEY_ZOOM_MODE, 0)
        Settings.Global.putFloat(cr, KEY_ZOOM_INTENSITY, 0.0f)
        Settings.Global.putFloat(cr, KEY_ZOOM_INTENSITY_HORIZ, 0.0f)
        Settings.Global.putFloat(cr, KEY_ZOOM_LIMIT_MIN, 0.3f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f)
        Settings.Global.putInt(cr, KEY_H_SCALE_MODE, 0)
        Settings.Global.putFloat(cr, KEY_H_SCALE_INTENSITY, 0.0f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_INTENSITY_HORIZ, 0.0f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_LIMIT_MIN, 0.3f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f)
    }

    suspend fun exportSettings(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val json = collectCurrentSettingsJson(context)
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(json.toString(4).toByteArray()) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun importSettings(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) { sb.append(line); line = reader.readLine() }
                }
            }
            applySettingsFromJson(context, JSONObject(sb.toString()))
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
            true
        } catch (e: Exception) { 
            e.printStackTrace()
            false 
        }
    }
}