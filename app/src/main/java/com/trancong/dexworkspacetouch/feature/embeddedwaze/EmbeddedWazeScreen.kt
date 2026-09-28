package com.trancong.dexworkspacetouch.feature.embeddedwaze

import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSession
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppState
import com.trancong.dexworkspacetouch.feature.embeddedapp.mapPoint
import com.trancong.dexworkspacetouch.feature.embeddedapp.pressureFor
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedWazeScreen(activity: Activity, onBack: () -> Unit) {
    var state by remember { mutableStateOf(EmbeddedAppState()) }
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    val target = WAZE_EMBEDDED_TARGET
    val session = remember(activity) {
        EmbeddedAppSession(activity.applicationContext, target) { state = it }
    }
    DisposableEffect(session) {
        session.start()
        onDispose { session.close() }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Embedded Waze (Experimental)") },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(state.status)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = session::connect, enabled = !state.shellReady && !state.busy) {
                    Text("Connect Shizuku")
                }
                Button(onClick = { surface?.holder?.surface?.let(session::startSession) },
                    enabled = state.shellReady && !state.active && !state.busy &&
                        surface?.holder?.surface?.isValid == true) { Text("Start Waze") }
                OutlinedButton(onClick = session::stop,
                    enabled = state.active && !state.busy) { Text("Stop") }
            }
            AndroidView(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f), factory = { context ->
                SurfaceView(context).also { view ->
                    surface = view
                    view.holder.setFixedSize(target.geometry.width, target.geometry.height)
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = Unit
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int,
                            width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            if (state.active) session.stop()
                        }
                    })
                    view.setOnTouchListener { _, event ->
                        if (!state.active) return@setOnTouchListener false
                        val point = mapPoint(event.x, event.y, view.width, view.height, target.geometry)
                        val action = runCatching { virtualAction(event.actionMasked) }
                            .getOrElse { return@setOnTouchListener false }
                        session.touch(action, point.x, point.y,
                            pressureFor(event.actionMasked, event.pressure),
                            event.eventTime * 1_000_000L)
                        true
                    }
                }
            })
        }
    }
}
