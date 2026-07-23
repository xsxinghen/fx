package com.github.king

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.Executors

class Pick(private val activity: AppCompatActivity) {
    private var callback: ValueCallback<Array<Uri>>? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (callback == null) return@registerForActivityResult
        var res: Array<Uri>? = null

        if (r.resultCode == AppCompatActivity.RESULT_OK && r.data != null) {
            val data = r.data!!
            val uri = data.data

            if (uri != null && uri.toString().contains("tree")) {
                val root = DocumentFile.fromTreeUri(activity, uri)
                if (root != null && root.isDirectory) {
                    Toas.show(activity, "正在解析文件夹，请稍候...")
                    executor.execute {
                        val list = ArrayList<Uri>()
                        traverse(root, list)
                        activity.runOnUiThread {
                            callback?.onReceiveValue(list.toTypedArray())
                            callback = null
                        }
                    }
                    return@registerForActivityResult
                }
            }

            if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                res = Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (uri != null) {
                res = arrayOf(uri)
            }
        }
        callback?.onReceiveValue(res)
        callback = null
    }

    fun pickFiles(multiple: Boolean, cb: ValueCallback<Array<Uri>>) {
        callback?.onReceiveValue(null)
        callback = cb
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            if (multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        launchIntent(intent)
    }

    fun pickFolder(cb: ValueCallback<Array<Uri>>) {
        callback?.onReceiveValue(null)
        callback = cb
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        launchIntent(intent)
    }

    private fun launchIntent(intent: Intent) {
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            callback?.onReceiveValue(null)
            callback = null
            Toas.show(activity, "无法打开选择器")
        }
    }

    private fun traverse(d: DocumentFile, l: MutableList<Uri>) {
        val files = d.listFiles()
        for (f in files) {
            if (f.isDirectory) traverse(f, l) else l.add(f.uri)
        }
    }
}
