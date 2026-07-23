{
  // ================= 网页加载配置 =================
  "web_config": {
    // 远程网址列表 (Array)。App 启动时会优先加载此数组中的第一个网址。
    "remote_urls": [
      "https://gk.ksx.qzz.io",       // 首选加载的网址
      "https://gk-8z2.pages.dev"     // 备用网址（如果第一个网址在 Web.kt 中被触发 fallback，会尝试加载下一个）
    ],
    
    // 本地网页或兜底网址 (String)。
    // 如果 remote_urls 为空，App 会读取这里。
    // 如果填网址 (如 "https://...")，App 会直接加载。
    // 如果填文件名 (如 "index.html")，App 会去 assets/web/ 目录下寻找。
    "local_html": "index.html" 
  },

  // ================= 开屏图片配置 =================
  "img_config": {
    // 是否启用开屏图片功能 (Boolean)。true 为开启，false 为关闭。
    "enabled": true,
    
    // 是否允许用户点击屏幕右上角跳过开屏 (Boolean)。true 允许，false 不允许。
    "can_skip": true,
    
    // 所有开屏图片展示的总时间(秒) (Integer)。
    // 如果下面的 images 数组里没单独配置 time，系统会用这个总时间平分给每张图。
    "total_time": 3,
    
    // 开屏图片列表 (Array)。图片文件需要放在 app 的 assets/img/ 目录下。
    "images": [
      {
        "name": "main.png",          // 图片的文件名，必须带后缀 (如 .png, .jpg)
        "time": 3                    // 该图片的独立展示时长(秒)。(可选填，若填写会覆盖平分逻辑)
      }
    ]
  }
}