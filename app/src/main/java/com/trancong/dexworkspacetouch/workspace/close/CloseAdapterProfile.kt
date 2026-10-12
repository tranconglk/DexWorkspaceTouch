package com.trancong.dexworkspacetouch.workspace.close

enum class CloseAdapterProfile(val sdk: Int, val fingerprint: String) {
    NOTE9_API29(29, "samsung/crownltexx/crownlte:10/QP1A.190711.020/N960FXXU9FUK2:user/release-keys"),
    S23_API36(36, "samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZI1:user/release-keys");
    companion object {
        fun select(sdk: Int, fingerprint: String): CloseAdapterProfile = entries.firstOrNull { it.sdk == sdk && it.fingerprint == fingerprint }
            ?: throw CloseBackendException("UNSUPPORTED_ADAPTER")
    }
}
