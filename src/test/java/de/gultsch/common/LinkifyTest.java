package de.gultsch.common;

import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;

import eu.siacs.conversations.Conversations;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LinkifyTest {
    @Test
    public void addLinksDoesNotLinkifyInvalidUris() {
        final var text = new SpannableStringBuilder("https://example.com?q=%s");
        Linkify.addLinks(text);
        Assert.assertEquals(0, text.getSpans(0, text.length(), URLSpan.class).length);
    }

    @Test
    public void malformedEscapeIsRejected() {
        final var links = Linkify.getLinks("https://example.com/?x=%");

        Assert.assertTrue(links.isEmpty());
    }

    @Test
    public void validEscapesStillProduceLinks() {
        final var links = Linkify.getLinks("https://example.com/?x=%20");

        Assert.assertEquals(1, links.size());
        Assert.assertEquals("https://example.com/?x=%20", links.get(0).getRaw());
    }

    @Test
    public void ipv6HostsStillProduceLinks() {
        final var links = Linkify.getLinks("http://[::1]/foo");

        Assert.assertEquals(1, links.size());
        Assert.assertEquals("http://[::1]/foo", links.get(0).getRaw());
    }

    @Test
    public void bracketsInPathAreRejected() {
        final var links = Linkify.getLinks("http://example.com/foo[bar]");

        Assert.assertTrue(links.isEmpty());
    }
}
