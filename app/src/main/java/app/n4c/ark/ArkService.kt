package app.n4c.ark

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class TaskState(
    val running: Boolean = false,
    val title: String = "",
    val percent: Int = 0,
    val log: String = "",
    val result: OpResult? = null
)

/** UI와 서비스가 공유하는 작업 상태 */
object TaskHub {
    val state = MutableStateFlow(TaskState())

    fun begin(title: String) {
        state.value = TaskState(running = true, title = title, percent = 0, log = "")
    }

    fun progress(p: Int, log: String) {
        state.update { it.copy(percent = p.coerceIn(0, 100), log = log) }
    }

    fun finish(r: OpResult) {
        state.value = TaskState(running = false, result = r)
    }

    fun clearResult() {
        state.update { it.copy(result = null) }
    }
}

class ArkService : Service() {

    companion object {
        private const val ACTION_BACKUP = "app.n4c.ark.BACKUP"
        private const val ACTION_RESTORE_LOCAL = "app.n4c.ark.RESTORE_LOCAL"
        private const val ACTION_RESTORE_CLOUD = "app.n4c.ark.RESTORE_CLOUD"
        private const val X_PKG = "pkg"
        private const val X_LABEL = "label"
        private const val X_MODE = "mode"
        private const val X_DEST = "dest"
        private const val X_CUSTOM = "custom"
        private const val X_PATH = "path"
        private const val X_TEMP = "temp"
        private const val X_NAME = "name"
        private const val CH_PROGRESS = "ark_progress"
        private const val CH_RESULT = "ark_result"
        private const val ID_PROGRESS = 1
        private const val ID_RESULT = 2

        private fun launch(ctx: Context, i: Intent) {
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                TaskHub.finish(OpResult(false, ctx.getString(R.string.res_backup_fail), e.message ?: e.toString()))
            }
        }

        fun backup(ctx: Context, app: AppItem, mode: BackupMode, dest: Dest, customUri: String?) = launch(
            ctx,
            Intent(ctx, ArkService::class.java).setAction(ACTION_BACKUP)
                .putExtra(X_PKG, app.pkg).putExtra(X_LABEL, app.label)
                .putExtra(X_MODE, mode.name).putExtra(X_DEST, dest.name).putExtra(X_CUSTOM, customUri)
        )

        fun restoreLocal(ctx: Context, path: String, label: String, temp: Boolean) = launch(
            ctx,
            Intent(ctx, ArkService::class.java).setAction(ACTION_RESTORE_LOCAL)
                .putExtra(X_PATH, path).putExtra(X_LABEL, label).putExtra(X_TEMP, temp)
        )

        fun restoreCloud(ctx: Context, name: String, label: String) = launch(
            ctx,
            Intent(ctx, ArkService::class.java).setAction(ACTION_RESTORE_CLOUD)
                .putExtra(X_NAME, name).putExtra(X_LABEL, label)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastPct = -1
    private var lastLog = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, getString(R.string.notif_ch_progress), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, getString(R.string.notif_ch_result), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val label = intent?.getStringExtra(X_LABEL) ?: ""
        val title = if (action == ACTION_BACKUP)
            getString(R.string.progress_backup_title, label)
        else
            getString(R.string.progress_restore_title, label)

        ServiceCompat.startForeground(
            this, ID_PROGRESS, progressNotification(title, 0, getString(R.string.log_prepare)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        if (intent == null || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }

        val report: (Int, String) -> Unit = { p, l ->
            TaskHub.progress(p, l)
            if (p != lastPct || l != lastLog) {
                lastPct = p; lastLog = l
                getSystemService(NotificationManager::class.java)
                    .notify(ID_PROGRESS, progressNotification(title, p, l))
            }
        }

        job = scope.launch {
            val pm = getSystemService(PowerManager::class.java)
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ark:task").apply { acquire(3 * 60 * 60 * 1000L) }
            val result = try {
                when (action) {
                    ACTION_BACKUP -> {
                        val app = AppItem(label, intent.getStringExtra(X_PKG) ?: "", false)
                        val mode = BackupMode.valueOf(intent.getStringExtra(X_MODE) ?: BackupMode.BOTH.name)
                        val dest = Dest.valueOf(intent.getStringExtra(X_DEST) ?: Dest.LOCAL.name)
                        BackupEngine.backup(
                            applicationContext, app, mode, dest,
                            intent.getStringExtra(X_CUSTOM), ArkPrefs.cloud(applicationContext), report
                        )
                    }
                    ACTION_RESTORE_LOCAL -> {
                        val path = intent.getStringExtra(X_PATH) ?: ""
                        try {
                            BackupEngine.restoreArchive(applicationContext, path, report)
                        } finally {
                            if (intent.getBooleanExtra(X_TEMP, false)) File(path).delete()
                        }
                    }
                    ACTION_RESTORE_CLOUD -> {
                        val name = intent.getStringExtra(X_NAME) ?: ""
                        val cached = File(cacheDir, "cloud_restore.ark")
                        try {
                            WebDavClient(ArkPrefs.cloud(applicationContext)).download(name, cached) { read, total ->
                                val pct = if (total > 0) (read * 30 / total).toInt() else 10
                                report(pct, getString(R.string.log_download, formatSize(read), formatSize(total.coerceAtLeast(read))))
                            }
                            BackupEngine.restoreArchive(applicationContext, cached.absolutePath, report)
                        } finally {
                            cached.delete()
                        }
                    }
                    else -> OpResult(false, getString(R.string.res_restore_fail), "?")
                }
            } catch (e: Exception) {
                OpResult(false, getString(R.string.res_restore_fail), e.message ?: e.toString())
            } finally {
                if (wl.isHeld) wl.release()
            }
            finish(result)
        }
        return START_NOT_STICKY
    }

    private fun finish(r: OpResult) {
        TaskHub.finish(r)
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).notify(ID_RESULT, resultNotification(r))
        stopSelf()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun progressNotification(title: String, pct: Int, log: String): Notification =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_archive)
            .setContentTitle(title)
            .setContentText("$pct% · $log")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()

    private fun resultNotification(r: OpResult): Notification =
        NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(R.drawable.ic_archive)
            .setContentTitle(r.title)
            .setContentText(r.details.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.details))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
