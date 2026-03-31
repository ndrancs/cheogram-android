package eu.siacs.conversations.entities;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import eu.siacs.conversations.Conversations;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ConversationAllColumnsLocaleTest {
    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale("fa"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
    }

    @Test
    public void truncatedAttributesColumnUsesAsciiDigits() {
        assertEquals(
            "SUBSTR(attributes, 0, 65534) AS attributes",
            Conversation.truncatedAttributesColumn()
        );
    }
}
