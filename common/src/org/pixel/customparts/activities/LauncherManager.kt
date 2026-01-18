package org.pixel.customparts.activities

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.AppConfig
import java.io.DataOutputStream

object LauncherManager {
    private const val KEY_NATIVE_SEARCH = "pixel_launcher_native_search"
    val KEY_DOCK_ENABLE: String
        get() = if (AppConfig.IS_XPOSED) "launcher_dock_enable_xposed" else "launcher_dock_enable"
    const val KEY_HIDE_SEARCH_BASE = "launcher_hidden_search"
    val KEY_HIDE_SEARCH: String
        get() = if (AppConfig.IS_XPOSED) "${KEY_HIDE_SEARCH_BASE}_xposed" else KEY_HIDE_SEARCH_BASE
    val KEY_HIDE_DOCK: String
        get() = if (AppConfig.IS_XPOSED) "launcher_hidden_dock_xposed" else "launcher_hidden_dock"
    const val KEY_PADDING_HOMEPAGE = "launcher_padding_homepage"
    const val KEY_PADDING_DOCK = "launcher_padding_dock"
    const val KEY_PADDING_SEARCH = "launcher_padding_search"
    const val KEY_PADDING_DOTS = "launcher_padding_dots"
    const val KEY_HOME_ENABLE_BASE = "launcher_homepage_sizer"
    val KEY_HOME_ENABLE: String
        get() = if (AppConfig.IS_XPOSED) "${KEY_HOME_ENABLE_BASE}_xposed" else KEY_HOME_ENABLE_BASE
    const val KEY_HOME_COLS = "launcher_homepage_h"
    const val KEY_HOME_ROWS = "launcher_homepage_v"
    const val KEY_HOME_HIDE_TEXT = "launcher_homepage_hide_text"
    const val KEY_MENU_ENABLE_BASE = "launcher_menupage_sizer"
    val KEY_MENU_ENABLE: String
        get() = if (AppConfig.IS_XPOSED) "${KEY_MENU_ENABLE_BASE}_xposed" else KEY_MENU_ENABLE_BASE
    const val KEY_MENU_COLS = "launcher_menupage_h"
    const val KEY_MENU_SEARCH_COLS = "launcher_menupage_search_h"
    const val KEY_MENU_HIDE_TEXT = "launcher_menupage_hide_text"
    const val KEY_MENU_ROW_HEIGHT = "launcher_menupage_row_height"
    const val KEY_DISABLE_TOP_WIDGET = "launcher_disable_top_widget"
    
    // Новый ключ для отключения Google Feed
    val KEY_DISABLE_GOOGLE_FEED: String
        get() = if (AppConfig.IS_XPOSED) "launcher_disable_google_feed_xposed" else "launcher_disable_google_feed"
        
    const val KEY_ICON_PACK = "launcher_current_icon_pack"
    val KEY_CLEAR_ALL_ENABLED: String
        get() = if (AppConfig.IS_XPOSED) "launcher_clear_all_xposed" else "launcher_clear_all"
    const val KEY_CLEAR_ALL_MODE = "launcher_replace_on_clear"
    const val KEY_CLEAR_ALL_MARGIN = "launcher_clear_all_bottom_margin"
    fun isNativeSearchEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_NATIVE_SEARCH, 1) == 1
    }
    suspend fun setNativeSearchEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Secure.putInt(context.contentResolver, KEY_NATIVE_SEARCH, if (enabled) 1 else 0)
        val cmdValue = if (enabled) "true" else "false"
        val command = "cmd device_config override launcher enable_one_search $cmdValue && am force-stop com.google.android.apps.nexuslauncher"
        runRootCommand(command)
    }
    suspend fun restartLauncher(context: Context) = withContext(Dispatchers.IO) {
        runRootCommand("am force-stop com.google.android.apps.nexuslauncher")
    }
    private fun runRootCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
            os.close()
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}

data class ClearAllMode(val id: Int, val labelRes: Int, val icon: ImageVector)