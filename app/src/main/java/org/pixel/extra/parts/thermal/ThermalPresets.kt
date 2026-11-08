package org.pixel.extra.parts.thermal

import androidx.annotation.StringRes
import org.pixel.extra.parts.R

// Throttling levels for one thermal axis. Offsets mirror the legacy
// thermal_info_config_* preset naming (soft +5, medium +9, hard +15, off +90).
enum class ThermalLevel(
    val key: String,
    @StringRes val labelRes: Int,
    val offsetC: Int,
) {
    STOCK("stock", R.string.thermal_level_stock, 0),
    SOFT("soft", R.string.thermal_level_soft, 5),
    MEDIUM("medium", R.string.thermal_level_medium, 9),
    HARD("hard", R.string.thermal_level_hard, 15),
    OFF("off", R.string.thermal_level_off, 90);

    val isRisky: Boolean
        get() = this == HARD || this == OFF

    companion object {
        fun fromKey(key: String?): ThermalLevel =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: STOCK
    }
}

// The two independent thermal axes of the legacy preset scheme.
enum class ThermalAxis(
    val prefix: String,
    @StringRes val labelRes: Int,
) {
    BATTERY("battery", R.string.thermal_axis_battery),
    SOC("soc", R.string.thermal_axis_soc),
}

// One combined preset: a battery level crossed with a SoC level. The id is
// the legacy config file name so the HAL keeps resolving it without any
// file handling on the app side: stock maps to thermal_info_config.json,
// anything else to battery_<b>_soc_<s>.json (either side omitted when stock).
data class ThermalPreset(
    val battery: ThermalLevel,
    val soc: ThermalLevel,
) {
    val id: String
        get() {
            if (battery == ThermalLevel.STOCK && soc == ThermalLevel.STOCK) {
                return ThermalKeys.STOCK_CONFIG_VALUE
            }
            return buildString {
                if (battery != ThermalLevel.STOCK) {
                    append(ThermalAxis.BATTERY.prefix).append('_').append(battery.key)
                }
                if (soc != ThermalLevel.STOCK) {
                    if (isNotEmpty()) append('_')
                    append(ThermalAxis.SOC.prefix).append('_').append(soc.key)
                }
                append(".json")
            }
        }

    val isRisky: Boolean
        get() = battery.isRisky || soc.isRisky

    companion object {
        fun fromId(id: String): ThermalPreset {
            val name = id.trim().substringAfterLast('/').lowercase()
            if (name.isEmpty() ||
                name == ThermalKeys.STOCK_CONFIG_ID ||
                name == ThermalKeys.STOCK_CONFIG_VALUE
            ) {
                return ThermalPreset(ThermalLevel.STOCK, ThermalLevel.STOCK)
            }
            val battery = Regex("(?:^|_)battery_(stock|soft|medium|hard|off)(?:_|\\.)")
                .find(name)?.groupValues?.getOrNull(1)
                .let(ThermalLevel::fromKey)
            val soc = Regex("(?:^|_)soc_(stock|soft|medium|hard|off)(?:_|\\.)")
                .find(name)?.groupValues?.getOrNull(1)
                .let(ThermalLevel::fromKey)
            return ThermalPreset(battery, soc)
        }
    }
}
