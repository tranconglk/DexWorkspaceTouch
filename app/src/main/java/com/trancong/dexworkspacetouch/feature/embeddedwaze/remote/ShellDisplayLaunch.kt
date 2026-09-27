package com.trancong.dexworkspacetouch.feature.embeddedwaze.remote

import android.app.ActivityOptions
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import java.lang.reflect.InvocationTargetException

/** Firmware-verified Android 16 signature. Launches only the harmless same-app lab marker. */
object ShellDisplayLaunch {
    // Intentional firmware-specific hidden API, executed only in Shizuku shell process.
    @android.annotation.SuppressLint("PrivateApi")
    fun launch(displayId: Int): Bundle = launchComponent(displayId,
        "com.trancong.taskviewlab", "com.trancong.taskviewlab.ProbeTargetActivity", false)

    @android.annotation.SuppressLint("PrivateApi")
    internal fun launchComponent(displayId: Int, packageName: String, className: String,
        thirdParty: Boolean): Bundle {
        val reply = Bundle()
        val identity = Binder.clearCallingIdentity()
        try {
            check(Process.myUid() == 2000) { "Requires shell UID 2000" }
            require(displayId > 0) { "Only a secondary display may be targeted" }
            reply.putInt("remoteUid", Process.myUid())
            val intent = Intent().setClassName(packageName,
                className).addFlags((if (thirdParty) Intent.FLAG_ACTIVITY_NEW_TASK else
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT) or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            if (thirdParty) {
                intent.action = Intent.ACTION_MAIN
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            val manager = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService").invoke(null)
            val api = Class.forName("android.app.IActivityTaskManager").getMethod(
                "startActivityAsUser", Class.forName("android.app.IApplicationThread"),
                String::class.java, String::class.java, Intent::class.java, String::class.java,
                IBinder::class.java, String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Class.forName("android.app.ProfilerInfo"),
                Bundle::class.java, Int::class.javaPrimitiveType)
            Log.i("TaskViewLab.VDRemote", "TVL005 launch component=${intent.component} flags=${intent.flags} before ATM uid=${Process.myUid()} binderCallingUid=${Binder.getCallingUid()} callingPackage=com.android.shell displayId=$displayId")
            val result = api.invoke(manager, null, "com.android.shell", null, intent, null,
                null, null, -1, 0, null, options, 0) as Int
            reply.putInt("result", result)
            reply.putBoolean("success", result == 0)
            Log.i("TaskViewLab.VDRemote", "TVL005 startActivityAsUser result=$result displayId=$displayId")
        } catch (e: Exception) {
            val cause = if (e is InvocationTargetException) e.targetException else e
            reply.putBoolean("success", false)
            reply.putString("exception", "${cause.javaClass.name}: ${cause.message}")
            Log.e("TaskViewLab.VDRemote", "TVL005 launch failed displayId=$displayId", cause)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        return reply
    }
}
