package com.screencast.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var running = true
    @Volatile private var current: String? = null
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        web = WebView(this)
        web.setBackgroundColor(Color.BLACK)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) {
                    v.loadUrl("about:blank")
                    showStatus("Can't reach the PC.\nMake sure ScreenCast TV is running on it.")
                    ui.postDelayed({ current?.let { web.loadUrl(it) } }, 3000)
                }
            }
            override fun onPageFinished(v: WebView, url: String) {
                if (url.startsWith("http")) status.visibility = View.GONE
            }
        }
        root.addView(web, FrameLayout.LayoutParams(match, match))

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.textSize = 28f
        status.gravity = Gravity.CENTER
        status.setBackgroundColor(Color.BLACK)
        root.addView(status, FrameLayout.LayoutParams(match, match))
        setContentView(root)

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        lock = wifi.createMulticastLock("screencast").also { it.acquire() }

        val saved = getSharedPreferences("sc", MODE_PRIVATE).getString("url", null)
        if (saved != null) open(saved) else showStatus("Looking for your PC...\nStart ScreenCast TV on the PC (same network).")
        discover()
    }

    private fun showStatus(t: String) { status.text = t; status.visibility = View.VISIBLE }

    private fun open(url: String) {
        current = url
        getSharedPreferences("sc", MODE_PRIVATE).edit().putString("url", url).apply()
        showStatus("Connecting...")
        web.loadUrl(url)
    }

    private fun discover() {
        Thread {
            try {
                val s = DatagramSocket(null)
                s.reuseAddress = true
                s.bind(InetSocketAddress(41234))
                socket = s
                val buf = ByteArray(128)
                while (running) {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val t = String(p.data, 0, p.length)
                    if (t.startsWith("SCREENCAST|")) {
                        val url = "http://" + p.address.hostAddress + ":" + t.substring(11).trim() + "/tv"
                        if (url != current) ui.post { open(url) }
                    }
                }
            } catch (e: Exception) { }
        }.start()
    }

    override fun onDestroy() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
        try { lock?.release() } catch (e: Exception) { }
        web.destroy()
        super.onDestroy()
    }
}
