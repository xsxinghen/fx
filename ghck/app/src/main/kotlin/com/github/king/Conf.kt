package com.github.king

import android.content.Context
import org.json.JSONObject

object Conf {
    var remoteUrls = listOf<String>()
    var localHtml = ""
    var imgEnabled = true
    var imgCanSkip = true
    var imgTotalTime = 0
    val imgList = mutableListOf<ImgItem>()

    data class ImgItem(val name: String, val time: Int)

    fun init(context: Context) {
        try {
            val stream = context.assets.open("config.json")
            val size = stream.available()
            val buffer = ByteArray(size)
            stream.read(buffer)
            stream.close()
            
            val json = JSONObject(String(buffer, Charsets.UTF_8))
            val webConfig = json.optJSONObject("web_config")
            if (webConfig != null) {
                val urlsArray = webConfig.optJSONArray("remote_urls")
                val urls = mutableListOf<String>()
                if (urlsArray != null) {
                    for (i in 0 until urlsArray.length()) {
                        val url = urlsArray.optString(i)
                        if (url.isNotEmpty()) urls.add(url)
                    }
                }
                remoteUrls = urls
                localHtml = webConfig.optString("local_html", "")
            }

            val imgConfig = json.optJSONObject("img_config")
            if (imgConfig != null) {
                imgEnabled = imgConfig.optBoolean("enabled", true)
                imgCanSkip = imgConfig.optBoolean("can_skip", true)
                imgTotalTime = imgConfig.optInt("total_time", 0)
                val imagesArray = imgConfig.optJSONArray("images")
                if (imagesArray != null && imagesArray.length() > 0) {
                    var specifiedTime = 0
                    var unspecifiedCount = 0
                    val tempItems = mutableListOf<Pair<String, Int>>()
                    for (i in 0 until imagesArray.length()) {
                        val item = imagesArray.optJSONObject(i)
                        if (item != null) {
                            val name = item.optString("name", "")
                            if (name.isNotEmpty()) {
                                val time = item.optInt("time", -1)
                                tempItems.add(Pair(name, time))
                                if (time > 0) specifiedTime += time else unspecifiedCount++
                            }
                        }
                    }
                    val remainTime = if (imgTotalTime > specifiedTime) imgTotalTime - specifiedTime else 0
                    val avgTime = if (unspecifiedCount > 0) remainTime / unspecifiedCount else 0
                    imgList.clear()
                    for (item in tempItems) {
                        val t = if (item.second > 0) item.second else avgTime
                        if (t > 0) imgList.add(ImgItem(item.first, t))
                    }
                }
            }
        } catch (e: Exception) {
            remoteUrls = emptyList()
            localHtml = ""
            imgEnabled = false
        }
    }

    fun getStartUrl(): String {
        if (remoteUrls.isNotEmpty()) {
            return remoteUrls[0]
        }
        if (localHtml.isNotEmpty()) {
            if (localHtml.startsWith("http")) return localHtml
            return "file:///android_asset/web/$localHtml"
        }
        return "file:///android_asset/web/index.html"
    }
}
