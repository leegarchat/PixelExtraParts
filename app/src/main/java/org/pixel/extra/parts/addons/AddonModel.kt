package org.pixel.extra.parts.addons

import androidx.annotation.StringRes

// Declarative addon UI model. An addon contributes static descriptors only:
// every user-visible label is a string resource, every persisted value is a
// Settings.Global int addressed by a plain key (no engine suffixes).
// The host never loads addon code; it only renders these nodes with the
// shared ui kit (see AddonEngine) and persists values via AddonStore.
sealed interface AddonNode {
    // On/off knob rendered with SettingSwitchRow.
    data class Toggle(
        val key: String,
        @StringRes val titleRes: Int,
        @StringRes val summaryRes: Int? = null,
        val default: Boolean = false,
    ) : AddonNode

    // Integer knob rendered with SettingSlider (step buttons + reset badge).
    data class Slider(
        val key: String,
        @StringRes val titleRes: Int,
        val min: Int,
        val max: Int,
        val step: Int,
        val default: Int,
        val unit: AddonSliderUnit = AddonSliderUnit.RAW,
    ) : AddonNode {
        init {
            require(min <= default && default <= max) { "Default out of range for $key" }
            require(step > 0) { "Step must be positive for $key" }
        }
    }

    // One-of-N knob rendered with ExpressiveSegmentedRow.
    data class Segment(
        val key: String,
        @StringRes val titleRes: Int,
        val options: List<SegmentChoice>,
        val default: Int = options.firstOrNull()?.value ?: 0,
    ) : AddonNode {
        init {
            require(options.isNotEmpty()) { "Segment $key needs options" }
        }
    }

    // Titled container for child nodes. Collapsible groups use
    // ExpandableGroupCard, static ones use GroupCard.
    data class Group(
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int? = null,
        val collapsible: Boolean = false,
        val children: List<AddonNode> = emptyList(),
    ) : AddonNode
}

// Single choice of a Segment node: persisted int value plus static label.
data class SegmentChoice(
    val value: Int,
    @StringRes val labelRes: Int,
)

// Value formatting for Slider nodes; labels reuse shared format resources.
enum class AddonSliderUnit { RAW, PERCENT, MS, SEC, DP }

// One settings page of an addon: header labels plus a flat node list.
// Groups inside the list provide their own cards.
data class AddonPage(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val nodes: List<AddonNode> = emptyList(),
)

// One addon entry of the hub: static identity plus its settings pages.
data class AddonDescriptor(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val pages: List<AddonPage> = emptyList(),
)
