package eu.siacs.conversations.utils;

import android.content.SharedPreferences;

public final class OnboardingUtils {

    private static final String PREF_CANCELED = "onboarding_action";
    private static final String PREF_CONTINUED = "onboarding_continued";
    private static final String PREF_RESUME = "onboarding_resume";
    private static final String CANCELED = "cancel";

    public static boolean isCanceled(final SharedPreferences preferences) {
        return CANCELED.equals(preferences.getString(PREF_CANCELED, null));
    }

    public static void markCanceled(final SharedPreferences preferences) {
        preferences.edit().putString(PREF_CANCELED, CANCELED).commit();
    }

    public static void clearCanceled(final SharedPreferences preferences) {
        preferences.edit().remove(PREF_CANCELED).commit();
    }

    public static String getSelectedGateway(final SharedPreferences preferences) {
        // onboarding_continued used to be a boolean. Read it as an Object so
        // installations that still have the old value do not crash.
        final Object value = preferences.getAll().get(PREF_CONTINUED);
        if (value instanceof String) {
            final String gateway = (String) value;
            return gateway.isEmpty() ? null : gateway;
        }
        // Older versions temporarily stored the selected gateway here while a
        // request was in flight. Keep treating that as a committed selection.
        final Object legacy = preferences.getAll().get(PREF_CANCELED);
        if (legacy instanceof String && !CANCELED.equals(legacy)) {
            final String gateway = (String) legacy;
            return gateway.isEmpty() ? null : gateway;
        }
        return null;
    }

    public static boolean isContinued(final SharedPreferences preferences) {
        final Object value = preferences.getAll().get(PREF_CONTINUED);
        if (value instanceof Boolean && (Boolean) value) {
            return true;
        }
        return getSelectedGateway(preferences) != null;
    }

    public static void setSelectedGateway(
            final SharedPreferences preferences, final String gateway) {
        if (gateway == null || gateway.isEmpty()) {
            return;
        }
        preferences
                .edit()
                .putString(PREF_CONTINUED, gateway)
                .remove(PREF_CANCELED)
                .remove(PREF_RESUME)
                .commit();
    }

    public static void clearSelectedGateway(final SharedPreferences preferences) {
        final SharedPreferences.Editor editor =
                preferences.edit().remove(PREF_CONTINUED).remove(PREF_RESUME);
        // Older versions temporarily stored the selected gateway in the cancel
        // preference. Drop that too, but leave the real cancel marker alone.
        final Object legacy = preferences.getAll().get(PREF_CANCELED);
        if (legacy instanceof String && !CANCELED.equals(legacy)) {
            editor.remove(PREF_CANCELED);
        }
        editor.commit();
    }

    public static void markResume(final SharedPreferences preferences) {
        preferences.edit().putBoolean(PREF_RESUME, true).commit();
    }

    public static boolean isResumePending(final SharedPreferences preferences) {
        return preferences.getBoolean(PREF_RESUME, false);
    }

    public static void clearProgress(final SharedPreferences preferences) {
        final SharedPreferences.Editor editor =
                preferences.edit().remove(PREF_CONTINUED).remove(PREF_RESUME);
        // Preserve the one-shot cancel marker so the next start-up does not
        // immediately offer cheogram onboarding again. Drop stale legacy values
        // that used to store the selected gateway here.
        if (!isCanceled(preferences)) {
            editor.remove(PREF_CANCELED);
        }
        editor.commit();
    }
}
