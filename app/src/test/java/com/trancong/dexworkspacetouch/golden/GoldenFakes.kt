package com.trancong.dexworkspacetouch.golden

import com.trancong.dexworkspacetouch.platform.launch.android.LaunchDelay
import com.trancong.dexworkspacetouch.platform.launch.android.SingleAppLauncher
import com.trancong.dexworkspacetouch.platform.launch.android.SingleAppLaunchResult
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLauncher
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceClock
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceIdGenerator
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWorkspaceRepository(initial: List<Workspace> = emptyList()) : WorkspaceRepository {
    private val state = MutableStateFlow(initial)
    val values: List<Workspace> get() = state.value
    var inserts = 0
    var updates = 0

    override fun observeAll(): Flow<List<Workspace>> = state
    override suspend fun getById(id: String) = values.firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) {
        check(values.none { it.id == workspace.id })
        inserts++
        state.value = listOf(workspace) + values
    }
    override suspend fun update(workspace: Workspace) {
        check(values.any { it.id == workspace.id })
        updates++
        state.value = values.map { if (it.id == workspace.id) workspace else it }
    }
    override suspend fun deleteById(id: String) { state.value = values.filterNot { it.id == id } }
    override suspend fun exists(id: String) = values.any { it.id == id }
    override suspend fun count() = values.size
    override suspend fun setPinned(id: String, isPinned: Boolean) {
        check(values.any { it.id == id })
        state.value = values.map { if (it.id == id) it.copy(isPinned = isPinned) else it }
    }
}

class FakeInstalledAppCatalog(private val apps: List<InstalledApp>) : InstalledAppCatalog {
    override fun getApps() = apps
    override fun findByIdentity(identity: AppIdentity) = apps.firstOrNull { it.identity == identity }
}

class FakeWorkspaceLauncher(private val result: WorkspaceLaunchResult) : WorkspaceLauncher {
    val requests = mutableListOf<WorkspaceLaunchRequest>()
    override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
        requests += request
        return result
    }
}

class FakeSingleAppLauncher(
    private val resultFor: (AppLaunchTarget) -> SingleAppLaunchResult,
) : SingleAppLauncher {
    val targets = mutableListOf<AppLaunchTarget>()
    override suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult {
        targets += target
        return resultFor(target)
    }
}

class FakeLaunchDelay : LaunchDelay {
    val waits = mutableListOf<Long>()
    override suspend fun wait(milliseconds: Long) { waits += milliseconds }
}

class FakeClock(private var next: Long = 1_000L) : WorkspaceClock {
    override fun nowEpochMillis(): Long = next++
}

class FakeIdGenerator(private val ids: ArrayDeque<String>) : WorkspaceIdGenerator {
    constructor(vararg ids: String) : this(ArrayDeque(ids.toList()))
    override fun newId(): String = ids.removeFirst()
}
