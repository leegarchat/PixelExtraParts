package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.addons.AddonPageScreen

/**
 * Activity that hosts the generated UI for a single addon's "main" page tree.
 *
 * Launch via [AddonPageActivity.start]:
 *   AddonPageActivity.start(context, addonId = "my_addon")
 *   AddonPageActivity.start(context, addonId = "my_addon", pageId = "my-settings")
 *
 * Extras:
 *   EXTRA_ADDON_ID  — required: the addon's id field from addon.json
 *   EXTRA_PAGE_ID   — optional: leafId of the page to open directly (skips root list)
 *   EXTRA_TITLE     — optional: override the top-bar title (defaults to addonId)
 */
class AddonPageActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ADDON_ID = "addon_id"
        const val EXTRA_PAGE_ID  = "page_id"
        const val EXTRA_TITLE    = "title"

        fun start(
            context: Context,
            addonId: String,
            pageId: String? = null,
            title: String? = null
        ) {
            val intent = Intent(context, AddonPageActivity::class.java).apply {
                putExtra(EXTRA_ADDON_ID, addonId)
                if (pageId != null) putExtra(EXTRA_PAGE_ID, pageId)
                if (title != null) putExtra(EXTRA_TITLE, title)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val addonId = intent.getStringExtra(EXTRA_ADDON_ID) ?: run {
            finish()
            return
        }
        val pageId = intent.getStringExtra(EXTRA_PAGE_ID)

        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AddonPageScreen(
                        addonId = addonId,
                        pageId = pageId,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
