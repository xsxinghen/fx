package com.github.king

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast

object Toas {
    @Suppress("DEPRECATION")
    fun show(activity: Activity?, message: String?) {
        if (activity == null || message == null) return
        activity.runOnUiThread {
            try {
                val toast = Toast(activity)
                val tv = TextView(activity)
                tv.text = message
                tv.setTextColor(Color.WHITE)
                tv.textSize = 14f
                tv.setPadding(60, 30, 60, 30)
                val gd = GradientDrawable()
                gd.setColor(Color.parseColor("#CC000000"))
                gd.cornerRadius = 60f
                tv.background = gd
                toast.view = tv
                toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 200)
                toast.duration = Toast.LENGTH_SHORT
                toast.show()
            } catch (e: Exception) {}
        }
    }
}
