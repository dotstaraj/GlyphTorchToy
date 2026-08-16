package com.dotstaraj.glyphtorchtoy;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
 * returns. No OK/Cancel in the picker -- tapping any entry commits it
 * immediately.
 *
 * Each row also gets an edit and a delete button once it has a target
 * configured: delete clears it back to "Not set", edit opens the raw
 * intent-uri string for hand editing (works for both app- and
 * shortcut-type targets -- an app target's own launch intent is
 * synthesized as the starting text).
 *
 * Also surfaces a one-time prompt for the SYSTEM_ALERT_WINDOW permission,
 * without which configured actions only fire while this app itself is in
 * the foreground -- see README for why.
 */
public class MainActivity extends Activity {

    private static final int PAD = 32;
    private static final int REQUEST_CREATE_SHORTCUT = 1001;

    private final Map<String, TextView> subtitleViews = new HashMap<>();
    private final Map<String, View[]> actionButtonViews = new HashMap<>();
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
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, PAD / 2, 0, PAD / 2);

        LinearLayout textArea = new LinearLayout(this);
        textArea.setOrientation(LinearLayout.VERTICAL);
        textArea.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);

        TextView subtitle = new TextView(this);
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.LTGRAY);
        subtitleViews.put(prefKey, subtitle);

        textArea.addView(title);
        textArea.addView(subtitle);
        textArea.setOnClickListener(v -> showPicker(prefKey));

        ImageView editButton = iconButton(R.drawable.ic_edit, v -> showEditIntentDialog(prefKey));
        ImageView deleteButton = iconButton(R.drawable.ic_delete, v -> clearTarget(prefKey));

        row.addView(textArea);
        row.addView(editButton);
        row.addView(deleteButton);

        actionButtonViews.put(prefKey, new View[]{editButton, deleteButton});
        refreshSubtitle(prefKey);
        refreshActionButtons(prefKey);

        return row;
    }

    private ImageView iconButton(int drawableRes, View.OnClickListener onClick) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawableRes);
        icon.setLayoutParams(new LinearLayout.LayoutParams(128, 128));
        icon.setPadding(10, 0, 10, 10);
        icon.setOnClickListener(onClick);
        return icon;
    }

    private void refreshSubtitle(String prefKey) {
        ActionTarget target = GlyphActionsConfig.get(this, prefKey);
        TextView subtitle = subtitleViews.get(prefKey);
        if (subtitle != null) {
            subtitle.setText(target != null ? target.label : "Not set");
        }
    }

    private void refreshActionButtons(String prefKey) {
        View[] buttons = actionButtonViews.get(prefKey);
        if (buttons == null) return;
        boolean configured = GlyphActionsConfig.get(this, prefKey) != null;
        for (View b : buttons) {
            b.setVisibility(configured ? View.VISIBLE : View.GONE);
        }
    }

    private void selectTarget(String prefKey, ActionTarget target) {
        GlyphActionsConfig.set(this, prefKey, target);
        refreshSubtitle(prefKey);
        refreshActionButtons(prefKey);
    }

    private void clearTarget(String prefKey) {
        GlyphActionsConfig.clear(this, prefKey);
        refreshSubtitle(prefKey);
        refreshActionButtons(prefKey);
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

        // 66% of the screen in both dimensions -- must be set after show(),
        // since the dialog theme's default layout params get finalized then.
        Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
        dialog.getWindow().setLayout((int) (bounds.width() * 0.75), (int) (bounds.height() * 0.75));
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

    // --- Manual intent-string editing ----------------------------------------

    private void showEditIntentDialog(String prefKey) {
        ActionTarget target = GlyphActionsConfig.get(this, prefKey);
        if (target == null) return;

        String currentUri;
        if (ActionTarget.TYPE_SHORTCUT.equals(target.type)) {
            currentUri = target.intentUri;
        } else {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(target.packageName);
            currentUri = launchIntent != null ? launchIntent.toUri(Intent.URI_INTENT_SCHEME) : "";
        }

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(PAD, PAD, PAD, PAD);

        EditText input = new EditText(this);
        input.setText(currentUri);
        input.setTextColor(Color.WHITE);
        input.setMinLines(4);
        container.addView(input);

        TextView saveButton = new TextView(this);
        saveButton.setText("Save");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setTextSize(16);
        saveButton.setPadding(0, PAD / 2, 0, 0);
        saveButton.setOnClickListener(v -> {
            try {
                Intent parsed = Intent.parseUri(input.getText().toString(), Intent.URI_INTENT_SCHEME);
                selectTarget(prefKey, ActionTarget.forShortcut(target.label, parsed));
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid intent string", Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(saveButton);

        dialog.setContentView(container);

        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.BLACK);
        border.setStroke(2, Color.WHITE);
        dialog.getWindow().setBackgroundDrawable(border);

        dialog.show();

        Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
        dialog.getWindow().setLayout((int) (bounds.width() * 0.85), ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
