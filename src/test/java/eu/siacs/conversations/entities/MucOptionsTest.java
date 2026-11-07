package eu.siacs.conversations.entities;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.model.Bookmark;
import junit.framework.Assert;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MucOptionsTest {
    private static final String OCCUPANT_ID = "occ-1";
    private static final MucOptions.User.OccupantId CACHE_KEY =
        new MucOptions.User.OccupantId(OCCUPANT_ID);

    private Conversation conversation;
    private MucOptions mucOptions;
    private MucOptions.User user;

    @Before
    public void setUp() throws Exception {
        final var bookmark = mock(Bookmark.class);
        when(bookmark.getNick()).thenReturn("testBookmarkNick");

        final var roster = mock(Roster.class);
        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(Jid.ofLocalAndDomain("testAccount", "example.org"));
        when(account.getRoster()).thenReturn(roster);

        conversation = mock(Conversation.class);
        when(conversation.getAccount()).thenReturn(account);
        when(conversation.getAttribute("mucNick")).thenReturn("testMucNick");
        when(conversation.getAttribute("affiliation")).thenReturn(null);
        when(conversation.getAttribute("role")).thenReturn(null);
        when(conversation.getAddress()).thenReturn(Jid.ofLocalAndDomain("testMuc", "example.org"));
        when(conversation.getBookmark()).thenReturn(bookmark);

        mucOptions = new MucOptions(conversation);

        when(conversation.setCachedOccupantNick(CACHE_KEY, "testNick")).thenReturn(true);
        user = new MucOptions.User(
            mucOptions,
            Jid.ofLocalAndDomain("testUser", "example.org"),
            null,
            OCCUPANT_ID,
            "testNick",
            im.conversations.android.xmpp.model.muc.Role.NONE,
            im.conversations.android.xmpp.model.muc.Affiliation.NONE,
            new HashSet<>()
        );
    }

    @Test
    public void testCtorCachesOccupantNick() throws Exception {
        verify(conversation).setCachedOccupantNick(CACHE_KEY, "testNick");
    }

    @Test
    public void testCtorReadsCachedNickAndAvatar() throws Exception {
        final var key = new MucOptions.User.OccupantId("occ-2");
        when(conversation.getCachedOccupantNick(key)).thenReturn("cachedNick");
        when(conversation.getCachedOccupantAvatar(key)).thenReturn("cachedAvatar");
        final var cachedUser = new MucOptions.User(
            mucOptions,
            Jid.ofLocalAndDomain("testUser2", "example.org"),
            null,
            "occ-2",
            null,
            im.conversations.android.xmpp.model.muc.Role.NONE,
            im.conversations.android.xmpp.model.muc.Affiliation.NONE,
            new HashSet<>()
        );
        Assert.assertEquals("cachedNick", cachedUser.getNick());
        Assert.assertEquals("cachedAvatar", cachedUser.getAvatar());
    }

    @Test
    public void testOfflineUserUsesContactNameWhenInContactList() throws Exception {
        final var realJid = Jid.ofLocalAndDomain("realUser", "example.org");
        final var contact = mock(Contact.class);
        when(contact.getDisplayName()).thenReturn("Contact Name");
        when(conversation.getAccount().getRoster().getContactFromContactList(realJid))
            .thenReturn(contact);
        final var user = new MucOptions.User(
            mucOptions,
            Jid.of("testMuc@example.org/testNick"),
            realJid,
            null,
            null,
            im.conversations.android.xmpp.model.muc.Role.NONE,
            im.conversations.android.xmpp.model.muc.Affiliation.NONE,
            new HashSet<>()
        );

        final var offlineUser = user.asOfflineUser();

        Assert.assertEquals("Contact Name", offlineUser.getDisplayName());
    }
    @Test
    public void testSetAvatarCachesAvatar() throws Exception {
        when(conversation.setCachedOccupantAvatar(CACHE_KEY, "newAvatar")).thenReturn(true);
        user.setAvatar("newAvatar");
        verify(conversation).setCachedOccupantAvatar(CACHE_KEY, "newAvatar");
    }

    @Test
    public void testSetAvatarCachesAvatarForRosterContacts() throws Exception {
        final var roster = conversation.getAccount().getRoster();
        final var realJid = Jid.ofLocalAndDomain("realUser", "example.org");
        final var contact = mock(Contact.class);
        when(roster.getContactFromContactList(realJid)).thenReturn(contact);
        user = new MucOptions.User(
                mucOptions,
                Jid.ofLocalAndDomain("testUser", "example.org"),
                realJid,
                OCCUPANT_ID,
                "testNick",
                im.conversations.android.xmpp.model.muc.Role.NONE,
                im.conversations.android.xmpp.model.muc.Affiliation.NONE,
                new HashSet<>());

        when(conversation.setCachedOccupantAvatar(CACHE_KEY, "avatarHash")).thenReturn(true);
        user.setAvatar("avatarHash");
        verify(conversation).setCachedOccupantAvatar(CACHE_KEY, "avatarHash");
    }
}
