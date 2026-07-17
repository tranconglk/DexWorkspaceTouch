package com.trancong.dexworkspacetouch.platform.launch.android

data class LaunchSequencingPolicy(
    val delayBetweenTargetsMs: Long = DEFAULT_DELAY_BETWEEN_TARGETS_MS,
) {
    init {
        require(delayBetweenTargetsMs in MIN_DELAY_MS..MAX_DELAY_MS) {
            "delayBetweenTargetsMs must be between $MIN_DELAY_MS and $MAX_DELAY_MS"
        }
    }

    private companion object {
        const val DEFAULT_DELAY_BETWEEN_TARGETS_MS = 400L
        const val MIN_DELAY_MS = 0L
        const val MAX_DELAY_MS = 5_000L
    }
}
