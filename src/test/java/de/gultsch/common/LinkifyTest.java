package de.gultsch.common;

import org.junit.Assert;
import org.junit.Test;

public class LinkifyTest {

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
