package com.trancong.dexworkspacetouch.workspace.library.state

import java.util.UUID

fun interface WorkspaceClock {
    fun nowEpochMillis(): Long
}

fun interface WorkspaceIdGenerator {
    fun newId(): String
}

object SystemWorkspaceClock : WorkspaceClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

object UuidWorkspaceIdGenerator : WorkspaceIdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
