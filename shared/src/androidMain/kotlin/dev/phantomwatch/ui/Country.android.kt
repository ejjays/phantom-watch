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
    val sim = LocalContext.current.getSystemService(TelephonyManager::class.java)?.networkCountryIso
    val code = sim?.uppercase()?.takeIf { it.length == 2 }
        ?: Locale.getDefault().country.uppercase().takeIf { it.length == 2 }
        ?: return Country("", "")
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    return Country(code, if (code in Articled) "the $name" else name)
}
