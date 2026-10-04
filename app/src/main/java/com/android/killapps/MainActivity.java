package com.android.killapps;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.*;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.*;
import androidx.recyclerview.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<AppRepository.App> apps = new ArrayList<>();
    private final Set<String> excluded = new HashSet<>();
    private Boolean root;
    private boolean scanning, resumed, wasRunning, controlsLocked;
    private String page = "home", query = "";
    private LinearLayout container, body;
    private MaterialToolbar toolbar;
    private AppAdapter adapter;
    private TextView summary, state, source, counts;
    private MaterialButton start;
    private MaterialButton selectAll, deselectAll;
    private LinearProgressIndicator progress;
    private AlertDialog permissionsDialog;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            if (StopSession.running || wasRunning) updateStatus();
            if (wasRunning && !StopSession.running) { wasRunning = false; scan(false, null); }
            handler.postDelayed(this, 400);
        }
    };
    @Override public void onCreate(Bundle saved) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT < 33)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(prefs.getString("language", "")));
        super.onCreate(saved);
        if (Build.VERSION.SDK_INT >= 33) {
            android.app.LocaleManager manager = getSystemService(android.app.LocaleManager.class);
            prefs.edit().putString("language", manager.getApplicationLocales().toLanguageTags()).apply();
        }
        if (saved != null) {
            page = saved.getString("page", "home");
            ArrayList<String> prior = saved.getStringArrayList("excluded"); if (prior != null) excluded.addAll(prior);
        }
        showPage(page);
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; handler.post(poll);
        if (permissionsDialog != null && permissionsDialog.isShowing()) { permissionsDialog.dismiss(); permissionsDialog = null; showPermissions(); }
        if (!StopSession.running) scan(false, null); else wasRunning = true;
    }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { worker.shutdown(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("page", page); out.putStringArrayList("excluded", new ArrayList<>(excluded)); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() { if (!page.equals("home")) showPage("home"); else super.onBackPressed(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private int color(int attr) { android.util.TypedValue v = new android.util.TypedValue(); getTheme().resolveAttribute(attr, v, true); return v.data; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(CharSequence value, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        if (bold) t.setTypeface(null, Typeface.BOLD); t.setPadding(0, dp(4), 0, dp(4)); return t;
    }
    private TextView text(int resource, int size, boolean bold) { return text(getString(resource), size, bold); }
    private MaterialButton button(int label, Runnable action) {
        MaterialButton b = new MaterialButton(this); b.setText(label); b.setOnClickListener(v -> action.run()); return b;
    }
    private boolean rootPath() { return Boolean.TRUE.equals(root) && prefs.getInt("method", 0) != 1; }
    private boolean users() { return prefs.getBoolean("users", true); }
    private boolean systems() { return prefs.getBoolean("systems", false); }
    private boolean blackMode() { return prefs.getBoolean("blackMode", false); }
    private Set<String> list(String key) { return new HashSet<>(prefs.getStringSet(key, Collections.emptySet())); }
    private boolean eligible(AppRepository.App a) {
        return SelectionPolicy.eligible(a.pkg, a.system, a.protectedApp, users(), systems(), blackMode(), list("blacklist"), list("whitelist"));
    }
    private List<AppRepository.App> selected() {
        List<AppRepository.App> result = new ArrayList<>();
        for (AppRepository.App a : apps) if (a.active && eligible(a) && !excluded.contains(a.pkg)) result.add(a);
        return result;
    }
    private void scan(boolean detect, Runnable after) {
        if (scanning || StopSession.running) return;
        scanning = true; updateStatus();
        boolean check = detect || root == null;
        worker.execute(() -> {
            boolean available = check ? Shell.available() : Boolean.TRUE.equals(root);
            boolean useRoot = available && prefs.getInt("method", 0) != 1;
            List<AppRepository.App> loaded = null;
            try { loaded = AppRepository.load(getApplicationContext(), useRoot); }
            catch (Exception e) { android.util.Log.w("KillApps", "Scan failed", e); }
            final List<AppRepository.App> result = loaded;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                root = available; scanning = false;
                if (result == null) { apps = new ArrayList<>(); toast(R.string.scan_failed); }
                else apps = result;
                if (page.equals("home")) { adapter.refresh(); updateStatus(); }
                else showPage(page);
                if (result != null && after != null) after.run();
                if (after == null) promptIfNeeded();
            });
        });
    }
    private void promptIfNeeded() {
        boolean usage = AppRepository.usageGranted(this), access = AppRepository.accessibilityEnabled(this);
        boolean first = !prefs.getBoolean("onboarded", false);
        boolean lost = prefs.getBoolean("hadUsage", false) && !usage || prefs.getBoolean("hadAccess", false) && !access;
        prefs.edit().putBoolean("hadUsage", usage).putBoolean("hadAccess", access).putBoolean("onboarded", true).apply();
        if (resumed && (first || lost)) showPermissions();
    }
    private void showPage(String target) {
        page = target; query = ""; adapter = null; summary = state = source = counts = null; start = null; progress = null;
        container = column(); container.setBackgroundColor(color(com.google.android.material.R.attr.colorSurface));
        ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean dark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT < 27) getWindow().setNavigationBarColor(android.graphics.Color.rgb(25, 33, 27));
        toolbar = new MaterialToolbar(this); toolbar.setTitle(target.equals("home") ? getString(R.string.app_name) : getString(
                target.equals("settings") ? R.string.settings : target.equals("whitelist") ? R.string.whitelist : R.string.blacklist));
        container.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        if (target.equals("home")) {
            toolbar.getMenu().add(0, 1, 0, R.string.refresh).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            toolbar.getMenu().add(0, 2, 1, R.string.settings).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            toolbar.getMenu().add(0, 3, 2, R.string.results).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 3) showResults();
                else if (StopSession.running) toast(R.string.task_in_progress);
                else if (item.getItemId() == 1) scan(true, null); else showPage("settings");
                return true;
            });
            home();
        } else {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationContentDescription(R.string.back);
            toolbar.setNavigationOnClickListener(v -> showPage(target.equals("settings") ? "home" : "settings"));
            if (target.equals("settings")) settings(); else listEditor(target);
        }
        setContentView(container);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), container);
        bars.setAppearanceLightStatusBars(!dark); bars.setAppearanceLightNavigationBars(!dark);
        ViewCompat.requestApplyInsets(container); updateStatus();
    }
    private void home() {
        body = column(); body.setPadding(dp(20), dp(8), dp(20), 0);
        container.addView(body);
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(24)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(color(com.google.android.material.R.attr.colorSecondaryContainer));
        LinearLayout cardBody = column(); cardBody.setPadding(dp(16), dp(10), dp(16), dp(10));
        source = text("", 12, false); summary = text("", 24, true); state = text("", 13, false);
        cardBody.addView(source); cardBody.addView(summary); cardBody.addView(state);
        progress = new LinearProgressIndicator(this); cardBody.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
        card.addView(cardBody); body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        MaterialButton access = button(R.string.permissions, this::showPermissions); body.addView(access);
        LinearLayout filters = new LinearLayout(this);
        MaterialCheckBox user = new MaterialCheckBox(this); user.setText(R.string.users); user.setChecked(users());
        MaterialCheckBox system = new MaterialCheckBox(this); system.setText(R.string.systems); system.setChecked(systems());
        filters.addView(user, new LinearLayout.LayoutParams(0, -2, 1)); filters.addView(system, new LinearLayout.LayoutParams(0, -2, 1)); body.addView(filters);
        user.setOnCheckedChangeListener((b, checked) -> { prefs.edit().putBoolean("users", checked).apply(); adapter.refresh(); updateStatus(); });
        system.setOnCheckedChangeListener((b, checked) -> { prefs.edit().putBoolean("systems", checked).apply(); adapter.refresh(); updateStatus(); });
        body.addView(search());
        counts = text("", 12, false); body.addView(counts);
        RecyclerView recycler = recycler(false, null); container.addView(recycler, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bottom = column(); bottom.setPadding(dp(20), 0, dp(20), dp(8));
        LinearLayout choices = new LinearLayout(this);
        selectAll = button(R.string.select_all, () -> { excluded.clear(); adapter.refresh(); updateStatus(); });
        deselectAll = button(R.string.deselect_all, () -> { for (AppRepository.App a : apps) excluded.add(a.pkg); adapter.refresh(); updateStatus(); });
        selectAll.setTextSize(11); deselectAll.setTextSize(11); choices.addView(selectAll, new LinearLayout.LayoutParams(0, -2, 1)); choices.addView(deselectAll, new LinearLayout.LayoutParams(0, -2, 1));
        bottom.addView(choices);
        start = button(R.string.start, () -> { if (StopSession.running) StopSession.cancel(); else requestStart(); });
        start.setMinHeight(dp(56)); start.setCornerRadius(dp(28)); bottom.addView(start); container.addView(bottom);
    }
    private View search() {
        TextInputLayout layout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        layout.setHint(R.string.search_apps); layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(layout.getContext()); input.setSingleLine(true); input.setTextSize(14);
        layout.addView(input); input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) { query = s.toString().toLowerCase(Locale.ROOT); if (adapter != null) adapter.refresh(); }
            public void afterTextChanged(Editable e) { }
        }); return layout;
    }
    private RecyclerView recycler(boolean editor, String key) {
        RecyclerView view = new RecyclerView(this); view.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter(editor, key); view.setAdapter(adapter); return view;
    }
    private void settings() {
        ScrollView scroll = new ScrollView(this); body = column(); body.setPadding(dp(20), dp(8), dp(20), dp(20)); scroll.addView(body); container.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setting(R.string.permissions, permissionSummary(), this::showPermissions);
        setting(R.string.stop_method, getString(methodResource()), () -> choose(R.string.stop_method,
                new int[]{R.string.method_auto, R.string.method_accessibility, R.string.method_root}, prefs.getInt("method", 0), n -> { prefs.edit().putInt("method", n).apply(); scan(false, null); }));
        body.addView(text(R.string.method_description, 13, false));
        setting(R.string.detect_root, getString(Boolean.TRUE.equals(root) ? R.string.root_available : R.string.root_unavailable), () -> scan(true, null));
        setting(R.string.list_mode, getString(modeResource()), () -> choose(R.string.list_mode,
                new int[]{R.string.mode_whitelist, R.string.mode_blacklist}, blackMode() ? 1 : 0, n -> { prefs.edit().putBoolean("blackMode", n == 1).apply(); showPage("settings"); }));
        setting(R.string.whitelist, getString(R.string.list_count, list("whitelist").size()), () -> showPage("whitelist"));
        setting(R.string.blacklist, getString(R.string.list_count, list("blacklist").size()), () -> showPage("blacklist"));
        body.addView(text(R.string.system_help, 13, false));
        String lang = prefs.getString("language", "");
        setting(R.string.language, getString(lang.equals("en") ? R.string.language_english : lang.equals("zh") ? R.string.language_chinese : R.string.language_system),
                () -> choose(R.string.language, new int[]{R.string.language_system, R.string.language_english, R.string.language_chinese}, lang.equals("en") ? 1 : lang.equals("zh") ? 2 : 0, n -> {
                    String tag = new String[]{"", "en", "zh"}[n]; prefs.edit().putString("language", tag).apply(); AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
                }));
        setting(R.string.results, "", this::showResults);
        setting(R.string.about, getString(R.string.app_name), () -> new MaterialAlertDialogBuilder(this).setTitle(R.string.about).setMessage(R.string.about_body).setPositiveButton(R.string.close, null).show());
    }
    private void setting(int title, String description, Runnable action) {
        LinearLayout row = column(); row.setPadding(0, dp(12), 0, dp(12)); row.addView(text(title, 18, true));
        if (!description.isEmpty()) row.addView(text(description, 13, false)); row.setBackgroundResource(android.R.drawable.list_selector_background); row.setOnClickListener(v -> action.run()); body.addView(row);
    }
    private void choose(int title, int[] items, int checked, java.util.function.IntConsumer apply) {
        if (StopSession.running) { toast(R.string.task_in_progress); return; }
        String[] labels = new String[items.length]; for (int i = 0; i < items.length; i++) labels[i] = getString(items[i]);
        new MaterialAlertDialogBuilder(this).setTitle(title).setSingleChoiceItems(labels, checked, (dialog, n) -> { dialog.dismiss(); apply.accept(n); }).setNegativeButton(R.string.close, null).show();
    }
    private void listEditor(String key) {
        body = column(); body.setPadding(dp(20), dp(8), dp(20), dp(8)); container.addView(body);
        body.addView(text(key.equals("blacklist") ? R.string.blacklist_help : R.string.whitelist_help, 14, false)); body.addView(search());
        container.addView(recycler(true, key), new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private int methodResource() { return new int[]{R.string.method_auto, R.string.method_accessibility, R.string.method_root}[prefs.getInt("method", 0)]; }
    private int modeResource() { return blackMode() ? R.string.mode_blacklist : R.string.mode_whitelist; }
    private String permissionSummary() {
        return getString(R.string.permission_row, getString(R.string.usage_access), getString(AppRepository.usageGranted(this) ? R.string.access_granted : R.string.access_missing)) + "\n"
                + getString(R.string.permission_row, getString(R.string.accessibility_access), getString(accessibilityState()));
    }
    private int accessibilityState() {
        return StopAccessibilityService.instance != null ? R.string.access_granted
                : AppRepository.accessibilityEnabled(this) ? R.string.access_reconnect : R.string.access_missing;
    }
    private void showPermissions() {
        if (StopSession.running) { toast(R.string.task_in_progress); return; }
        if (permissionsDialog != null && permissionsDialog.isShowing()) return;
        LinearLayout view = column(); view.setPadding(dp(24), dp(8), dp(24), 0);
        view.addView(text(R.string.permission_intro, 14, false));
        view.addView(text(Boolean.TRUE.equals(root) ? R.string.root_available : R.string.root_unavailable, 16, true));
        MaterialButton usage = button(R.string.usage_access, () -> openSettings(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        usage.setText(getString(R.string.permission_row, getString(R.string.usage_access), getString(AppRepository.usageGranted(this) ? R.string.access_granted : R.string.access_missing))); view.addView(usage);
        MaterialButton access = button(R.string.accessibility_access, () -> new MaterialAlertDialogBuilder(this).setTitle(R.string.accessibility_disclosure)
                .setMessage(R.string.accessibility_description).setPositiveButton(R.string.accept_open, (d, w) -> openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton(R.string.later, null).show());
        access.setText(getString(R.string.permission_row, getString(R.string.accessibility_access), getString(accessibilityState()))); view.addView(access);
        view.addView(button(R.string.detect_root, () -> { permissionsDialog.dismiss(); scan(true, this::showPermissions); }));
        permissionsDialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.permissions).setView(view).setPositiveButton(R.string.done, null).show();
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); } catch (Exception e) {
            if (Settings.ACTION_USAGE_ACCESS_SETTINGS.equals(intent.getAction()) && intent.getData() != null) { intent.setData(null); openSettings(intent); }
            else toast(R.string.settings_unavailable);
        }
    }
    private void requestStart() {
        if (scanning) return;
        if (prefs.getInt("method", 0) == 2 && !rootPath()) { toast(R.string.root_required); showPermissions(); return; }
        if (!rootPath() && (!AppRepository.usageGranted(this) || StopAccessibilityService.instance == null)) { toast(R.string.permissions_required); showPermissions(); return; }
        List<AppRepository.App> targets = selected(); if (targets.isEmpty()) { toast(R.string.no_selection); return; }
        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.confirm_title, targets.size()))
                .setMessage(getString(R.string.confirm_body, getString(rootPath() ? R.string.method_root : R.string.method_accessibility), getString(modeResource())))
                .setNegativeButton(R.string.later, null).setPositiveButton(R.string.start, (d, w) -> {
                    Set<String> packages = new HashSet<>(); for (AppRepository.App a : targets) packages.add(a.pkg);
                    scan(true, () -> {
                        if (prefs.getInt("method", 0) == 2 && !rootPath()) { toast(R.string.root_required); return; }
                        if (!rootPath() && (!AppRepository.usageGranted(this) || StopAccessibilityService.instance == null)) { showPermissions(); return; }
                        List<AppRepository.App> fresh = new ArrayList<>(); for (AppRepository.App a : selected()) if (packages.contains(a.pkg)) fresh.add(a);
                        if (fresh.isEmpty()) { toast(R.string.selection_changed); return; }
                        StopSession.begin(this, fresh, rootPath()); wasRunning = StopSession.running; updateStatus();
                    });
                }).show();
    }
    private void updateStatus() {
        if (!page.equals("home") || summary == null) return;
        source.setText(rootPath() ? R.string.running_source : R.string.recent_source);
        source.setOnClickListener(v -> { if (!rootPath()) new MaterialAlertDialogBuilder(this).setMessage(R.string.recent_explanation).setPositiveButton(R.string.close, null).show(); });
        summary.setText(getString(R.string.selected_count, selected().size()));
        state.setText(scanning ? getString(R.string.scanning) : StopSession.cancelled && StopSession.running ? getString(R.string.cancelling) : StopSession.status(this));
        int candidates = 0, protect = 0;
        for (AppRepository.App a : apps) if (a.active && (a.system ? systems() : users())) { candidates++; if (a.protectedApp) protect++; }
        counts.setText(getString(R.string.candidate_count, candidates, protect));
        progress.setVisibility(scanning || StopSession.running ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(scanning);
        if (!scanning) { progress.setMax(Math.max(1, StopSession.total)); progress.setProgressCompat(StopSession.done, true); }
        start.setText(StopSession.running ? R.string.cancel_task : R.string.start);
        if (StopSession.running) wasRunning = true;
        // Freeze filters and selection while a task is in progress.
        enableChildren(body, !StopSession.running && !scanning);
        selectAll.setEnabled(!StopSession.running && !scanning); deselectAll.setEnabled(!StopSession.running && !scanning);
        boolean locked = StopSession.running || scanning;
        if (controlsLocked != locked) { controlsLocked = locked; if (adapter != null) adapter.refresh(); }
        start.setEnabled(!scanning && !(StopSession.running && StopSession.cancelled));
    }
    private void enableChildren(View view, boolean enabled) {
        if (view instanceof CompoundButton || view instanceof EditText) view.setEnabled(enabled);
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup)view; for (int i = 0; i < group.getChildCount(); i++) enableChildren(group.getChildAt(i), enabled); }
    }
    private void showResults() {
        StringBuilder result = new StringBuilder(StopSession.status(this));
        synchronized (StopSession.logs) {
            if (StopSession.logs.isEmpty()) result.append("\n\n").append(getString(R.string.no_results));
            else for (StopSession.Entry entry : StopSession.logs) result.append("\n\n").append(entry.text(this));
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.results).setMessage(result.toString()).setPositiveButton(R.string.close, null).show();
    }
    private void toast(int resource) { Toast.makeText(this, resource, Toast.LENGTH_LONG).show(); }
    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
        final boolean editor; final String key; final List<AppRepository.App> visible = new ArrayList<>();
        AppAdapter(boolean editor, String key) { this.editor = editor; this.key = key; refresh(); }
        void refresh() {
            visible.clear();
            for (AppRepository.App a : apps) if ((editor || a.active && (a.system ? systems() : users()))
                    && (query.isEmpty() || a.pkg.toLowerCase(Locale.ROOT).contains(query) || a.label.toLowerCase(Locale.ROOT).contains(query))) visible.add(a);
            notifyDataSetChanged();
        }
        class Holder extends RecyclerView.ViewHolder {
            ImageView icon; TextView title, subtitle; MaterialCheckBox checked;
            Holder(LinearLayout row) {
                super(row); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(20), dp(5), dp(16), dp(5));
                icon = new ImageView(MainActivity.this); row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                LinearLayout labels = column(); labels.setPadding(dp(12), 0, dp(4), 0); title = text("", 15, true); subtitle = text("", 11, false);
                title.setMaxLines(1); title.setEllipsize(TextUtils.TruncateAt.END); subtitle.setMaxLines(2); subtitle.setEllipsize(TextUtils.TruncateAt.END);
                labels.addView(title); labels.addView(subtitle); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                checked = new MaterialCheckBox(MainActivity.this); row.addView(checked);
            }
        }
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(MainActivity.this); row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); return new Holder(row);
        }
        @Override public void onBindViewHolder(Holder holder, int position) {
            if (visible.isEmpty()) {
                holder.title.setText(R.string.no_candidates); holder.title.setMaxLines(4); holder.subtitle.setText(""); holder.icon.setVisibility(View.GONE); holder.checked.setVisibility(View.GONE); holder.itemView.setOnClickListener(null); return;
            }
            AppRepository.App a = visible.get(position); holder.icon.setVisibility(View.VISIBLE); holder.checked.setVisibility(View.VISIBLE); holder.title.setMaxLines(1);
            holder.title.setText(a.label); holder.icon.setImageDrawable(a.info.loadIcon(getPackageManager()));
            holder.subtitle.setText(getString(R.string.app_subtitle, getString(a.protectedApp ? R.string.protected_app : !editor && !eligible(a) ? R.string.kept_by_list : a.system ? R.string.system_app : R.string.user_app), a.pkg));
            holder.checked.setOnCheckedChangeListener(null);
            holder.checked.setChecked(editor ? list(key).contains(a.pkg) : eligible(a) && !excluded.contains(a.pkg));
            holder.checked.setEnabled(!a.protectedApp && !StopSession.running && !scanning && (editor || eligible(a)));
            holder.checked.setContentDescription(getString(editor && key.equals("whitelist") ? R.string.whitelist : editor ? R.string.blacklist : R.string.start) + " · " + a.label);
            holder.checked.setOnCheckedChangeListener((b, checked) -> {
                if (editor) { Set<String> value = list(key); if (checked) value.add(a.pkg); else value.remove(a.pkg); prefs.edit().putStringSet(key, value).apply(); }
                else { if (checked) excluded.remove(a.pkg); else excluded.add(a.pkg); updateStatus(); }
            });
            holder.itemView.setOnClickListener(v -> { if (!StopSession.running && !scanning && holder.checked.isEnabled()) holder.checked.toggle(); });
        }
        @Override public int getItemCount() { return Math.max(1, visible.size()); }
    }
}
