package com.github.king

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private lateinit var pick: Pick

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Conf.init(this)
        Them.initSystemBar(this)
        pick = Pick(this)
        val root = FrameLayout(this)
        
        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            overScrollMode = WebView.OVER_SCROLL_NEVER
        }

        root.addView(webView, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        Web.setup(this, webView!!, pick, root)
        
        webView?.loadUrl(Conf.getStartUrl())
        Back.register(this, webView)
        Img.show(this)

        val filter = android.content.IntentFilter("GK_MEDIA_ACTION")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mediaReceiver, filter)
        }

        checkGithubCallback(intent)
    }

    private val mediaReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "GK_MEDIA_ACTION") {
                val cmd = intent.getStringExtra("cmd")
                if (cmd == "NEXT") {
                    webView?.evaluateJavascript("if(window.playNextAudio) window.playNextAudio();", null)
                } else if (cmd == "PREV") {
                    webView?.evaluateJavascript("if(window.playPrevAudio) window.playPrevAudio();", null)
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mediaReceiver)
        } catch (e: Exception) {}
        
        webView?.let {
            it.stopLoading()
            it.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
            it.clearHistory()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    fun launchGithubAuth() {
        runOnUiThread {
            val clientId = "Ov23liHvV3pKNTQ1G3Dt"
            val redirectUri = Uri.encode("gk://login")
            val allScopes = "repo,repo:status,repo_deployment,public_repo,repo:invite,security_events,admin:repo_hook,write:repo_hook,read:repo_hook,admin:org,write:org,read:org,admin:public_key,write:public_key,read:public_key,admin:org_hook,gist,notifications,user,read:user,user:email,user:follow,project,read:project,delete_repo,write:packages,read:packages,delete:packages,admin:gpg_key,write:gpg_key,read:gpg_key,workflow"
            val url = "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$redirectUri&scope=$allScopes"
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkGithubCallback(intent)
    }

    private fun checkGithubCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "gk" && uri.host == "login") {
                runOnUiThread { Toas.show(this, "正在验证授权，请稍候...") }
                
                val code = uri.getQueryParameter("code")
                if (!code.isNullOrEmpty()) {
                    exchangeCodeForToken(code)
                } else {
                    runOnUiThread { Toas.show(this, "授权失败: 未获取到 Code") }
                    webView?.postDelayed({
                        webView?.evaluateJavascript("window.onReceiveAuthToken(null)", null)
                    }, 800)
                }
            }
        }
    }

    private fun exchangeCodeForToken(code: String) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val url = URL("https://github.com/login/oauth/access_token")
                val connection = url.openConnection() as HttpURLConnection
                
                connection.connectTimeout = 10000 
                connection.readTimeout = 10000
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.doOutput = true
                
                val clientId = "Ov23liHvV3pKNTQ1G3Dt"
                val clientSecret = "aef6d96382c7fab2f4bc0a1ec97870fc4be63397"
                val params = "client_id=$clientId&client_secret=$clientSecret&code=$code"
                
                connection.outputStream.write(params.toByteArray())
                
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val response = stream.bufferedReader().readText()
                
                val json = JSONObject(response)
                val token = json.optString("access_token")
                val errorDesc = json.optString("error_description", "未知授权错误")
                
                runOnUiThread {
                    webView?.postDelayed({
                        if (token.isNotEmpty()) {
                            Toas.show(this@MainActivity, "安全登录成功！")
                            webView?.evaluateJavascript("window.onReceiveAuthToken('$token')", null)
                        } else {
                            Toas.show(this@MainActivity, "GitHub拒绝: $errorDesc")
                            webView?.evaluateJavascript("window.onReceiveAuthToken(null)", null)
                        }
                    }, 800)
                }
            } catch (e: Exception) {
                runOnUiThread { 
                    val msg = e.message?.lowercase() ?: ""
                    val hint = if (msg.contains("timeout") || msg.contains("timed out")) {
                        "官网超时：请检查 VPN 是否已代理此 APP！"
                    } else {
                        "网络异常: ${e.message}"
                    }
                    Toas.show(this@MainActivity, hint)
                    
                    webView?.postDelayed({
                        webView?.evaluateJavascript("window.onReceiveAuthToken(null)", null)
                    }, 800)
                }
            }
        }
    }
}
