package com.trancong.dexworkspacetouch.workspace.library.state

object WorkspaceDuplicateNamePolicy {
    private val copySuffix = Regex("\\s*\\(Bản sao(?:\\s+[1-9][0-9]*)?\\)\\s*$", RegexOption.IGNORE_CASE)

    fun nextName(sourceName: String, existingNames: Collection<String>): String {
        val base = sourceName.trim().replace(copySuffix, "").trim()
        require(base.isNotEmpty()) { "Source workspace name must not be blank" }
        val used = existingNames.map { it.trim().lowercase() }.toSet()
        var number = 1
        while (true) {
            val candidate = if (number == 1) "$base (Bản sao)" else "$base (Bản sao $number)"
            if (candidate.lowercase() !in used) return candidate
            number++
        }
    }
}
