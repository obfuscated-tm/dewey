package com.owenleung.dewey

import android.Manifest
import android.content.Intent
import com.owenleung.dewey.ui.tour.Tour
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.owenleung.dewey.ui.RootView
import com.owenleung.dewey.web.WebConfig
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    companion object {
        private var current = WeakReference<MainActivity>(null)
        private var permissionWaiter: CompletableDeferred<Boolean>? = null

        /** Android 13+: the notification permission, asked through the open activity. */
        suspend fun requestNotifications(): Boolean {
            if (Build.VERSION.SDK_INT < 33) return true
            val a = current.get() ?: return false
            val d = CompletableDeferred<Boolean>()
            permissionWaiter = d
            a.notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return d.await()
        }

        /** A web page's <input type=file>: Files / Photos, plus the camera (spike S1 on iOS). */
        fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
            val a = current.get() ?: return false
            a.fileCallback?.onReceiveValue(null)
            a.fileCallback = callback
            // OPEN_DOCUMENT opens the Files picker (which also reaches Photos and Drive). The page's own
            // createIntent() is GET_CONTENT, which on Android 14+ can resolve to the photo picker only, so PDFs
            // and documents couldn't be picked.
            val types = params.acceptTypes.orEmpty().map { it.trim() }.filter { it.contains('/') }
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                if (types.isNotEmpty()) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }
            val dir = File(a.cacheDir, "camera").apply { mkdirs() }
            val photo = File(dir, "scan-${System.currentTimeMillis()}.jpg")
            a.cameraUri = FileProvider.getUriForFile(a, "${a.packageName}.files", photo)
            val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, a.cameraUri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val chooser = Intent.createChooser(pick, "Attach a file").putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
            return runCatching { a.fileChooser.launch(chooser); true }.getOrElse {
                a.fileCallback = null
                false
            }
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permissionWaiter?.complete(ok)
        permissionWaiter = null
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val cb = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        val data = r.data
        val uris: Array<Uri>? = when {
            r.resultCode != RESULT_OK -> null
            data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            data?.data != null -> arrayOf(data.data!!)
            // The camera answers with no data: the photo is in the file we gave it.
            else -> cameraUri?.let { arrayOf(it) }
        }
        cb.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        current = WeakReference(this)
        val model = AppModel.get(this)
        handle(intent, model)
        setContent { RootView(model) }
        model.engine.attach(findViewById(android.R.id.content))
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { WidgetBridge.publish(model) }
            override fun onStop(owner: LifecycleOwner) { WebConfig.keepSession() }
        })
    }

    override fun onDestroy() {
        AppModel.get(this).engine.detach(findViewById(android.R.id.content))
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        current = WeakReference(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent, AppModel.get(this))
    }

    /** dewey://assignment/{id}, dewey://todo, dewey://grades, dewey://inbox, dewey://message/{id} */
    private fun handle(intent: Intent?, model: AppModel) {
        // Debug builds: `adb shell am start -n … --es tour tour|cards` opens the tour (iOS's Demo launch arguments).
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            when (intent?.getStringExtra("tour")) {
                "tour" -> Tour.mode = Tour.Mode.TOUR
                "cards" -> Tour.mode = Tour.Mode.CARDS
            }
        }
        val u = intent?.data ?: return
        if (u.scheme != "dewey") return
        model.deepLink = u.toString()
    }
}
