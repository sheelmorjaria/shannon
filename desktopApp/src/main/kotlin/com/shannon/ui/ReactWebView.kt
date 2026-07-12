package com.shannon.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView

/**
 * Embeds the Shannon React UI (a built index.html) in the Compose Desktop window using a JavaFX
 * [WebView], bridged into Compose via [SwingPanel]/[JFXPanel]. Task §4.2.
 *
 * Runtime requires a display + the JavaFX platform natives (bundled in the classifier'd jars).
 * Build the UI with VITE_BRIDGE_URL=ws://127.0.0.1:47329/bridge so it connects to the bridge.
 */
@Composable
fun ReactWebView(url: String, modifier: Modifier = Modifier) {
    SwingPanel(
        factory = {
            JFXPanel().also { panel ->
                Platform.runLater {
                    val webView = WebView()
                    webView.engine.load(url)
                    panel.scene = Scene(webView)
                }
            }
        },
        modifier = modifier,
    )
}
