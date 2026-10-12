package com.trancong.dexworkspacetouch.workspace.close

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONObject

class FileCloseOperationStore(private val directory: File, private val syncDirectory: (File) -> Unit) : CloseOperationStore {
    private fun file(id: String): File {
        require(UUID.fromString(id).toString() == id) { "INVALID_OPERATION_ID" }
        return File(directory, "$id.json")
    }
    @Synchronized override fun read(operationId: String): CloseOperationRecord? {
        val file = file(operationId)
        if (!file.exists()) return null
        return CloseCodec.record(JSONObject(file.readText())).also { check(it.request.operationId == operationId) }
    }
    @Synchronized override fun pending(): List<CloseOperationRecord> {
        if (!directory.exists()) return emptyList()
        return checkNotNull(directory.listFiles()).filter { it.extension == "json" }.map { checkNotNull(read(it.nameWithoutExtension)) }
            .filter { it.terminal == null }
    }
    @Synchronized override fun write(record: CloseOperationRecord) {
        val existed = directory.isDirectory
        check(directory.isDirectory || directory.mkdirs()) { "JOURNAL_DIRECTORY_FAILED" }
        if (!existed) syncDirectory(checkNotNull(directory.parentFile))
        val old = read(record.request.operationId)
        check(old?.terminal == null) { "TERMINAL_IMMUTABLE" }
        check(record.revision == (old?.revision?.plus(1) ?: 0)) { "JOURNAL_REVISION_CONFLICT" }
        check(old == null || old.request == record.request) { "OPERATION_ID_CONFLICT" }
        val target = file(record.request.operationId)
        val temporary = File(directory, "${record.request.operationId}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(CloseCodec.record(record).toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        // No non-atomic fallback: failure closes coordinator admission.
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(directory)
    }
}
