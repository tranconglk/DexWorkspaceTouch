package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits

class WorkspaceTemplateCatalog private constructor(
    templates: List<WorkspaceTemplate>,
) {
    val allTemplates: List<WorkspaceTemplate> = templates.toList()

    init {
        require(allTemplates.isNotEmpty()) { "templates must not be empty" }
        require(allTemplates.map { it.id }.distinct().size == allTemplates.size) {
            "template IDs must be unique"
        }
        require(allTemplates.all { it.factory().cells.size <= WorkspaceLimits.MaxCells }) {
            "templates must not exceed ${WorkspaceLimits.MaxCells} cells"
        }
    }

    fun find(id: String): WorkspaceTemplate? = allTemplates.firstOrNull { it.id == id }

    companion object {
        fun default(): WorkspaceTemplateCatalog = WorkspaceTemplateCatalog(defaultTemplates())

        private fun defaultTemplates(): List<WorkspaceTemplate> = listOf(
            template("full-screen", "Toàn màn hình", "Một ô toàn màn hình", WorkspaceTemplateCategory.BASIC) {
                WorkspaceCanvas.singleCell()
            },
            template("two-columns", "2 Cột", "Hai cột bằng nhau", WorkspaceTemplateCategory.COLUMNS) {
                splitVertical(0.5f)
            },
            template("three-columns", "3 Cột", "Ba cột bằng nhau", WorkspaceTemplateCategory.COLUMNS) {
                threeColumns()
            },
            template("four-grid", "4 Ô", "Lưới 2 × 2", WorkspaceTemplateCategory.GRID) {
                grid2x2()
            },
            template("left-sidebar", "Sidebar trái", "Cột trái 30%, nội dung 70%", WorkspaceTemplateCategory.SIDEBAR) {
                splitVertical(0.3f)
            },
            template("right-sidebar", "Sidebar phải", "Nội dung 70%, cột phải 30%", WorkspaceTemplateCategory.SIDEBAR) {
                splitVertical(0.7f)
            },
            template("top-bottom", "Trên / Dưới", "Hai hàng bằng nhau", WorkspaceTemplateCategory.ROWS) {
                editor().splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL)
            },
            template("top-two-bottom", "Trên + 2 Dưới", "Một ô trên, hai ô dưới", WorkspaceTemplateCategory.ROWS) {
                val editor = editor()
                val rows = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL)
                editor.splitCell(rows, "cell_b", SplitDirection.VERTICAL)
            },
            template("two-top-bottom", "2 Trên + Dưới", "Hai ô trên, một ô dưới", WorkspaceTemplateCategory.ROWS) {
                val editor = editor()
                val rows = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL)
                editor.splitCell(rows, "cell_a", SplitDirection.VERTICAL)
            },
            template("three-top-two-bottom", "3 Trên + 2 Dưới", "Ba ô trên, hai ô dưới", WorkspaceTemplateCategory.GRID) {
                threeTopTwoBottom()
            },
        )

        private fun template(
            id: String,
            name: String,
            description: String,
            category: WorkspaceTemplateCategory,
            factory: () -> WorkspaceCanvas,
        ) = WorkspaceTemplate(id, name, description, category, id, factory)

        private fun splitVertical(ratio: Float): WorkspaceCanvas = editor().splitCell(
            WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, ratio,
        )

        private fun threeColumns(): WorkspaceCanvas {
            val editor = editor()
            val firstSplit = editor.splitCell(
                WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, 1f / 3f,
            )
            return editor.splitCell(firstSplit, "cell_b", SplitDirection.VERTICAL, 0.5f)
        }

        private fun grid2x2(): WorkspaceCanvas {
            val editor = editor()
            val columnsCanvas = splitVertical(0.5f)
            return columnsCanvas.cells.map { it.id }.fold(columnsCanvas) { canvas, cellId ->
                editor.splitCell(canvas, cellId, SplitDirection.HORIZONTAL)
            }
        }

        private fun threeTopTwoBottom(): WorkspaceCanvas {
            val editor = editor()
            val rows = editor.splitCell(
                WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL,
            )
            val topFirst = editor.splitCell(rows, "cell_a", SplitDirection.VERTICAL, 1f / 3f)
            val topThree = editor.splitCell(topFirst, "cell_a_b", SplitDirection.VERTICAL, 0.5f)
            return editor.splitCell(topThree, "cell_b", SplitDirection.VERTICAL, 0.5f)
        }

        private fun editor() = WorkspaceCanvasEditor()
    }
}
