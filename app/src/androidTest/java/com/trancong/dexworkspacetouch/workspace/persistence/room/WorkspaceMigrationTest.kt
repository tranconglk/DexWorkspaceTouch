package com.trancong.dexworkspacetouch.workspace.persistence.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun cleanUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test fun migrate1To2DefaultsExistingRowsToUnpinnedAndPersistsUpdates() {
        createVersionOneDatabase()

        val database = Room.databaseBuilder(context, DexWorkspaceDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertFalse(requireNotNull(database.workspaceDao().getById("existing")).isPinned)
                database.workspaceDao().setPinned("existing", true)
                assertTrue(requireNotNull(database.workspaceDao().getById("existing")).isPinned)
            }
        } finally {
            database.close()
        }
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE IF NOT EXISTS workspaces (
                            id TEXT NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            canvasJson TEXT NOT NULL,
                            modifiedSequence INTEGER NOT NULL,
                            schemaVersion INTEGER NOT NULL,
                            createdAtEpochMillis INTEGER NOT NULL,
                            updatedAtEpochMillis INTEGER NOT NULL
                        )""".trimIndent(),
                    )
                    db.insertVersionOneWorkspace()
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configuration).apply {
            writableDatabase
            close()
        }
    }

    private fun SupportSQLiteDatabase.insertVersionOneWorkspace() {
        execSQL(
            """INSERT INTO workspaces
                (id, name, canvasJson, modifiedSequence, schemaVersion,
                 createdAtEpochMillis, updatedAtEpochMillis)
                VALUES ('existing', 'Existing', '{"cells":[]}', 4, 1, 10, 20)
            """.trimIndent(),
        )
    }

    private companion object {
        const val DATABASE_NAME = "workspace-migration-test"
    }
}
