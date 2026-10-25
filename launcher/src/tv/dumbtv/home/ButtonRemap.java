package tv.dumbtv.home;

import android.content.Context;
import android.content.Intent;

/** Redirects the app launched by a remote shortcut button to the owner's chosen app. */
final class ButtonRemap {
    private static final String SOURCE_KEY = "button.source";
    private static final String TARGET_KEY = "button.target";
    private static final String OFF = "off";
    private static final String LEGACY_SOURCE = "br.com.skymais";
    private static final String LEGACY_TARGET = "com.amazon.firetv.youtube";

    private ButtonRemap() {}

    static String source(Context c) {
        return Config.get(c).get(SOURCE_KEY, LEGACY_SOURCE);
    }

    static String target(Context c) {
        return Config.get(c).get(TARGET_KEY, LEGACY_TARGET);
    }

    static boolean matches(Context c, String foregroundPackage) {
        String source = source(c), target = target(c);
        return !OFF.equals(source) && !OFF.equals(target) && !source.isEmpty()
                && !target.isEmpty() && !source.equals(target)
                && source.equals(foregroundPackage);
    }

    /** Returns true only when the configured target was successfully launched. */
    static boolean redirect(Context c, String foregroundPackage) {
        if (!matches(c, foregroundPackage)) return false;
        String target = target(c);
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(target);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(launch);
            android.util.Log.i("dumbtv", "button app remap: " + foregroundPackage + " -> " + target);
            return true;
        } catch (Throwable t) {
            android.util.Log.w("dumbtv", "button app remap failed", t);
            return false;
        }
    }
}
