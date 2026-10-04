package com.android.killapps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Page { APPS, SETTINGS, WHITELIST, BLACKLIST, RESULTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KillAppsApp(model: KillAppsViewModel, openSettings: (Boolean) -> Unit, changeLanguage: (String) -> Unit) {
    val state by model.ui.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.APPS) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) { if (message != null) { snackbar.showSnackbar(message); model.clearMessage() } }

    fun navigate(target: Page) {
        if (target == Page.RESULTS || !state.task.running) { page = target; query = "" }
        else model.message(R.string.task_in_progress)
    }
    BackHandler(page != Page.APPS) { page = if (page == Page.WHITELIST || page == Page.BLACKLIST) Page.SETTINGS else Page.APPS; query = "" }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(when (page) {
                        Page.APPS -> R.string.app_name; Page.SETTINGS -> R.string.settings
                        Page.WHITELIST -> R.string.whitelist; Page.BLACKLIST -> R.string.blacklist; Page.RESULTS -> R.string.results
                    }), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (page != Page.APPS) IconButton(onClick = { page = if (page == Page.WHITELIST || page == Page.BLACKLIST) Page.SETTINGS else Page.APPS; query = "" }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    } else Box(Modifier.padding(start = 20.dp, end = 8.dp).size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        StopMark(18.dp)
                    }
                },
                actions = {
                    if (page == Page.APPS) {
                        IconButton(onClick = { if (model.editable()) model.scan(true) }, enabled = !state.busy) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.settings)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.results)) }, onClick = { menu = false; navigate(Page.RESULTS) }, leadingIcon = { Icon(painterResource(R.drawable.ic_task_results), null) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; navigate(Page.SETTINGS) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column {
                if (page == Page.APPS) HomeActionBar(state, onStart = model::requestStart, onCancel = model::cancel)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = page == Page.APPS || page == Page.RESULTS, onClick = { navigate(Page.APPS) },
                        icon = { Icon(painterResource(R.drawable.ic_apps), null, Modifier.size(22.dp)) }, label = { Text(stringResource(R.string.apps_title)) })
                    NavigationBarItem(selected = page == Page.SETTINGS || page == Page.WHITELIST || page == Page.BLACKLIST, onClick = { navigate(Page.SETTINGS) },
                        icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text(stringResource(R.string.settings)) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                when (page) {
                    Page.APPS -> HomePage(state, query, { query = it }, model)
                    Page.SETTINGS -> SettingsPage(state, model, ::navigate)
                    Page.WHITELIST, Page.BLACKLIST -> ListPage(state, page == Page.BLACKLIST, query, { query = it }, model)
                    Page.RESULTS -> ResultsPage(state.task)
                }
            }
        }
    }

    if (state.permissions) ModalBottomSheet(onDismissRequest = { model.permissions(false) }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.permissions), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permission_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip(onClick = {}, label = { Text(stringResource(if (state.root == true) R.string.root_available else R.string.root_unavailable)) }, leadingIcon = { Icon(Icons.Rounded.Build, null, Modifier.size(18.dp)) })
            PermissionAction(R.string.usage_access, if (state.usage) R.string.access_granted else R.string.access_missing, state.usage) { openSettings(false) }
            PermissionAction(R.string.accessibility_access, state.accessLabel, state.accessConnected) { model.dialog(UiDialog.DISCLOSURE) }
            OutlinedButton(onClick = { model.scan(true) }, enabled = !state.scanning, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.detect_root))
            }
            FilledTonalButton(onClick = { model.permissions(false) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
        }
    }
    state.dialog?.let { dialog ->
        when (dialog) {
            UiDialog.METHOD -> ChoiceDialog(R.string.stop_method, listOf(R.string.method_auto, R.string.method_accessibility, R.string.method_root), state.method, model::setMethod) { model.dialog(null) }
            UiDialog.MODE -> ChoiceDialog(R.string.list_mode, listOf(R.string.mode_whitelist, R.string.mode_blacklist), if (state.blackMode) 1 else 0, { model.setMode(it == 1) }) { model.dialog(null) }
            UiDialog.LANGUAGE -> ChoiceDialog(R.string.language, listOf(R.string.language_system, R.string.language_english, R.string.language_chinese), when (state.language) { "en" -> 1; "zh" -> 2; else -> 0 }, { changeLanguage(listOf("", "en", "zh")[it]) }) { model.dialog(null) }
            UiDialog.ABOUT -> InfoDialog(R.string.about, stringResource(R.string.about_body, BuildConfig.VERSION_NAME)) { model.dialog(null) }
            UiDialog.SOURCE -> InfoDialog(if (state.rootPath) R.string.source_root else R.string.source_recent, stringResource(if (state.rootPath) R.string.root_explanation else R.string.recent_explanation)) { model.dialog(null) }
            UiDialog.DISCLOSURE -> AlertDialog(onDismissRequest = { model.dialog(null) }, icon = { Icon(Icons.Rounded.Lock, null) },
                title = { Text(stringResource(R.string.accessibility_disclosure)) }, text = { Text(stringResource(R.string.accessibility_description)) },
                confirmButton = { TextButton(onClick = { model.dialog(null); openSettings(true) }) { Text(stringResource(R.string.accept_open)) } },
                dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.later)) } })
            UiDialog.CONFIRM -> AlertDialog(onDismissRequest = { model.dialog(null) }, icon = { Icon(Icons.Rounded.PlayArrow, null) },
                title = { Text(stringResource(R.string.confirm_title, state.targets.size)) },
                text = { Text(stringResource(R.string.confirm_body, stringResource(if (state.rootPath) R.string.method_root else R.string.method_accessibility), stringResource(state.modeLabel))) },
                confirmButton = { TextButton(onClick = model::confirmStart) { Text(stringResource(R.string.start)) } },
                dismissButton = { TextButton(onClick = { model.dialog(null) }) { Text(stringResource(R.string.later)) } })
        }
    }
}

@Composable
private fun HomePage(state: AppState, query: String, setQuery: (String) -> Unit, model: KillAppsViewModel) {
    val apps = remember(state.apps, state.users, state.systems, state.blackMode, state.whitelist, state.blacklist, query) {
        state.candidates.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
            .sortedBy { when { state.eligible(it) -> 0; it.protectedApp -> 2; else -> 1 } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StatusCard(state) { model.dialog(UiDialog.SOURCE) } }
        item {
            Surface(onClick = { model.permissions(true) }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (state.ready) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.permissions), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (state.ready) R.string.permissions_ready else R.string.permissions_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.users, onClick = { model.setFilter(false, !state.users) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.users)) }, leadingIcon = if (state.users) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
                FilterChip(selected = state.systems, onClick = { model.setFilter(true, !state.systems) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.systems)) }, leadingIcon = if (state.systems) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
            }
        }
        item { AppSearch(query, setQuery, !state.busy) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.candidate_count, state.candidates.size, state.protectedCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.selectAll(state.selected.isEmpty()) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(if (state.selected.isNotEmpty()) R.string.deselect_all_short else R.string.select_all_short))
                }
            }
        }
        if (apps.isEmpty()) item { EmptyApps() }
        items(apps, key = { it.pkg }) { app ->
            AppRow(app, state.eligible(app) && app.pkg !in state.excluded, !state.busy && state.eligible(app),
                if (app.protectedApp) R.string.protected_app else if (!state.eligible(app)) R.string.kept_by_list else if (app.system) R.string.system_app else R.string.user_app,
                onSelected = { model.setSelected(app.pkg, it) })
        }
    }
}

@Composable
private fun StatusCard(state: AppState, onSource: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.selection_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { StopMark(14.dp) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(state.selected.size.toString(), fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(taskText(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (state.scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else if (state.task.running) LinearProgressIndicator(progress = { state.task.done.toFloat() / state.task.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().clickable(onClick = onSource), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(if (state.rootPath) R.string.source_root else R.string.source_recent), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun taskText(state: AppState): String {
    val task = state.task
    return when {
        state.scanning -> stringResource(R.string.scanning)
        task.running && task.cancelled -> stringResource(R.string.cancelling)
        task.status == R.string.stopping_app -> stringResource(R.string.stopping_app, task.label)
        task.status == R.string.task_finished || task.status == R.string.task_cancelled -> stringResource(task.status, task.success, task.failed, task.total - task.done)
        else -> stringResource(task.status)
    }
}

@Composable
private fun HomeActionBar(state: AppState, onStart: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.selected_count, state.selected.size), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(state.modeLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = if (state.task.running) onCancel else onStart, enabled = !state.scanning && !(state.task.running && state.task.cancelled), modifier = Modifier.heightIn(min = 52.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                Icon(if (state.task.running) Icons.Rounded.Close else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(stringResource(if (state.task.running) R.string.cancel_task else R.string.start))
            }
        }
    }
}

@Composable
private fun AppSearch(query: String, setQuery: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value = query, onValueChange = setQuery, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        placeholder = { Text(stringResource(R.string.search_apps), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { setQuery("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
}

@Composable
private fun AppRow(app: AppRepository.App, checked: Boolean, enabled: Boolean, status: Int, onSelected: (Boolean) -> Unit) {
    val context = LocalContext.current
    var icon by remember(app.pkg) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.pkg, app.info.icon) {
        icon = withContext(Dispatchers.IO) { runCatching { app.info.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onSelected)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon!!, null, Modifier.fillMaxSize()) else Icon(Icons.AutoMirrored.Rounded.List, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (app.protectedApp) Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(status), style = MaterialTheme.typography.labelSmall, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun EmptyApps() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) { Icon(Icons.Rounded.Search, null, Modifier.padding(20.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(stringResource(R.string.no_matching_apps), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.no_candidates), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsPage(state: AppState, model: KillAppsViewModel, navigate: (Page) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            SettingsGroup(R.string.section_access) {
                SettingsRow(R.string.permissions, stringResource(if (state.ready) R.string.permissions_ready else R.string.permissions_needed), Icons.Rounded.Lock) { model.permissions(true) }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                SettingsRow(R.string.stop_method, stringResource(state.methodLabel), Icons.Rounded.Build) { model.dialog(UiDialog.METHOD) }
                SettingsRow(R.string.detect_root, stringResource(if (state.root == true) R.string.root_available else R.string.root_unavailable), Icons.Rounded.Refresh) { model.scan(true) }
            }
        }
        item { HelpCard(R.string.method_description) }
        item {
            SettingsGroup(R.string.section_rules) {
                SettingsRow(R.string.list_mode, stringResource(state.modeLabel), ImageVector.vectorResource(R.drawable.ic_list_mode)) { model.dialog(UiDialog.MODE) }
                SettingsRow(R.string.whitelist, stringResource(R.string.list_count, state.whitelist.size), Icons.Rounded.CheckCircle) { navigate(Page.WHITELIST) }
                SettingsRow(R.string.blacklist, stringResource(R.string.list_count, state.blacklist.size), Icons.Rounded.Close) { navigate(Page.BLACKLIST) }
            }
        }
        item { HelpCard(R.string.system_help) }
        item {
            SettingsGroup(R.string.section_preferences) {
                SettingsRow(R.string.language, stringResource(when (state.language) { "en" -> R.string.language_english; "zh" -> R.string.language_chinese; else -> R.string.language_system }), Icons.Rounded.Settings) { model.dialog(UiDialog.LANGUAGE) }
                SettingsRow(R.string.results, null, ImageVector.vectorResource(R.drawable.ic_task_results)) { navigate(Page.RESULTS) }
                SettingsRow(R.string.about, BuildConfig.VERSION_NAME, Icons.Rounded.Info) { model.dialog(UiDialog.ABOUT) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) { Column(content = content) }
    }
}

@Composable
private fun SettingsRow(title: Int, supporting: String?, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HelpCard(text: Int) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListPage(state: AppState, black: Boolean, query: String, setQuery: (String) -> Unit, model: KillAppsViewModel) {
    val apps = remember(state.apps, query) { state.apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) } }
    val listed = if (black) state.blacklist else state.whitelist
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { HelpCard(if (black) R.string.blacklist_help else R.string.whitelist_help) }
        item { Spacer(Modifier.height(4.dp)); AppSearch(query, setQuery, !state.busy) }
        item { Text(stringResource(R.string.list_count, listed.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        if (apps.isEmpty()) item { EmptyApps() }
        items(apps, key = { it.pkg }) { app -> AppRow(app, app.pkg in listed, !state.busy && !app.protectedApp,
            if (app.protectedApp) R.string.protected_app else if (app.system) R.string.system_app else R.string.user_app,
            onSelected = { model.setListed(if (black) "blacklist" else "whitelist", app.pkg, it) }) }
    }
}

@Composable
private fun ResultsPage(task: TaskState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text(stringResource(R.string.results), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric(task.success, R.string.metric_stopped); Metric(task.failed, R.string.metric_failed); Metric((task.total - task.done).coerceAtLeast(0), R.string.metric_skipped)
                    }
                    if (task.running) LinearProgressIndicator(progress = { task.done.toFloat() / task.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (task.entries.isEmpty()) item { Text(stringResource(R.string.no_results), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(task.entries, key = { it.pkg }) { entry ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(if (entry.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, tint = if (entry.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(if (entry.ok) R.string.result_success else R.string.result_failure, entry.label), style = MaterialTheme.typography.titleSmall)
                        Text(entry.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!entry.ok) Text(stringResource(entry.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(count: Int, label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PermissionAction(title: Int, status: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(status), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.open_settings))
        }
    }
}

@Composable
private fun ChoiceDialog(title: Int, items: List<Int>, selected: Int, choose: (Int) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(title)) },
        text = {
            Column {
                items.forEachIndexed { index, resource ->
                    Row(Modifier.fillMaxWidth().toggleable(selected == index, role = Role.RadioButton, onValueChange = { choose(index) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == index, onClick = null); Spacer(Modifier.width(8.dp)); Text(stringResource(resource), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun InfoDialog(title: Int, body: String, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Rounded.Info, null) }, title = { Text(stringResource(title)) },
        text = { Text(body) }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
internal fun StopMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size / 4)))
}
