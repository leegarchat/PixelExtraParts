package org.pixel.extra.parts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import org.pixel.extra.parts.home.HomeScreen
import org.pixel.extra.parts.settings.SettingsScreen
import org.pixel.extra.parts.ui.AppExpressiveTheme
import org.pixel.extra.parts.ui.BottomBlurBar
import org.pixel.extra.parts.ui.DockStrip
import org.pixel.extra.parts.ui.ExpressiveFloatingDock
import org.pixel.extra.parts.ui.TopBarBlurOverlay
import org.pixel.extra.parts.ui.TopStatusVeil
import org.pixel.extra.parts.ui.recordLayer
import org.pixel.extra.parts.ui.rememberGraphicsLayerRecordingState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge via WindowCompat; icon contrast is synced in AppExpressiveTheme.
        WindowCompat.enableEdgeToEdge(window)
        setContent {
            AppExpressiveTheme {
                PepRoot()
            }
        }
    }
}

private enum class PepTab(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    Home(R.drawable.ic_home, R.string.tab_home),
    Settings(R.drawable.ic_tune, R.string.tab_settings),
}

private const val BLUR_RADIUS = 20f
private val TOP_BAR_HEIGHT = 64.dp

@Composable
private fun PepRoot() {
    var tab by rememberSaveable { mutableStateOf(PepTab.Home) }
    val blurState = rememberGraphicsLayerRecordingState()
    val rootBg = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(rootBg)
    ) {
        // Main scrollable content: recorded once per frame for the shared blur.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .recordLayer(
                    state = blurState,
                    backgroundColor = rootBg,
                    blurRadius = BLUR_RADIUS,
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(TOP_BAR_HEIGHT + 8.dp))
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> (fullWidth * 0.18f * direction).toInt() }
                        ) + fadeIn(
                            animationSpec = tween(260)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.18f * direction).toInt() }
                            ) + fadeOut(
                                animationSpec = tween(200)
                            ) + scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(260, easing = FastOutSlowInEasing)
                            )
                        ).using(SizeTransform(clip = false))
                    },
                    label = "ScreenTransition",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                ) { current ->
                    when (current) {
                        PepTab.Home -> HomeScreen()
                        PepTab.Settings -> SettingsScreen()
                    }
                }
                // Clearance above the floating dock (guideline 128-140.dp zone).
                Spacer(modifier = Modifier.height(132.dp))
            }
        }

        // Floating overlays: siblings OUTSIDE recordLayer (no render loop).
        TopStatusVeil(
            backgroundColor = rootBg,
            veilHeight = 32.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = TOP_BAR_HEIGHT,
                blurState = blurState,
                blurRadius = BLUR_RADIUS,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp),
            )
        }
        DockStrip(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ExpressiveFloatingDock(
                tabs = PepTab.entries,
                selectedTab = tab,
                onSelectTab = { tab = it },
                iconProvider = { painterResource(it.iconRes) },
                labelProvider = { stringResource(it.labelRes) },
                blurState = blurState,
                blurRadius = BLUR_RADIUS,
            )
        }
    }
}
