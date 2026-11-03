package eu.siacs.conversations.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import eu.siacs.conversations.Conversations;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class OnboardingUtilsTest {

    private static final String PREF_CANCELED = "onboarding_action";
    private static final String PREF_CONTINUED = "onboarding_continued";
    private static final String PREF_RESUME = "onboarding_resume";

    private SharedPreferences preferences;

    @Before
    public void setUp() {
        preferences =
                RuntimeEnvironment.getApplication()
                        .getSharedPreferences("onboarding-test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void selectedGatewayMarksContinuedAndClearsCancel() {
        OnboardingUtils.markCanceled(preferences);
        OnboardingUtils.markResume(preferences);
        assertTrue(OnboardingUtils.isCanceled(preferences));

        OnboardingUtils.setSelectedGateway(preferences, "snikket.example");

        assertFalse(OnboardingUtils.isCanceled(preferences));
        assertFalse(OnboardingUtils.isResumePending(preferences));
        assertTrue(OnboardingUtils.isContinued(preferences));
        assertEquals("snikket.example", OnboardingUtils.getSelectedGateway(preferences));
    }

    @Test
    public void legacyBooleanIsTreatedAsContinuedWithoutGateway() {
        preferences.edit().putBoolean(PREF_CONTINUED, true).commit();

        assertTrue(OnboardingUtils.isContinued(preferences));
        assertNull(OnboardingUtils.getSelectedGateway(preferences));
    }

    @Test
    public void legacyGatewayInCancelPreferenceIsTreatedAsContinued() {
        preferences.edit().putString(PREF_CANCELED, "snikket.example").commit();

        assertTrue(OnboardingUtils.isContinued(preferences));
        assertEquals("snikket.example", OnboardingUtils.getSelectedGateway(preferences));
    }

    @Test
    public void clearingSelectionRemovesGatewayResumeAndLegacyValue() {
        OnboardingUtils.setSelectedGateway(preferences, "snikket.example");
        OnboardingUtils.markResume(preferences);
        preferences.edit().putString(PREF_CANCELED, "legacy.example").commit();

        OnboardingUtils.clearSelectedGateway(preferences);

        assertFalse(OnboardingUtils.isContinued(preferences));
        assertNull(OnboardingUtils.getSelectedGateway(preferences));
        assertFalse(OnboardingUtils.isResumePending(preferences));
        assertNull(preferences.getString(PREF_CANCELED, null));
    }

    @Test
    public void clearingSelectionKeepsCancelMarker() {
        OnboardingUtils.markCanceled(preferences);

        OnboardingUtils.clearSelectedGateway(preferences);

        assertTrue(OnboardingUtils.isCanceled(preferences));
    }

    @Test
    public void resumeStaysPendingUntilProgressIsCleared() {
        OnboardingUtils.markResume(preferences);

        assertTrue(OnboardingUtils.isResumePending(preferences));
        assertTrue(OnboardingUtils.isResumePending(preferences));

        OnboardingUtils.clearProgress(preferences);

        assertFalse(OnboardingUtils.isResumePending(preferences));
    }

    @Test
    public void clearProgressPreservesCancelButClearsGateway() {
        OnboardingUtils.setSelectedGateway(preferences, "snikket.example");
        OnboardingUtils.markResume(preferences);
        OnboardingUtils.markCanceled(preferences);

        OnboardingUtils.clearProgress(preferences);

        assertNull(OnboardingUtils.getSelectedGateway(preferences));
        assertFalse(OnboardingUtils.isResumePending(preferences));
        assertTrue(OnboardingUtils.isCanceled(preferences));
    }

    @Test
    public void clearProgressRemovesStaleLegacyGatewayFromCancelPreference() {
        preferences.edit().putString(PREF_CANCELED, "snikket.example").commit();

        OnboardingUtils.clearProgress(preferences);

        assertNull(preferences.getString(PREF_CANCELED, null));
    }
}
