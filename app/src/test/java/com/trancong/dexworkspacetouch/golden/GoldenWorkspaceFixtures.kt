package com.trancong.dexworkspacetouch.golden

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.assignApp

object GoldenWorkspaceFixtures {
    val mapsIdentity = AppIdentity("golden.maps", "golden.maps.Main")
    val musicIdentity = AppIdentity("golden.music", "golden.music.Main")
    val missingIdentity = AppIdentity("golden.missing", "golden.missing.Main")

    val mapsInstalled = InstalledApp(mapsIdentity.packageName, mapsIdentity.activityName, "Golden Maps", true)
    val musicInstalled = InstalledApp(musicIdentity.packageName, musicIdentity.activityName, "Golden Music", true)
    val installedApps = listOf(mapsInstalled, musicInstalled)

    val mapsAssigned = AssignedApp(mapsIdentity.packageName, mapsIdentity.activityName, "Golden Maps")
    val musicAssigned = AssignedApp(musicIdentity.packageName, musicIdentity.activityName, "Golden Music")
    val missingAssigned = AssignedApp(missingIdentity.packageName, missingIdentity.activityName, "Missing")

    fun oneEmpty(): WorkspaceCanvas = WorkspaceCanvas.singleCell()

    fun twoCells(): WorkspaceCanvas = WorkspaceCanvasEditor().splitCell(
        oneEmpty(), "cell", SplitDirection.VERTICAL,
    )

    fun fourCells(): WorkspaceCanvas {
        val editor = WorkspaceCanvasEditor()
        var canvas = twoCells()
        canvas = editor.splitCell(canvas, "cell_a", SplitDirection.HORIZONTAL)
        return editor.splitCell(canvas, "cell_b", SplitDirection.HORIZONTAL)
    }

    fun nestedValid(): WorkspaceCanvas {
        val editor = WorkspaceCanvasEditor()
        return editor.splitCell(twoCells(), "cell_a", SplitDirection.HORIZONTAL, 0.4f)
    }

    fun assigned(): WorkspaceCanvas = oneEmpty().assignApp("cell", mapsAssigned)

    fun duplicateIdentity(): WorkspaceCanvas {
        val canvas = twoCells()
        return canvas.assignApp("cell_a", mapsAssigned).assignApp("cell_b", mapsAssigned)
    }

    fun missingApp(): WorkspaceCanvas = oneEmpty().assignApp("cell", missingAssigned)

    fun twoAssigned(): WorkspaceCanvas = twoCells()
        .assignApp("cell_a", mapsAssigned)
        .assignApp("cell_b", musicAssigned)
}
