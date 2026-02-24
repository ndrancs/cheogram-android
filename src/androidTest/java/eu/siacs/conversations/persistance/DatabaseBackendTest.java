package eu.siacs.conversations.persistance;

import android.content.ContentValues;
import android.database.sqlite.SQLiteBlobTooBigException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.UUID;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;

@RunWith(AndroidJUnit4.class)
public class DatabaseBackendTest {
    private record AccountFixture(String uuid, String username, String server) {
        void write(DatabaseBackend db) {
            final var cv = new ContentValues();
            cv.put("uuid", uuid);
            cv.put("username", username);
            cv.put("server", server);
            cv.put("password", "test");
            cv.put("options", 0);
            db.getWritableDatabase().insertWithOnConflict(
                "accounts", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private record ConversationFixture(
        String conversationUuid,
        AccountFixture account,
        String name,
        String contactJid,
        String attributes,
        HashMap<MucOptions.User.OccupantId, MucOptions.User.CacheEntry> occupantCache
    ) {
        void writeConversation(DatabaseBackend db) {
            final var cv = new ContentValues();
            cv.put("uuid", conversationUuid);
            cv.put("name", name);
            cv.put("contactUuid", "");
            cv.put("accountUuid", account.uuid());
            cv.put("contactJid", contactJid);
            cv.put("created", System.currentTimeMillis());
            cv.put("status", Conversation.STATUS_AVAILABLE);
            cv.put("mode", Conversation.MODE_MULTI);
            cv.put("attributes", attributes);
            db.getWritableDatabase().insert("conversations", null, cv);
        }

        void writeOccupants(DatabaseBackend db) {
            for (final var entry : occupantCache.entrySet()) {
                final var cv = new ContentValues();
                cv.put(MucOptions.User.CacheEntry.OCCUPANT_ID, entry.getKey().inner());
                cv.put(MucOptions.User.CacheEntry.CONVERSATION_UUID, conversationUuid);
                cv.put(MucOptions.User.CacheEntry.AVATAR, entry.getValue().avatar());
                cv.put(MucOptions.User.CacheEntry.NICK, entry.getValue().nick());
                db.getWritableDatabase().insert(
                    MucOptions.User.CacheEntry.TABLENAME, null, cv);
            }
        }

        void writeAll(DatabaseBackend db) {
            writeConversation(db);
            writeOccupants(db);
        }

        Conversation extractAndConfigure(DatabaseBackend db)
        {
            final var conversations = db.getConversations(Conversation.STATUS_AVAILABLE);
            Assert.assertNotNull("getConversations should not return null", conversations);

            Conversation match = null;
            for (final var c : conversations) {
                if (conversationUuid.equals(c.getUuid())) {
                    match = c;
                    break;
                }
            }
            Assert.assertNotNull(
                "Fixture conversation " + conversationUuid + " not found", match);

            match.setAccount(db.getAccounts().get(0));
            match.getMucOptions().updateConfiguration(INFO_QUERY_WITH_OCCUPANT_ID);
            match.putAllInMucOccupantCache(db.getMucUsersForConversation(match));
            return match;
        }
    }

    private DatabaseBackend db;
    private static final InfoQuery INFO_QUERY_WITH_OCCUPANT_ID = new InfoQuery();
    private static final int MANY_USERS = 20_000;

    private static AccountFixture ACCOUNT;
    private static ConversationFixture ROW_TOO_BIG;
    private static ConversationFixture CONFORMING;
    private static ConversationFixture NO_CACHED_MUC_USERS;
    private static ConversationFixture[] FIXTURES;

    @BeforeClass
    public static void setupClass() throws JSONException {
        final var occupantIdFeature = new Feature();
        occupantIdFeature.setVar(Namespace.OCCUPANT_ID);
        INFO_QUERY_WITH_OCCUPANT_ID.addChild(occupantIdFeature);

        ACCOUNT = new AccountFixture(
            UUID.randomUUID().toString(), "test", "example.com");

        final var rowTooBigAttrs = new JSONObject();
        for (int i = 0; i < MANY_USERS; i++) {
            final var occupantId = UUID.randomUUID().toString();
            rowTooBigAttrs.put("occupantNick/" + occupantId, "User" + i);
            rowTooBigAttrs.put("occupantAvatar/" + occupantId,
                UUID.randomUUID().toString().repeat(5));
        }
        rowTooBigAttrs.put("mucNick", "testMucNick");

        final var rowTooBigCache =
            new HashMap<MucOptions.User.OccupantId, MucOptions.User.CacheEntry>();
        rowTooBigCache.put(
            new MucOptions.User.OccupantId(UUID.randomUUID().toString()),
            new MucOptions.User.CacheEntry(UUID.randomUUID().toString(), "RowTooBigUser"));

        ROW_TOO_BIG = new ConversationFixture(
            UUID.randomUUID().toString(),
            ACCOUNT,
            "Big MUC",
            "room@conference.example.com",
            rowTooBigAttrs.toString(),
            rowTooBigCache
        );

        final var conformingCache =
            new HashMap<MucOptions.User.OccupantId, MucOptions.User.CacheEntry>();
        conformingCache.put(
            new MucOptions.User.OccupantId(UUID.randomUUID().toString()),
            new MucOptions.User.CacheEntry(UUID.randomUUID().toString(), "ConformingUser"));

        CONFORMING = new ConversationFixture(
            UUID.randomUUID().toString(),
            ACCOUNT,
            "Normal MUC",
            "normalroom@conference.example.com",
            new JSONObject().put("mucNick", "testMucNick").toString(),
            conformingCache
        );

        NO_CACHED_MUC_USERS = new ConversationFixture(
            UUID.randomUUID().toString(),
            ACCOUNT,
            "Empty Cache MUC",
            "emptycache@conference.example.com",
            new JSONObject().put("mucNick", "testMucNick").toString(),
            new HashMap<>()
        );

        FIXTURES = new ConversationFixture[] {
            ROW_TOO_BIG, CONFORMING, NO_CACHED_MUC_USERS };
    }

    @Before
    public void setUp() throws Exception {
        db = DatabaseBackend.getInstance(
                ApplicationProvider.getApplicationContext());
        ACCOUNT.write(db);
        for (final var fixture : FIXTURES) {
            fixture.writeAll(db);
        }
    }

    @After
    public void tearDown() {
        SQLiteDatabase sqDb = db.getWritableDatabase();
        sqDb.delete(Conversation.TABLENAME, null, null);
        sqDb.delete(Account.TABLENAME, null, null);
        sqDb.delete(MucOptions.User.CacheEntry.TABLENAME, null, null);
    }

    @Test
    public void getConversationsCorrectlyReadsMucUsers() throws Exception {
        Assert.assertTrue(
            "Occupant cache should be empty when no occupants are written",
            NO_CACHED_MUC_USERS
                .extractAndConfigure(db)
                    .getMucOccupantCache()
                    .isEmpty()
        );

        Assert.assertEquals(
            "Cached entries should match fixture",
            CONFORMING
                .extractAndConfigure(db)
                .getMucOccupantCache(),
            CONFORMING.occupantCache()
        );
    }

    @Test
    public void getConversationsTruncatesTooBigRow() throws Exception {
        final var conversation = ROW_TOO_BIG.extractAndConfigure(db);
        java.lang.reflect.Field attributesField =
            conversation.getClass().getDeclaredField("attributes");

        attributesField.setAccessible(true);
        org.json.JSONObject attributes =
            (org.json.JSONObject) attributesField.get(conversation);

        final var expected = new JSONObject();
        expected.put("members_only", "false");
        expected.put("moderated", "false");
        expected.put("non_anonymous", "false");

        Assert.assertEquals(
            "Attributes should not contain occupant cache after truncation:\n" + attributes.toString(4),
            expected.toString(),
            attributes.toString()
        );
    }

    @Test
    public void updateConversationWritesMucOccupantsCache() throws Exception {
        final var conversation = NO_CACHED_MUC_USERS.extractAndConfigure(db);
        conversation.putAllInMucOccupantCache(CONFORMING.occupantCache());
        db.updateConversation(conversation);

        final var readBackCache = db.getMucUsersForConversation(conversation);
        Assert.assertEquals(
            "Cache should match after updateConversation",
            CONFORMING.occupantCache(),
            readBackCache
        );
    }
}
