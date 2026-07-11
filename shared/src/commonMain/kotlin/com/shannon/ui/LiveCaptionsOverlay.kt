package com.shannon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shannon.viewmodel.CaptionViewModel

/** Languages offered in the source/target pickers (task 5.2). */
private val LANGUAGES = listOf("en", "es", "fr", "de", "hi", "zh", "ar", "pt")

/**
 * Live-captions overlay for the call surface (tasks 5.1–5.4). Renders received captions,
 * language pickers, and the captions / speak-translations toggles. Shows a "model unavailable"
 * state when the on-device engine is not ready (graceful degradation, 5.4).
 */
@Composable
fun LiveCaptionsOverlay(
    viewModel: CaptionViewModel,
    modifier: Modifier = Modifier,
) {
    val captionsEnabled by viewModel.captionsEnabled.collectAsState()
    val speak by viewModel.speakTranslations.collectAsState()
    val sourceLang by viewModel.sourceLang.collectAsState()
    val targetLang by viewModel.targetLang.collectAsState()
    val captions by viewModel.captions.collectAsState()
    val engineAvailable = viewModel.isEngineAvailable

    Surface(modifier = modifier, tonalElevation = 4.dp) {
        Column(Modifier.padding(12.dp)) {
            if (!engineAvailable) {
                Text(
                    "Live captions unavailable — download a speech model to enable.",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }

            // Toggles (5.3)
            ToggleRow(captionsEnabled, viewModel::setCaptionsEnabled, "Live Captions")
            ToggleRow(speak, viewModel::setSpeakTranslations, "Speak Translations")

            Spacer(Modifier.height(8.dp))

            // Language pickers (5.2)
            Text("Source", style = MaterialTheme.typography.labelSmall)
            LanguageChips(selected = sourceLang, includeAuto = true, onSelect = viewModel::setSourceLang)
            Text("Target", style = MaterialTheme.typography.labelSmall)
            LanguageChips(selected = targetLang, includeAuto = false, onSelect = viewModel::setTargetLang)

            Spacer(Modifier.height(8.dp))

            // Captions (5.1)
            if (captionsEnabled) {
                val recent = captions.takeLast(5)
                if (recent.isEmpty()) {
                    Text("Waiting for speech…", style = MaterialTheme.typography.bodySmall)
                }
                recent.forEach { c ->
                    Text(
                        text = c.translated ?: c.text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun LanguageChips(selected: String?, includeAuto: Boolean, onSelect: (String?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (includeAuto) {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("Auto") })
        }
        LANGUAGES.forEach { lang ->
            FilterChip(selected = selected == lang, onClick = { onSelect(lang) }, label = { Text(lang) })
        }
    }
}
