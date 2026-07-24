package com.github.king

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.browser.customtabs.CustomTabsIntent

class TBr(private val activity: Activity) {
    @JavascriptInterface
    fun postMessage(jsonStr: String) {
        Them.apply(activity, jsonStr)
    }

    @JavascriptInterface
    fun setUploadType(isFolder: Boolean) {
        Web.isFolderUpload = isFolder
    }

    @JavascriptInterface
    fun startGithubAuth() {
        if (activity is MainActivity) {
            activity.launchGithubAuth()
        }
    }

    @JavascriptInterface
    fun openInBrowser(url: String) {
        activity.runOnUiThread {
            try {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(activity, Uri.parse(url))
            } catch (e: Throwable) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    activity.startActivity(intent)
                } catch (e2: Throwable) {}
            }
        }
    }

    @JavascriptInterface
    fun playAudio(url: String, title: String) {
        activity.runOnUiThread {
            try {
                val intent = Intent(activity, MediaService::class.java).apply {
                    action = "PLAY"
                    putExtra("url", url)
                    putExtra("title", title)
                }
                activity.startService(intent)
            } catch (e: Throwable) {}
        }
    }

    @JavascriptInterface
    fun stopAudio() {
        activity.runOnUiThread {
            try {
                val intent = Intent(activity, MediaService::class.java).apply { action = "STOP" }
                activity.startService(intent)
            } catch (e: Throwable) {}
        }
    }
}
