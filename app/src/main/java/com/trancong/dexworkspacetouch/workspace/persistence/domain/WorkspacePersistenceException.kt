package com.trancong.dexworkspacetouch.workspace.persistence.domain

sealed class WorkspacePersistenceException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class SerializationFailure(message: String, cause: Throwable? = null) :
        WorkspacePersistenceException(message, cause)
    class UnsupportedSchema(val schemaVersion: Int) :
        WorkspacePersistenceException("Unsupported workspace schema version: $schemaVersion")
    class DuplicateId(val workspaceId: String) :
        WorkspacePersistenceException("Workspace '$workspaceId' already exists")
    class DatabaseFailure(message: String, cause: Throwable? = null) :
        WorkspacePersistenceException(message, cause)
}
