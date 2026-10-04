package com.android.killapps;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.graphics.PixelFormat;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import java.util.*;

public class StopAccessibilityService extends AccessibilityService {
    static volatile StopAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<AppRepository.App> queue = new ArrayDeque<>();
    private AppRepository.App current;
    private long deadline, clickedAt;
    private int phase; // 0: app details, 1: confirmation, 2: verify stopped flag
    private boolean scheduled;
    private boolean batchActive;
    private View overlay;
    private TextView overlayStatus;
    private String[] confirmLabels;
    private final PositiveButtonFinder<AccessibilityNodeInfo> positiveButtons = new PositiveButtonFinder<>(
            new PositiveButtonFinder.Tree<AccessibilityNodeInfo>() {
                public CharSequence text(AccessibilityNodeInfo n) { return n.getText(); }
                public CharSequence description(AccessibilityNodeInfo n) { return n.getContentDescription(); }
                public String id(AccessibilityNodeInfo n) { return n.getViewIdResourceName(); }
                public boolean enabled(AccessibilityNodeInfo n) { return n.isEnabled(); }
                public boolean visible(AccessibilityNodeInfo n) { return n.isVisibleToUser(); }
                public boolean clickable(AccessibilityNodeInfo n) { return n.isClickable(); }
                public int childCount(AccessibilityNodeInfo n) { return n.getChildCount(); }
                public AccessibilityNodeInfo child(AccessibilityNodeInfo n, int i) { return n.getChild(i); }
                public AccessibilityNodeInfo parent(AccessibilityNodeInfo n) { return n.getParent(); }
                public AccessibilityNodeInfo copy(AccessibilityNodeInfo n) { return AccessibilityNodeInfo.obtain(n); }
                public void release(AccessibilityNodeInfo n) { n.recycle(); }
            });
    private final Runnable tick = () -> { scheduled = false; advance(); };
    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { schedule(150); }
    @Override public void onInterrupt() { cancelBatch(); }
    @Override public void onDestroy() {
        if (batchActive) { StopSession.cancelled = true; StopSession.finish(this, false); }
        removeOverlay();
        instance = null; handler.removeCallbacksAndMessages(null); super.onDestroy();
    }
    void start(List<AppRepository.App> apps) {
        confirmLabels = getResources().getStringArray(R.array.force_stop_confirm_labels);
        batchActive = true; queue.clear(); queue.addAll(apps); showOverlay(); next();
    }
    void cancelBatch() {
        if (!batchActive) return;
        batchActive = false; queue.clear(); current = null; handler.removeCallbacksAndMessages(null); scheduled = false;
        removeOverlay();
        StopSession.finish(this, true);
    }
    private void next() {
        if (!batchActive) return;
        handler.removeCallbacks(tick); scheduled = false;
        if (StopSession.cancelled || queue.isEmpty()) { batchActive = false; current = null; removeOverlay(); StopSession.finish(this, true); return; }
        current = queue.removeFirst(); phase = 0; deadline = android.os.SystemClock.uptimeMillis() + 10000;
        StopSession.currentLabel = current.label; StopSession.statusRes = R.string.stopping_app;
        if (overlayStatus != null) overlayStatus.setText(localized().getString(R.string.overlay_progress, current.label, StopSession.done, StopSession.total));
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current.pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            schedule(500);
        } catch (Exception e) { complete(false, R.string.details_failed); }
    }
    private void schedule(long delay) {
        if (current != null && !scheduled) { scheduled = true; handler.postDelayed(tick, delay); }
    }
    private void complete(boolean ok, int message) {
        StopSession.result(current, ok, message); current = null;
        if (queue.isEmpty() || StopSession.cancelled) next(); else handler.postDelayed(this::next, 350);
    }
    private void advance() {
        if (current == null) return;
        if (StopSession.cancelled) { cancelBatch(); return; }
        long now = android.os.SystemClock.uptimeMillis();
        if (now > deadline) { complete(false, R.string.page_timeout); return; }
        if (phase == 2) {
            try {
                if ((getPackageManager().getApplicationInfo(current.pkg, 0).flags & ApplicationInfo.FLAG_STOPPED) != 0) {
                    complete(true, 0); return;
                }
            } catch (Exception ignored) { }
            schedule(250); return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { schedule(250); return; }
        try {
            // Never click in third-party windows. Only the resolved system Settings package is trusted.
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current.pkg));
            android.content.pm.ResolveInfo settings = getPackageManager().resolveActivity(intent, 0);
            if (settings == null || !settings.activityInfo.packageName.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
                schedule(250); return;
            }
            if (phase == 0) {
                if (!containsText(root, current.label) && !containsText(root, current.pkg)) { schedule(250); return; }
                AccessibilityNodeInfo button = find(root, getResources().getStringArray(R.array.force_stop_labels));
                if (button != null) {
                    try {
                        if (!button.isEnabled()) { complete(false, R.string.stop_disabled); return; }
                        if (click(button)) { phase = 1; clickedAt = now; }
                    } finally { button.recycle(); }
                }
            } else if (now - clickedAt > 250) {
                // Confirmation is only accepted after our force-stop click and a matching warning.
                AccessibilityNodeInfo warning = find(root, getResources().getStringArray(R.array.force_stop_warning_labels));
                // Compose dialogs have no android:id/button1. Match the exact positive label,
                // then click its actionable parent, only inside the verified Settings warning.
                AccessibilityNodeInfo button = warning == null ? null : positiveButtons.find(root,
                        new String[]{"android:id/button1", settings.activityInfo.packageName + ":id/button1"}, confirmLabels);
                boolean confirmed = button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (warning != null) warning.recycle(); if (button != null) button.recycle();
                if (confirmed) phase = 2;
                else {
                    try { if ((getPackageManager().getApplicationInfo(current.pkg, 0).flags & ApplicationInfo.FLAG_STOPPED) != 0) { complete(true, 0); return; } }
                    catch (Exception ignored) { }
                }
            }
        } finally { root.recycle(); }
        schedule(250);
    }
    private boolean containsText(AccessibilityNodeInfo node, String expected) {
        if (node.getText() != null && node.getText().toString().equals(expected)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
            boolean result = containsText(child, expected); child.recycle(); if (result) return true;
        }
        return false;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, String[] words) {
        String text = node.getText() == null ? "" : node.getText().toString().toLowerCase(Locale.ROOT);
        for (String word : words) if (text.contains(word)) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
            AccessibilityNodeInfo found = find(child, words); child.recycle(); if (found != null) return found;
        }
        return null;
    }
    private boolean click(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo cursor = AccessibilityNodeInfo.obtain(node);
        try {
            for (int i = 0; cursor != null && i < 5; i++) {
                if (cursor.isClickable()) return cursor.isEnabled() && cursor.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent = cursor.getParent(); cursor.recycle(); cursor = parent;
            }
            return false;
        } finally { if (cursor != null) cursor.recycle(); }
    }
    private Context localized() {
        String tag = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "");
        if (tag.isEmpty()) return this;
        Configuration configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(tag)); return createConfigurationContext(configuration);
    }
    private void showOverlay() {
        removeOverlay();
        Context c = localized(); float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int)(12 * density); row.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.rgb(35, 57, 44)); background.setCornerRadius(24 * density); row.setBackground(background);
        overlayStatus = new TextView(c); overlayStatus.setTextColor(Color.WHITE); overlayStatus.setTextSize(13);
        row.addView(overlayStatus, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = new Button(c); cancel.setText(R.string.cancel_task); cancel.setText(c.getString(R.string.cancel_task));
        cancel.setOnClickListener(v -> StopSession.cancel()); row.addView(cancel);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                getResources().getDisplayMetrics().widthPixels - (int)(32 * density), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; params.y = (int)(40 * density);
        try { ((WindowManager)getSystemService(WINDOW_SERVICE)).addView(row, params); overlay = row; }
        catch (Exception e) { android.util.Log.w("KillApps", "Overlay unavailable", e); overlayStatus = null; }
    }
    private void removeOverlay() {
        if (overlay != null) {
            try { ((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(overlay); } catch (Exception ignored) { }
            overlay = null; overlayStatus = null;
        }
    }
}
