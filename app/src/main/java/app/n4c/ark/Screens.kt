package app.n4c.ark

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(dynamicColor: Boolean, onDynamicChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val task by TaskHub.state.collectAsState()

    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(1) } // 0 all, 1 user, 2 system
    var cfg by remember { mutableStateOf(ArkPrefs.cloud(ctx)) }

    var selected by remember { mutableStateOf<AppItem?>(null) }
    var pendingCustom by remember { mutableStateOf<Pair<AppItem, BackupMode>?>(null) }

    var showRestoreMenu by remember { mutableStateOf(false) }
    var listSource by remember { mutableStateOf<Dest?>(null) }
    var entries by remember { mutableStateOf<List<BackupEntry>?>(null) }
    var confirmEntry by remember { mutableStateOf<BackupEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<BackupEntry?>(null) }
    var manual by remember { mutableStateOf<Pair<File, BackupMeta>?>(null) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progressHidden by remember { mutableStateOf(false) }

    fun startBackup(app: AppItem, mode: BackupMode, dest: Dest, custom: String?) {
        progressHidden = false
        TaskHub.begin(ctx.getString(R.string.progress_backup_title, app.label))
        ArkService.backup(ctx, app, mode, dest, custom)
    }

    fun startRestoreLocal(path: String, label: String, temp: Boolean) {
        progressHidden = false
        TaskHub.begin(ctx.getString(R.string.progress_restore_title, label))
        ArkService.restoreLocal(ctx, path, label, temp)
    }

    fun startRestoreCloud(name: String, label: String) {
        progressHidden = false
        TaskHub.begin(ctx.getString(R.string.progress_restore_title, label))
        ArkService.restoreCloud(ctx, name, label)
    }

    fun openList(src: Dest) {
        if (src == Dest.CLOUD && !cfg.ready) {
            message = ctx.getString(R.string.rs_cloud_need_connect)
            return
        }
        listSource = src
        entries = null
        scope.launch {
            try {
                entries = if (src == Dest.LOCAL) BackupEngine.listLocal() else BackupEngine.listCloud(cfg)
            } catch (e: Exception) {
                listSource = null
                message = ctx.getString(R.string.rs_load_fail, e.message ?: e.toString())
            }
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = pendingCustom
        pendingCustom = null
        if (uri != null && p != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            startBackup(p.first, p.second, Dest.CUSTOM, uri.toString())
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busyText = ctx.getString(R.string.copying_file)
            val f = BackupEngine.copyUriToCache(ctx, uri)
            val meta = f?.let { BackupEngine.readMeta(it.absolutePath) }
            busyText = null
            if (f != null && meta != null) {
                manual = f to meta
            } else {
                f?.delete()
                message = ctx.getString(R.string.res_bad_archive)
            }
        }
    }

    LaunchedEffect(Unit) {
        val root = BackupEngine.hasRoot()
        rootOk = root
        if (root) apps = BackupEngine.loadApps(ctx)
    }
    LaunchedEffect(task.result) {
        if (task.result != null && rootOk == true) apps = BackupEngine.loadApps(ctx)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp
                    )
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRestoreMenu = true },
                icon = { Icon(Icons.Default.Refresh, null) },
                text = { Text(stringResource(R.string.fab_restore)) }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val list = apps
            when {
                rootOk == false -> CenterMessage(
                    stringResource(R.string.no_root_title),
                    stringResource(R.string.no_root_body)
                )
                rootOk == null || list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    val filtered = remember(list, query, filter) {
                        list.filter { a ->
                            (filter == 0 || (filter == 1 && !a.isSystem) || (filter == 2 && a.isSystem)) &&
                                (query.isBlank() ||
                                    a.label.contains(query, ignoreCase = true) ||
                                    a.pkg.contains(query, ignoreCase = true))
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        if (task.running && progressHidden) {
                            RunningBanner(task) { progressHidden = false }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, null) }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filter == 1, onClick = { filter = 1 },
                                label = { Text(stringResource(R.string.chip_user)) }
                            )
                            FilterChip(
                                selected = filter == 2, onClick = { filter = 2 },
                                label = { Text(stringResource(R.string.chip_system)) }
                            )
                            FilterChip(
                                selected = filter == 0, onClick = { filter = 0 },
                                label = { Text(stringResource(R.string.chip_all)) }
                            )
                        }
                        Text(
                            stringResource(R.string.home_subtitle, filtered.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                        if (filtered.isEmpty()) {
                            Text(
                                stringResource(R.string.no_apps),
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filtered, key = { it.pkg }) { app ->
                                    AppRow(app) {
                                        if (task.running) progressHidden = false else selected = app
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1) 백업 시트
    selected?.let { app ->
        BackupSheet(
            app = app,
            cloud = cfg,
            onDismiss = { selected = null },
            onStart = { mode, dest ->
                selected = null
                if (dest == Dest.CUSTOM) {
                    pendingCustom = app to mode
                    treePicker.launch(null)
                } else {
                    startBackup(app, mode, dest, null)
                }
            }
        )
    }

    // 2) 복원 메뉴
    if (showRestoreMenu) {
        RestoreMenuSheet(
            cloud = cfg,
            onLocal = { showRestoreMenu = false; openList(Dest.LOCAL) },
            onCloud = { showRestoreMenu = false; openList(Dest.CLOUD) },
            onOther = { showRestoreMenu = false; filePicker.launch(arrayOf("*/*")) },
            onDismiss = { showRestoreMenu = false }
        )
    }

    // 3) 백업 목록
    listSource?.let { src ->
        EntryListSheet(
            source = src,
            entries = entries,
            onPick = { confirmEntry = it },
            onDelete = { deleteEntry = it },
            onDismiss = { listSource = null; entries = null }
        )
    }

    // 4) 복원 확인 (목록에서 선택)
    confirmEntry?.let { e ->
        RestoreConfirmDialog(
            meta = e.meta,
            size = e.size,
            onConfirm = {
                confirmEntry = null
                listSource = null
                if (e.source == Dest.LOCAL) startRestoreLocal(e.archive, e.meta.label, false)
                else startRestoreCloud(e.archive, e.meta.label)
            },
            onDismiss = { confirmEntry = null }
        )
    }

    // 5) 복원 확인 (직접 선택한 파일)
    manual?.let { (file, meta) ->
        RestoreConfirmDialog(
            meta = meta,
            size = file.length(),
            onConfirm = {
                manual = null
                startRestoreLocal(file.absolutePath, meta.label, true)
            },
            onDismiss = { file.delete(); manual = null }
        )
    }

    // 6) 백업 삭제 확인
    deleteEntry?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text(stringResource(R.string.rs_delete_title)) },
            text = { Text("${e.meta.label}\n${formatDate(e.meta.time)}") },
            confirmButton = {
                TextButton(onClick = {
                    deleteEntry = null
                    scope.launch {
                        BackupEngine.deleteEntry(cfg, e)
                            .onSuccess { entries = entries?.filter { it.archive != e.archive } }
                            .onFailure { message = it.message ?: it.toString() }
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 설정
    if (showSettings) {
        SettingsSheet(
            cfg = cfg,
            dynamic = dynamicColor,
            onDynamic = onDynamicChange,
            onCfg = { cfg = it },
            onDismiss = { showSettings = false }
        )
    }

    // 파일 읽는 중
    busyText?.let { t ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(t) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        )
    }

    // 간단한 메시지
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            icon = { Icon(Icons.Default.Warning, null) },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.ok)) } }
        )
    }

    // 진행률 다이얼로그
    if (task.running && !progressHidden) {
        val animated by animateFloatAsState(task.percent / 100f, label = "progress")
        AlertDialog(
            onDismissRequest = { progressHidden = true },
            title = { Text(task.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${task.percent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        task.log,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.progress_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { progressHidden = true }) { Text(stringResource(R.string.hide)) } }
        )
    }

    // 결과 다이얼로그
    task.result?.let { r ->
        AlertDialog(
            onDismissRequest = { TaskHub.clearResult() },
            icon = {
                Icon(
                    if (r.ok) Icons.Default.CheckCircle else Icons.Default.Warning, null,
                    tint = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(r.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(r.details, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Default)
                }
            },
            confirmButton = { TextButton(onClick = { TaskHub.clearResult() }) { Text(stringResource(R.string.ok)) } }
        )
    }
}

@Composable
private fun RunningBanner(task: TaskState, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("${task.percent}%", style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(progress = { task.percent / 100f }, modifier = Modifier.fillMaxWidth())
            Text(
                task.log,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RestoreConfirmDialog(
    meta: BackupMeta,
    size: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null) },
        title = { Text(stringResource(R.string.rs_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MetaDetails(meta, size)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.rs_confirm_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
