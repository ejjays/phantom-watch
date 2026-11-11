package dev.phantomwatch.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun deviceCountry(): Country {
    val code = window.navigator.language.substringAfter('-', "").uppercase()
    return if (code.length == 2) Country(code, code) else Country("", "")
}
