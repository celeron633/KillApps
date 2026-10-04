package com.android.killapps;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.*;
import android.os.Process;
import android.provider.Settings;
import android.telecom.TelecomManager;
import java.util.*;

final class AppRepository {
    static final class App {
        final String pkg, label; final ApplicationInfo info;
        final boolean system, active, protectedApp;
        App(ApplicationInfo i, String label, boolean active, boolean protect) {
            info = i; pkg = i.packageName; this.label = label; this.active = active; protectedApp = protect;
            system = (i.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        }
    }
    static boolean usageGranted(Context c) {
        return ((AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE)).checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static boolean accessibilityEnabled(Context c) {
        String value = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null) return false;
        ComponentName expected = new ComponentName(c, StopAccessibilityService.class);
        for (String entry : value.split(":")) if (expected.equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }
    static List<App> load(Context c, boolean root) {
        PackageManager pm = c.getPackageManager();
        Set<String> active = new HashSet<>(); Set<Integer> uids = new HashSet<>();
        if (root) {
            Shell.Result result = Shell.root("ps -A -o UID,NAME");
            if (result.code != 0) throw new IllegalStateException("Root process query failed");
            for (String line : result.output.split("\n")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) continue;
                try { uids.add(Integer.parseInt(parts[0])); } catch (NumberFormatException ignored) { }
                try {
                    int uid = Integer.parseInt(parts[0]);
                    if (uid / 100000 == Process.myUid() / 100000) active.add(parts[1].split(":")[0]);
                } catch (NumberFormatException ignored) { }
            }
        } else if (usageGranted(c)) {
            long now = System.currentTimeMillis();
            List<UsageStats> stats = ((UsageStatsManager)c.getSystemService(Context.USAGE_STATS_SERVICE))
                    .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24 * 60 * 60 * 1000L, now);
            if (stats != null) for (UsageStats stat : stats)
                if (stat.getLastTimeUsed() >= now - 24 * 60 * 60 * 1000L) active.add(stat.getPackageName());
        }
        Set<String> protect = protectedPackages(c);
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        Set<Integer> protectedUids = new HashSet<>();
        for (ApplicationInfo info : installed) if (protect.contains(info.packageName)) protectedUids.add(info.uid);
        List<App> apps = new ArrayList<>();
        for (ApplicationInfo info : installed) {
            if (!info.enabled) continue;
            boolean running = active.contains(info.packageName) || (root && uids.contains(info.uid));
            if ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0) running = false;
            apps.add(new App(info, info.loadLabel(pm).toString(), running,
                    info.uid % 100000 < 10000 || protectedUids.contains(info.uid) || protect.contains(info.packageName)));
        }
        apps.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        return apps;
    }
    static Set<String> protectedPackages(Context c) {
        Set<String> set = new HashSet<>(Arrays.asList(c.getPackageName(), "android", "com.android.systemui",
                "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller"));
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null) set.add(ri.activityInfo.packageName);
        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        addComponent(set, ime);
        String services = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (services != null) for (String s : services.split(":")) addComponent(set, s);
        List<ComponentName> admins = ((DevicePolicyManager)c.getSystemService(Context.DEVICE_POLICY_SERVICE)).getActiveAdmins();
        if (admins != null) for (ComponentName admin : admins) set.add(admin.getPackageName());
        String dialer = ((TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
        if (dialer != null) set.add(dialer);
        // Connectivity / credential providers and root managers must survive the batch.
        set.addAll(Arrays.asList("com.android.phone", "com.android.networkstack", "com.google.android.networkstack",
                "com.android.providers.settings", "com.android.providers.telephony", "com.topjohnwu.magisk",
                "me.weishu.kernelsu", "me.bmax.apatch"));
        return set;
    }
    private static void addComponent(Set<String> set, String value) {
        if (value == null) return;
        ComponentName component = ComponentName.unflattenFromString(value);
        if (component != null) set.add(component.getPackageName());
    }
}
