package com.trancong.dexworkspacetouch.workspace.close.remote

import android.content.ComponentName
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.trancong.dexworkspacetouch.workspace.close.*
import java.lang.reflect.InvocationTargetException

/** getTasks traverses activity leaf tasks; root/stack IDs are diagnostic metadata only. */
internal abstract class AndroidCloseTaskControl(private val profile: CloseAdapterProfile) : CloseTaskControl {
    protected val api: Class<*> = Class.forName("android.app.IActivityTaskManager")
    protected val manager: Any = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)!!
    private val remove = api.getMethod("removeTask", Int::class.javaPrimitiveType)
    private var sequence = 0
    protected abstract fun rawTasks(): List<*>
    protected abstract fun id(value: Any): Int
    protected abstract fun parent(value: Any): Int

    init { check(Process.myUid() == 2000) { "SHELL_UID_REQUIRED" } }

    override fun enumerate(): CloseSnapshot {
        val raw = rawTasks()
        val tasks = raw.map { entry ->
            val value = checkNotNull(entry)
            CloseTask(id(value), integer(value, "userId"), integer(value, "displayId"), parent(value),
                component(value, "baseActivity"), component(value, "topActivity"), true)
        }
        return CloseSnapshot(tasks, raw.size < 10_000, ++sequence, SystemClock.elapsedRealtime(), profile.name, Build.FINGERPRINT,
            if (raw.size >= 10_000) "ENUMERATION_INCOMPLETE" else null)
    }
    override fun removeTask(taskId: Int) = invoke(remove, taskId) as Boolean
    protected fun invoke(method: java.lang.reflect.Method, vararg args: Any): Any? = try { method.invoke(manager, *args) }
    catch (e: InvocationTargetException) { throw (e.cause ?: e) }
    protected fun integer(value: Any, field: String) = value.javaClass.getField(field).getInt(value)
    protected fun optionalInteger(value: Any, field: String) = try { integer(value, field) } catch (_: NoSuchFieldException) { -1 }
    private fun component(value: Any, field: String): String? = (value.javaClass.getField(field).get(value) as ComponentName?)?.flattenToString()
}

internal class Api29CloseTaskControl : AndroidCloseTaskControl(CloseAdapterProfile.NOTE9_API29) {
    private val enumerate = api.getMethod("getTasks", Int::class.javaPrimitiveType)
    override fun rawTasks() = invoke(enumerate, 10_000) as List<*>
    override fun id(value: Any) = integer(value, "id")
    override fun parent(value: Any) = optionalInteger(value, "stackId")
}
internal class Api36CloseTaskControl : AndroidCloseTaskControl(CloseAdapterProfile.S23_API36) {
    private val enumerate = api.getMethod("getTasks", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    override fun rawTasks() = invoke(enumerate, 10_000, false, false, -1) as List<*>
    override fun id(value: Any) = integer(value, "taskId")
    override fun parent(value: Any) = optionalInteger(value, "parentTaskId")
}
