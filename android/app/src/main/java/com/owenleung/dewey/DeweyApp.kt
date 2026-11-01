package com.owenleung.dewey

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.owenleung.dewey.store.Cache
import com.owenleung.dewey.store.ItemStore
import com.owenleung.dewey.store.Prefs
import com.owenleung.dewey.store.Reminders
import com.owenleung.dewey.widget.WidgetBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class DeweyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setUp(this)
        RefreshWorker.schedule(this)
    }

    companion object {
        private var done = false

        /** Storage and notification channels; safe to call more than once. */
        fun setUp(ctx: Context) {
            if (done) return
            done = true
            Prefs.init(ctx)
            Cache.init(ctx)
            ItemStore.init(ctx.filesDir)
            Reminders.init(ctx)
        }
    }
}

/**
 * Background refresh (BGAppRefreshTask on iOS): about every 2 hours with a
 * network, Android decides exactly when. To Do, a few gradebooks in turn and
 * the Inbox, so reminders, widgets and new-grade / message alerts stay current.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        // Signed out: nothing to fetch until the student signs in again.
        DeweyApp.setUp(applicationContext)
        if (Reminders.sessionEnded) return@withContext Result.success()
        // The tour's pretend week has the saved copies switched over: wait for the next run.
        if (com.owenleung.dewey.engine.Practice.active) return@withContext Result.success()
        val m = AppModel.get(applicationContext)
        val t0 = System.currentTimeMillis()
        m.boot()
        if (m.session == AppModel.Session.SIGNED_IN) {
            // The app only went to the background: this is its own model (one per process),
            // so there's no second engine racing it. Stops early when Android says time is up.
            m.refreshTodo()
            if (!isStopped) m.refreshSomeGrades()
            if (!isStopped) m.inbox.refresh(m, notify = true)
        }
        WidgetBridge.publishNow(m)
        android.util.Log.d("DeweyRefresh", "background refresh ${System.currentTimeMillis() - t0} ms, ${m.session}")
        Result.success()
    }

    companion object {
        private const val NAME = "refresh"

        /** Settings → Developer: one refresh after `delaySec`, to test it with the app closed. */
        fun runOnce(ctx: Context, delaySec: Long) {
            val req = androidx.work.OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("refresh-test", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
