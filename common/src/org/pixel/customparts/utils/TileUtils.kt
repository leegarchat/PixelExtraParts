package org.pixel.customparts.utils

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import org.pixel.customparts.R

object TileUtils {
    fun requestAddTileService(
        context: Context,
        tileServiceClass: Class<*>,
        labelResId: Int,
        iconResId: Int
    ) {
        val statusBarManager = context.getSystemService(Context.STATUS_BAR_SERVICE) as? StatusBarManager
        if (statusBarManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.tile_request_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        statusBarManager.requestAddTileService(
            ComponentName(context, tileServiceClass),
            context.getString(labelResId),
            Icon.createWithResource(context, iconResId),
            context.mainExecutor
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> R.string.tile_not_added
                else -> R.string.tile_request_unavailable
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}