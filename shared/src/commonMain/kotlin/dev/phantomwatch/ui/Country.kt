package dev.phantomwatch.ui

import androidx.compose.runtime.Composable

data class Country(val code: String, val name: String)

// the sim's country, not the ui locale: an en-gb phone in manila should still get ph content
@Composable
expect fun deviceCountry(): Country
