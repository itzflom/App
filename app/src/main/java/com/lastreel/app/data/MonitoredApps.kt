package com.lastreel.app.data

/** The apps Last Reel watches. Matching is by Android package name only. */
object MonitoredApps {
    private val packages = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok (most regions)
        "com.ss.android.ugc.trill", // TikTok (Asia-Pacific build)
    )

    fun isMonitored(packageName: String): Boolean = packageName in packages
}
