package dev.phantomwatch.ui

import androidx.compose.runtime.Composable

data class Country(val code: String, val name: String)

// sim country not ui locale, en gb phone in manila still gets ph
@Composable
expect fun deviceCountry(): Country
