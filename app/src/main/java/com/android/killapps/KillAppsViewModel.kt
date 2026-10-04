package com.android.killapps

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class UiDialog { METHOD, MODE, LANGUAGE, ABOUT, SOURCE, DISCLOSURE, CONFIRM }

internal data class TaskState(
    val running: Boolean = false, val cancelled: Boolean = false, val done: Int = 0,
    val total: Int = 0, val success: Int = 0, val failed: Int = 0,
    val status: Int = R.string.ready, val label: String = "", val entries: List<StopSession.Entry> = emptyList()
)

internal data class AppState(
    val apps: List<AppRepository.App> = emptyList(), val root: Boolean? = null, val scanning: Boolean = false,
    val users: Boolean = true, val systems: Boolean = false, val blackMode: Boolean = false, val method: Int = 0,
    val whitelist: Set<String> = emptySet(), val blacklist: Set<String> = emptySet(), val excluded: Set<String> = emptySet(),
    val language: String = "", val usage: Boolean = false, val accessEnabled: Boolean = false, val accessConnected: Boolean = false,
    val task: TaskState = TaskState(), val permissions: Boolean = false, val dialog: UiDialog? = null,
    val targets: Set<String> = emptySet(), val message: Int? = null
) {
    val rootPath: Boolean get() = root == true && method != 1
    val busy: Boolean get() = scanning || task.running
    val ready: Boolean get() = rootPath || usage && accessConnected
    fun eligible(app: AppRepository.App): Boolean = SelectionPolicy.eligible(
        app.pkg, app.system, app.protectedApp, users, systems, blackMode, blacklist, whitelist
    )
    val candidates: List<AppRepository.App> get() = apps.filter { it.active && if (it.system) systems else users }
    val selected: List<AppRepository.App> get() = candidates.filter { eligible(it) && it.pkg !in excluded }
    val protectedCount: Int get() = candidates.count { it.protectedApp }
    val methodLabel: Int get() = when (method) { 1 -> R.string.method_accessibility; 2 -> R.string.method_root; else -> R.string.method_auto }
    val modeLabel: Int get() = if (blackMode) R.string.mode_blacklist else R.string.mode_whitelist
    val accessLabel: Int get() = when { accessConnected -> R.string.access_granted; accessEnabled -> R.string.access_reconnect; else -> R.string.access_missing }
}

internal class KillAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AppState())
    val ui = state.asStateFlow()
    private var observer: Job? = null
    private var foreground = false

    init { syncPreferences(); updatePermissions(); sampleTask() }

    private fun syncPreferences() {
        state.update { it.copy(
            users = prefs.getBoolean("users", true), systems = prefs.getBoolean("systems", false),
            blackMode = prefs.getBoolean("blackMode", false), method = prefs.getInt("method", 0),
            whitelist = prefs.getStringSet("whitelist", emptySet())!!.toSet(),
            blacklist = prefs.getStringSet("blacklist", emptySet())!!.toSet(), language = prefs.getString("language", "") ?: ""
        ) }
    }

    fun onResume() {
        foreground = true
        syncPreferences(); updatePermissions(); sampleTask()
        if (!StopSession.running) scan()
        observer?.cancel()
        observer = viewModelScope.launch {
            while (isActive) {
                val wasRunning = state.value.task.running
                sampleTask()
                if (wasRunning && !StopSession.running) scan()
                delay(400)
            }
        }
    }

    fun onPause() { foreground = false; observer?.cancel(); observer = null }

    private fun sampleTask() {
        val entries = synchronized(StopSession.logs) { StopSession.logs.toList() }
        val task = TaskState(StopSession.running, StopSession.cancelled, StopSession.done, StopSession.total,
            StopSession.success, StopSession.failed, StopSession.statusRes, StopSession.currentLabel, entries)
        state.update { if (it.task == task) it else it.copy(task = task) }
    }

    private fun updatePermissions() {
        state.update { it.copy(usage = AppRepository.usageGranted(app), accessEnabled = AppRepository.accessibilityEnabled(app),
            accessConnected = StopAccessibilityService.instance != null) }
    }

    fun scan(detectRoot: Boolean = false, after: (() -> Unit)? = null) {
        if (state.value.busy || StopSession.running) return
        val old = state.value
        state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val root = if (detectRoot || old.root == null) Shell.available() else old.root
                val apps = runCatching { AppRepository.load(app, root == true && old.method != 1).toList() }
                root to apps
            }
            state.update { it.copy(root = result.first, apps = result.second.getOrDefault(emptyList()), scanning = false) }
            updatePermissions()
            if (result.second.isFailure) {
                android.util.Log.w("KillApps", "Scan failed", result.second.exceptionOrNull())
                message(R.string.scan_failed)
            } else if (after != null) after()
            if (after == null) {
                val current = state.value
                val first = !prefs.getBoolean("onboarded", false)
                val lost = prefs.getBoolean("hadUsage", false) && !current.usage || prefs.getBoolean("hadAccess", false) && !current.accessEnabled
                prefs.edit().putBoolean("onboarded", true).putBoolean("hadUsage", current.usage).putBoolean("hadAccess", current.accessEnabled).apply()
                if (foreground && (first || lost)) state.update { it.copy(permissions = true) }
            }
        }
    }

    fun message(id: Int) { state.update { it.copy(message = id) } }
    fun clearMessage() { state.update { it.copy(message = null) } }
    fun editable(): Boolean {
        if (state.value.task.running || StopSession.running) { message(R.string.task_in_progress); return false }
        return !state.value.scanning
    }
    fun permissions(show: Boolean) {
        if (!show || editable()) { updatePermissions(); state.update { it.copy(permissions = show) } }
    }
    fun dialog(dialog: UiDialog?) {
        if (dialog == null || editable()) state.update { it.copy(dialog = dialog) }
    }
    fun setFilter(system: Boolean, selected: Boolean) {
        if (!editable()) return
        prefs.edit().putBoolean(if (system) "systems" else "users", selected).apply(); syncPreferences()
    }
    fun setMethod(method: Int) {
        if (!editable()) return
        prefs.edit().putInt("method", method).apply(); syncPreferences(); dialog(null); scan()
    }
    fun setMode(black: Boolean) {
        if (!editable()) return
        prefs.edit().putBoolean("blackMode", black).apply(); syncPreferences(); dialog(null)
    }
    fun setLanguage(language: String) {
        prefs.edit().putString("language", language).apply(); syncPreferences(); dialog(null)
    }
    fun setListed(key: String, pkg: String, selected: Boolean) {
        if (!editable()) return
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        if (selected) set.add(pkg) else set.remove(pkg)
        prefs.edit().putStringSet(key, set).apply(); syncPreferences()
    }
    fun setSelected(pkg: String, selected: Boolean) {
        if (!editable()) return
        state.update { it.copy(excluded = if (selected) it.excluded - pkg else it.excluded + pkg) }
    }
    fun selectAll(selected: Boolean) {
        if (!editable()) return
        state.update { it.copy(excluded = if (selected) emptySet() else it.apps.map { a -> a.pkg }.toSet()) }
    }
    fun restoreSelection(excluded: Set<String>) { state.update { it.copy(excluded = excluded) } }

    private fun checkAccess(): Boolean {
        updatePermissions()
        val current = state.value
        if (current.method == 2 && !current.rootPath) { message(R.string.root_required); permissions(true); return false }
        if (!current.rootPath && (!current.usage || !current.accessConnected)) { message(R.string.permissions_required); permissions(true); return false }
        return true
    }
    fun requestStart() {
        if (!editable() || !checkAccess()) return
        val selected = state.value.selected
        if (selected.isEmpty()) { message(R.string.no_selection); return }
        state.update { it.copy(targets = selected.map { a -> a.pkg }.toSet(), dialog = UiDialog.CONFIRM) }
    }
    fun confirmStart() {
        val targets = state.value.targets
        dialog(null)
        scan(detectRoot = true) {
            if (checkAccess()) {
                val current = state.value
                val fresh = current.selected.filter { it.pkg in targets }
                if (fresh.isEmpty()) message(R.string.selection_changed)
                else { StopSession.begin(app, fresh, current.rootPath); sampleTask() }
            }
        }
    }
    fun cancel() { StopSession.cancel(); sampleTask() }
}
