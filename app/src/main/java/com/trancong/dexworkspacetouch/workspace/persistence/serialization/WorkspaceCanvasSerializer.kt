package com.trancong.dexworkspacetouch.workspace.persistence.serialization

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

interface WorkspaceCanvasSerializer {
    fun encode(canvas: WorkspaceCanvas): String
    fun decode(json: String, schemaVersion: Int): WorkspaceCanvas
}
