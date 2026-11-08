package org.pixel.extra.parts.addons

// Static addon catalog for the hub. Entries are compile-time descriptors;
// runtime discovery of addon JARs (scan + metadata bridge) is a host-side
// contract and is deliberately out of scope here.
object AddonsRegistry {
    val addons: List<AddonDescriptor> = listOf(SystemUiHooksAddon.descriptor)

    // Resolves a hub route of the form "addonId/pageId" to its pair.
    fun findPage(route: String): Pair<AddonDescriptor, AddonPage>? {
        val slash = route.indexOf('/')
        if (slash <= 0) return null
        val addon = addons.firstOrNull { it.id == route.substring(0, slash) } ?: return null
        val page = addon.pages.firstOrNull { it.id == route.substring(slash + 1) } ?: return null
        return addon to page
    }
}
