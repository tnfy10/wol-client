package xyz.myeoru.wolclient

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow

@Composable
fun App(
    window: ComposeWindow
) {
    MaterialTheme {
        WolScreen(
            window = window
        )
    }
}