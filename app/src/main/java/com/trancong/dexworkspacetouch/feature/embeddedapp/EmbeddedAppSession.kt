package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

data class EmbeddedAppState(
    val shellReady: Boolean = false,
    val busy: Boolean = false,
    val active: Boolean = false,
    val status: String = "Connect Shizuku to begin",
    val displayId: Int = -1,
)

class EmbeddedAppSession(
    private val context: Context,
    private val target: EmbeddedAppTarget,
    private val changed: (EmbeddedAppState) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val stopGate = SessionStopGate()
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName,
        "com.trancong.dexworkspacetouch.feature.embeddedapp.remote.EmbeddedAppUserService"))
        .daemon(false).processNameSuffix("embedded_app").tag("dwt-vdm-002")
        .version(BuildConfig.VERSION_CODE + 1001).debuggable(BuildConfig.DEBUG)
    private var state = EmbeddedAppState()
    private var remote: IEmbeddedAppService? = null
    private var connection: ServiceConnection? = null
    private var closed = false
    private val received = Shizuku.OnBinderReceivedListener { refreshPermission() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST) {
            if (result == PackageManager.PERMISSION_GRANTED) bind()
            else update(state.copy(status = "Shizuku permission denied"))
        }
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addRequestPermissionResultListener(permission)
        changed(state)
    }

    fun connect() {
        if (!Shizuku.pingBinder()) return update(state.copy(status = "Shizuku is unavailable"))
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST)
        } else bind()
    }

    private fun refreshPermission() {
        if (!closed && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
    }

    private fun bind() {
        if (connection != null || closed) return
        update(state.copy(busy = true, status = "Connecting shell UserService"))
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                remote = IEmbeddedAppService.Stub.asInterface(binder)
                val uid = runCatching { remote!!.uid }.getOrDefault(-1)
                update(state.copy(shellReady = uid == 2000, busy = false,
                    status = if (uid == 2000) "Shell UID 2000 ready" else "Unexpected remote UID $uid"))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                remote = null
                connection = null
                update(EmbeddedAppState(status = "Shell UserService disconnected"))
            }
        }
        connection = newConnection
        runCatching { Shizuku.bindUserService(args, newConnection) }.onFailure {
            connection = null
            update(state.copy(busy = false, status = "Bind failed: ${it.message}"))
        }
    }

    fun startSession(surface: Surface) {
        val service = remote ?: return update(state.copy(status = "Connect Shizuku first"))
        if (!surface.isValid) return update(state.copy(status = "Surface is not valid"))
        update(state.copy(busy = true, status = "Creating trusted VDM session"))
        worker.execute {
            val geometry = target.geometry
            val result = runCatching { service.startSession(surface, target.packageName,
                target.componentName, geometry.width, geometry.height, geometry.densityDpi) }
            main.post {
                result.fold({ bundle ->
                    if (bundle.getBoolean("success")) {
                        stopGate.reset()
                        update(state.copy(busy = false, active = true,
                            displayId = bundle.getInt("displayId"),
                            status = "App launched on trusted display ${bundle.getInt("displayId")}"))
                    } else update(state.copy(busy = false,
                        status = bundle.getString("exception") ?: "Session start failed"))
                }, { update(state.copy(busy = false, status = "Session start failed: ${it.message}")) })
            }
        }
    }

    fun touch(action: Int, x: Float, y: Float, pressure: Float, time: Long) {
        val service = remote ?: return
        if (!state.active) return
        worker.execute {
            val result = runCatching { service.sendTouch(action, x, y, pressure, time) }.getOrNull()
            if (result?.getBoolean("success") != true) main.post {
                update(state.copy(status = result?.getString("exception") ?: "Touch delivery failed"))
            }
        }
    }

    fun stop() {
        val service = remote
        if (service == null) return update(state.copy(active = false, displayId = -1))
        if (!stopGate.request()) return
        update(state.copy(busy = true, status = "Stopping embedded session"))
        worker.execute {
            val result = runCatching { service.stopSession() }.getOrNull()
            main.post {
                update(EmbeddedAppState(shellReady = true,
                    status = if (result?.getBoolean("success") == true) "Embedded session stopped"
                    else result?.getString("exception") ?: "Cleanup failed"))
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        if (state.active && stopGate.request()) runCatching { remote?.stopSession() }
        connection?.let { runCatching { Shizuku.unbindUserService(args, it, true) } }
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeRequestPermissionResultListener(permission)
        worker.shutdownNow()
    }

    private fun update(value: EmbeddedAppState) {
        state = value
        if (!closed) changed(value)
    }

    private companion object { const val REQUEST = 7101 }
}
