package com.github.king

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

object Back {
    private var lastBackTime: Long = 0

    fun register(activity: ComponentActivity, webView: WebView?) {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView ?: return
                wv.evaluateJavascript("window.forceAppBack()") { v ->
                    if (v != "true" && v != "\"true\"") {
                        if (wv.canGoBack()) {
                            wv.goBack()
                        } else {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastBackTime > 2000) {
                                Toas.show(activity, "再按一次返回桌面")
                                lastBackTime = currentTime
                            } else {
                                activity.moveTaskToBack(true)
                            }
                        }
                    }
                }
            }
        })
    }
}
