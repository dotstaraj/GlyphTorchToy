package com.dotstaraj.glyphtorchtoy;

import android.content.Intent;

/**
 * A single configured action target for one of the four toy signals:
 * either "launch this app" or "launch this specific static shortcut of
 * this app". Persisted as a simple pipe-delimited string in
 * SharedPreferences via encode()/decode() -- no JSON dependency needed
 * for something this small.
 */
class ActionTarget {
    static final String TYPE_APP = "APP";
    static final String TYPE_SHORTCUT = "SHORTCUT";

    final String type;
    final String label;
    final String packageName;   // set for TYPE_APP
    final String intentUri;     // set for TYPE_SHORTCUT (full Intent, encoded)

    private ActionTarget(String type, String label, String packageName, String intentUri) {
        this.type = type;
        this.label = label;
        this.packageName = packageName;
        this.intentUri = intentUri;
    }

    static ActionTarget forApp(String packageName, String label) {
        return new ActionTarget(TYPE_APP, label, packageName, null);
    }

    static ActionTarget forShortcut(String label, Intent intent) {
        return new ActionTarget(TYPE_SHORTCUT, label, null, intent.toUri(Intent.URI_INTENT_SCHEME));
    }

    String encode() {
        if (TYPE_APP.equals(type)) {
            return TYPE_APP + "|" + packageName + "|" + label;
        }
        return TYPE_SHORTCUT + "|" + intentUri + "|" + label;
    }

    static ActionTarget decode(String encoded) {
        if (encoded == null) return null;
        String[] parts = encoded.split("\\|", 3);
        if (parts.length < 3) return null;
        if (TYPE_APP.equals(parts[0])) {
            return new ActionTarget(TYPE_APP, parts[2], parts[1], null);
        } else if (TYPE_SHORTCUT.equals(parts[0])) {
            return new ActionTarget(TYPE_SHORTCUT, parts[2], null, parts[1]);
        }
        return null;
    }
}
