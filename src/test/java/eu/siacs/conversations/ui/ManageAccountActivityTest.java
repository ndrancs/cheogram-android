package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.ListView;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowActivity;

import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.Jid;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ManageAccountActivityTest {

    @Test
    public void onItemClickNavigatesToCorrectAccountAndIgnoresFooter() {
        ManageAccountActivity activity =
                Robolectric.buildActivity(ManageAccountActivity.class).create().get();

        Account[] accounts = new Account[] {
            new Account(Jid.ofLocalAndDomain("alice", "example.org"), "password"),
            new Account(Jid.ofLocalAndDomain("bob", "example.org"), "password"),
            new Account(Jid.ofLocalAndDomain("carol", "example.org"), "password"),
        };
        for (Account account : accounts) {
            activity.accountList.add(account);
        }
        activity.mAccountAdapter.notifyDataSetChanged();

        ListView listView = activity.accountListView;
        AdapterView.OnItemClickListener listener = listView.getOnItemClickListener();
        ShadowActivity shadow = Shadows.shadowOf(activity);

        for (int position = 0; position < accounts.length; position++) {
            listener.onItemClick(listView, null, position, 0);
            Intent started = shadow.getNextStartedActivity();
            Assert.assertNotNull("position " + position + ": should start activity", started);
            Assert.assertEquals(
                    "position " + position + ": should navigate to correct account",
                    accounts[position].getJid().asBareJid().toString(),
                    started.getStringExtra("jid"));
        }

        int footerPosition = accounts.length;
        Assert.assertEquals(
                "position " + footerPosition + ": should be a header/footer view type",
                AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER,
                listView.getAdapter().getItemViewType(footerPosition));
        listener.onItemClick(listView, null, footerPosition, 0);
        Assert.assertNull(
                "position " + footerPosition + " (footer): should not start activity",
                shadow.getNextStartedActivity());
    }
}
