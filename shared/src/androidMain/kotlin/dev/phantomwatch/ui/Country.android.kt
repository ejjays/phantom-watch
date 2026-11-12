package dev.phantomwatch.ui

import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

// english names that take a definite article, so a rail reads "in the Philippines" and not "in Philippines"
private val Articled = setOf(
    "AE", "BS", "CD", "CF", "CG", "CZ", "DO", "FK", "GB", "GM", "IM", "KM",
    "KY", "MH", "MP", "MV", "NL", "PH", "SB", "SC", "TC", "US", "VA", "VG", "VI",
)

@Composable
actual fun deviceCountry(): Country {
    val tm = LocalContext.current.getSystemService(TelephonyManager::class.java)
    // a roaming or unprovisioned sim reports no country, but the carrier id still names it
    val code = sequenceOf(
        tm?.networkCountryIso,
        tm?.simCountryIso,
        tm?.simOperator?.takeIf { it.length >= 5 }?.let { it.substring(0, 3) }
            ?.toIntOrNull()?.let { mcc -> McCountry[mcc] },
        Locale.getDefault().country,
    ).mapNotNull { it?.uppercase()?.takeIf { c -> c.length == 2 } }.firstOrNull()
        ?: return Country("", "")
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    return Country(code, if (code in Articled) "the $name" else name)
}

private val McCountry = mapOf(515 to "PH", 310 to "US", 302 to "CA", 214 to "ES", 262 to "DE", 234 to "GB")
