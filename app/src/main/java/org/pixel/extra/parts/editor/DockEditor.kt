package org.pixel.extra.parts.editor

// Pure editor-mode list rules for the expanded dock (stage 1).
// Two zones: PINNED favorites (1..4 entries) and NORMAL overflow items.
// The expanded list is one unified vertical stack: overflow rows on top,
// pinned rows below them, one slot per entry.
object DockEditor {
    const val MIN_PINNED = 1
    const val MAX_PINNED = 4

    enum class Zone { PINNED, OVERFLOW }

    data class DropResult(
        val pinned: List<String>,
        val overflow: List<String>,
    )

    fun orderKeys(
        entries: List<String>,
        keyOrder: List<String>,
    ): List<String> {
        if (keyOrder.isEmpty()) return entries.toList()
        val rank = keyOrder.withIndex().associate { it.value to it.index }
        return entries.sortedWith(
            compareBy(
                { rank[it] ?: Int.MAX_VALUE },
                { entries.indexOf(it) },
            ),
        )
    }

    // Applies a drop of [dragKey] onto unified slot [hoverSlot].
    // Unified slots: 0..overflow.size-1 = overflow, overflow.size.. = pinned.
    // Returns null when the drop is invalid (no state change, caller rejects).
    fun applyDrop(
        pinned: List<String>,
        overflow: List<String>,
        dragKey: String,
        hoverSlot: Int,
    ): DropResult? {
        val fromPinned = pinned.contains(dragKey)
        val fromOverflow = overflow.contains(dragKey)
        if (!fromPinned && !fromOverflow) return null
        val total = overflow.size + pinned.size
        val slot = hoverSlot.coerceIn(0, (total - 1).coerceAtLeast(0))

        if (fromPinned && fromOverflow) return null

        if (fromPinned) {
            val fromIdx = pinned.indexOf(dragKey)
            val inPinnedSlot = slot >= overflow.size
            if (inPinnedSlot) {
                // Reorder inside favorites.
                val toIdx = (slot - overflow.size).coerceIn(0, pinned.size - 1)
                if (toIdx == fromIdx) return DropResult(pinned.toList(), overflow.toList())
                val next = pinned.toMutableList()
                next.removeAt(fromIdx)
                next.add(toIdx.coerceIn(0, next.size), dragKey)
                return DropResult(next, overflow.toList())
            }
            // Demote into the overflow area: only if favorites stay >= 1.
            if (pinned.size <= MIN_PINNED) return null
            val nextPinned = pinned.toMutableList()
            nextPinned.removeAt(fromIdx)
            val nextOverflow = overflow.toMutableList()
            // Map the unified slot to an overflow insert position.
            // Slots above the overflow block clamp to its edges.
            val toIdx = slot.coerceIn(0, nextOverflow.size)
            nextOverflow.add(toIdx, dragKey)
            return DropResult(nextPinned, nextOverflow)
        } else {
            val fromIdx = overflow.indexOf(dragKey)
            val inPinnedSlot = slot >= overflow.size
            if (!inPinnedSlot) {
                // Reorder inside the overflow list.
                val toIdx = slot.coerceIn(0, overflow.size - 1)
                if (toIdx == fromIdx) return DropResult(pinned.toList(), overflow.toList())
                val next = overflow.toMutableList()
                next.removeAt(fromIdx)
                next.add(toIdx.coerceIn(0, next.size), dragKey)
                return DropResult(pinned.toList(), next)
            }
            // Promote a normal item to a favorite slot.
            val toIdx = (slot - overflow.size).coerceIn(0, pinned.size)
            if (pinned.size < MAX_PINNED) {
                val nextPinned = pinned.toMutableList()
                nextPinned.add(toIdx.coerceIn(0, nextPinned.size), dragKey)
                val nextOverflow = overflow.toMutableList()
                nextOverflow.removeAt(fromIdx)
                return DropResult(nextPinned, nextOverflow)
            }
            // Favorites full (== 4): one-in + first-out. The first favorite
            // flies to the dragged item's old list place.
            val nextPinned = pinned.toMutableList()
            val evicted = nextPinned.removeAt(0)
            nextPinned.add(toIdx.coerceIn(0, nextPinned.size), dragKey)
            val nextOverflow = overflow.toMutableList()
            nextOverflow.removeAt(fromIdx)
            nextOverflow.add(fromIdx.coerceIn(0, nextOverflow.size), evicted)
            return DropResult(nextPinned, nextOverflow)
        }
    }
}
