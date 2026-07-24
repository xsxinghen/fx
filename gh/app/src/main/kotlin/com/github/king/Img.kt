package com.github.king

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object Img {
    fun show(a: Activity) {
        if (!Conf.imgEnabled || Conf.imgList.isEmpty()) return
        try {
            val w = a.window
            val decor = w.decorView as ViewGroup
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = w.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                w.attributes = lp
            }
            
            val controller = WindowInsetsControllerCompat(w, decor)
            controller.hide(WindowInsetsCompat.Type.statusBars())

            val fl = FrameLayout(a)
            fl.setBackgroundColor(Color.WHITE)
            fl.elevation = 999f
            val iv = ImageView(a)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            fl.addView(iv, FrameLayout.LayoutParams(-1, -1))
            
            val tv = TextView(a)
            tv.setTextColor(Color.WHITE)
            tv.textSize = 13f
            tv.setPadding(40, 15, 40, 15)
            val gd = GradientDrawable()
            gd.setColor(0x55000000)
            gd.cornerRadius = 50f
            tv.background = gd
            val lpBtn = FrameLayout.LayoutParams(-2, -2)
            lpBtn.gravity = Gravity.TOP or Gravity.END
            lpBtn.topMargin = 280
            lpBtn.rightMargin = 90
            fl.addView(tv, lpBtn)
            decor.addView(fl, ViewGroup.LayoutParams(-1, -1))
            
            val h = Handler(Looper.getMainLooper())
            var currentIndex = 0
            var currentRemaining = 0
            var isFinished = false

            val end = Runnable {
                if (isFinished) return@Runnable
                isFinished = true
                fl.animate().alpha(0f).setDuration(500).withEndAction { 
                    decor.removeView(fl) 
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }.start()
            }

            if (Conf.imgCanSkip) {
                tv.setOnClickListener { end.run() }
            }

            fun loadNextImage() {
                if (isFinished) return
                if (currentIndex >= Conf.imgList.size) {
                    end.run()
                    return
                }
                val item = Conf.imgList[currentIndex]
                try {
                    val inputStream = a.assets.open("img/${item.name}")
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    if (bmp != null) {
                        iv.setImageBitmap(bmp)
                        currentRemaining = item.time
                    } else {
                        currentIndex++
                        loadNextImage()
                    }
                } catch (e: Exception) {
                    currentIndex++
                    loadNextImage()
                }
            }

            loadNextImage()

            val tick = object : Runnable {
                override fun run() {
                    if (isFinished) return
                    if (currentRemaining > 0) {
                        if (Conf.imgCanSkip) {
                            tv.text = "点击跳过 $currentRemaining"
                        } else {
                            tv.text = "倒计时 $currentRemaining"
                        }
                        currentRemaining--
                        h.postDelayed(this, 1000)
                    } else {
                        currentIndex++
                        loadNextImage()
                        if (currentIndex < Conf.imgList.size) {
                            h.post(this)
                        } else {
                            end.run()
                        }
                    }
                }
            }
            h.post(tick)
        } catch (e: Exception) {}
    }
}
