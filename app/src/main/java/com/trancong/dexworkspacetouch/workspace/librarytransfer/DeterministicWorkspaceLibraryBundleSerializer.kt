package com.trancong.dexworkspacetouch.workspace.librarytransfer

import com.trancong.dexworkspacetouch.workspace.transfer.DeterministicWorkspaceTransferSerializer
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceExport
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferException
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferFailure
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferSerializer

class DeterministicWorkspaceLibraryBundleSerializer(
    private val singleSerializer: WorkspaceTransferSerializer = DeterministicWorkspaceTransferSerializer(),
) : WorkspaceLibraryBundleSerializer {
    override fun encode(export: WorkspaceLibraryExport): ByteArray {
        validateCounts(export.workspaces)
        if (export.exportedAtEpochMillis < 0) fail(WorkspaceLibraryTransferFailure.INVALID_FORMAT)
        val bodies = export.workspaces.map { bodyOf(it) }.sortedWith(
            compareBy<WorkspaceBody>({ it.payload.name.lowercase(java.util.Locale.ROOT) }, { it.payload.name }, { it.json }),
        )
        val json = buildString {
            append("{\"format\":\"dex-workspace-touch-library\",\"formatVersion\":1,\"exportedAtEpochMillis\":")
            append(export.exportedAtEpochMillis)
            append(",\"workspaceCount\":").append(bodies.size).append(",\"workspaces\":[")
            bodies.forEachIndexed { index, body -> if (index > 0) append(','); append(body.json) }
            append("]}")
        }
        return json.toByteArray(Charsets.UTF_8).also { if (it.size > WorkspaceLibraryTransferFormat.MaxBytes) tooLarge() }
    }

    override fun decode(bytes: ByteArray): WorkspaceLibraryImportPayload {
        if (bytes.size > WorkspaceLibraryTransferFormat.MaxBytes) tooLarge()
        val source = bytes.toString(Charsets.UTF_8)
        val prefix = "{\"format\":\"dex-workspace-touch-library\",\"formatVersion\":"
        if (!source.startsWith(prefix)) invalid()
        var index = prefix.length
        val version = readLong(source, index).also { index = it.second }.first
        if (version != 1L) fail(WorkspaceLibraryTransferFailure.UNSUPPORTED_VERSION)
        index = expect(source, index, ",\"exportedAtEpochMillis\":")
        index = readLong(source, index).second
        index = expect(source, index, ",\"workspaceCount\":")
        val countResult = readLong(source, index); val declaredCount = countResult.first; index = countResult.second
        index = expect(source, index, ",\"workspaces\":[")
        val bodies = mutableListOf<String>()
        if (index < source.length && source[index] != ']') {
            while (true) {
                val result = readObject(source, index)
                bodies += result.first
                index = result.second
                if (index < source.length && source[index] == ',') { index++; continue }
                break
            }
        }
        index = expect(source, index, "]}")
        if (index != source.length || declaredCount != bodies.size.toLong()) invalid()
        if (bodies.size > WorkspaceLibraryTransferFormat.MaxWorkspaces) fail(WorkspaceLibraryTransferFailure.TOO_MANY_WORKSPACES)
        val payloads = bodies.map(::decodeBody)
        validateCounts(payloads)
        return WorkspaceLibraryImportPayload(payloads)
    }

    private fun bodyOf(payload: WorkspaceImportPayload): WorkspaceBody {
        if (payload.workspaceSchemaVersion != 1) fail(WorkspaceLibraryTransferFailure.UNSUPPORTED_WORKSPACE_SCHEMA)
        val envelope = try {
            singleSerializer.encode(WorkspaceExport(payload.name, payload.canvas, 0L))
                .toString(Charsets.UTF_8)
                .also { singleSerializer.decode(it.toByteArray()) }
        }
        catch (error: WorkspaceTransferException) {
            val failure = if (error.failure == WorkspaceTransferFailure.FILE_TOO_LARGE) {
                WorkspaceLibraryTransferFailure.FILE_TOO_LARGE
            } else WorkspaceLibraryTransferFailure.INVALID_WORKSPACE
            throw WorkspaceLibraryTransferException(failure, error)
        }
        val marker = ",\"workspace\":"
        val start = envelope.indexOf(marker).takeIf { it >= 0 } ?: invalid()
        return WorkspaceBody(payload, envelope.substring(start + marker.length, envelope.length - 1))
    }

    private fun decodeBody(body: String): WorkspaceImportPayload {
        val envelope = "{\"format\":\"dex-workspace-touch\",\"formatVersion\":1,\"exportedAtEpochMillis\":0,\"workspace\":$body}"
        return try { singleSerializer.decode(envelope.toByteArray()) }
        catch (error: WorkspaceTransferException) {
            val failure = when (error.failure) {
                WorkspaceTransferFailure.UNSUPPORTED_VERSION -> WorkspaceLibraryTransferFailure.UNSUPPORTED_WORKSPACE_SCHEMA
                WorkspaceTransferFailure.FILE_TOO_LARGE -> WorkspaceLibraryTransferFailure.FILE_TOO_LARGE
                WorkspaceTransferFailure.INVALID_WORKSPACE -> WorkspaceLibraryTransferFailure.INVALID_WORKSPACE
                else -> WorkspaceLibraryTransferFailure.INVALID_FORMAT
            }
            throw WorkspaceLibraryTransferException(failure, error)
        }
    }

    private fun validateCounts(values: List<WorkspaceImportPayload>) {
        if (values.isEmpty()) fail(WorkspaceLibraryTransferFailure.EMPTY_LIBRARY)
        if (values.size > WorkspaceLibraryTransferFormat.MaxWorkspaces) fail(WorkspaceLibraryTransferFailure.TOO_MANY_WORKSPACES)
        if (values.sumOf { it.canvas.cells.size } > WorkspaceLibraryTransferFormat.MaxTotalCells) {
            fail(WorkspaceLibraryTransferFailure.TOO_MANY_CELLS)
        }
    }

    private fun readLong(source: String, start: Int): Pair<Long, Int> {
        var end = start
        while (end < source.length && source[end].isDigit()) end++
        return (source.substring(start, end).toLongOrNull() ?: invalid()) to end
    }

    private fun expect(source: String, start: Int, expected: String): Int {
        if (!source.startsWith(expected, start)) invalid()
        return start + expected.length
    }

    private fun readObject(source: String, start: Int): Pair<String, Int> {
        if (start >= source.length || source[start] != '{') invalid()
        var depth = 0; var quoted = false; var escaped = false; var index = start
        while (index < source.length) {
            val char = source[index]
            if (quoted) {
                if (char == '"' && !escaped) quoted = false
                escaped = char == '\\' && !escaped
                if (char != '\\') escaped = false
            } else when (char) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return source.substring(start, index + 1) to (index + 1) }
            }
            index++
        }
        invalid()
    }

    private fun invalid(): Nothing = fail(WorkspaceLibraryTransferFailure.INVALID_FORMAT)
    private fun tooLarge(): Nothing = fail(WorkspaceLibraryTransferFailure.FILE_TOO_LARGE)
    private fun fail(failure: WorkspaceLibraryTransferFailure): Nothing = throw WorkspaceLibraryTransferException(failure)
    private data class WorkspaceBody(val payload: WorkspaceImportPayload, val json: String)
}
