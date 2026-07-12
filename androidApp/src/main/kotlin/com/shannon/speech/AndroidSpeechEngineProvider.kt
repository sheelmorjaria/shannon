package com.shannon.speech

import com.shannon.appContext
import java.io.File

/**
 * Android [SpeechEngineProvider] (§2.1). Constructs a [SherpaSpeechEngine] whose model cache is
 * rooted at the app's internal files dir (`Context.getFilesDir()/sherpa`), satisfying the spec's
 * "Android Context.getFilesDir() for model cache" requirement.
 *
 * Registered in Koin to override the stub provider from `captionModule` (see ShannonApplication).
 */
class AndroidSpeechEngineProvider : SpeechEngineProvider {
    override fun create(config: SpeechConfig): SpeechEngine =
        SherpaSpeechEngine(File(appContext.filesDir, "sherpa"))
}
