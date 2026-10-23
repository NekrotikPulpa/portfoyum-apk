package com.yatirim.portfoyum

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#0B1120")
        window.navigationBarColor = Color.parseColor("#0B1120")

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.setBackgroundColor(Color.parseColor("#0B1120"))
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // Jsoup Native Köprüsünü bağla
        webView.addJavascriptInterface(DunyaKatilimJsoupBridge(), "AndroidBridge")

        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class DunyaKatilimJsoupBridge {
        private val pool = Executors.newCachedThreadPool()

        private fun parseTurkishNumber(text: String): Double? {
            return try {
                val cleaned = text.trim()
                val hasDot = cleaned.contains(".")
                val hasComma = cleaned.contains(",")
                val normalized = when {
                    hasDot && hasComma -> cleaned.replace(",", "")
                    hasComma -> {
                        val afterComma = cleaned.substringAfterLast(",")
                        if (afterComma.length == 3) cleaned.replace(",", "")
                        else cleaned.replace(",", ".")
                    }
                    else -> cleaned
                }
                normalized.filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull()
            } catch (e: Exception) {
                null
            }
        }

        @JavascriptInterface
        fun fetchDunyaRatesJson(): String {
            return try {
                val future = pool.submit<String> {
                    val doc = Jsoup.connect("https://dunyakatilim.com.tr/gunluk-kurlar")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Referer", "https://www.google.com/")
                        .timeout(20_000)
                        .get()

                    val rows = doc.select("tr:has(td.col > span), tr")
                    val arr = JSONArray()

                    for (row in rows) {
                        val cells = row.select("> td.col, > td")
                        if (cells.size >= 3) {
                            val name = (cells[0].selectFirst("> span")?.text() ?: cells[0].text()).trim()
                            val buyPrice = parseTurkishNumber(cells[1].text().trim())
                            val sellPrice = parseTurkishNumber(cells[2].text().trim())
                            val changeVal = if (cells.size >= 4) parseTurkishNumber(cells[3].text().trim()) else 0.0

                            if (!name.isNullOrEmpty() && buyPrice != null && sellPrice != null && buyPrice > 0) {
                                val obj = JSONObject()
                                obj.put("name", name)
                                obj.put("buy", buyPrice)
                                obj.put("sell", sellPrice)
                                obj.put("change", changeVal ?: 0.0)
                                arr.put(obj)
                            }
                        }
                    }
                    arr.toString()
                }
                future.get(22, TimeUnit.SECONDS)
            } catch (e: Exception) {
                "[]"
            }
        }

        @JavascriptInterface
        fun fetchUrl(targetUrl: String): String {
            return try {
                val future = pool.submit<String> {
                    Jsoup.connect(targetUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,application/json,*/*;q=0.8")
                        .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                        .header("Referer", "https://www.google.com/")
                        .ignoreContentType(true)
                        .timeout(20_000)
                        .execute()
                        .body()
                }
                future.get(22, TimeUnit.SECONDS)
            } catch (e: Exception) {
                ""
            }
        }
    }
}
