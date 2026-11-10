package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.TopStatusVeil
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.SliderMorphRoot
import org.pixel.customparts.ui.rememberSliderMorphHostState
import org.pixel.customparts.ui.addons.AddonManagerSection
import androidx.compose.ui.res.stringResource

class AddonManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = androidx.compose.ui.platform.LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AddonManagerScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddonManagerScreen(onBack: () -> Unit) {
    val blurState = rememberGraphicsLayerRecordingState()
    // Morph-time header blur (scroll+modal) + hoisted morph host so the header
    // tint boost runs at exactly the modal expand speed.
    val morphBlurState = rememberGraphicsLayerRecordingState()
    val morphHost = rememberSliderMorphHostState()
    val lazyListState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.addon_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.nav_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        // Parent for the header overlays: they must stay OUTSIDE SliderMorphRoot.
        // The morph-time header blur is baked from the Root's subtree — a header
        // reading it from inside would reference itself (RenderNode cycle = SIGSEGV).
        Box(modifier = Modifier.fillMaxSize()) {
        SliderMorphRoot(
            host = morphHost,
            blurState = blurState,
            morphBlurState = morphBlurState,
            morphBackgroundColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(state = blurState, backgroundColor = MaterialTheme.colorScheme.surfaceContainer)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AddonManagerSection()
                }
            }

        } // end SliderMorphRoot content — header below stays outside the recorded subtree

        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = innerPadding.calculateTopPadding(),
            blurState = blurState,
            morphBlurState = morphBlurState,
            morphActive = morphHost.request != null,
            tintBoost = 0.25f * morphHost.morphProgress
        )
        TopStatusVeil(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            veilHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }
}
