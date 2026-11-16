package dev.phantomwatch.ui

import androidx.compose.runtime.Composable

data class Country(val code: String, val name: String)

// english names that take an article, rail reads "in the Philippines" not "in Philippines"
private val Articled = setOf(
    "AE", "BS", "CD", "CF", "CG", "CZ", "DO", "FK", "GB", "GM", "IM", "KM",
    "KY", "MH", "MP", "MV", "NL", "PH", "SB", "SC", "TC", "US", "VA", "VG", "VI",
)

internal fun countryName(code: String, name: String): String = if (code in Articled) "the $name" else name

// sim country not ui locale, en gb phone in manila still gets ph
@Composable
expect fun deviceCountry(): Country

// where a host without its own geo route asks for visitor country, blank means this origin
object Geo {
    var url: String = ""
}
