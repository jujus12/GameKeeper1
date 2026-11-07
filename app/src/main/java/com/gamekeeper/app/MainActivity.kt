package com.gamekeeper.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : Activity() {
    private data class GameApp(val label: String, val packageName: String) {
        override fun toString(): String = "$label ($packageName)"
    }

    private lateinit var appSpinner: Spinner
    private lateinit var statusText: TextView
    private var apps: List<GameApp> = emptyList()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) applyShizukuRules()
            else showMessage("Shizuku izni verilmedi.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildScreen()
        loadApps()
        updateStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(0xFFF4F6FA.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content.addView(TextView(this).apply {
            text = "GameKeeper"
            textSize = 30f
            setTextColor(0xFF17233B.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Seçtiğin oyunun arka planda çalışma şansını artırmak için izinleri düzenle."
            textSize = 15f
            setTextColor(0xFF536174.toInt())
            setPadding(0, dp(8), 0, dp(20))
        })

        content.addView(sectionTitle("1. Oyun seç"))
        appSpinner = Spinner(this)
        content.addView(appSpinner, matchWrap())
        addActionButton(content, "Oyun listesini yenile") { loadApps() }
        addActionButton(content, "Seçili oyunu aç") { launchSelectedApp() }

        content.addView(sectionTitle("2. Pil kısıtlamasını kaldır"))
        addActionButton(content, "Pil optimizasyonu ayarını aç") { requestBatteryExemption() }

        content.addView(sectionTitle("3. Shizuku ile arka plan izinleri"))
        content.addView(TextView(this).apply {
            text = "Shizuku uygulamasını ayrıca kurup kablosuz hata ayıklama üzerinden başlatmalısın. Ardından aşağıdaki düğme, desteklenen cihazlarda seçili oyun için arka plan AppOps kurallarını dener."
            textSize = 14f
            setTextColor(0xFF536174.toInt())
            setPadding(0, dp(4), 0, dp(8))
        })
        addActionButton(content, "Shizuku iznini al ve uygula") { requestShizukuAndApply() }

        content.addView(sectionTitle("Durum"))
        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF26364D.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        content.addView(statusText, matchWrap())

        content.addView(TextView(this).apply {
            text = "Önemli: Android ve Lenovo güç yönetimi, bellek gerektiğinde oyun işlemini yine kapatabilir. GameKeeper oyunu görünür tutamaz ve kapanmama garantisi vermez; yalnızca desteklenen izinleri düzenlemeye yardımcı olur."
            textSize = 13f
            setTextColor(0xFF8A4B16.toInt())
            setPadding(0, dp(18), 0, dp(8))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 18f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(0xFF20324D.toInt())
        setPadding(0, dp(22), 0, dp(8))
    }

    private fun addActionButton(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }, matchWrap())
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        apps = resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == applicationContext.packageName) return@mapNotNull null
            GameApp(info.loadLabel(packageManager)?.toString() ?: pkg, pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase(Locale.getDefault()) }

        appSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps)
        updateStatus()
        if (apps.isEmpty()) showMessage("Açılabilir uygulama bulunamadı.")
    }

    private fun selectedApp(): GameApp? {
        val index = if (::appSpinner.isInitialized) appSpinner.selectedItemPosition else -1
        if (index !in apps.indices) {
            showMessage("Önce listeden bir oyun seç.")
            return null
        }
        return apps[index]
    }

    private fun launchSelectedApp() {
        val app = selectedApp() ?: return
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch == null) showMessage("Bu uygulama başlatılamıyor.")
        else startActivity(launch)
    }

    private fun requestBatteryExemption() {
        val app = selectedApp() ?: return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(app.packageName)) {
            showMessage("Seçili uygulama pil optimizasyonu dışında bırakılmış.")
            updateStatus()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${app.packageName}")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestShizukuAndApply() {
        if (!Shizuku.pingBinder()) {
            showMessage("Shizuku çalışmıyor. Shizuku uygulamasından kablosuz hata ayıklama ile başlat.")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) applyShizukuRules()
        else Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    private fun applyShizukuRules() {
        val app = selectedApp() ?: return
        val pkg = app.packageName
        Thread {
            try {
                val command = "cmd appops set $pkg RUN_IN_BACKGROUND allow; cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow"
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val errors = process.errorStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                runOnUiThread {
                    val detail = when {
                        exitCode == 0 -> "Komutlar gönderildi. Android sürümüne göre etkisiz kalabilir."
                        errors.isNotBlank() -> errors.trim()
                        output.isNotBlank() -> output.trim()
                        else -> "Komut tamamlanamadı (kod $exitCode)."
                    }
                    statusText.text = "Shizuku sonucu: $detail"
                    showMessage(if (exitCode == 0) "Arka plan izin komutları gönderildi." else "İşlem başarısız: $detail")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Shizuku hatası: ${e.message ?: "Bilinmeyen hata"}"
                    showMessage("Shizuku komutu çalıştırılamadı.")
                }
            }
        }.start()
    }

    private fun updateStatus() {
        val app = if (apps.isNotEmpty() && ::appSpinner.isInitialized) apps.getOrNull(appSpinner.selectedItemPosition) else null
        val battery = if (app == null) "Oyun seçilmedi" else {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(app.packageName)) "Pil optimizasyonu dışında" else "Pil optimizasyonu etkin"
        }
        val shizuku = when {
            !Shizuku.pingBinder() -> "Shizuku bağlı değil"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Shizuku izni verilmiş"
            else -> "Shizuku izni bekleniyor"
        }
        if (::statusText.isInitialized) statusText.text = "Listelenen uygulama: ${apps.size}\nPil: $battery\nShizuku: $shizuku"
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 7101
    }
}
