package org.pixel.customparts.utils

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import org.pixel.customparts.R

object TileUtils {
    fun requestAddTileService(
        context: Context,
        tileServiceClass: Class<*>,
        labelResId: Int,
        iconResId: Int
    ) {
        requestAddTileService(
            context = context,
            tileServiceClass = tileServiceClass,
            label = context.getString(labelResId),
            iconResId = iconResId
        )
    }

    fun requestAddTileService(
        context: Context,
        tileServiceClass: Class<*>,
        label: String,
        iconResId: Int
    ) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as? StatusBarManager
        if (statusBarManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.tile_request_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val componentName = ComponentName(context, tileServiceClass)
        statusBarManager.requestAddTileService(
            componentName,
            label,
            Icon.createWithResource(context, iconResId),
            context.mainExecutor
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                requestTileRefresh(context, componentName)
            }

            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_not_added
                else -> R.string.tile_request_unavailable
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun requestTileRefresh(context: Context, tileServiceClass: Class<*>) {
        requestTileRefresh(context, ComponentName(context, tileServiceClass))
    }

    private fun requestTileRefresh(context: Context, componentName: ComponentName) {
        runCatching {
            TileService.requestListeningState(context, componentName)
        }
    }
}