package eu.siacs.conversations.entities;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import android.os.Build;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xmpp.Jid;
import junit.framework.Assert;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ContactCompareToTest {

    private static final Jid ACCOUNT_JID = Jid.of("test@example.org");

    private Contact contact(String systemName, String jid) {
        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(ACCOUNT_JID);
        final var contact = new Contact(
            null, systemName, null, null,
            Jid.of(jid), 0, null, null,
            null, null, 0, null, null, null
        );
        contact.setAccount(account);
        return contact;
    }

    private Bookmark bookmark(String name, String jid) {
        final var account = mock(Account.class);
        when(account.getJid()).thenReturn(Jid.of("test@example.org"));
        final var bookmark = new Bookmark(account, Jid.of(jid));
        bookmark.setBookmarkName(name);
        return bookmark;
    }

    @Test
    public void testSortContactsWithCaseVariantNames() {
        final var items = new ArrayList<ListItem>();
        for (int i = 0; i < 32; i++) {
            items.add(contact("alice", String.format("a%02d@example.com", i)));
            items.add(contact("Alice", String.format("b%02d@example.com", i)));
        }

        for (int seed = 0; seed < 100; seed++) {
            Collections.shuffle(items, new Random(seed));
            Collections.sort(items);
        }
    }

    @Test
    public void testSortMixedContactsAndBookmarksWithCaseVariantNames() {
        final var items = new ArrayList<ListItem>();
        for (int i = 0; i < 32; i++) {
            items.add(contact("alice", String.format("a%02d@example.com", i)));
            items.add(bookmark("Alice", String.format("b%02d@example.com", i)));
        }

        for (int seed = 0; seed < 100; seed++) {
            Collections.shuffle(items, new Random(seed));
            Collections.sort(items);
        }
    }

    @Test
    public void testCaseVariantNamesTieOnNameThenSortByJid() {
        final var AliceAtB = contact("Alice", "b@example.com");
        final var aliceAtA = contact("alice", "a@example.com");

        Assert.assertTrue(aliceAtA.compareTo(AliceAtB) < 0);
        Assert.assertTrue(AliceAtB.compareTo(aliceAtA) > 0);

        final var AliceAtA = contact("Alice", "a@example.com");
        Assert.assertTrue(AliceAtA.compareTo(AliceAtB) < 0);
        Assert.assertTrue(AliceAtB.compareTo(AliceAtA) > 0);
    }
}
