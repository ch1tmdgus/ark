package app.n4c.ark

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.pm.PackageInfoCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupEngine {

    init {
        // 글로벌 마운트 네임스페이스에서 루트 셸 실행 (다른 앱의 /data/data가 보이도록)
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }

    const val LOCAL_DIR = "/sdcard/ARK"
    private const val WORK = "/data/local/tmp/ark_work"

    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) { Shell.getShell().isRoot }

    suspend fun loadApps(ctx: Context): List<AppItem> = withContext(Dispatchers.Default) {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0)
            .filter { it.packageName != ctx.packageName }
            .map {
                AppItem(
                    label = pm.getApplicationLabel(it).toString(),
                    pkg = it.packageName,
                    isSystem = it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    // ---------------- shell helpers ----------------
    private fun run(vararg cmds: String): Pair<Boolean, String> {
        val out = mutableListOf<String>()
        val err = mutableListOf<String>()
        val r = Shell.cmd(*cmds).to(out, err).exec()
        return Pair(r.isSuccess, (out + err).joinToString("\n").trim())
    }

    private fun num(cmd: String): Long =
        Shell.cmd(cmd).exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L

    private fun pump(input: InputStream, out: OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(256 * 1024)
        var total = 0L
        var last = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
            if (total - last >= 1_048_576L) { onBytes(total); last = total }
        }
        out.flush()
        onBytes(total)
    }

    private fun copyToSaf(ctx: Context, tree: Uri, srcPath: String, name: String, onBytes: (Long) -> Unit) {
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: throw IOException("createDocument failed")
        val out = resolver.openOutputStream(doc) ?: throw IOException("openOutputStream failed")
        out.use { o -> SuFileInputStream.open(srcPath).use { pump(it, o, onBytes) } }
    }

    // ---------------- backup ----------------
    suspend fun backup(
        ctx: Context,
        app: AppItem,
        mode: BackupMode,
        dest: Dest,
        customUri: String?,
        cfg: DavConfig,
        progress: (Int, String) -> Unit
    ): OpResult = withContext(Dispatchers.IO) {
        val pkg = app.pkg
        val st = "$WORK/${pkg}_stage"
        val failTitle = ctx.getString(R.string.res_backup_fail)
        val metaFile = File(ctx.cacheDir, "meta_$pkg.json")
        var arkPath = ""
        var jsonPath = ""
        try {
            progress(2, ctx.getString(R.string.log_prepare))
            val withApk = mode != BackupMode.DATA
            val withData = mode != BackupMode.APK
            val apkPaths = Shell.cmd("pm path $pkg").exec().out
                .map { it.removePrefix("package:").trim() }
                .filter { it.endsWith(".apk") }
            if (withApk && apkPaths.isEmpty()) {
                return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_no_apk))
            }

            run("rm -rf $st", "mkdir -p $st/apk $LOCAL_DIR $WORK")
            if (withData) run("am force-stop $pkg")

            val members = mutableListOf("meta.json")

            if (withApk) {
                progress(10, ctx.getString(R.string.log_apk))
                apkPaths.forEach { run("cp \"$it\" $st/apk/") }
                members += "apk"
            }

            var hasExt = false
            if (withData) {
                progress(20, ctx.getString(R.string.log_data))
                run("tar -cpf $st/data.tar -C /data/data $pkg 2>&1; true")
                members += "data.tar"
                hasExt = Shell.cmd("[ -d \"/sdcard/Android/data/$pkg\" ]").exec().isSuccess
                if (hasExt) {
                    progress(45, ctx.getString(R.string.log_ext))
                    run("tar -cpf $st/ext.tar -C /sdcard/Android/data $pkg 2>&1; true")
                    members += "ext.tar"
                }
            }

            val pi = runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.getOrNull()
            val now = System.currentTimeMillis()
            val meta = BackupMeta(
                pkg = pkg,
                label = app.label,
                versionName = pi?.versionName ?: "?",
                versionCode = pi?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                androidVersion = Build.VERSION.RELEASE,
                sdk = Build.VERSION.SDK_INT,
                time = now,
                mode = mode,
                hasExt = hasExt
            )
            metaFile.writeText(meta.toJson())
            run("cp \"${metaFile.absolutePath}\" $st/meta.json")

            val base = "${pkg}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
            val dir = if (dest == Dest.LOCAL) LOCAL_DIR else WORK
            arkPath = "$dir/$base.ark"
            jsonPath = "$dir/$base.json"

            progress(65, ctx.getString(R.string.log_pack))
            run("tar -cpf $arkPath -C $st ${members.joinToString(" ")} 2>&1; true")
            run("cp $st/meta.json $jsonPath")

            val size = num("stat -c %s $arkPath")
            val dataOk = !withData || num("tar -tf $st/data.tar | wc -l") > 1
            val apkOk = !withApk || num("ls $st/apk | wc -l") > 0
            if (size <= 0 || !dataOk || !apkOk) {
                if (dest == Dest.LOCAL) Shell.cmd("rm -f $arkPath $jsonPath").exec()
                return@withContext OpResult(
                    false, failTitle,
                    ctx.getString(if (!dataOk) R.string.res_empty_data else R.string.res_no_apk)
                )
            }

            val where: String
            when (dest) {
                Dest.LOCAL -> {
                    progress(98, ctx.getString(R.string.log_cleanup))
                    where = ctx.getString(R.string.res_saved_to, arkPath)
                }
                Dest.CUSTOM -> {
                    progress(72, ctx.getString(R.string.log_save_custom))
                    val tree = Uri.parse(customUri ?: throw IOException("no folder"))
                    copyToSaf(ctx, tree, arkPath, "$base.ark") { b ->
                        progress(72 + (b * 25 / size).toInt().coerceIn(0, 25), ctx.getString(R.string.log_save_custom))
                    }
                    copyToSaf(ctx, tree, jsonPath, "$base.json") { }
                    where = ctx.getString(R.string.res_saved_to, "$base.ark")
                }
                Dest.CLOUD -> {
                    val client = WebDavClient(cfg)
                    client.ensureFolder()
                    SuFileInputStream.open(arkPath).use { input ->
                        client.upload("$base.ark", input, size) { sent ->
                            progress(
                                72 + (sent * 25 / size).toInt().coerceIn(0, 25),
                                ctx.getString(R.string.log_upload, formatSize(sent), formatSize(size))
                            )
                        }
                    }
                    val jsize = num("stat -c %s $jsonPath")
                    SuFileInputStream.open(jsonPath).use { client.upload("$base.json", it, jsize) { } }
                    where = ctx.getString(R.string.res_saved_cloud, "${WebDavClient.REMOTE_DIR}/$base.ark")
                }
            }

            progress(100, ctx.getString(R.string.log_cleanup))
            val details = buildString {
                appendLine(where)
                appendLine()
                appendLine("${app.label} (${meta.pkg})")
                appendLine("v${meta.versionName} · ${meta.device} · Android ${meta.androidVersion}")
                append(formatSize(size))
            }
            OpResult(true, ctx.getString(R.string.res_backup_ok), details)
        } catch (e: Exception) {
            OpResult(false, failTitle, e.message ?: e.toString())
        } finally {
            Shell.cmd("rm -rf $st").exec()
            if (dest != Dest.LOCAL && arkPath.isNotEmpty()) Shell.cmd("rm -f $arkPath $jsonPath").exec()
            metaFile.delete()
        }
    }

    // ---------------- list / meta ----------------
    suspend fun readMeta(archivePath: String): BackupMeta? = withContext(Dispatchers.IO) {
        val d = "$WORK/metaread"
        run("rm -rf $d", "mkdir -p $d", "tar -xpf \"$archivePath\" -C $d meta.json 2>&1; true")
        val text = Shell.cmd("cat $d/meta.json 2>/dev/null").exec().out.joinToString("\n")
        Shell.cmd("rm -rf $d").exec()
        BackupMeta.parse(text)
    }

    suspend fun listLocal(): List<BackupEntry> = withContext(Dispatchers.IO) {
        Shell.cmd("ls $LOCAL_DIR/*.json 2>/dev/null").exec().out
            .filter { it.endsWith(".json") }
            .mapNotNull { j ->
                val meta = BackupMeta.parse(Shell.cmd("cat \"$j\"").exec().out.joinToString("\n"))
                    ?: return@mapNotNull null
                val ark = j.removeSuffix(".json") + ".ark"
                val size = num("stat -c %s \"$ark\"")
                if (size <= 0) null else BackupEntry(meta, ark, size, Dest.LOCAL)
            }
            .sortedByDescending { it.meta.time }
    }

    suspend fun listCloud(cfg: DavConfig): List<BackupEntry> = withContext(Dispatchers.IO) {
        val client = WebDavClient(cfg)
        val files = client.listAll()
        val bySize = files.associate { it.name to it.size }
        files.filter { it.name.endsWith(".json") }
            .mapNotNull { f ->
                val ark = f.name.removeSuffix(".json") + ".ark"
                val size = bySize[ark] ?: return@mapNotNull null
                val meta = BackupMeta.parse(client.readText(f.name)) ?: return@mapNotNull null
                BackupEntry(meta, ark, size, Dest.CLOUD)
            }
            .sortedByDescending { it.meta.time }
    }

    suspend fun deleteEntry(cfg: DavConfig, e: BackupEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (e.source == Dest.LOCAL) {
                Shell.cmd("rm -f \"${e.archive}\" \"${e.archive.removeSuffix(".ark")}.json\"").exec()
                Unit
            } else {
                val c = WebDavClient(cfg)
                c.delete(e.archive.removeSuffix(".ark") + ".json")
                c.delete(e.archive)
            }
        }
    }

    suspend fun testConnection(cfg: DavConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { WebDavClient(cfg).test() }
    }

    suspend fun copyUriToCache(ctx: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val f = File(ctx.cacheDir, "manual_restore.ark")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> pump(i, o) { } } }
                ?: return@withContext null
            f
        } catch (e: Exception) {
            f.delete()
            null
        }
    }

    // ---------------- restore ----------------
    /** archivePath: 루트가 읽을 수 있는 경로. 진행률은 32%부터 시작 */
    suspend fun restoreArchive(ctx: Context, archivePath: String, progress: (Int, String) -> Unit): OpResult =
        withContext(Dispatchers.IO) {
            val st = "$WORK/restore_stage"
            val failTitle = ctx.getString(R.string.res_restore_fail)
            try {
                progress(32, ctx.getString(R.string.log_extract))
                run("rm -rf $st", "mkdir -p $st", "tar -xpf \"$archivePath\" -C $st 2>&1; true")
                val meta = BackupMeta.parse(Shell.cmd("cat $st/meta.json 2>/dev/null").exec().out.joinToString("\n"))
                    ?: return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_bad_archive))
                val pkg = meta.pkg

                val apks = Shell.cmd("ls $st/apk/*.apk 2>/dev/null").exec().out.filter { it.isNotBlank() }
                val hasData = Shell.cmd("[ -s $st/data.tar ]").exec().isSuccess
                val log = StringBuilder()

                // 1) APK 설치 (스플릿 APK 포함)
                if (apks.isNotEmpty()) {
                    progress(45, ctx.getString(R.string.log_install))
                    val createOut = Shell.cmd("pm install-create -r -d 2>&1").exec().out.joinToString(" ")
                    val sid = Regex("\\[(\\d+)]").find(createOut)?.groupValues?.get(1)
                        ?: return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_install_fail) + "\n" + createOut)
                    var ok = true
                    for (p in apks) {
                        val size = Shell.cmd("stat -c %s \"$p\"").exec().out.firstOrNull()?.trim() ?: "0"
                        val name = File(p).name
                        val (o, l) = run("pm install-write -S $size $sid \"$name\" \"$p\" 2>&1")
                        log.appendLine("$name: $l")
                        if (!o) { ok = false; break }
                    }
                    if (ok) {
                        val (o, l) = run("pm install-commit $sid 2>&1")
                        log.appendLine(l)
                        ok = o && l.contains("Success")
                    } else {
                        Shell.cmd("pm install-abandon $sid").exec()
                    }
                    if (!ok) return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_install_fail) + "\n" + log)
                }

                // 2) 내부 데이터 복원
                if (hasData) {
                    val installed = Shell.cmd("pm path $pkg").exec().out.isNotEmpty()
                    if (!installed) return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_not_installed))
                    val total = num("tar -tf $st/data.tar | wc -l")
                    if (total < 2) return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_empty_data))

                    progress(70, ctx.getString(R.string.log_restore_data))
                    val d = "/data/data/$pkg"
                    val (_, l) = run(
                        "am force-stop $pkg",
                        "OWN=$(stat -c %u $d)",
                        "rm -rf $d/* $d/.[!.]* 2>/dev/null",
                        "tar -xpf $st/data.tar -C /data/data 2>&1; true",
                        "chown -R \$OWN:\$OWN $d",
                        "restorecon -R $d >/dev/null 2>&1"
                    )
                    log.appendLine(l)
                    val after = num("find $d | wc -l")
                    if (after < total - 1) {
                        return@withContext OpResult(false, failTitle, ctx.getString(R.string.res_count_mismatch) + "\n$after / $total")
                    }

                    // 3) 외부 데이터
                    if (Shell.cmd("[ -s $st/ext.tar ]").exec().isSuccess) {
                        progress(90, ctx.getString(R.string.log_restore_ext))
                        val (_, el) = run(
                            "mkdir -p /data/media/0/Android/data",
                            "tar -xpf $st/ext.tar -C /data/media/0/Android/data 2>&1; true",
                            "OWN=$(stat -c %u $d)",
                            "chown -R \$OWN:\$OWN /data/media/0/Android/data/$pkg 2>&1; true",
                            "restorecon -R /data/media/0/Android/data/$pkg >/dev/null 2>&1; true"
                        )
                        log.appendLine(el)
                    }
                }

                progress(100, ctx.getString(R.string.log_cleanup))
                val details = buildString {
                    appendLine(ctx.getString(R.string.res_restored_pkg, "${meta.label} (${meta.pkg})"))
                    append("v${meta.versionName} · ${meta.device} · Android ${meta.androidVersion}")
                }
                OpResult(true, ctx.getString(R.string.res_restore_ok), details)
            } catch (e: Exception) {
                OpResult(false, failTitle, e.message ?: e.toString())
            } finally {
                Shell.cmd("rm -rf $st").exec()
            }
        }
}
