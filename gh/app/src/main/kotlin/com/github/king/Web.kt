package com.github.king

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import java.lang.ref.WeakReference

object Web {

    @Volatile
    var isFolderUpload = false

    @Volatile
    var isReceiverRegistered = false
    var currentActivityRef: WeakReference<MainActivity>? = null
    var currentUrlIndex = 0

    fun setup(activity: MainActivity, webView: WebView, pick: Pick, root: ViewGroup) {
        currentActivityRef = WeakReference(activity)
        currentUrlIndex = 0
        val tip = Tip(activity, root)
        
        if (!isReceiverRegistered) {
            isReceiverRegistered = true
            val appContext = activity.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id != -1L) {
                            val q = DownloadManager.Query().setFilterById(id)
                            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                            val c = dm?.query(q)
                            if (c != null && c.moveToFirst()) {
                                val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                if (statusIndex >= 0 && c.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                                    val titleIndex = c.getColumnIndex(DownloadManager.COLUMN_TITLE)
                                    val title = if (titleIndex >= 0) c.getString(titleIndex) else "文件"
                                    val currAct = currentActivityRef?.get()
                                    if (currAct != null) {
                                        currAct.runOnUiThread {
                                            Toas.show(currAct, "下载成功: $title")
                                        }
                                    }
                                }
                                c.close()
                            }
                        }
                    }
                }
            }
            try {
                appContext.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            } catch (e: Exception) {}
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.addJavascriptInterface(TBr(activity), "AndroidBridge")
        webView.addJavascriptInterface(Down.Bridge(activity), "DownBridge")
        
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun download(url: String, filename: String) {
                activity.runOnUiThread {
                    Down.execute(activity, url, "", "attachment; filename=\"$filename\"", "*/*")
                }
            }
        }, "Html5Downloader")

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            Down.execute(activity, url, userAgent, contentDisposition, mimetype)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
                    val ext = url.substringAfterLast('.', "").substringBefore('?').lowercase()
                    val exts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "rar", "7z", "apk", "txt", "csv")
                    if (ext in exts) {
                        Down.execute(activity, url, "", "", "*/*")
                        return true
                    }
                    return false
                }
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toas.show(activity, "未安装相关应用")
                }
                return true
            }

            private fun handleFallback(v: WebView) {
                if (Conf.remoteUrls.isNotEmpty() && currentUrlIndex < Conf.remoteUrls.size - 1) {
                    currentUrlIndex++
                    v.loadUrl(Conf.remoteUrls[currentUrlIndex])
                } else {
                    val fallbackUrl = if (Conf.localHtml.isNotEmpty()) {
                        if (Conf.localHtml.startsWith("http")) Conf.localHtml else "file:///android_asset/web/${Conf.localHtml}"
                    } else "file:///android_asset/web/index.html"
                    
                    if (v.url != fallbackUrl) {
                        v.loadUrl(fallbackUrl)
                    } else {
                        tip.showError { v.reload() }
                    }
                }
            }

            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) {
                    val urlStr = r.url.toString()
                    if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                        handleFallback(v)
                    } else {
                        tip.showError { v.reload() }
                    }
                }
            }

            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                if (r.isForMainFrame && e.statusCode == 404) {
                    val urlStr = r.url.toString()
                    if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                        handleFallback(v)
                    } else {
                        tip.showError { v.reload() }
                    }
                }
            }

            override fun onPageStarted(v: WebView?, u: String?, b: android.graphics.Bitmap?) {
                tip.hide()
            }

            override fun onPageFinished(v: WebView, u: String) {
                val inputJs = """
                    (function(){
                        if (window.__webcore_injected) return;
                        window.__webcore_injected = true;
                        function checkDir(el) {
                            var isDir = el.hasAttribute('webkitdirectory') || el.hasAttribute('directory');
                            if (window.AndroidBridge && window.AndroidBridge.setUploadType) {
                                window.AndroidBridge.setUploadType(isDir);
                            }
                        }
                        var origClick = HTMLInputElement.prototype.click;
                        HTMLInputElement.prototype.click = function() {
                            if (this.type === 'file') checkDir(this);
                            origClick.apply(this, arguments);
                        };
                        document.addEventListener('click', function(e) {
                            if (e.target && e.target.tagName === 'INPUT' && e.target.type === 'file') {
                                checkDir(e.target);
                            }
                            var a = e.target.closest('a');
                            if (a) {
                                var href = a.href || '';
                                var hasDownload = a.hasAttribute('download');
                                var isMedia = /\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|apk|txt|csv)(\?.*)?$/i.test(href);
                                if (hasDownload || isMedia) {
                                    e.preventDefault();
                                    var name = a.getAttribute('download') || href.split('/').pop().split('?')[0] || 'download_file';
                                    if (href.startsWith('blob:')) {
                                        var xhr = new XMLHttpRequest();
                                        xhr.open('GET', href);
                                        xhr.responseType = 'blob';
                                        xhr.onload = function() {
                                            var reader = new FileReader();
                                            reader.onloadend = function() {
                                                if (window.DownBridge && window.DownBridge.postBlob) {
                                                    window.DownBridge.postBlob(reader.result, xhr.response.type, name);
                                                }
                                            };
                                            reader.readAsDataURL(xhr.response);
                                        };
                                        xhr.send();
                                    } else if (href.startsWith('data:')) {
                                         if (window.DownBridge && window.DownBridge.postBlob) {
                                             window.DownBridge.postBlob(href, '', name);
                                         }
                                    } else {
                                        if (window.Html5Downloader && window.Html5Downloader.download) {
                                            window.Html5Downloader.download(href, name);
                                        }
                                    }
                                }
                            }
                        }, true);
                    })();
                """.trimIndent()
                v.loadUrl("javascript:${Them.getInjectedJs()};$inputJs")
                
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(wv: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
                if (isFolderUpload) {
                    pick.pickFolder(filePathCallback)
                } else {
                    val isMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    pick.pickFiles(isMultiple, filePathCallback)
                }
                isFolderUpload = false
                return true
            }
        }
    }
}
