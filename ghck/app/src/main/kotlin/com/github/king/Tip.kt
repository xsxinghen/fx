package com.github.king

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

class Tip(private val activity: Activity, root: ViewGroup) {
    private val container: FrameLayout = FrameLayout(activity)
    private val progress: ProgressBar
    private val errorTv: TextView

    init {
        container.setBackgroundColor(Color.WHITE)
        container.visibility = View.GONE

        progress = ProgressBar(activity)
        val lpProg = FrameLayout.LayoutParams(120, 120)
        lpProg.gravity = Gravity.CENTER
        container.addView(progress, lpProg)

        errorTv = TextView(activity)
        errorTv.setTextColor(Color.parseColor("#64748B"))
        errorTv.textSize = 14f
        errorTv.gravity = Gravity.CENTER
        errorTv.text = "页面加载失败\n点击屏幕重试"
        errorTv.visibility = View.GONE
        val lpText = FrameLayout.LayoutParams(-2, -2)
        lpText.gravity = Gravity.CENTER
        container.addView(errorTv, lpText)

        root.addView(container, ViewGroup.LayoutParams(-1, -1))
    }

    fun showError(retry: Runnable) {
        activity.runOnUiThread {
            container.visibility = View.VISIBLE
            progress.visibility = View.GONE
            errorTv.visibility = View.VISIBLE
            container.setOnClickListener {
                container.setOnClickListener(null)
                progress.visibility = View.VISIBLE
                errorTv.visibility = View.GONE
                retry.run()
            }
        }
    }

    fun hide() {
        activity.runOnUiThread {
            container.visibility = View.GONE
            container.setOnClickListener(null)
        }
    }
}
