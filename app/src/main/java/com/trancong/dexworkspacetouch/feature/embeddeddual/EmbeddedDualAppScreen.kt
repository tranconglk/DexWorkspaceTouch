package com.trancong.dexworkspacetouch.feature.embeddeddual

import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedcalculator.CALCULATOR_EMBEDDED_TARGET
import com.trancong.dexworkspacetouch.feature.embeddedwaze.WAZE_EMBEDDED_TARGET

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EmbeddedDualAppScreen(activity: Activity, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Embedded Dual App (Experimental/Frozen)") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Row(Modifier.padding(padding).fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmbeddedProofPane(activity, "A — Waze", WAZE_EMBEDDED_TARGET, Modifier.weight(1f))
            EmbeddedProofPane(activity, "B — Calculator", CALCULATOR_EMBEDDED_TARGET, Modifier.weight(1f))
        }
    }
}

@Composable private fun EmbeddedProofPane(activity: Activity, label: String, target: EmbeddedAppTarget,
    modifier: Modifier) {
    var state by remember { mutableStateOf(EmbeddedAppState()) }
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    val session = remember(activity, target) { EmbeddedAppSession(activity.applicationContext, target) { state = it } }
    DisposableEffect(session) { session.start(); onDispose { session.close() } }
    Card(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label); Text("${state.status} | display=${state.displayId} | session=${session.sessionId.value.take(8)}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = session::connect, enabled = !state.shellReady && !state.busy) { Text("Connect") }
                Button(onClick = { surface?.holder?.surface?.let(session::startSession) },
                    enabled = state.shellReady && !state.active && !state.busy && surface?.holder?.surface?.isValid == true) { Text("Start") }
                OutlinedButton(onClick = session::stop, enabled = state.active && !state.busy) { Text("Stop") }
            }
            AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), factory = { context ->
                SurfaceView(context).also { view ->
                    surface = view; view.holder.setFixedSize(target.geometry.width, target.geometry.height)
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) { if (state.active) session.stop() }
                    })
                    view.setOnTouchListener { _, event ->
                        if (!state.active) return@setOnTouchListener false
                        val p = mapPoint(event.x, event.y, view.width, view.height, target.geometry)
                        val action = runCatching { virtualAction(event.actionMasked) }.getOrElse { return@setOnTouchListener false }
                        session.touch(action, p.x, p.y, pressureFor(event.actionMasked, event.pressure), event.eventTime * 1_000_000L)
                        true
                    }
                }
            })
        }
    }
}
