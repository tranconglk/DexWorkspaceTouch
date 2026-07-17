package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLauncher
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AndroidWorkspaceLauncher(
    private val singleAppLauncher: SingleAppLauncher,
    private val sequencingPolicy: LaunchSequencingPolicy = LaunchSequencingPolicy(),
    private val launchDelay: LaunchDelay = CoroutineLaunchDelay,
    private val logger: WorkspaceLaunchLogger = WorkspaceLaunchLogger.Android,
) : WorkspaceLauncher {
    override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
        val orderedTargets = request.targets.sortedBy(AppLaunchTarget::order)
        val launchedTargets = mutableListOf<AppLaunchTargetResult>()
        val failedTargets = mutableListOf<AppLaunchFailure>()

        for ((index, target) in orderedTargets.withIndex()) {
            currentCoroutineContext().ensureActive()
            logger.log(target.logPrefix(request.workspaceId) + " start")

            when (val result = singleAppLauncher.launch(target)) {
                is SingleAppLaunchResult.Success -> {
                    launchedTargets += result.launchedTarget
                    logger.log(target.logPrefix(request.workspaceId) + " SUCCESS")
                }
                is SingleAppLaunchResult.Failure -> {
                    failedTargets += result.failure
                    logger.log(
                        target.logPrefix(request.workspaceId) + " ${result.failure.reason}",
                    )
                    if (result.failure.reason == AppLaunchFailureReason.DISPLAY_UNAVAILABLE) {
                        logger.log("workspaceId=${request.workspaceId} stop=DISPLAY_UNAVAILABLE")
                        orderedTargets.drop(index + 1).forEach { pendingTarget ->
                            failedTargets += AppLaunchFailure(
                                target = pendingTarget,
                                reason = AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
                                technicalMessage = DISPLAY_UNAVAILABLE_BEFORE_LAUNCH,
                            )
                        }
                        break
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            if (index < orderedTargets.lastIndex) {
                launchDelay.wait(sequencingPolicy.delayBetweenTargetsMs)
            }
        }

        return aggregate(launchedTargets, failedTargets)
    }

    private fun aggregate(
        launchedTargets: List<AppLaunchTargetResult>,
        failedTargets: List<AppLaunchFailure>,
    ): WorkspaceLaunchResult = when {
        failedTargets.isEmpty() -> WorkspaceLaunchResult.Success(launchedTargets)
        launchedTargets.isNotEmpty() -> WorkspaceLaunchResult.PartialSuccess(
            launchedTargets = launchedTargets,
            failedTargets = failedTargets,
        )
        else -> WorkspaceLaunchResult.Failure(failedTargets)
    }

    private fun AppLaunchTarget.logPrefix(workspaceId: String): String =
        "workspaceId=$workspaceId order=$order identity=${identity.toStableKey()}"

    private companion object {
        const val DISPLAY_UNAVAILABLE_BEFORE_LAUNCH =
            "Display became unavailable before launch."
    }
}
