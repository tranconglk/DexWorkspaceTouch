package com.trancong.dexworkspacetouch.workspace.transfer

import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.WorkspaceCanvasSerializer

class DeterministicWorkspaceTransferSerializer(
    private val canvasSerializer: WorkspaceCanvasSerializer = DeterministicWorkspaceCanvasJsonSerializer(),
) : WorkspaceTransferSerializer {
    override fun encode(export: WorkspaceExport): ByteArray {
        if (export.name.trim().isEmpty() || export.name.length > MAX_NAME || export.exportedAtEpochMillis < 0) invalidWorkspace()
        val canvas = try { canvasSerializer.encode(export.canvas) } catch (error: Exception) {
            throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_WORKSPACE, error)
        }
        val json = buildString {
            append("{\"format\":\"dex-workspace-touch\",\"formatVersion\":1,\"exportedAtEpochMillis\":")
            append(export.exportedAtEpochMillis)
            append(",\"workspace\":{\"name\":").appendQuoted(export.name.trim())
            append(",\"workspaceSchemaVersion\":1,\"canvas\":").append(canvas).append("}}")
        }
        return json.toByteArray(Charsets.UTF_8).also { if (it.size > WorkspaceTransferFormat.MaxBytes) tooLarge() }
    }

    override fun decode(bytes: ByteArray): WorkspaceImportPayload {
        if (bytes.size > WorkspaceTransferFormat.MaxBytes) tooLarge()
        val json = try { bytes.toString(Charsets.UTF_8) } catch (error: Exception) { invalid(error) }
        val cursor = EnvelopeCursor(json)
        cursor.literal("{\"format\":\"dex-workspace-touch\",\"formatVersion\":")
        val version = cursor.number()
        if (version != WorkspaceTransferFormat.FormatVersion.toLong()) {
            throw WorkspaceTransferException(WorkspaceTransferFailure.UNSUPPORTED_VERSION)
        }
        cursor.literal(",\"exportedAtEpochMillis\":")
        cursor.number()
        cursor.literal(",\"workspace\":{\"name\":\"")
        val name = unescape(cursor.quotedContent())
        cursor.literal(",\"workspaceSchemaVersion\":")
        val schema = cursor.number()
        if (schema != 1L) throw WorkspaceTransferException(WorkspaceTransferFailure.UNSUPPORTED_VERSION)
        cursor.literal(",\"canvas\":")
        val canvasJson = cursor.canvasAndFinish()
        if (name.trim().isEmpty() || name.length > MAX_NAME) invalidWorkspace()
        val canvas = try { canvasSerializer.decode(canvasJson, schema.toInt()) } catch (error: Exception) {
            throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_WORKSPACE, error)
        }
        if (canvas.cells.size !in 1..5) invalidWorkspace()
        return WorkspaceImportPayload(name.trim(), canvas, schema.toInt())
    }

    private fun StringBuilder.appendQuoted(value: String): StringBuilder {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n")
            '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        } }
        return append('"')
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c != '\\') append(c) else {
                if (i >= value.length) invalid()
                when (val escaped = value[i++]) {
                    '"', '\\', '/' -> append(escaped); 'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t')
                    'b' -> append('\b'); 'f' -> append('\u000c')
                    'u' -> { if (i + 4 > value.length) invalid(); append(value.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> invalid()
                }
            }
        }
    }

    private fun invalid(cause: Throwable? = null): Nothing = throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT, cause)
    private fun invalidWorkspace(): Nothing = throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_WORKSPACE)
    private fun tooLarge(): Nothing = throw WorkspaceTransferException(WorkspaceTransferFailure.FILE_TOO_LARGE)

    private companion object {
        const val MAX_NAME = 200
    }

    private class EnvelopeCursor(private val source: String) {
        private var index = 0
        fun literal(expected: String) {
            if (!source.startsWith(expected, index)) throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT)
            index += expected.length
        }
        fun number(): Long {
            val start = index
            while (index < source.length && source[index].isDigit()) index++
            return source.substring(start, index).toLongOrNull()
                ?: throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT)
        }
        fun quotedContent(): String {
            val start = index
            var escaped = false
            while (index < source.length) {
                val char = source[index]
                if (char == '"' && !escaped) return source.substring(start, index++)
                escaped = char == '\\' && !escaped
                if (char != '\\') escaped = false
                index++
            }
            throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT)
        }
        fun canvasAndFinish(): String {
            if (!source.endsWith("}}") || index >= source.length - 2) throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT)
            val canvas = source.substring(index, source.length - 2)
            if (!canvas.startsWith('{') || !canvas.endsWith('}')) throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_FORMAT)
            index = source.length
            return canvas
        }
    }
}
