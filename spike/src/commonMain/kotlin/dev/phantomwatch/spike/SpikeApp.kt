package dev.phantomwatch.spike

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpikeApp() {
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("Phantom Watch - Compose/Wasm spike")
            Text("CMP 1.12.2 / Kotlin 2.4.20 / AGP 9.4.0")
        }
    }
}
