package eu.siacs.conversations.services;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Build;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import eu.siacs.conversations.Conversations;
import io.ipfs.cid.Cid;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class XmppConnectionServiceTest {

    private XmppConnectionService service;

    @Before
    public void setUp() {
        service = mock(XmppConnectionService.class);
        when(service.isBlockedMediaSha1(any())).thenCallRealMethod();
        when(service.isBlockedMedia(any(Cid.class))).thenReturn(false);
    }

    @Test
    public void testIsBlockedMediaSha1ReturnsFalseForNonSha1Length() {
        // 36 hex chars = 18 bytes; valid SHA-1 is 40 hex chars = 20 bytes.
        // Reproduces: IllegalStateException: Incorrect hash length: 18 != 20
        assertFalse(service.isBlockedMediaSha1("aabbccddee1122334455667788990011aabb"));
    }

    @Test
    public void testIsBlockedMediaSha1ReturnsFalseForNull() {
        assertFalse(service.isBlockedMediaSha1(null));
    }

    @Test
    public void testIsBlockedMediaSha1AcceptsValidSha1() {
        // 40 hex chars = 20 bytes = valid SHA-1
        assertFalse(service.isBlockedMediaSha1("aabbccddee112233445566778899001122334455"));
    }
}
