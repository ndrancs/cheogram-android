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
}
