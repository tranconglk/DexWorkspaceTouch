package com.trancong.dexworkspacetouch.debuglaunch

import android.util.Log
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.trancong.dexworkspacetouch.platform.launch.android.AndroidSingleAppLauncher
import com.trancong.dexworkspacetouch.platform.launch.android.ActivityDisplayWorkAreaProvider
import com.trancong.dexworkspacetouch.platform.launch.android.LaunchDisplayRoutingMode
import com.trancong.dexworkspacetouch.platform.launch.android.SingleAppLaunchResult
import com.trancong.dexworkspacetouch.platform.launch.bounds.BoundsCalculationResult
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReference
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyWorkAreaDiagnostics
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsCalculator
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsSanity
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.launchMarginPx
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SingleAppLaunchDebugScreen(
    catalog: InstalledAppCatalog,
    workAreaProvider: ActivityDisplayWorkAreaProvider,
    launcherFactory: (LaunchDisplayRoutingMode) -> AndroidSingleAppLauncher,
    hostDisplayIdProvider: () -> Int?,
    referenceStore: LegacyDisplayWorkAreaReferenceStore,
    onFinishHarness: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var catalogFailed by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var selectedPreset by remember { mutableStateOf(LaunchBoundsPreset.FULL) }
    var snapshot by remember { mutableStateOf<DisplayWorkAreaSnapshot?>(null) }
    var calculatedBounds by remember { mutableStateOf<PixelBounds?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var launchInProgress by remember { mutableStateOf(false) }
    var routingMode by remember { mutableStateOf(LaunchDisplayRoutingMode.INHERITED) }
    var finishHarnessAfterLaunch by remember { mutableStateOf(false) }
    var lastLaunchSummary by remember { mutableStateOf<String?>(null) }
    var legacyDiagnostics by remember { mutableStateOf<LegacyWorkAreaDiagnostics?>(null) }
    var savedReference by remember {
        mutableStateOf<LegacyDisplayWorkAreaReference?>(referenceStore.current())
    }
    val coroutineScope = rememberCoroutineScope()

    fun refreshWorkArea() {
        snapshot = workAreaProvider.getSnapshot()
        legacyDiagnostics = workAreaProvider.lastLegacyDiagnostics
        savedReference = referenceStore.current()
        snapshot?.let { Log.d(DEBUG_LOG_TAG, it.diagnosticMessage()) }
        calculatedBounds = null
        resultMessage = if (snapshot == null) {
            "Không đọc được vùng làm việc DeX."
        } else {
            "Đã đọc lại vùng hiển thị."
        }
    }

    LaunchedEffect(catalog) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { catalog.getApps() }
        }
        apps = loaded.getOrNull().orEmpty()
        catalogFailed = loaded.isFailure
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Button(
                onClick = {
                    if (launchInProgress) return@Button
                    val app = selectedApp
                    if (app == null) {
                        resultMessage = "Chưa chọn ứng dụng."
                        return@Button
                    }
                    val currentSnapshot = workAreaProvider.getSnapshot()
                    snapshot = currentSnapshot
                    legacyDiagnostics = workAreaProvider.lastLegacyDiagnostics
                    savedReference = referenceStore.current()
                    if (currentSnapshot == null) {
                        calculatedBounds = null
                        resultMessage = "Không đọc được vùng làm việc DeX."
                        return@Button
                    }
                    Log.d(DEBUG_LOG_TAG, currentSnapshot.diagnosticMessage())
                    val boundsResult = LaunchBoundsCalculator(
                        launchMarginPx(currentSnapshot.density),
                    ).calculate(selectedPreset.bounds, currentSnapshot.workArea)
                    calculatedBounds = (boundsResult as? BoundsCalculationResult.Success)?.bounds
                    if (boundsResult is BoundsCalculationResult.Failure) {
                        resultMessage = "Bounds không hợp lệ để mở ứng dụng."
                        return@Button
                    }
                    val finalBounds = calculatedBounds
                    if (
                        finalBounds == null ||
                        !LaunchBoundsSanity.isWithinWorkArea(
                            finalBounds,
                            currentSnapshot.workArea,
                        )
                    ) {
                        resultMessage = "LAUNCH_REJECTED: PixelBounds nằm ngoài usable area."
                        Log.w(
                            DEBUG_LOG_TAG,
                            "Rejected bounds=$finalBounds\n${currentSnapshot.diagnosticMessage()}",
                        )
                        return@Button
                    }
                    val activityName = app.activityName
                    if (activityName == null) {
                        resultMessage = "Không tìm thấy activity để mở ứng dụng."
                        return@Button
                    }
                    val target = AppLaunchTarget(
                        identity = app.identity,
                        bounds = selectedPreset.bounds,
                        order = 0,
                    )
                    launchInProgress = true
                    resultMessage = "Đang gửi yêu cầu mở ${app.label}..."
                    val hostDisplayId = hostDisplayIdProvider()
                    lastLaunchSummary = buildString {
                        append("routing=$routingMode")
                        append(", hostDisplayId=$hostDisplayId")
                        append(", snapshotDisplayId=${currentSnapshot.displayId}")
                        append(", requestedDisplayId=")
                        append(
                            if (routingMode == LaunchDisplayRoutingMode.EXPLICIT) {
                                currentSnapshot.displayId
                            } else {
                                "inherited"
                            },
                        )
                        append(", rect=$finalBounds")
                    }
                    coroutineScope.launch {
                        try {
                            val launchResult = launcherFactory(routingMode).launch(target)
                            resultMessage = launchResult.toUiMessage(app.label)
                            lastLaunchSummary += ", result=$launchResult"
                            if (
                                finishHarnessAfterLaunch &&
                                launchResult is SingleAppLaunchResult.Success
                            ) {
                                delay(DEBUG_FINISH_DELAY_MS)
                                onFinishHarness()
                            }
                        } finally {
                            launchInProgress = false
                        }
                    }
                },
                enabled = !launchInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.L)
                    .heightIn(min = TouchTargets.PrimaryButton),
            ) {
                Text(if (launchInProgress) "Đang mở..." else "Mở thử")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item {
                Text(
                    "Kiểm thử mở một ứng dụng",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item {
                OutlinedButton(
                    onClick = ::refreshWorkArea,
                    modifier = Modifier.fillMaxWidth().heightIn(
                        min = TouchTargets.SecondaryButton,
                    ),
                ) {
                    Text("Chụp lại thông số")
                }
            }
            item {
                WorkAreaDiagnostics(
                    snapshot = snapshot,
                    calculatedBounds = calculatedBounds,
                    hostDisplayId = hostDisplayIdProvider(),
                    legacyDiagnostics = legacyDiagnostics,
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        val current = snapshot
                        if (
                            current != null &&
                            referenceStore.saveTrusted(current, Build.VERSION.SDK_INT)
                        ) {
                            savedReference = referenceStore.current()
                            resultMessage = "Đã lưu vùng làm việc hiện tại làm tham chiếu."
                        } else {
                            resultMessage = "Snapshot hiện tại không đủ tin cậy để lưu."
                        }
                    },
                    enabled = snapshot != null,
                    modifier = Modifier.fillMaxWidth().heightIn(
                        min = TouchTargets.SecondaryButton,
                    ),
                ) {
                    Text("Lưu vùng làm việc hiện tại làm tham chiếu")
                }
            }
            item {
                Text(
                    savedReference?.let { reference ->
                        "Reference: displayId=${reference.displayId}, " +
                            "real=${reference.realDisplayBounds}, " +
                            "density=${reference.density}, " +
                            "usable=${reference.workArea.usableWidth}×" +
                            "${reference.workArea.usableHeight}, " +
                            "source=${reference.originalSource}, " +
                            "status=${reference.captureMode}, sequence=${reference.sequence}"
                    } ?: "Reference status: NONE",
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        referenceStore.clear()
                        savedReference = null
                        resultMessage = "Đã xóa reference trong RAM."
                    },
                    enabled = savedReference != null,
                    modifier = Modifier.fillMaxWidth().heightIn(
                        min = TouchTargets.SecondaryButton,
                    ),
                ) {
                    Text("Clear reference")
                }
            }
            if (snapshot == null && savedReference == null) {
                item {
                    Text("Hãy phóng to ứng dụng một lần để nhận diện vùng làm việc DeX.")
                }
            }
            item {
                HorizontalDivider()
                Text(
                    "Display routing A/B",
                    modifier = Modifier.padding(top = Spacing.M),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            item {
                SelectionButton(
                    selected = routingMode == LaunchDisplayRoutingMode.INHERITED,
                    text = "Kế thừa display từ Activity",
                    onClick = { routingMode = LaunchDisplayRoutingMode.INHERITED },
                )
            }
            item {
                SelectionButton(
                    selected = routingMode == LaunchDisplayRoutingMode.EXPLICIT,
                    text = "Chỉ định display rõ ràng",
                    onClick = { routingMode = LaunchDisplayRoutingMode.EXPLICIT },
                )
            }
            item {
                SelectionButton(
                    selected = finishHarnessAfterLaunch,
                    text = if (finishHarnessAfterLaunch) {
                        "Đóng harness sau launch: Bật"
                    } else {
                        "Đóng harness sau launch: Tắt"
                    },
                    onClick = { finishHarnessAfterLaunch = !finishHarnessAfterLaunch },
                )
            }
            lastLaunchSummary?.let { summary -> item { Text(summary) } }
            resultMessage?.let { message ->
                item {
                    Text(message, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                HorizontalDivider()
                Text(
                    "Bounds mẫu",
                    modifier = Modifier.padding(top = Spacing.M),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(LaunchBoundsPreset.entries, key = LaunchBoundsPreset::name) { preset ->
                SelectionButton(
                    selected = preset == selectedPreset,
                    text = preset.label,
                    onClick = { selectedPreset = preset },
                )
            }
            item {
                HorizontalDivider()
                Text(
                    "Ứng dụng launchable",
                    modifier = Modifier.padding(top = Spacing.M),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            when {
                apps == null -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                catalogFailed -> item { Text("Không thể đọc danh sách ứng dụng.") }
                apps.isNullOrEmpty() -> item { Text("Không có ứng dụng có thể mở.") }
                else -> items(
                    items = apps.orEmpty().take(DEBUG_APP_LIMIT),
                    key = { it.identity.toString() },
                ) { app ->
                    AppSelectionCard(
                        app = app,
                        selected = app.identity == selectedApp?.identity,
                        onClick = { selectedApp = app },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton),
        ) { Text(text) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton),
        ) { Text(text) }
    }
}

@Composable
private fun AppSelectionCard(
    app: InstalledApp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selected) "✓ ${app.label}" else app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(app.packageName, style = MaterialTheme.typography.bodyMedium)
            Text(app.activityName.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WorkAreaDiagnostics(
    snapshot: DisplayWorkAreaSnapshot?,
    calculatedBounds: PixelBounds?,
    hostDisplayId: Int?,
    legacyDiagnostics: LegacyWorkAreaDiagnostics?,
) {
    if (snapshot == null) {
        Text("Không đọc được vùng làm việc DeX.")
        LegacyCandidateDiagnostics(legacyDiagnostics)
        return
    }
    val area = snapshot.workArea
    val marginPx = launchMarginPx(snapshot.density)
    val displayMetricsBounds = snapshot.insetCandidates
        .firstOrNull { it.label == "display.metrics" }
        ?.referenceBounds
    val realWidth = snapshot.rawDisplayBounds.width
    val realHeight = snapshot.rawDisplayBounds.height
    val appWidth = displayMetricsBounds?.width
    val appHeight = displayMetricsBounds?.height
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text("host displayId: ${hostDisplayId ?: "N/A"}")
        Text("snapshot displayId: ${snapshot.displayId}")
        Text("raw display: ${snapshot.rawDisplayBounds}")
        Text("realMetrics width/height: $realWidth / $realHeight")
        Text("display.getMetrics width/height: ${appWidth ?: "N/A"} / ${appHeight ?: "N/A"}")
        Text("realHeight: $realHeight")
        Text("appHeight: ${appHeight ?: "N/A"}")
        Text("realHeight - appHeight: ${appHeight?.let { realHeight - it } ?: "N/A"}")
        Text(
            "metrics delta L/T/R/B: 0/0/" +
                "${appWidth?.let { realWidth - it } ?: "N/A"}/" +
                "${appHeight?.let { realHeight - it } ?: "N/A"}",
        )
        Text("host window: ${snapshot.hostWindowBounds}")
        Text(
            "insets L/T/R/B: ${area.insetLeftPx}/${area.insetTopPx}/" +
                "${area.insetRightPx}/${area.insetBottomPx}",
        )
        Text("usable: ${area.usableWidth} × ${area.usableHeight}")
        Text("density: ${snapshot.density}")
        Text("marginPx: $marginPx")
        Text("host mode: ${snapshot.hostWindowMode}")
        Text("selected inset source: ${snapshot.selectedInsetSource}")
        snapshot.insetCandidates.forEachIndexed { index, candidate ->
            val candidateArea = candidate.workAreaOrNull(snapshot.rawDisplayBounds)
            Text(
                "Candidate ${index + 1} (${candidate.label}): " +
                    "source=${candidate.source}, space=${candidate.coordinateSpace}, " +
                    "insets=${candidate.insets}, workArea=${candidateArea ?: "REJECTED"}",
            )
        }
        Text("Final work area: $area")
        Text("calculated bounds: ${calculatedBounds ?: "Chưa tính"}")
        Text(
            "PixelBounds inside usable area: " +
                (calculatedBounds?.let {
                    LaunchBoundsSanity.isWithinWorkArea(it, area)
                } ?: "Chưa tính"),
        )
        LegacyCandidateDiagnostics(legacyDiagnostics)
    }
}

@Composable
private fun LegacyCandidateDiagnostics(diagnostics: LegacyWorkAreaDiagnostics?) {
    if (diagnostics == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            "metrics display IDs host/real/app: " +
                "${diagnostics.hostDisplayId}/${diagnostics.realMetricsDisplayId}/" +
                diagnostics.displayMetricsDisplayId,
        )
        Text("legacy real: ${diagnostics.realDisplayBounds ?: "N/A"}")
        Text("legacy app metrics: ${diagnostics.displayMetricsBounds ?: "N/A"}")
        Text("legacy host: ${diagnostics.hostWindowBounds ?: "N/A"}")
        diagnostics.candidates.forEach { candidate ->
            Text(
                "${candidate.label}: source=${candidate.source}, " +
                    "evaluation=${candidate.evaluation}, insets=${candidate.insets ?: "N/A"}",
            )
        }
        Text("legacy selected source: ${diagnostics.selectedSource ?: "UNAVAILABLE"}")
    }
}

private fun SingleAppLaunchResult.toUiMessage(label: String): String = when (this) {
    is SingleAppLaunchResult.Success -> "Đã gửi yêu cầu mở $label."
    is SingleAppLaunchResult.Failure -> failure.reason.toUiMessage()
}

private fun AppLaunchFailureReason.toUiMessage(): String = when (this) {
    AppLaunchFailureReason.APP_NOT_FOUND -> "APP_NOT_FOUND: Ứng dụng không còn tồn tại."
    AppLaunchFailureReason.ACTIVITY_NOT_FOUND ->
        "ACTIVITY_NOT_FOUND: Không tìm thấy activity đã chọn."
    AppLaunchFailureReason.DISPLAY_UNAVAILABLE ->
        "DISPLAY_UNAVAILABLE: Không đọc được màn hình DeX."
    AppLaunchFailureReason.SECURITY_RESTRICTION ->
        "SECURITY_RESTRICTION: Hệ thống từ chối quyền mở ứng dụng."
    AppLaunchFailureReason.LAUNCH_REJECTED ->
        "LAUNCH_REJECTED: Bounds hoặc yêu cầu mở bị từ chối."
    AppLaunchFailureReason.UNKNOWN -> "UNKNOWN: Không thể mở ứng dụng."
}

private enum class LaunchBoundsPreset(
    val label: String,
    val bounds: NormalizedBounds,
) {
    FULL("Toàn màn hình", NormalizedBounds.FullCanvas),
    LEFT("Nửa trái", NormalizedBounds(0f, 0f, 0.5f, 1f)),
    RIGHT("Nửa phải", NormalizedBounds(0.5f, 0f, 1f, 1f)),
    TOP("Nửa trên", NormalizedBounds(0f, 0f, 1f, 0.5f)),
    BOTTOM("Nửa dưới", NormalizedBounds(0f, 0.5f, 1f, 1f)),
    TOP_LEFT("Góc trên trái 50% × 50%", NormalizedBounds(0f, 0f, 0.5f, 0.5f)),
}

private const val DEBUG_APP_LIMIT = 12
private const val DEBUG_LOG_TAG = "DexLaunchWorkAreaDebug"
private const val DEBUG_FINISH_DELAY_MS = 750L
