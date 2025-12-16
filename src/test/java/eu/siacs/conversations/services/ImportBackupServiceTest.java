package eu.siacs.conversations.services;

import static org.junit.Assert.assertNotNull;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import eu.siacs.conversations.Conversations;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.TIRAMISU, application = Conversations.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ImportBackupServiceTest {

    @Test
    public void testBackupChannelExistsAfterOnCreate() {
        Robolectric.buildService(ImportBackupService.class).create().get();

        NotificationManager nm = (NotificationManager)
                RuntimeEnvironment.getApplication()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = nm.getNotificationChannel("backup");
        assertNotNull("backup channel should exist after onCreate", channel);
    }
}
