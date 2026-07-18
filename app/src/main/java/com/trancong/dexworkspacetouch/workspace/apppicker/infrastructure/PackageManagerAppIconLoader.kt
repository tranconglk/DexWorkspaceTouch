package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey

class PackageManagerAppIconLoader(
    packageManagerAdapter: PackageManagerAdapter,
    maximumEntries: Int = APP_ICON_CACHE_ENTRIES,
) : AppIconLoader {
    private val cache = SafeCachedLoader(
        maximumEntries = maximumEntries,
        fallback = AppIconState.Fallback,
    ) { identityKey: String ->
        val separator = identityKey.indexOf(KEY_SEPARATOR)
        val identity = AppIdentity(
            packageName = identityKey.substring(0, separator),
            activityName = identityKey.substring(separator + 1).ifEmpty { null },
        )
        packageManagerAdapter.loadIcon(identity)
            .toBitmap()
            .asImageBitmap()
            .let(AppIconState::Ready)
    }

    override fun loadIcon(identity: AppIdentity): AppIconState =
        cache.load(identity.toStableKey())

    companion object {
        fun create(context: Context): PackageManagerAppIconLoader =
            PackageManagerAppIconLoader(PackageManagerAdapter(context.packageManager))

        private const val KEY_SEPARATOR = "#"
    }
}

internal const val APP_ICON_CACHE_ENTRIES = 64
