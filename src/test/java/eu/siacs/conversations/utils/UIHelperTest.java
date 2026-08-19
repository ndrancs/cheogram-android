package eu.siacs.conversations.utils;

import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;

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
public class UIHelperTest {

    @Test
    public void shortenPreservesRelativeSizeSpanAcrossCutoff() {
        final var preview = new SpannableStringBuilder("a".repeat(300));
        preview.setSpan(
                new RelativeSizeSpan(0.8f),
                0,
                preview.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        final var shortened = UIHelper.shorten(preview);

        Assert.assertEquals(256, shortened.length());
    }
}
