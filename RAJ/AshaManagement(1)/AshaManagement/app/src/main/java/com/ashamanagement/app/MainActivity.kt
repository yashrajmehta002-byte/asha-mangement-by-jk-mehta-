package com.ashamanagement.app

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var unlocked = false
    private var stoppedAt = 0L
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var camUri: Uri? = null
    private var camFile: File? = null
    private var downloadId = -1L
    private var printView: WebView? = null

    private val allowedAuth =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    // ---------- फ़ोटो / गैलरी चुनने का नतीजा ----------
    private val chooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            var result: Array<Uri>? = null
            if (r.resultCode == RESULT_OK) {
                val d = r.data?.data
                val camOk = ((camFile?.length() ?: 0L) > 0L) && camUri != null
                result = if (d != null) arrayOf(d) else if (camOk) arrayOf(camUri!!) else null
            }
            fileCb?.onReceiveValue(result)
            fileCb = null
        }

    // ---------- बैकअप फ़ाइल चुनकर वापस लाना ----------
    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val text = try {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                if (text != null) {
                    web.evaluateJavascript("window.onBackupPicked(" + JSONObject.quote(text) + ")", null)
                } else {
                    toast("फ़ाइल पढ़ी नहीं जा सकी")
                }
            }
        }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val id = i?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id == downloadId) installApk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 11)
        }

        web = WebView(this)
        setContentView(web)
        web.visibility = View.INVISIBLE
        setupWebView()
        ContextCompat.registerReceiver(
            this, downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.onBack ? window.onBack() : false)") { r ->
                    if (r != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        authenticate()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // 60 सेकंड से ज़्यादा बाहर रहने पर दोबारा फ़िंगरप्रिंट मांगें
        if (unlocked && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 60_000) {
            unlocked = false
            web.visibility = View.INVISIBLE
            authenticate()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- फ़िंगरप्रिंट लॉगिन ----------
    private fun authenticate() {
        val can = BiometricManager.from(this).canAuthenticate(allowedAuth)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // फ़ोन में कोई लॉक/फ़िंगरप्रिंट सेट नहीं है, तो रोक नहीं सकते
            toast("फ़ोन में फ़िंगरप्रिंट/स्क्रीन लॉक सेट करें, ताकि ऐप सुरक्षित रहे")
            unlock()
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("आशा मैनेजमेंट")
            .setSubtitle("फ़िंगरप्रिंट से खोलें")
            .setAllowedAuthenticators(allowedAuth)
            .build()
        prompt.authenticate(info)
    }

    private fun unlock() {
        unlocked = true
        web.visibility = View.VISIBLE
    }

    // ---------- WebView ----------
    private fun setupWebView() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            builtInZoomControls = false
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == "appassets.androidplatform.net") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (_: Exception) {}
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = callback

                val photo = File(cacheDir, "cam_" + System.currentTimeMillis() + ".jpg")
                camFile = photo
                camUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", photo)

                val cam = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, camUri)
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                for (ri in packageManager.queryIntentActivities(cam, PackageManager.MATCH_DEFAULT_ONLY)) {
                    grantUriPermission(
                        ri.activityInfo.packageName, camUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                val content = Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                val ch = Intent.createChooser(content, "फ़ोटो चुनें")
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cam))
                return try {
                    chooser.launch(ch)
                    true
                } catch (e: Exception) {
                    fileCb = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun toast(m: String) = runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

    // ---------- फ़ाइल लिखने के सहायक ----------
    /** collection: "Documents" या "Download". sub: उप-फ़ोल्डर. */
    private fun writePublic(collection: String, sub: String, name: String, mime: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val rel = "$collection/$sub"
            val base = if (collection == "Download") MediaStore.Downloads.EXTERNAL_CONTENT_URI
            else MediaStore.Files.getContentUri("external")
            var existing: Uri? = null
            contentResolver.query(
                base, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(name, "$rel/"), null
            )?.use { c -> if (c.moveToFirst()) existing = ContentUris.withAppendedId(base, c.getLong(0)) }

            val target = existing ?: contentResolver.insert(base, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }) ?: throw IllegalStateException("फ़ाइल नहीं बन सकी")
            contentResolver.openOutputStream(target, "wt")!!.use { it.write(bytes) }
            return "$rel/$name"
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(collection), sub)
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
            return "$collection/$sub/$name"
        }
    }

    private fun installApk() {
        val f = File(getExternalFilesDir(null), "update.apk")
        if (!f.exists()) return
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    // ---------- JavaScript से जुड़ने वाला पुल ----------
    inner class Bridge {

        @JavascriptInterface
        fun getVersionCode(): Int =
            if (Build.VERSION.SDK_INT >= 28) packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            else @Suppress("DEPRECATION") packageManager.getPackageInfo(packageName, 0).versionCode

        @JavascriptInterface
        fun getVersionName(): String = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

        /** ऑटो-बैकअप: ऐप के अंदर (Google Auto Backup के लिए) और Documents/AshaManagement में भी। */
        @JavascriptInterface
        fun saveBackup(json: String): String {
            val bytes = json.toByteArray(Charsets.UTF_8)
            try { File(filesDir, "backup.json").writeBytes(bytes) } catch (_: Exception) {}
            return try {
                writePublic("Documents", "AshaManagement", "asha_backup.json", "application/json", bytes)
            } catch (e: Exception) {
                "ERR:" + e.message
            }
        }

        /** ऐप के अंदर रखा बैकअप (अगर Google Auto Backup से वापस आया हो)। */
        @JavascriptInterface
        fun loadInternalBackup(): String {
            val f = File(filesDir, "backup.json")
            return if (f.exists()) f.readText(Charsets.UTF_8) else ""
        }

        @JavascriptInterface
        fun pickBackup() {
            runOnUiThread { picker.launch(arrayOf("*/*")) }
        }

        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String): String {
            return try {
                val p = writePublic("Download", "AshaManagement", name, mime, Base64.decode(base64, Base64.DEFAULT))
                toast("सेव हुआ: $p")
                p
            } catch (e: Exception) {
                toast("सेव नहीं हो सका: " + e.message)
                "ERR"
            }
        }

        @JavascriptInterface
        fun shareFile(name: String, mime: String, base64: String, pkg: String) {
            runOnUiThread {
                try {
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    val f = File(dir, name)
                    f.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                    val i = Intent(Intent.ACTION_SEND).setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (pkg.isNotEmpty()) i.setPackage(pkg)
                    try {
                        startActivity(i)
                    } catch (e: ActivityNotFoundException) {
                        i.setPackage(null)
                        startActivity(Intent.createChooser(i, "शेयर करें"))
                    }
                } catch (e: Exception) {
                    toast("शेयर नहीं हो सका: " + e.message)
                }
            }
        }

        @JavascriptInterface
        fun shareText(text: String, pkg: String) {
            runOnUiThread {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                if (pkg.isNotEmpty()) i.setPackage(pkg)
                try {
                    startActivity(i)
                } catch (e: ActivityNotFoundException) {
                    i.setPackage(null)
                    startActivity(Intent.createChooser(i, "शेयर करें"))
                }
            }
        }

        @JavascriptInterface
        fun sms(number: String, text: String) {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
                        .putExtra("sms_body", text)
                    startActivity(i)
                } catch (e: Exception) {
                    toast("SMS ऐप नहीं मिला")
                }
            }
        }

        /** रिपोर्ट/नोटिस का HTML प्रिंट या PDF (प्रिंट स्क्रीन में "Save as PDF") के लिए। */
        @JavascriptInterface
        fun printHtml(title: String, html: String) {
            runOnUiThread {
                val wv = WebView(this@MainActivity)
                printView = wv
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                        pm.print(
                            title, view.createPrintDocumentAdapter(title),
                            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                        )
                    }
                }
                wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        }

        /** नया वर्ज़न है या नहीं, यह जांचने के लिए version.json मंगाना। */
        @JavascriptInterface
        fun checkUpdate(url: String) {
            Thread {
                val result = try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000
                    c.readTimeout = 10000
                    c.instanceFollowRedirects = true
                    c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                runOnUiThread {
                    web.evaluateJavascript("window.onUpdateInfo(" + JSONObject.quote(result) + ")", null)
                }
            }.start()
        }

        @JavascriptInterface
        fun installUpdate(url: String) {
            runOnUiThread {
                if (!packageManager.canRequestPackageInstalls()) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    )
                    toast("इस ऐप को इंस्टॉल की अनुमति देकर वापस आएं और दोबारा 'अपडेट' दबाएं")
                    return@runOnUiThread
                }
                val f = File(getExternalFilesDir(null), "update.apk")
                if (f.exists()) f.delete()
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle("आशा मैनेजमेंट अपडेट")
                    .setDestinationUri(Uri.fromFile(f))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadId = dm.enqueue(req)
                toast("अपडेट डाउनलोड हो रहा है...")
            }
        }
    }
}
