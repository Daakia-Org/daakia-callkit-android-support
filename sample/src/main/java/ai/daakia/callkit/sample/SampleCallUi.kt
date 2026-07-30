package ai.daakia.callkit.sample

import ai.daakia.callkit.sample.config.CallUiModule
import ai.daakia.callkit.ui.IncomingCallStyle
import ai.daakia.callkit.ui.compose.DaakiaIncomingCallUi as ComposeCallUi
import ai.daakia.callkit.ui.views.DaakiaIncomingCallUi as ViewsCallUi

/**
 * Registers the SDK's full-screen incoming-call screen for a given [CallUiModule] + style.
 *
 * Only one module can be installed at a time (each registers its own Activity), so switching
 * modules re-registers. Real incoming calls and the in-app preview both go through here so they
 * always show the same screen.
 */
object SampleCallUi {
    fun install(
        module: CallUiModule,
        style: IncomingCallStyle,
    ) {
        when (module) {
            CallUiModule.COMPOSE -> ComposeCallUi.install(style = style)
            CallUiModule.VIEWS -> ViewsCallUi.install(style = style)
        }
    }
}
