package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState

class PackageManagerAppIconLoader(
    packageManagerAdapter: PackageManagerAdapter,
    maximumEntries: Int = DEFAULT_CACHE_ENTRIES,
) : AppIconLoader {
    private val cache = SafeCachedLoader(
        maximumEntries = maximumEntries,
        fallback = AppIconState.Fallback,
    ) { identity: AppIdentity ->
        packageManagerAdapter.loadIcon(identity)
            .toBitmap()
            .asImageBitmap()
            .let(AppIconState::Ready)
    }

    override fun loadIcon(identity: AppIdentity): AppIconState =
        cache.load(identity)

    companion object {
        fun create(context: Context): PackageManagerAppIconLoader =
            PackageManagerAppIconLoader(PackageManagerAdapter(context.packageManager))

        private const val DEFAULT_CACHE_ENTRIES = 96
    }
}
