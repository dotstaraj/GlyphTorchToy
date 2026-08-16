package com.dotstaraj.glyphtorchtoy;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates an app's STATIC shortcuts by parsing its declared
 * shortcuts.xml resource directly via PackageManager. This works without
 * any special permission, unlike LauncherApps.getShortcuts()/startShortcut()
 * -- those require the calling app to be the current default launcher
 * (hasShortcutHostPermission()), which this app deliberately isn't.
 *
 * Limitation: only static, manifest-declared shortcuts are visible this
 * way. Dynamic shortcuts (generated at runtime, e.g. "message Mom") and
 * pinned shortcuts are invisible to a non-launcher app -- there's no
 * workaround for that short of becoming the default launcher. Some static
 * shortcuts also point at non-exported activities and will throw a
 * SecurityException if launched from outside the app; that's caught at
 * fire time in GlyphTorchToyService, not filtered out here.
 */
class ShortcutUtils {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    static class AppShortcut {
        final String id;
        final String label;
        final Intent intent;

        AppShortcut(String id, String label, Intent intent) {
            this.id = id;
            this.label = label;
            this.intent = intent;
        }
    }

    static List<AppShortcut> getStaticShortcuts(PackageManager pm, String packageName) {
        List<AppShortcut> result = new ArrayList<>();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launcherIntent.setPackage(packageName);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.GET_META_DATA);
        for (ResolveInfo ri : resolveInfos) {
            try {
                XmlResourceParser parser = ri.activityInfo.loadXmlMetaData(pm, "android.app.shortcuts");
                if (parser == null) continue;
                Resources appRes = pm.getResourcesForApplication(packageName);
                parseShortcutsXml(parser, appRes, result);
            } catch (Exception e) {
                // Malformed or unreadable shortcuts.xml for this app -- skip it.
            }
        }
        return result;
    }

    private static void parseShortcutsXml(XmlResourceParser parser, Resources appRes,
                                           List<AppShortcut> out) throws Exception {
        int eventType = parser.getEventType();
        String currentId = null;
        String currentLabel = null;
        Intent currentIntent = null;

        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                String tag = parser.getName();
                if ("shortcut".equals(tag)) {
                    currentId = getAttr(parser, "shortcutId");
                    currentLabel = currentId;
                    int labelResId = parser.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0);
                    if (labelResId != 0) {
                        try {
                            currentLabel = appRes.getString(labelResId);
                        } catch (Exception ignored) {
                            // Fall back to the shortcut id as the label.
                        }
                    }
                    currentIntent = null;
                } else if ("intent".equals(tag)) {
                    String action = getAttr(parser, "action");
                    String targetPackage = getAttr(parser, "targetPackage");
                    String targetClass = getAttr(parser, "targetClass");
                    if (targetClass == null) {
                        targetClass = getAttr(parser, "targetActivity");
                    }
                    Intent intent = new Intent(action != null ? action : Intent.ACTION_VIEW);
                    if (targetPackage != null && targetClass != null) {
                        if (targetClass.startsWith(".")) {
                            targetClass = targetPackage + targetClass;
                        }
                        intent.setClassName(targetPackage, targetClass);
                    } else if (targetPackage != null) {
                        intent.setPackage(targetPackage);
                    }
                    // A <shortcut> may list a chain of <intent> tags (for a
                    // back stack); the last one is the actual target shown.
                    currentIntent = intent;
                }
            } else if (eventType == XmlResourceParser.END_TAG) {
                if ("shortcut".equals(parser.getName())) {
                    if (currentId != null && currentIntent != null) {
                        out.add(new AppShortcut(currentId, currentLabel, currentIntent));
                    }
                    currentId = null;
                    currentLabel = null;
                    currentIntent = null;
                }
            }
            eventType = parser.next();
        }
    }

    private static String getAttr(XmlResourceParser parser, String attr) {
        String value = parser.getAttributeValue(ANDROID_NS, attr);
        if (value == null) {
            value = parser.getAttributeValue(null, attr);
        }
        return value;
    }
}
