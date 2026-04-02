package eu.siacs.conversations.entities;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
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

    @Test
    public void setupViewPagerListenerDoesNotLeakBetweenConversations() {
        final var context = RuntimeEnvironment.getApplication();
        final var pager = new ViewPager(context);
        final var tabs = mock(TabLayout.class);

        final int[] selectedTabPosition = {0};
        doAnswer(invocation -> {
            ViewPager vp = invocation.getArgument(0);
            vp.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                public void onPageScrollStateChanged(int state) {}
                public void onPageScrolled(int p, float o, int opx) {}
                public void onPageSelected(int position) {
                    selectedTabPosition[0] = position;
                }
            });
            return null;
        }).when(tabs).setupWithViewPager(any(ViewPager.class));
        when(tabs.getSelectedTabPosition()).thenAnswer(inv -> selectedTabPosition[0]);

        final var page1 = new RelativeLayout(context);
        final var page2 = new RelativeLayout(context);
        final var commandsView = new ListView(context);
        commandsView.setId(R.id.commands_view);
        page2.addView(commandsView);
        pager.addView(page1);
        pager.addView(page2);

        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(Jid.ofLocalAndDomain("testAccount", "example.org"));
        final var roster = mock(Roster.class);
        when(account.getRoster()).thenReturn(roster);

        final var mucJid = Jid.ofLocalAndDomain("operations", "conference.soprani.ca");
        final var mucContact = mock(Contact.class);
        when(mucContact.isApp()).thenReturn(false);
        when(mucContact.getJid()).thenReturn(mucJid);
        when(roster.getContact(mucJid)).thenReturn(mucContact);

        final var appJid = Jid.ofDomain("jmp.chat");
        final var appContact = mock(Contact.class);
        when(appContact.isApp()).thenReturn(true);
        when(appContact.getJid()).thenReturn(appJid);
        when(roster.getContact(appJid)).thenReturn(appContact);

        final var muc = new Conversation("MUC", account, mucJid, Conversation.MODE_MULTI);
        final var app = new Conversation("App", account, appJid, Conversation.MODE_SINGLE);

        muc.setupViewPager(pager, tabs, false, null);
        muc.showViewPager();

        pager.layout(0, 0, 1024, 768);
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        Assert.assertEquals("MUC should start on Conversation tab", 0, muc.getCurrentTab());

        app.setupViewPager(pager, tabs, false, muc);
        app.showViewPager();

        Shadows.shadowOf(Looper.getMainLooper()).idle();

        pager.setCurrentItem(1);

        Assert.assertEquals(
            "Page change on app conversation must not corrupt MUC's tab state",
            0,
            muc.getCurrentTab());

        Assert.assertEquals(
            "Tab indicator must stay in sync with the selected page",
            1,
            tabs.getSelectedTabPosition());
    }
}
