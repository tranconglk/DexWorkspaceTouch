package com.trancong.dexworkspacetouch.workspace.close

object CloseTaskPolicy {
    val protectedPackages = setOf(DWT_PROTECTED_PACKAGE, "moe.shizuku.privileged.api", "com.android.systemui",
        "com.sec.android.app.launcher", "com.sec.android.app.desktoplauncher", "com.samsung.desktopsystemui", "android")

    fun preflight(request: PackageCloseRequest): String? = when {
        request.userId != 0 -> "UNSUPPORTED_USER"
        request.packagesRequested.any { it in protectedPackages } -> "PROTECTED_PACKAGE"
        else -> null
    }

    fun related(request: PackageCloseRequest, snapshot: CloseSnapshot): List<CloseTask> = snapshot.tasks
        .filter { it.userId == request.userId && (it.basePackage in request.packagesRequested || it.topPackage in request.packagesRequested) }
        .distinctBy { it.userId to it.taskId }

    fun coverageProblem(snapshot: CloseSnapshot): String? {
        if (!snapshot.complete) return snapshot.reason ?: "ENUMERATION_INCOMPLETE"
        if (snapshot.tasks.size >= 10_000) return "ENUMERATION_INCOMPLETE"
        if (snapshot.tasks.any { it.taskId < 0 || it.userId < 0 || it.displayId < 0 ||
                it.baseActivity?.contains('/') != true || it.topActivity?.contains('/') != true || !it.leaf }) return "MISSING_TASK_METADATA"
        if (snapshot.tasks.groupBy { it.userId to it.taskId }.values.any { copies -> copies.any { !it.sameIdentity(copies.first()) } })
            return "CONFLICTING_TASK_IDENTITY"
        return null
    }

    fun safe(request: PackageCloseRequest, task: CloseTask): Boolean = task.userId == 0 && task.leaf &&
        task.basePackage != null && task.basePackage == task.topPackage && task.basePackage in request.packagesRequested &&
        task.basePackage !in protectedPackages && task.baseActivity?.contains('/') == true && task.topActivity?.contains('/') == true
}
