package eu.siacs.conversations.entities;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import junit.framework.Assert;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ConversationTest {
    private static final InfoQuery INFO_QUERY_WITH_OCCUPANT_ID = new InfoQuery();
    private static final InfoQuery INFO_QUERY_WITHOUT_OCCUPANT_ID = new InfoQuery();

    private Conversation withOccupantId;
    private Conversation withoutOccupantId;
    private Conversation nullMucOptions;

    @BeforeClass
    public static void setupClass() {
        final var occupantIdFeature = new Feature();
        occupantIdFeature.setVar(Namespace.OCCUPANT_ID);
        INFO_QUERY_WITH_OCCUPANT_ID.addChild(occupantIdFeature);
    }

    @Before
    public void setUp() throws Exception {
        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(Jid.ofLocalAndDomain("testAccount", "example.org"));

        withOccupantId = new Conversation(
            "Test MUC",
            account,
            Jid.ofLocalAndDomain("testMuc", "example.org"),
            Conversation.MODE_MULTI
        );
        withOccupantId.getMucOptions().updateConfiguration(INFO_QUERY_WITH_OCCUPANT_ID);

        withoutOccupantId = new Conversation(
            "Test MUC",
            account,
            Jid.ofLocalAndDomain("testMuc", "example.org"),
            Conversation.MODE_MULTI
        );
        withoutOccupantId.getMucOptions().updateConfiguration(INFO_QUERY_WITHOUT_OCCUPANT_ID);

        nullMucOptions = new Conversation(
            "Test MUC",
            account,
            Jid.ofLocalAndDomain("testMuc", "example.org"),
            Conversation.MODE_MULTI
        );
        final var mucOptionsField = Conversation.class.getDeclaredField("mucOptions");
        mucOptionsField.setAccessible(true);
        ((AtomicReference<?>) mucOptionsField.get(nullMucOptions)).set(null);
    }

    @Test
    public void getMucOccupantsCacheReturnsCacheWhenMucOptionsIsNull() throws Exception {
        var cache = nullMucOptions.getMucOccupantCache();
        Assert.assertNotNull("Should return cache when mucOptions is null", cache);
    }

    @Test
    public void getMucOccupantsCacheReturnsCacheWhenFeatureSupported() throws Exception {
        var cache = withOccupantId.getMucOccupantCache();
        Assert.assertNotNull("Should return cache when occupant-id is supported", cache);
    }

    @Test
    public void getMucOccupantsCacheReturnsCacheWhenFeatureNotSupported() throws Exception {
        var cache = withoutOccupantId.getMucOccupantCache();
        Assert.assertNotNull("Should return cache even when occupant-id is not supported", cache);
    }
}
