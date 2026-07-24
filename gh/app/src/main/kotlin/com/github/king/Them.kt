package com.github.king

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

object Them {

    fun initSystemBar(activity: Activity) {
        val window = activity.window
        // 允许内容延伸到状态栏和导航栏底部
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun isDarkColor(color: Int): Boolean {
        // 计算颜色的明暗度，小于 0.5 视为深色
        return ColorUtils.calculateLuminance(color) < 0.5
    }

    fun apply(activity: Activity, jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            var colorStr = json.optString("color", "#FFFFFF")
            if (!colorStr.startsWith("#")) colorStr = "#FFFFFF"
            val colorInt = Color.parseColor(colorStr)
            val isDark = isDarkColor(colorInt)

            activity.runOnUiThread {
                val window = activity.window
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                
                // 将背景色设置在最底层的 DecorView 上
                window.decorView.setBackgroundColor(colorInt)
                
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                // 核心控制：如果是深色背景，设置非 Light 模式（即字体和小白条变白）
                // 如果是浅色背景，设置 Light 模式（即字体和小白条变黑）
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        } catch (e: Exception) {}
    }

    fun getInjectedJs(): String {
        return """
            (function() {
                function rgbToHex(rgb) {
                    var r = 255, g = 255, b = 255;
                    if (rgb && rgb.indexOf('rgb') !== -1) {
                        var res = rgb.match(/[\d.]+/g);
                        if (res && res.length >= 3) {
                            var a = res.length > 3 ? parseFloat(res[3]) : 1;
                            if (a > 0) {
                                r = parseInt(res[0]);
                                g = parseInt(res[1]);
                                b = parseInt(res[2]);
                            }
                        }
                    }
                    // 移除掉旧版多余的反转逻辑，直接返回真实颜色
                    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1).toUpperCase();
                }
                function syncTheme() {
                    var m = document.querySelector('meta[name="theme-color"]');
                    if (m && m.content) {
                         window.AndroidBridge.postMessage(JSON.stringify({color: m.content}));
                         return;
                    }
                    var bg = window.getComputedStyle(document.body).backgroundColor;
                    if (bg === 'transparent' || bg === 'rgba(0, 0, 0, 0)') {
                        bg = window.getComputedStyle(document.documentElement).backgroundColor;
                    }
                    window.AndroidBridge.postMessage(JSON.stringify({color: rgbToHex(bg)}));
                }
                window.setThemeColor = function(c) { if (window.AndroidBridge) { window.AndroidBridge.postMessage(JSON.stringify({color: c})); } };
                
                window.forceAppBack = function() {
                    if (typeof window.handleAppBack === 'function' && window.handleAppBack() === true) return true;
                    var d = document.querySelectorAll('dialog[open]');
                    if (d.length > 0) { d[d.length - 1].close(); return true; }
                    
                    var s = ['.van-overlay', '.van-popup__close-icon', '.el-dialog__headerbtn', '.btn-close', '[aria-label="Close"]', '[aria-label="关闭"]', '.close', '.modal-close', '.ant-modal-close', '.ant-drawer-close'];
                    for (var i = 0; i < s.length; i++) {
                        var els = document.querySelectorAll(s[i]);
                        for (var j = els.length - 1; j >= 0; j--) {
                            if (els[j] && els[j].offsetParent !== null) {
                                els[j].click();
                                return true;
                            }
                        }
                    }
                    return false;
                };

                if (document.readyState === 'complete') { syncTheme(); } else { window.addEventListener('load', syncTheme); }
                var observer = new MutationObserver(syncTheme);
                observer.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class'] });
                setTimeout(syncTheme, 500);
                setTimeout(syncTheme, 1500);
            })();
        """.trimIndent()
    }
}
