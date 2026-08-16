package com.dotstaraj.glyphtorchtoy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal config screen: one row per toy signal (Activated, Deactivated,
 * Long press, Extra long press), each opening a flat pop-up list of
 * installed apps (tap the app name to select the app itself, tap the "+"
 * to expand its static shortcuts and pick one of those instead -- no
 * OK/Cancel, tapping an entry commits it immediately), plus one plain
 * numeric field for the extra long press timer.
 */
public class MainActivity extends Activity {

    private static final int PAD = 32;

    private final Map<String, TextView> subtitleViews = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(root);
        setContentView(scroll);

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

    private void showPicker(String prefKey) {
        Dialog dialog = new Dialog(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(PAD, PAD, PAD, PAD);
        scroll.addView(list);
        dialog.setContentView(scroll);

        PackageManager pm = getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
        Collections.sort(apps, Comparator.comparing(ri -> ri.loadLabel(pm).toString().toLowerCase()));

        for (ResolveInfo ri : apps) {
            addAppRow(list, pm, ri.activityInfo.packageName, ri.loadLabel(pm).toString(), prefKey, dialog);
        }

        dialog.show();
    }

    private void addAppRow(LinearLayout list, PackageManager pm, String packageName,
                            String appLabel, String prefKey, Dialog dialog) {
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
            GlyphActionsConfig.set(this, prefKey, ActionTarget.forApp(packageName, appLabel));
            refreshSubtitle(prefKey);
            dialog.dismiss();
        });

        TextView expandView = new TextView(this);
        expandView.setText("+");
        expandView.setTextSize(20);
        expandView.setTextColor(Color.LTGRAY);
        expandView.setPadding(24, 0, 24, 0);

        row.addView(nameView);
        row.addView(expandView);
        list.addView(row);

        LinearLayout shortcutContainer = new LinearLayout(this);
        shortcutContainer.setOrientation(LinearLayout.VERTICAL);
        shortcutContainer.setPadding(48, 0, 0, 0);
        shortcutContainer.setVisibility(View.GONE);
        list.addView(shortcutContainer);

        final boolean[] loaded = {false};
        expandView.setOnClickListener(v -> {
            boolean expanding = shortcutContainer.getVisibility() != View.VISIBLE;
            if (expanding && !loaded[0]) {
                loaded[0] = true;
                List<ShortcutUtils.AppShortcut> shortcuts = ShortcutUtils.getStaticShortcuts(pm, packageName);
                if (shortcuts.isEmpty()) {
                    TextView empty = new TextView(this);
                    empty.setText("No static shortcuts");
                    empty.setTextColor(Color.GRAY);
                    empty.setPadding(0, 16, 0, 16);
                    shortcutContainer.addView(empty);
                } else {
                    for (ShortcutUtils.AppShortcut shortcut : shortcuts) {
                        TextView shortcutView = new TextView(this);
                        shortcutView.setText(shortcut.label);
                        shortcutView.setTextSize(15);
                        shortcutView.setTextColor(Color.WHITE);
                        shortcutView.setPadding(0, 16, 0, 16);
                        shortcutView.setOnClickListener(sv -> {
                            GlyphActionsConfig.set(this, prefKey,
                                    ActionTarget.forShortcut(appLabel + ": " + shortcut.label, shortcut.intent));
                            refreshSubtitle(prefKey);
                            dialog.dismiss();
                        });
                        shortcutContainer.addView(shortcutView);
                    }
                }
            }
            shortcutContainer.setVisibility(expanding ? View.VISIBLE : View.GONE);
            expandView.setText(expanding ? "-" : "+");
        });
    }
}
