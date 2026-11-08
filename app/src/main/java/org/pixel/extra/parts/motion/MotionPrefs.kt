package org.pixel.extra.parts.motion

import android.content.Context
import android.util.Log
import java.io.File

// Persistent cache for the tuning panel: SharedPreferences keep values across
// restarts, and every save is mirrored to a plain JSON file in the app's
// external files dir (pullable via adb, no root needed) plus a logcat line.
object MotionPrefs {
    private const val TAG = "MotionTune"
    private const val FILE = "motion_tuning_prefs"
    const val EXPORT_NAME = "motion_settings.json"
    private const val KEY_PINNED_ORDER = "pinnedOrder"
    private const val KEY_OVERFLOW_ORDER = "overflowOrder"

    fun load(context: Context): MotionSettings {
        val d = MotionSettings()
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val gapCount = p.getInt("flightGaps_size", d.flightGaps.size)
        val gaps = (0 until gapCount).map { i ->
            p.getFloat("flightGap_$i", d.flightGaps.getOrElse(i) { 0.14f })
        }
        return d.copy(
            expandMs = p.getInt("expandMs", d.expandMs),
            collapseMs = p.getInt("collapseMs", d.collapseMs),
            widthPhaseEnd = p.getFloat("widthPhaseEnd", d.widthPhaseEnd),
            heightPhaseStart = p.getFloat("heightPhaseStart", d.heightPhaseStart),
            expansionPulsePct = p.getFloat("expansionPulsePct", d.expansionPulsePct),
            pressScalePct = p.getFloat("pressScalePct", d.pressScalePct),
            flightGaps = gaps,
            flightSpan = p.getFloat("flightSpan", d.flightSpan),
            arcBulgeBaseDp = p.getFloat("arcBulgeBaseDp", d.arcBulgeBaseDp),
            arcBulgeStepDp = p.getFloat("arcBulgeStepDp", d.arcBulgeStepDp),
            widthMorphStart = p.getFloat("widthMorphStart", d.widthMorphStart),
            labelRevealStart = p.getFloat("labelRevealStart", d.labelRevealStart),
            labelRevealEnd = p.getFloat("labelRevealEnd", d.labelRevealEnd),
            rowCornerDp = p.getFloat("rowCornerDp", d.rowCornerDp),
            collapseTextStart = p.getFloat("collapseTextStart", d.collapseTextStart),
            collapseTextEnd = p.getFloat("collapseTextEnd", d.collapseTextEnd),
            capsuleFadeStart = p.getFloat("capsuleFadeStart", d.capsuleFadeStart),
            capsuleFadeEnd = p.getFloat("capsuleFadeEnd", d.capsuleFadeEnd),
            iconMorphStart = p.getFloat("iconMorphStart", d.iconMorphStart),
            iconMorphEnd = p.getFloat("iconMorphEnd", d.iconMorphEnd),
            revealOvershoot = p.getFloat("revealOvershoot", d.revealOvershoot),
            riseOffsetDp = p.getFloat("riseOffsetDp", d.riseOffsetDp),
            dividerFadeStart = p.getFloat("dividerFadeStart", d.dividerFadeStart),
            dividerFadeEnd = p.getFloat("dividerFadeEnd", d.dividerFadeEnd),
            bottomLiftDp = p.getFloat("bottomLiftDp", d.bottomLiftDp),
            compactScalePct = p.getFloat("compactScalePct", d.compactScalePct),
            blurRadiusPx = p.getFloat("blurRadiusPx", d.blurRadiusPx),
            stripTapEater = p.getBoolean("stripTapEater", d.stripTapEater),
        )
    }

    fun save(context: Context, s: MotionSettings) {
        val e = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("expandMs", s.expandMs)
            .putInt("collapseMs", s.collapseMs)
            .putFloat("widthPhaseEnd", s.widthPhaseEnd)
            .putFloat("heightPhaseStart", s.heightPhaseStart)
            .putFloat("expansionPulsePct", s.expansionPulsePct)
            .putFloat("pressScalePct", s.pressScalePct)
            .putInt("flightGaps_size", s.flightGaps.size)
            .putFloat("flightSpan", s.flightSpan)
            .putFloat("arcBulgeBaseDp", s.arcBulgeBaseDp)
            .putFloat("arcBulgeStepDp", s.arcBulgeStepDp)
            .putFloat("widthMorphStart", s.widthMorphStart)
            .putFloat("labelRevealStart", s.labelRevealStart)
            .putFloat("labelRevealEnd", s.labelRevealEnd)
            .putFloat("rowCornerDp", s.rowCornerDp)
            .putFloat("collapseTextStart", s.collapseTextStart)
            .putFloat("collapseTextEnd", s.collapseTextEnd)
            .putFloat("capsuleFadeStart", s.capsuleFadeStart)
            .putFloat("capsuleFadeEnd", s.capsuleFadeEnd)
            .putFloat("iconMorphStart", s.iconMorphStart)
            .putFloat("iconMorphEnd", s.iconMorphEnd)
            .putFloat("revealOvershoot", s.revealOvershoot)
            .putFloat("riseOffsetDp", s.riseOffsetDp)
            .putFloat("dividerFadeStart", s.dividerFadeStart)
            .putFloat("dividerFadeEnd", s.dividerFadeEnd)
            .putFloat("bottomLiftDp", s.bottomLiftDp)
            .putFloat("compactScalePct", s.compactScalePct)
            .putFloat("blurRadiusPx", s.blurRadiusPx)
            .putBoolean("stripTapEater", s.stripTapEater)
        s.flightGaps.forEachIndexed { i, g -> e.putFloat("flightGap_$i", g) }
        e.apply()
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val json = toJsonWithOrders(
            s,
            readOrderCsv(prefs, KEY_PINNED_ORDER),
            readOrderCsv(prefs, KEY_OVERFLOW_ORDER),
        )
        try {
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                File(dir, EXPORT_NAME).writeText(json)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "export failed: " + ex.message)
        }
        Log.d(TAG, json)
    }

    fun toJson(s: MotionSettings): String {
        return "{" +
            "\"expandMs\":" + s.expandMs +
            ",\"collapseMs\":" + s.collapseMs +
            ",\"widthPhaseEnd\":" + s.widthPhaseEnd +
            ",\"heightPhaseStart\":" + s.heightPhaseStart +
            ",\"expansionPulsePct\":" + s.expansionPulsePct +
            ",\"pressScalePct\":" + s.pressScalePct +
            ",\"flightGaps\":[" + s.flightGaps.joinToString(",") + "]" +
            ",\"flightSpan\":" + s.flightSpan +
            ",\"arcBulgeBaseDp\":" + s.arcBulgeBaseDp +
            ",\"arcBulgeStepDp\":" + s.arcBulgeStepDp +
            ",\"widthMorphStart\":" + s.widthMorphStart +
            ",\"labelRevealStart\":" + s.labelRevealStart +
            ",\"labelRevealEnd\":" + s.labelRevealEnd +
            ",\"rowCornerDp\":" + s.rowCornerDp +
            ",\"collapseTextStart\":" + s.collapseTextStart +
            ",\"collapseTextEnd\":" + s.collapseTextEnd +
            ",\"capsuleFadeStart\":" + s.capsuleFadeStart +
            ",\"capsuleFadeEnd\":" + s.capsuleFadeEnd +
            ",\"iconMorphStart\":" + s.iconMorphStart +
            ",\"iconMorphEnd\":" + s.iconMorphEnd +
            ",\"revealOvershoot\":" + s.revealOvershoot +
            ",\"riseOffsetDp\":" + s.riseOffsetDp +
            ",\"dividerFadeStart\":" + s.dividerFadeStart +
            ",\"dividerFadeEnd\":" + s.dividerFadeEnd +
            ",\"bottomLiftDp\":" + s.bottomLiftDp +
            ",\"compactScalePct\":" + s.compactScalePct +
            ",\"blurRadiusPx\":" + s.blurRadiusPx +
            ",\"stripTapEater\":" + s.stripTapEater +
            "}"
    }

    // Additive dock-order persistence (editor mode): CSV strings of entry keys.
    // Unknown/new keys are appended deterministically at the end, so a fresh
    // install or a new section never loses content.
    fun loadDockOrder(
        context: Context,
        defaultPinned: List<String>,
        defaultOverflow: List<String>,
    ): Pair<List<String>, List<String>> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val savedPinned = readOrderCsv(prefs, KEY_PINNED_ORDER)
        val savedOverflow = readOrderCsv(prefs, KEY_OVERFLOW_ORDER)
        // Saved placements own their zone: filter the other zone's stale defaults so a
        // moved key is not resurrected in both lists after a restart. Unknown/new keys
        // (absent from both saved lists) still append deterministically via mergeOrder.
        return Pair(
            mergeOrder(savedPinned, defaultPinned.filter { it !in savedOverflow }),
            mergeOrder(savedOverflow, defaultOverflow.filter { it !in savedPinned }),
        )
    }

    fun saveDockOrder(context: Context, pinned: List<String>, overflow: List<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PINNED_ORDER, pinned.joinToString(","))
            .putString(KEY_OVERFLOW_ORDER, overflow.joinToString(","))
            .apply()
        // Mirror into the adb-readable JSON export without touching existing keys.
        try {
            val json = toJsonWithOrders(load(context), pinned, overflow)
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                File(dir, EXPORT_NAME).writeText(json)
            }
            Log.d(TAG, json)
        } catch (ex: Exception) {
            Log.w(TAG, "order export failed: " + ex.message)
        }
    }

    fun toJsonWithOrders(s: MotionSettings, pinned: List<String>, overflow: List<String>): String {
        val base = toJson(s).trimEnd()
        val trimmed = if (base.endsWith("}")) base.dropLast(1) else base
        return trimmed +
            ",\"pinnedOrder\":[" + pinned.joinToString(",") { "\"" + it + "\"" } + "]" +
            ",\"overflowOrder\":[" + overflow.joinToString(",") { "\"" + it + "\"" } + "]" +
            "}"
    }

    private fun readOrderCsv(
        prefs: android.content.SharedPreferences,
        key: String,
    ): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun mergeOrder(saved: List<String>, defaults: List<String>): List<String> {
        if (saved.isEmpty()) return defaults.toList()
        // Keep saved order first; append defaults entries missing from storage
        // (new keys), then any stored keys not in defaults (forward safety).
        val seen = LinkedHashSet<String>()
        val out = ArrayList<String>(saved.size + defaults.size)
        for (k in saved) {
            if (seen.add(k)) out.add(k)
        }
        for (k in defaults) {
            if (seen.add(k)) out.add(k)
        }
        return out
    }
}
