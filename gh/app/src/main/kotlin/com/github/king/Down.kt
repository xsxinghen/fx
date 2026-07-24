package com.github.king

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import java.io.File
import java.io.FileOutputStream

object Down {
    private const val SAVE_FOLDER = "GK"

    fun init() {
        try {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SAVE_FOLDER)
            if (!folder.exists()) folder.mkdirs()
        } catch (e: Exception) {}
    }

    private fun getUniqueFileName(folder: File, fileName: String): String {
        var file = File(folder, fileName)
        if (!file.exists()) return fileName
        val dotIndex = fileName.lastIndexOf('.')
        val name = if (dotIndex == -1) fileName else fileName.substring(0, dotIndex)
        val ext = if (dotIndex == -1) "" else fileName.substring(dotIndex)
        var counter = 1
        while (file.exists()) {
            file = File(folder, "$name($counter)$ext")
            counter++
        }
        return file.name
    }

    fun execute(a: Activity, url: String, ua: String, cd: String, mime: String) {
        try {
            if (url.startsWith("blob:")) {
                Toas.show(a, "正在提取数据流...")
                return
            }
            val request = DownloadManager.Request(Uri.parse(url))
            request.setMimeType(mime)
            val fName = URLUtil.guessFileName(url, cd, mime)
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SAVE_FOLDER)
            if (!folder.exists()) folder.mkdirs()
            val uniqueName = getUniqueFileName(folder, fName)
            request.addRequestHeader("User-Agent", ua)
            request.setTitle(uniqueName)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$SAVE_FOLDER/$uniqueName")
            (a.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toas.show(a, "开始下载: $uniqueName")
        } catch (e: Exception) {
            Toas.show(a, "下载失败")
        }
    }

    class Bridge(private val activity: Activity) {
        @JavascriptInterface
        fun postBlob(data: String, mime: String, name: String) {
            activity.runOnUiThread {
                try {
                    if (data.contains(",")) {
                        val bytes = Base64.decode(data.split(",")[1], Base64.DEFAULT)
                        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SAVE_FOLDER)
                        if (!folder.exists()) folder.mkdirs()
                        val file = File(folder, getUniqueFileName(folder, name))
                        val os = FileOutputStream(file)
                        os.write(bytes)
                        os.close()
                        Toas.show(activity, "保存成功: ${file.name}")
                    }
                } catch (e: Exception) {
                    Toas.show(activity, "保存失败")
                }
            }
        }
    }
}
