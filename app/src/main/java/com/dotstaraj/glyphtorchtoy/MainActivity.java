package com.dotstaraj.glyphtorchtoy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal config screen: one row per toy signal (Activated, Deactivated,
 * Long press, Extra long press), each opening a flat pop-up list of
 * installed apps. Tap the app name to select the app itself. If an app
 * declares one or more ACTION_CREATE_SHORTCUT entries, a "+" expands a
 * sub-list of those -- tapping one hands off to that app's own picker UI
 * (e.g. Automate's flow list, or nothing visible at all if the app has
 * only one option and returns it directly) and captures whatever it
 * returns. No OK/Cancel -- tapping any entry commits it immediately.
 *
 * Also surfaces a one-time prompt for the SYSTEM_ALERT_WINDOW permission,
 * without which configured actions only fire while this app itself is in
 * the foreground -- see README for why.
 */
public class MainActivity extends Activity {

    private static final int PAD = 32;
    private static final int REQUEST_CREATE_SHORTCUT = 1001;

    private final Map<String, TextView> subtitleViews = new HashMap<>();
    private String pendingPrefKey;
    private String pendingAppLabel;
    private TextView permissionRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDecorFitsSystemWindows(false);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(root);
        setContentView(scroll);

        permissionRow = new TextView(this);
        permissionRow.setTextColor(Color.YELLOW);
        permissionRow.setTextSize(14);
        permissionRow.setPadding(0, 0, 0, PAD);
        permissionRow.setText("Tap to grant \"display over other apps\" \u2014 " +
                "without it, actions only fire while this screen is open");
        permissionRow.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        root.addView(permissionRow);

        root.addView(sectionRow("Activated", GlyphActionsConfig.KEY_ACTIVATED));
        root.addView(divider());
        root.addView(sectionRow("Deactivated", GlyphActionsConfig.KEY_DEACTIVATED));
        root.addView(divider());
        root.addView(sectionRow("Long press", GlyphActionsConfig.KEY_LONGPRESS));
        root.addView(divider());
        root.addView(sectionRow("Extra long press", GlyphActionsConfig.KEY_EXTRA_LONGPRESS));
        root.addView(divider());
        root.addView(timerRow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionRow.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
    }

    private View divider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        v.setBackgroundColor(Color.DKGRAY);
        return v;
    }

    private View sectionRow(String label, String prefKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, PAD / 2, 0, PAD / 2);

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);

        TextView subtitle = new TextView(this);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.LTGRAY);
        subtitleViews.put(prefKey, subtitle);
        refreshSubtitle(prefKey);

        row.addView(title);
        row.addView(subtitle);
        row.setOnClickListener(v -> showPicker(prefKey));
        return row;
    }

    private void refreshSubtitle(String prefKey) {
        ActionTarget target = GlyphActionsConfig.get(this, prefKey);
        TextView subtitle = subtitleViews.get(prefKey);
        if (subtitle != null) {
            subtitle.setText(target != null ? target.label : "Not set");
        }
    }

    private void selectTarget(String prefKey, ActionTarget target) {
        GlyphActionsConfig.set(this, prefKey, target);
        refreshSubtitle(prefKey);
    }

    private View timerRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, PAD / 2, 0, PAD / 2);

        TextView title = new TextView(this);
        title.setText("Extra long press timer (ms)");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setText(String.valueOf(GlyphActionsConfig.getExtraLongPressTimerMs(this)));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                try {
                    int ms = Integer.parseInt(s.toString());
                    if (ms > 0) {
                        GlyphActionsConfig.setExtraLongPressTimerMs(MainActivity.this, ms);
                    }
                } catch (NumberFormatException ignored) {
                    // Leave the last valid saved value in place until this parses.
                }
            }
        });

        row.addView(title);
        row.addView(input);
        return row;
    }

    // --- Picker -------------------------------------------------------------

    private void showPicker(String prefKey) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(list);
        dialog.setContentView(scroll);

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.BLACK);
        border.setStroke(2, Color.WHITE);
        dialog.getWindow().setBackgroundDrawable(border);

        PackageManager pm = getPackageManager();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
        Collections.sort(apps, Comparator.comparing(ri -> ri.loadLabel(pm).toString().toLowerCase()));

        // Group ACTION_CREATE_SHORTCUT resolvers by package, so each app's
        // row can offer them as an expandable sub-list.
        Map<String, List<ResolveInfo>> creatorsByPackage = new HashMap<>();
        List<ResolveInfo> creators = pm.queryIntentActivities(new Intent(Intent.ACTION_CREATE_SHORTCUT), 0);
        for (ResolveInfo ri : creators) {
            String pkg = ri.activityInfo.packageName;
            creatorsByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(ri);
        }

        Set<String> handledPackages = new HashSet<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            handledPackages.add(pkg);
            addAppRow(list, pm, pkg, ri.loadLabel(pm).toString(), prefKey, dialog,
                    creatorsByPackage.getOrDefault(pkg, Collections.emptyList()));
        }

        // Packages that expose a shortcut-creator activity but have no
        // regular launcher icon of their own -- rare, but without this
        // their creator flow would be unreachable from this picker at all.
        for (Map.Entry<String, List<ResolveInfo>> entry : creatorsByPackage.entrySet()) {
            if (handledPackages.contains(entry.getKey())) continue;
            String label = entry.getValue().get(0).loadLabel(pm).toString();
            addAppRow(list, pm, entry.getKey(), label, prefKey, dialog, entry.getValue());
        }

        dialog.show();
    }

    private void addAppRow(LinearLayout list, PackageManager pm, String packageName,
                            String appLabel, String prefKey, Dialog dialog,
                            List<ResolveInfo> shortcutCreators) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 24, 0, 24);

        TextView nameView = new TextView(this);
        nameView.setText(appLabel);
        nameView.setTextSize(16);
        nameView.setTextColor(Color.WHITE);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nameView.setOnClickListener(v -> {
            selectTarget(prefKey, ActionTarget.forApp(packageName, appLabel));
            dialog.dismiss();
        });
        row.addView(nameView);

        // Only show the expand affordance -- and the sub-list -- if this
        // app actually has at least one shortcut-creator to offer.
        if (!shortcutCreators.isEmpty()) {
            TextView expandView = new TextView(this);
            expandView.setText("+");
            expandView.setTextSize(20);
            expandView.setTextColor(Color.LTGRAY);
            expandView.setPadding(24, 0, 24, 0);
            row.addView(expandView);
            list.addView(row);

            LinearLayout subList = new LinearLayout(this);
            subList.setOrientation(LinearLayout.VERTICAL);
            subList.setPadding(48, 0, 0, 0);
            subList.setVisibility(View.GONE);
            for (ResolveInfo creator : shortcutCreators) {
                String label = creator.loadLabel(pm).toString() + "\u2026";
                TextView rowView = new TextView(this);
                rowView.setText(label);
                rowView.setTextSize(15);
                rowView.setTextColor(Color.WHITE);
                rowView.setPadding(0, 16, 0, 16);
                rowView.setOnClickListener(v -> {
                    pendingPrefKey = prefKey;
                    pendingAppLabel = appLabel;
                    dialog.dismiss();
                    Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                    intent.setClassName(creator.activityInfo.packageName, creator.activityInfo.name);
                    try {
                        startActivityForResult(intent, REQUEST_CREATE_SHORTCUT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                subList.addView(rowView);
            }
            list.addView(subList);

            expandView.setOnClickListener(v -> {
                boolean expanding = subList.getVisibility() != View.VISIBLE;
                subList.setVisibility(expanding ? View.VISIBLE : View.GONE);
                expandView.setText(expanding ? "-" : "+");
            });
        } else {
            list.addView(row);
        }
    }

    // --- ACTION_CREATE_SHORTCUT result handling ------------------------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CREATE_SHORTCUT || pendingPrefKey == null) return;
        String prefKey = pendingPrefKey;
        String appLabel = pendingAppLabel;
        pendingPrefKey = null;
        pendingAppLabel = null;

        if (resultCode != RESULT_OK || data == null) return;

        Intent shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent.class);
        if (shortcutIntent == null) return;
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        String label = (name != null && !name.isEmpty()) ? (appLabel + ": " + name) : appLabel;

        selectTarget(prefKey, ActionTarget.forShortcut(label, shortcutIntent));
    }
}
