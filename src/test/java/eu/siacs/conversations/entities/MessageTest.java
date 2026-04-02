package eu.siacs.conversations.entities;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Build;

import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.xmpp.Jid;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class MessageTest {

    @Test
    public void extractLinksIgnoresInvalidUris() {
        final var conversation = mock(Conversational.class);
        when(conversation.getUuid()).thenReturn("test-uuid");
        when(conversation.getJid()).thenReturn(Jid.ofLocalAndDomain("test", "example.com"));

        final var message = new Message(conversation, "https://example.com?q=%s", Message.ENCRYPTION_NONE);
        Assert.assertEquals(0, message.getLinks().size());
    }
}
