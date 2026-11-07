package app.n4c.ark

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(
    app: AppItem,
    cloud: DavConfig,
    onDismiss: () -> Unit,
    onStart: (BackupMode, Dest) -> Unit
) {
    var mode by remember { mutableStateOf<BackupMode?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        app.pkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(targetState = mode, label = "backup-step") { m ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (m == null) {
                        Text(
                            stringResource(R.string.bs_what),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OptionCard(
                            painterResource(R.drawable.ic_archive),
                            stringResource(R.string.mode_apk), stringResource(R.string.mode_apk_desc),
                            { mode = BackupMode.APK }
                        )
                        OptionCard(
                            painterResource(R.drawable.ic_storage),
                            stringResource(R.string.mode_data), stringResource(R.string.mode_data_desc),
                            { mode = BackupMode.DATA }
                        )
                        OptionCard(
                            painterResource(R.drawable.ic_layers),
                            stringResource(R.string.mode_both), stringResource(R.string.mode_both_desc),
                            { mode = BackupMode.BOTH }
                        )
                    } else {
                        Text(
                            stringResource(R.string.bs_where),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OptionCard(
                            painterResource(R.drawable.ic_phone),
                            stringResource(R.string.dest_local), stringResource(R.string.dest_local_desc),
                            { onStart(m, Dest.LOCAL) }
                        )
                        OptionCard(
                            painterResource(R.drawable.ic_folder),
                            stringResource(R.string.dest_custom), stringResource(R.string.dest_custom_desc),
                            { onStart(m, Dest.CUSTOM) }
                        )
                        if (cloud.ready) {
                            OptionCard(
                                painterResource(R.drawable.ic_cloud),
                                stringResource(R.string.dest_cloud), stringResource(R.string.dest_cloud_desc, cloud.host),
                                { onStart(m, Dest.CLOUD) }
                            )
                        }
                        TextButton(onClick = { mode = null }) { Text(stringResource(R.string.back)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreMenuSheet(
    cloud: DavConfig,
    onLocal: () -> Unit,
    onCloud: () -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.rs_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            OptionCard(
                painterResource(R.drawable.ic_phone),
                stringResource(R.string.rs_local), stringResource(R.string.rs_local_desc), onLocal
            )
            if (cloud.ready) {
                OptionCard(
                    painterResource(R.drawable.ic_cloud),
                    stringResource(R.string.rs_cloud), stringResource(R.string.rs_cloud_desc), onCloud
                )
            }
            OptionCard(
                painterResource(R.drawable.ic_folder),
                stringResource(R.string.rs_other), stringResource(R.string.rs_other_desc), onOther
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListSheet(
    source: Dest,
    entries: List<BackupEntry>?,
    onPick: (BackupEntry) -> Unit,
    onDelete: (BackupEntry) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                stringResource(if (source == Dest.CLOUD) R.string.rs_list_cloud else R.string.rs_list_local),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            when {
                entries == null -> Row(
                    Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.rs_loading))
                }
                entries.isEmpty() -> Text(
                    stringResource(R.string.rs_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.archive }) { e ->
                        EntryRow(e, onClick = { onPick(e) }, onDelete = { onDelete(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: BackupEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(e.meta.pkg, 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.meta.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    modeLabel(e.meta.mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.entry_line1, e.meta.versionName, formatDate(e.meta.time)),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.entry_line2, e.meta.device, e.meta.androidVersion, formatSize(e.size)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MetaDetails(meta: BackupMeta, size: Long) {
    @Composable
    fun line(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f)
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
        }
    }
    Column {
        Text(
            "${meta.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            meta.pkg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        line(stringResource(R.string.meta_device), meta.device)
        line(stringResource(R.string.meta_android), "${meta.androidVersion} (API ${meta.sdk})")
        line(stringResource(R.string.meta_app_version), "${meta.versionName} (${meta.versionCode})")
        line(stringResource(R.string.meta_date), formatDate(meta.time))
        line(stringResource(R.string.meta_contains), modeLabel(meta.mode))
        line(stringResource(R.string.meta_size), formatSize(size))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    cfg: DavConfig,
    dynamic: Boolean,
    onDynamic: (Boolean) -> Unit,
    onCfg: (DavConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(cfg.url) }
    var user by remember { mutableStateOf(cfg.user) }
    var pass by remember { mutableStateOf(cfg.pass) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.set_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.set_cloud),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.set_cloud_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (cfg.connected) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.set_connected, cfg.host),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val c = cfg.copy(connected = false)
                        ArkPrefs.saveCloud(ctx, c)
                        onCfg(c)
                        status = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.set_disconnect)) }
            } else {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.set_url)) },
                    placeholder = { Text("https://example.com/remote.php/dav/files/me") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text(stringResource(R.string.set_user)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.set_pass)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            val c = DavConfig(url.trim(), user.trim(), pass, true)
                            BackupEngine.testConnection(c)
                                .onSuccess {
                                    ArkPrefs.saveCloud(ctx, c)
                                    onCfg(c)
                                    statusOk = true
                                    status = ctx.getString(R.string.set_conn_ok)
                                }
                                .onFailure {
                                    statusOk = false
                                    status = ctx.getString(R.string.set_conn_fail, it.message ?: it.toString())
                                }
                            busy = false
                        }
                    },
                    enabled = url.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.set_connect))
                }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.set_dynamic),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.set_dynamic_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = dynamic, onCheckedChange = onDynamic)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.set_lang_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
