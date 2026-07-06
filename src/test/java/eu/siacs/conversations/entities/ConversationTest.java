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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.RelativeLayout;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.CommandSliderFieldBinding;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
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

    @Test
    public void sliderSpecRejectsInRangeValueMissingFromOptionLattice() {
        final var field = sliderField("xs:integer", "0", "68", "7", "0", "34", "68");

        assertNotSlider("A value the option-derived slider cannot represent should fall back to text input", field);
    }

    @Test
    public void sliderSpecAcceptsCompatibleOptionLattice() {
        final var field = sliderField("xs:integer", "0", "70", "35", "0", "35", "70");

        assertSliderStep(field, 35f);
    }

    @Test
    public void sliderSpecUsesIntegerStepWithoutOptions() {
        final var field = sliderField("xs:integer", "0", "10", "7");

        assertSliderStep(field, 1f);
    }

    @Test
    public void sliderSpecUsesContinuousStepWithoutOptionsForDecimal() {
        final var field = sliderField("xs:decimal", "0", "1", "0.000001");

        assertSliderStep(field, 0f);
    }

    @Test
    public void sliderSpecRejectsMissingOrMalformedRangeBounds() {
        final var missingMin = sliderField("xs:integer", null, "10", "5");
        final var malformedMax = sliderField("xs:integer", "0", "not-a-number", "5");

        assertNotSlider(missingMin);
        assertNotSlider(malformedMax);
    }

    @Test
    public void sliderSpecRejectsIntegerValueThatCannotLandOnStep() {
        final var field = sliderField("xs:integer", "0", "10", "0.5");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsFractionalIntegerBounds() {
        final var field = sliderField("xs:integer", "0.5", "10.5", "1.5");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsFractionalIntegerOptions() {
        final var field = sliderField("xs:integer", "0", "1", "0", "0", "0.5", "1");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsFractionalIntegerValueAtFloatPrecisionBoundary() {
        final var field = sliderField("xs:integer", "16777216", "16777218", "16777216.5");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsIntegerValueRoundedByFloatParsing() {
        final var field = sliderField("xs:integer", "16777216", "16777218", "16777217");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsIntegerRangeWithUnrepresentableIntermediateValue() {
        final var field = sliderField("xs:integer", "16777216", "16777218", "16777216");

        assertNotSlider(field);
    }

    @Test
    public void formatSliderValueDoesNotClampLargeIntegerDatatypesToInt() {
        Assert.assertEquals("3000000000", Conversation.formatSliderValue(3_000_000_000f, "xs:long"));
    }

    @Test
    public void formatSliderValueUsesPlainDecimalLexicalFormForDecimalDatatype() {
        Assert.assertEquals("0.000001", Conversation.formatSliderValue(0.000001f, "xs:decimal"));
    }

    @Test
    public void sliderSpecRejectsIncompatibleBounds() {
        final var field = sliderField("xs:integer", "0", "70", "34", "0", "34", "68");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsMalformedOptions() {
        final var field = sliderField("xs:integer", "0", "70", "35", "0", "not-a-number", "70");

        assertNotSlider(field);
    }

    @Test
    public void sliderSpecRejectsValuesOutsideRange() {
        final var below = sliderField("xs:integer", "0", "70", "-5", "0", "35", "70");
        final var above = sliderField("xs:integer", "0", "70", "999", "0", "35", "70");

        assertNotSlider(below);
        assertNotSlider(above);
    }

    @Test
    public void sliderSpecRejectsDuplicateOptions() {
        final var field = sliderField("xs:integer", "0", "70", "35", "0", "35", "35", "70");

        assertNotSlider(field);
    }

    @Test
    public void rangedListSingleWithCustomValueFallsBackToTextInputWhenSliderCannotRepresentIt() {
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            session.responseElement = new Element("x", Namespace.DATA);
            session.responseElement.setAttribute("type", "form");
            final var field = sliderField("xs:integer", "0", "68", "7", "0", "34", "68");
            field.setAttribute("type", "list-single");

            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(field).viewType);
        } finally {
            session.loadingTimer.cancel();
        }
    }

    @Test
    public void rangedNumericFieldWithBadBoundsFallsBackToTextInput() {
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            session.responseElement = new Element("x", Namespace.DATA);
            session.responseElement.setAttribute("type", "form");
            final var missingMin = sliderField("xs:integer", null, "10", "5");
            final var malformedMax = sliderField("xs:integer", "0", "not-a-number", "5");

            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(missingMin).viewType);
            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(malformedMax).viewType);
        } finally {
            session.loadingTimer.cancel();
        }
    }

    @Test
    public void rangedNumericFieldWithEmptyValueFallsBackToTextInput() {
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            session.responseElement = new Element("x", Namespace.DATA);
            session.responseElement.setAttribute("type", "form");
            final var emptyValue = sliderField("xs:integer", "0", "70", "", "0", "35", "70");
            final var missingValue = sliderField("xs:integer", "0", "70", null, "0", "35", "70");

            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(emptyValue).viewType);
            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(missingValue).viewType);
        } finally {
            session.loadingTimer.cancel();
        }
    }

    @Test
    public void rangedIntegerFieldWithFractionalValuesFallsBackToTextInput() {
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            session.responseElement = new Element("x", Namespace.DATA);
            session.responseElement.setAttribute("type", "form");
            final var fractionalBounds = sliderField("xs:integer", "0.5", "10.5", "1.5");
            final var fractionalOptions = sliderField("xs:integer", "0", "1", "0", "0", "0.5", "1");

            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(fractionalBounds).viewType);
            Assert.assertEquals(session.TYPE_TEXT_FIELD, session.mkField(fractionalOptions).viewType);
        } finally {
            session.loadingTimer.cancel();
        }
    }

    @Test
    public void sliderFieldViewHolderRebindsDifferentStepSizesWithoutCrashing() {
        final var context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            final var binding = CommandSliderFieldBinding.inflate(LayoutInflater.from(context));
            final var holder = session.new SliderFieldViewHolder(binding);
            final var steppedField = sliderField("xs:integer", "0", "70", "35", "0", "35", "70");
            holder.bind(session.new Field(
                    eu.siacs.conversations.xmpp.forms.Field.parse(steppedField),
                    session.TYPE_SLIDER_FIELD));

            final var integerField = sliderField("xs:integer", "0", "10", "7");
            holder.bind(session.new Field(
                    eu.siacs.conversations.xmpp.forms.Field.parse(integerField),
                    session.TYPE_SLIDER_FIELD));

            Assert.assertEquals(0f, binding.slider.getValueFrom(), 0.0001f);
            Assert.assertEquals(10f, binding.slider.getValueTo(), 0.0001f);
            Assert.assertEquals(7f, binding.slider.getValue(), 0.0001f);
            Assert.assertEquals(1f, binding.slider.getStepSize(), 0.0001f);
        } finally {
            session.loadingTimer.cancel();
        }
    }

    @Test
    public void sliderFieldViewHolderRebindsFromLargeDiscreteToSmallDiscreteRangeWithoutCrashing() {
        final var context = new ContextThemeWrapper(
                RuntimeEnvironment.getApplication(),
                com.google.android.material.R.style.Theme_MaterialComponents_DayNight_NoActionBar);
        final var session = withOccupantId.pagerAdapter.new CommandSession(
                "test", "node", mock(XmppConnectionService.class));
        try {
            final var binding = CommandSliderFieldBinding.inflate(LayoutInflater.from(context));
            final var holder = session.new SliderFieldViewHolder(binding);
            final var discreteField = sliderField("xs:integer", "0", "100", "50", "0", "10", "20");
            holder.bind(session.new Field(
                    eu.siacs.conversations.xmpp.forms.Field.parse(discreteField),
                    session.TYPE_SLIDER_FIELD));

            final var continuousField = sliderField(
                    "xs:decimal", "0", "1", "0.5", "0", "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1");
            holder.bind(session.new Field(
                    eu.siacs.conversations.xmpp.forms.Field.parse(continuousField),
                    session.TYPE_SLIDER_FIELD));

            Assert.assertEquals(0f, binding.slider.getValueFrom(), 0.0001f);
            Assert.assertEquals(1f, binding.slider.getValueTo(), 0.0001f);
            Assert.assertEquals(0.5f, binding.slider.getValue(), 0.0001f);
            Assert.assertEquals(0.1f, binding.slider.getStepSize(), 0.0001f);
            binding.slider.measure(
                    View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
            binding.slider.layout(0, 0, 100, 100);
            binding.slider.draw(new Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)));
        } finally {
            session.loadingTimer.cancel();
        }
    }

    private static void assertSliderStep(final Element field, final float step) {
        final var spec = Conversation.sliderSpec(field);
        Assert.assertNotNull("Field should be usable as a slider", spec);
        Assert.assertEquals(step, spec.step(), 0.0001f);
    }

    private static void assertNotSlider(final Element field) {
        Assert.assertNull(Conversation.sliderSpec(field));
    }

    private static void assertNotSlider(final String message, final Element field) {
        Assert.assertNull(message, Conversation.sliderSpec(field));
    }

    private static Element sliderField(
            final String datatype,
            final String min,
            final String max,
            final String value,
            final String... options) {
        final var field = new Element("field", Namespace.DATA);
        field.setAttribute("type", "text-single");
        final var validate = field.addChild("validate", "http://jabber.org/protocol/xdata-validate");
        validate.setAttribute("datatype", datatype);
        final var range = validate.addChild("range", "http://jabber.org/protocol/xdata-validate");
        range.setAttribute("min", min);
        range.setAttribute("max", max);
        if (value != null) {
            field.addChild("value", Namespace.DATA).setContent(value);
        }
        for (final String option : options) {
            field.addChild("option", Namespace.DATA)
                    .addChild("value", Namespace.DATA)
                    .setContent(option);
        }
        return field;
    }
}
