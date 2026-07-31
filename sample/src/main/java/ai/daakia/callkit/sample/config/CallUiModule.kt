package ai.daakia.callkit.sample.config

/** Which SDK UI module renders the full-screen incoming-call (lock-screen) screen. */
enum class CallUiModule {
    /** `callkit-ui-compose` — Jetpack Compose call screens. */
    COMPOSE,

    /** `callkit-ui-views` — classic XML Views call screens. */
    VIEWS,
}
