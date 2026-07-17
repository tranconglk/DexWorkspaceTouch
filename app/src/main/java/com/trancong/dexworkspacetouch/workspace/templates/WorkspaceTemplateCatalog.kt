package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits

class WorkspaceTemplateCatalog private constructor(
    templates: List<WorkspaceTemplate>,
) {
    val allTemplates: List<WorkspaceTemplate> = templates.toList()

    init {
        require(allTemplates.isNotEmpty()) { "templates must not be empty" }
        require(allTemplates.map(WorkspaceTemplate::id).distinct().size == allTemplates.size) {
            "template IDs must be unique"
        }
        val canvases = allTemplates.map { it.factory() }
        require(canvases.all { it.cells.size in 1..WorkspaceLimits.MaxCells }) {
            "templates must contain 1..${WorkspaceLimits.MaxCells} cells"
        }
        require(canvases.all { canvas -> canvas.cells.all { it.app == null } }) {
            "templates must not assign applications"
        }
        require(canvases.map { it.canonicalTemplateSignature() }.distinct().size == canvases.size) {
            "template bounds must be canonically unique"
        }
    }

    fun find(id: String): WorkspaceTemplate? = allTemplates.firstOrNull { it.id == id }

    fun templatesIn(category: WorkspaceTemplateCategory): List<WorkspaceTemplate> =
        allTemplates.filter { it.category == category }

    companion object {
        fun default(): WorkspaceTemplateCatalog = WorkspaceTemplateCatalog(defaultTemplates())

        private fun defaultTemplates(): List<WorkspaceTemplate> {
            val builder = WorkspaceTemplateCanvasBuilder()
            return listOf(
                template("full-screen", "Toàn màn hình", "Một ô toàn màn hình", WorkspaceTemplateCategory.BASIC) {
                    builder.fullScreen()
                },
                template("two-columns", "2 Cột", "Hai cột bằng nhau", WorkspaceTemplateCategory.BASIC) {
                    builder.columns(2)
                },
                template("two-rows", "2 Hàng", "Hai hàng bằng nhau", WorkspaceTemplateCategory.BASIC) {
                    builder.rows(2)
                },
                template("three-columns", "3 Cột", "Ba cột bằng nhau", WorkspaceTemplateCategory.BASIC) {
                    builder.columns(3)
                },
                template("three-rows", "3 Hàng", "Ba hàng bằng nhau", WorkspaceTemplateCategory.BASIC) {
                    builder.rows(3)
                },
                template("four-grid", "Lưới 2×2", "Bốn ô bằng nhau", WorkspaceTemplateCategory.BASIC) {
                    builder.bothSidesTwoRows()
                },

                template("left-sidebar", "Sidebar trái", "Trái 30%, phải 70%", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sidebar(largeOnLeft = false)
                },
                template("right-sidebar", "Sidebar phải", "Trái 70%, phải 30%", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sidebar(largeOnLeft = true)
                },
                template("left-large-two-right", "Trái lớn + 2 phải", "Một ô lớn trái, hai ô phải", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sideWithRows(largeOnLeft = true, rowCount = 2)
                },
                template("right-large-two-left", "Phải lớn + 2 trái", "Hai ô trái, một ô lớn phải", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sideWithRows(largeOnLeft = false, rowCount = 2)
                },
                template("left-large-grid-right", "Trái lớn + lưới phải", "Một ô lớn trái, lưới 2×2 phải", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sideWithGrid(largeOnLeft = true)
                },
                template("right-large-grid-left", "Phải lớn + lưới trái", "Lưới 2×2 trái, một ô lớn phải", WorkspaceTemplateCategory.LEFT_RIGHT) {
                    builder.sideWithGrid(largeOnLeft = false)
                },

                template("top-large-two-bottom", "Trên lớn + 2 dưới", "Một ô lớn trên, hai ô dưới", WorkspaceTemplateCategory.TOP_BOTTOM) {
                    builder.topWithColumns(largeOnTop = true, columnCount = 2)
                },
                template("two-top-bottom-large", "2 trên + dưới lớn", "Hai ô trên, một ô lớn dưới", WorkspaceTemplateCategory.TOP_BOTTOM) {
                    builder.topWithColumns(largeOnTop = false, columnCount = 2)
                },
                template("top-large-four-bottom", "Trên lớn + 4 dưới", "Một ô lớn trên, bốn ô dưới", WorkspaceTemplateCategory.TOP_BOTTOM) {
                    builder.topWithColumns(largeOnTop = true, columnCount = 4)
                },
                template("four-top-bottom-large", "4 trên + dưới lớn", "Bốn ô trên, một ô lớn dưới", WorkspaceTemplateCategory.TOP_BOTTOM) {
                    builder.topWithColumns(largeOnTop = false, columnCount = 4)
                },
            )
        }

        private fun template(
            id: String,
            name: String,
            description: String,
            category: WorkspaceTemplateCategory,
            factory: () -> com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas,
        ) = WorkspaceTemplate(id, name, description, category, id, factory)
    }
}
