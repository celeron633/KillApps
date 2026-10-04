package com.android.killapps;

import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

final class StopSession {
    static volatile boolean running, cancelled;
    static volatile int done, success, failed, total;
    static volatile int statusRes = R.string.ready;
    static volatile String currentLabel = "";
    static final class Entry {
        final String label, pkg; final boolean ok; final int reason;
        Entry(AppRepository.App app, boolean ok, int reason) { label = app.label; pkg = app.pkg; this.ok = ok; this.reason = reason; }
        String text(Context c) { return c.getString(ok ? R.string.result_success : R.string.result_failure, label)
                + "\n" + pkg + (ok ? "" : "\n" + c.getString(reason)); }
    }
    static final List<Entry> logs = Collections.synchronizedList(new ArrayList<>());
    static String status(Context c) {
        if (statusRes == R.string.stopping_app) return c.getString(R.string.stopping_app, currentLabel);
        if (statusRes == R.string.task_finished) return c.getString(R.string.task_finished, success, failed, total - done);
        if (statusRes == R.string.task_cancelled) return c.getString(R.string.task_cancelled, success, failed, total - done);
        return c.getString(statusRes);
    }
    static final Handler main = new Handler(Looper.getMainLooper());
    static void begin(Context context, List<AppRepository.App> apps, boolean root) {
        if (running) return;
        running = true; cancelled = false; done = success = failed = 0; total = apps.size(); logs.clear();
        statusRes = R.string.preparing;
        if (!root) {
            StopAccessibilityService service = StopAccessibilityService.instance;
            if (service == null) { running = false; statusRes = R.string.service_disconnected; return; }
            service.start(apps); return;
        }
        Context c = context.getApplicationContext();
        new Thread(() -> {
            try {
                for (AppRepository.App app : apps) {
                    if (cancelled) break;
                    currentLabel = app.label; statusRes = R.string.stopping_app;
                    // Package names come only from PackageManager; validate again before shell use.
                    if (!app.pkg.matches("[A-Za-z0-9_.]+")) { result(app, false, R.string.invalid_package); continue; }
                    Shell.Result r = Shell.root("am force-stop --user " + android.os.Process.myUid() / 100000 + " " + app.pkg);
                    boolean stopped = false;
                    try { stopped = (c.getPackageManager().getApplicationInfo(app.pkg, 0).flags & ApplicationInfo.FLAG_STOPPED) != 0; }
                    catch (Exception ignored) { }
                    result(app, r.code == 0 && stopped, r.code == 0 ? R.string.verify_failed : R.string.root_failed);
                }
            } finally { finish(c, false); }
        }, "killapps-root").start();
    }
    static void result(AppRepository.App app, boolean ok, int reason) {
        done++; if (ok) success++; else failed++;
        logs.add(new Entry(app, ok, reason));
    }
    static void cancel() {
        cancelled = true;
        main.post(() -> { if (StopAccessibilityService.instance != null) StopAccessibilityService.instance.cancelBatch(); });
    }
    static void finish(Context c, boolean bringBack) {
        statusRes = cancelled ? R.string.task_cancelled : R.string.task_finished; running = false;
        if (bringBack) c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
