package dev.phantomwatch.ui

import android.telephony.TelephonyManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
actual fun deviceCountry(): Country {
    val sim = LocalContext.current.getSystemService(TelephonyManager::class.java)?.networkCountryIso
    val code = sim?.uppercase()?.takeIf { it.length == 2 }
        ?: Locale.getDefault().country.uppercase().takeIf { it.length == 2 }
        ?: return Country("", "")
    val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    return Country(code, name)
}
