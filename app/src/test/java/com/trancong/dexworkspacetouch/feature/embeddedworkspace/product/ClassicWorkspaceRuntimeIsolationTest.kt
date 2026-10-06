package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchViewModel
import java.io.File
import java.util.jar.JarFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks compiled Classic classes, including coroutine/factory classes, rather than source spelling. */
class ClassicWorkspaceRuntimeIsolationTest {
    @Test fun classicLaunchClassesCannotConstructOrCallEmbeddedExecutionMachinery() {
        val classes = classicClasses()
        for (required in listOf("WorkspaceLaunchViewModel.class", "WorkspaceLaunchRequestFactory.class",
            "AndroidWorkspaceLaunchRuntime.class", "AndroidWorkspaceLauncher.class")) {
            assertTrue("Missing compiled Classic class: $required", classes.keys.any { it.endsWith("/$required") })
        }
        val forbidden = listOf(
            "com/trancong/dexworkspacetouch/feature/embeddedapp/",
            "com/trancong/dexworkspacetouch/feature/embeddedworkspace/",
            "com/trancong/dexworkspacetouch/workspace/execution/embedded/",
            "android/view/Surface",
            "android/hardware/display/VirtualDisplay",
        )
        for ((name, bytes) in classes) {
            val constants = bytes.toString(Charsets.ISO_8859_1)
            for (dependency in forbidden) {
                assertFalse("$name depends on Embedded execution/resource type $dependency", constants.contains(dependency))
            }
        }
    }

    private fun classicClasses(): Map<String, ByteArray> {
        val root = File(checkNotNull(WorkspaceLaunchViewModel::class.java.protectionDomain.codeSource).location.toURI())
        fun includes(name: String): Boolean = name.endsWith(".class") && (
            name.startsWith("com/trancong/dexworkspacetouch/workspace/launcher/") ||
                name.startsWith("com/trancong/dexworkspacetouch/platform/launch/android/AndroidWorkspaceLaunchRuntime") ||
                name.startsWith("com/trancong/dexworkspacetouch/platform/launch/android/AndroidWorkspaceLauncher"))
        return if (root.isDirectory) {
            root.walkTopDown().filter { it.isFile && includes(it.relativeTo(root).invariantSeparatorsPath) }
                .associate { it.relativeTo(root).invariantSeparatorsPath to it.readBytes() }
        } else {
            JarFile(root).use { jar ->
                jar.entries().asSequence().filter { includes(it.name) }
                    .associate { it.name to jar.getInputStream(it).use { stream -> stream.readBytes() } }
            }
        }
    }
}
