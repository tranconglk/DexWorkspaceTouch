package com.trancong.dexworkspacetouch.about.presentation

import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferFormat
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceDatabaseMetadata
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferFormat

object ReleaseFormatVersions {
    const val Database = WorkspaceDatabaseMetadata.Version
    const val WorkspaceTransfer = WorkspaceTransferFormat.FormatVersion
    const val LibraryBundle = WorkspaceLibraryTransferFormat.FormatVersion
}
