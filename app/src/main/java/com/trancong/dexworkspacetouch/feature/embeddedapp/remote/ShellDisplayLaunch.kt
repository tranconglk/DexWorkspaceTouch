package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.app.ActivityOptions
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import java.lang.reflect.InvocationTargetException

object ShellDisplayLaunch {
    @android.annotation.SuppressLint("PrivateApi")
    fun launch(displayId: Int, target: EmbeddedAppTarget): Bundle {
        val reply = Bundle()
        val identity = Binder.clearCallingIdentity()
        try {
            check(Process.myUid() == 2000) { "Requires shell UID 2000" }
            require(displayId > 0) { "Only a secondary display may be targeted" }
            reply.putInt("remoteUid", Process.myUid())
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(target.packageName, target.componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
            val manager = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService").invoke(null)
            val api = Class.forName("android.app.IActivityTaskManager").getMethod(
                "startActivityAsUser", Class.forName("android.app.IApplicationThread"),
                String::class.java, String::class.java, Intent::class.java, String::class.java,
                IBinder::class.java, String::class.java, Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Class.forName("android.app.ProfilerInfo"),
                Bundle::class.java, Int::class.javaPrimitiveType,
            )
            Log.i(TAG, "launch component=${intent.component} flags=${intent.flags} uid=${Process.myUid()} displayId=$displayId")
            val result = api.invoke(manager, null, "com.android.shell", null, intent, null,
                null, null, -1, 0, null, options, 0) as Int
            reply.putInt("result", result)
            reply.putBoolean("success", result == 0)
        } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            reply.putBoolean("success", false)
            reply.putString("exception", "${cause.javaClass.name}: ${cause.message}")
            Log.e(TAG, "launch failed displayId=$displayId", cause)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        return reply
    }

    private const val TAG = "DWT.EmbeddedAppLaunch"
}
