package com.trancong.dexworkspacetouch.workspace.transfer

object WorkspaceImportNamePolicy {
    private val suffix = Regex("\\s*\\(Đã nhập(?: [0-9]+)?\\)$", RegexOption.IGNORE_CASE)
    fun nextName(sourceName: String, existingNames: Collection<String>): String {
        val base = sourceName.trim().replace(suffix, "").trim().ifEmpty { "Workspace" }
        val used = existingNames.map { it.trim().lowercase(java.util.Locale.ROOT) }.toSet()
        if (base.lowercase(java.util.Locale.ROOT) !in used) return base
        val first = "$base (Đã nhập)"
        if (first.lowercase(java.util.Locale.ROOT) !in used) return first
        var number = 2
        while ("$base (Đã nhập $number)".lowercase(java.util.Locale.ROOT) in used) number++
        return "$base (Đã nhập $number)"
    }
}

fun sanitizeWorkspaceFileName(name: String): String {
    val safe = name.map { if (it.code < 0x20 || it in "\\/:*?\"<>|") ' ' else it }
        .joinToString("").replace(Regex("\\s+"), " ").trim().take(120).trimEnd('.', ' ')
    return "${safe.ifEmpty { "workspace" }}.${WorkspaceTransferFormat.Extension}"
}

fun readWorkspaceBytes(input: java.io.InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > WorkspaceTransferFormat.MaxBytes) throw WorkspaceTransferException(WorkspaceTransferFailure.FILE_TOO_LARGE)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
