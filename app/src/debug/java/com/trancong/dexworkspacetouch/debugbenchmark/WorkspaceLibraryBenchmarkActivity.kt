package com.trancong.dexworkspacetouch.debugbenchmark

import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import androidx.room.withTransaction
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.model.toLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceLibraryCard
import com.trancong.dexworkspacetouch.workspace.persistence.repository.RoomWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.room.DexWorkspaceDatabase
import com.trancong.dexworkspacetouch.workspace.persistence.room.toDomain
import com.trancong.dexworkspacetouch.workspace.persistence.room.toEntity
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication

class WorkspaceLibraryBenchmarkActivity : ComponentActivity() {
    private var items by mutableStateOf<List<WorkspaceLibraryItem>>(emptyList())
    private var loading by mutableStateOf(true)
    private var database: DexWorkspaceDatabase? = null
    private var loadStartedNanos = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val count = intent.getIntExtra(EXTRA_COUNT, 100).takeIf { it in DATASET_SIZES } ?: 100
        val heapBefore = heapUsedBytes()
        val benchmarkNowEpochMillis = System.currentTimeMillis()
        loadStartedNanos = SystemClock.elapsedRealtimeNanos()
        setContent {
            DexWorkspaceTouchTheme {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    val gridState = rememberLazyGridState()
                    LaunchedEffect(items) {
                        withFrameNanos {
                            metric(count, "renderReadyMs", elapsedMs(loadStartedNanos))
                            metric(count, "heapAfterRenderBytes", heapUsedBytes())
                        }
                        val scrollStart = SystemClock.elapsedRealtimeNanos()
                        gridState.animateScrollToItem(items.lastIndex)
                        gridState.animateScrollToItem(0)
                        metric(count, "scrollRoundTripMs", elapsedMs(scrollStart))
                        metric(count, "heapAfterScrollBytes", heapUsedBytes())
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(Dimensions.WorkspaceCardMinWidth),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.L),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
                        verticalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
                    ) {
                        item { Text("Benchmark: $count workspaces") }
                        items(items, key = WorkspaceLibraryItem::id) { workspace ->
                            WorkspaceLibraryCard(
                                workspace = workspace,
                                selected = false,
                                onSelect = {},
                                onOpen = {},
                                onEdit = {},
                                onDuplicate = {},
                                onPinToggle = {},
                                onRename = {},
                                onDelete = {},
                                onManage = {},
                                canDelete = false,
                                openEnabled = false,
                                appIconLoader = (application as DexWorkspaceTouchApplication).appIconLoader,
                                nowEpochMillis = benchmarkNowEpochMillis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { prepareAndMeasure(count, heapBefore) }
            items = loaded
            loading = false
        }
    }

    private suspend fun prepareAndMeasure(count: Int, heapBefore: Long): List<WorkspaceLibraryItem> {
        deleteDatabase(BENCHMARK_DATABASE)
        val serializer = DeterministicWorkspaceCanvasJsonSerializer()
        val openStart = SystemClock.elapsedRealtimeNanos()
        val db = Room.databaseBuilder(applicationContext, DexWorkspaceDatabase::class.java, BENCHMARK_DATABASE).build()
        database = db
        val dao = db.workspaceDao()
        dao.count()
        metric(count, "databaseOpenMs", elapsedMs(openStart))
        metric(count, "heapBeforeBytes", heapBefore)

        val generated = WorkspaceBenchmarkDataGenerator.generate(count)
        val seedStart = SystemClock.elapsedRealtimeNanos()
        db.withTransaction {
            generated.forEach { dao.insert(it.toEntity(serializer)) }
        }
        metric(count, "seedMs", elapsedMs(seedStart))

        val rawStart = SystemClock.elapsedRealtimeNanos()
        val entities = dao.observeAll().first()
        metric(count, "rawFirstEmissionMs", elapsedMs(rawStart))

        val mapStart = SystemClock.elapsedRealtimeNanos()
        val mapped = entities.map { it.toDomain(serializer) }
        metric(count, "entityToWorkspaceMs", elapsedMs(mapStart))

        val repository = RoomWorkspaceRepository(dao, serializer)
        val repositoryStart = SystemClock.elapsedRealtimeNanos()
        val snapshot = repository.observeSnapshot().first()
        metric(count, "repositoryFirstEmissionMs", elapsedMs(repositoryStart))
        metric(count, "deserializeFailures", snapshot.issues.size.toLong())

        val editStart = SystemClock.elapsedRealtimeNanos()
        repository.getById(mapped[count / 2].id)
        metric(count, "openForEditMs", elapsedMs(editStart))
        metric(count, "workspaceCount", snapshot.workspaces.size.toLong())
        metric(count, "selectedItemCount", 0L)
        metric(count, "heapAfterLoadBytes", heapUsedBytes())
        return snapshot.workspaces.map { it.toLibraryItem() }
    }

    override fun onDestroy() {
        database?.close()
        database = null
        super.onDestroy()
    }

    private fun metric(count: Int, name: String, value: Long) {
        Log.i(TAG, "count=$count metric=$name value=$value")
    }

    private fun elapsedMs(startNanos: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000L

    private fun heapUsedBytes(): Long = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() +
        Debug.getNativeHeapAllocatedSize()

    private companion object {
        const val TAG = "DWT_BENCH"
        const val EXTRA_COUNT = "workspace_count"
        const val BENCHMARK_DATABASE = "dex_workspace_benchmark.db"
        val DATASET_SIZES = setOf(100, 250, 500)
    }
}
