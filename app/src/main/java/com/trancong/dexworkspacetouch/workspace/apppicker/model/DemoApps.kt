package com.trancong.dexworkspacetouch.workspace.apppicker.model

object DemoApps {
    val all: List<DemoApp> = listOf(
        DemoApp("com.google.android.apps.maps", "com.google.android.maps.MapsActivity", "Google Maps"),
        DemoApp("com.waze", "com.waze.FreeMapAppActivity", "Waze"),
        DemoApp("com.android.chrome", "com.google.android.apps.chrome.Main", "Chrome"),
        DemoApp("com.google.android.youtube", "com.google.android.youtube.HomeActivity", "YouTube"),
        DemoApp("com.spotify.music", "com.spotify.music.MainActivity", "Spotify"),
        DemoApp("com.sec.android.app.camera", "com.sec.android.app.camera.Camera", "Camera"),
        DemoApp("com.google.android.calendar", "com.android.calendar.AllInOneActivity", "Calendar"),
        DemoApp("org.videolan.vlc", "org.videolan.vlc.StartActivity", "VLC"),
    )

    fun search(query: String): List<DemoApp> {
        val term = query.trim()
        if (term.isEmpty()) return all
        return all.filter { app ->
            app.label.contains(term, ignoreCase = true) ||
                app.packageName.contains(term, ignoreCase = true)
        }
    }
}
