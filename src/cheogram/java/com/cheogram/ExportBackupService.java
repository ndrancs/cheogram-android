package com.cheogram.android;

import static eu.siacs.conversations.utils.Compatibility.s;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.IBinder;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bouncycastle.bcpg.S2K.Argon2Params;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPBEKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;

public class ExportBackupService extends Worker {

    private static final int NOTIFICATION_ID = 19;
    private static final int PAGE_SIZE = 20;
    private static final int BACKUP_CREATED_NOTIFICATION_ID = 23;
    private static final int PENDING_INTENT_FLAGS =
            s()
                    ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT;

    private static List<Intent> getPossibleFileOpenIntents(final Context context, final String path) {

        //http://www.openintents.org/action/android-intent-action-view/file-directory
        //do not use 'vnd.android.document/directory' since this will trigger system file manager
        final Intent openIntent = new Intent(Intent.ACTION_VIEW);
        openIntent.addCategory(Intent.CATEGORY_DEFAULT);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            openIntent.setType("resource/folder");
        } else {
            openIntent.setDataAndType(Uri.parse("file://" + path), "resource/folder");
        }
        openIntent.putExtra("org.openintents.extra.ABSOLUTE_PATH", path);

        final Intent amazeIntent = new Intent(Intent.ACTION_VIEW);
        amazeIntent.setDataAndType(Uri.parse("com.amaze.filemanager:" + path), "resource/folder");

        //will open a file manager at root and user can navigate themselves
        final Intent systemFallBack = new Intent(Intent.ACTION_VIEW);
        systemFallBack.addCategory(Intent.CATEGORY_DEFAULT);
        systemFallBack.setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"));

        return Arrays.asList(openIntent, amazeIntent, systemFallBack);
    }

    public ExportBackupService(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        setForegroundAsync(getForegroundInfo());
        final List<File> files;
        try {
            files = export();
        } catch (final IOException | PGPException e) {
            Log.d(Config.LOGTAG, "could not create backup", e);
            return Result.failure();
        } finally {
            getApplicationContext()
                    .getSystemService(NotificationManager.class)
                    .cancel(NOTIFICATION_ID);
        }
        Log.d(Config.LOGTAG, "done creating " + files.size() + " backup files");
        if (files.isEmpty()) {
            return Result.success();
        }
        notifySuccess(files);
        return Result.success();
    }

    @Override
    public ForegroundInfo getForegroundInfo() {
        Log.d(Config.LOGTAG, "getForegroundInfo()");
        final NotificationCompat.Builder notification = getNotification();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return new ForegroundInfo(
                    NOTIFICATION_ID,
                    notification.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(NOTIFICATION_ID, notification.build());
        }
    }

    private static final String MESSAGE_EXPORT_COLUMNS =
            "conversations.mode, conversations.contactJid, messages.type, messages.status, "
                    + "messages.serverMsgId, messages.timeSent, cheogram_messages.timeReceived, "
                    + "messages.counterpart, messages.trueCounterpart, messages.body, "
                    + "cheogram_messages.subject, cheogram_messages.payloads, messages.reactions, "
                    + "cheogram_messages.occupant_id, messages.occupantId, messages.uuid, "
                    + "messages.remoteMsgId";

    private int messageExport(
            final Cursor cursor,
            final Account account,
            final PrintWriter writer,
            final Progress progress,
            final int total,
            int exported) {
        final ImmutableMap<String,String> emptyMap = ImmutableMap.of();
        final var mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        final var accountJid = account.getJid().toString();
        final var notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        Element archive = new Element("archive", "urn:xmpp:pie:0#mam");
        writer.write(archive.startTag().toString());
        while (cursor != null && cursor.moveToNext()) {
            try {
                final var conversationMode = cursor.getInt(cursor.getColumnIndexOrThrow(Conversation.MODE));
                final var mType = cursor.getInt(cursor.getColumnIndex(Message.TYPE));
                final var mStatus = cursor.getInt(cursor.getColumnIndex(Message.STATUS));
                final var serverMsgId = cursor.getString(cursor.getColumnIndex(Message.SERVER_MSG_ID));
                final var timeSent = cursor.getLong(cursor.getColumnIndex(Message.TIME_SENT));
                final var timeReceived = cursor.getLong(cursor.getColumnIndex("timeReceived"));
                final var counterpart = cursor.getString(cursor.getColumnIndex(Message.COUNTERPART));
                final var rawBody = cursor.getString(cursor.getColumnIndex(Message.BODY));
                final var subject = cursor.getString(cursor.getColumnIndex("subject"));
                final var payloads = cursor.getString(cursor.getColumnIndex("payloads"));

                var result = new Element("result", "urn:xmpp:mam:2");
                if (serverMsgId != null) result.setAttribute("id", serverMsgId);
                var forwarded = new Element("forwarded", "urn:xmpp:forward:0");
                final var delay = forwarded.addChild("delay", "urn:xmpp:delay");
                final var date = new Date(timeReceived > 0 ? timeReceived : timeSent);
                delay.setAttribute("stamp", mDateFormat.format(date));

                var message = new Element("message", "jabber:client").setAttribute("type", conversationMode == Conversation.MODE_MULTI && mType != Message.TYPE_PRIVATE && mType != Message.TYPE_PRIVATE_FILE ? "groupchat" : "chat");
                String outerId = null;
                     if (conversationMode == Conversation.MODE_MULTI) {
                          message.setAttribute("from", counterpart);
                          outerId = cursor.getString(cursor.getColumnIndex(Message.REMOTE_MSG_ID));
                     } else {
                          if (mStatus <= Message.STATUS_RECEIVED) {
                                message.setAttribute("to", accountJid).setAttribute("from", counterpart);
                                outerId = cursor.getString(cursor.getColumnIndex(Message.REMOTE_MSG_ID));
                          } else {
                                message.setAttribute("from", accountJid).setAttribute("to", counterpart);
                                outerId = cursor.getString(cursor.getColumnIndex(Message.UUID));
                          }
                     }
                if (outerId != null) message.setAttribute("id", outerId);
                if (rawBody != null) message.addChild(new Element("body").setContent(rawBody));
                if (subject != null) message.addChild(new Element("subject").setContent(subject));
                if (conversationMode == Conversation.MODE_MULTI) {
                    final var trueCounterpart = cursor.getString(cursor.getColumnIndex(Message.TRUE_COUNTERPART));
                    var occupantId = cursor.getString(cursor.getColumnIndexOrThrow(Message.OCCUPANT_ID));
                    final String legacyOccupant = cursor.getString(cursor.getColumnIndex("occupant_id"));
                    if (legacyOccupant != null) occupantId = legacyOccupant;
                    final var x = new Element("x", "http://jabber.org/protocol/muc#user");
                    if (trueCounterpart != null) x.addChild("item", "http://jabber.org/protocol/muc#user").setAttribute("jid", trueCounterpart);
                    message.addChild(x);
                    if (occupantId != null) message.addChild("occupant-id", "urn:xmpp:occupant-id:0").setAttribute("id", occupantId);
                }
                final var rDelay = message.addChild("delay", "urn:xmpp:delay");
                final var rDate = new Date(timeSent);
                rDelay.setAttribute("stamp", mDateFormat.format(rDate));
                forwarded.addChild(message);
                result.addChild(forwarded);
                final StringBuilder elementOutput = new StringBuilder();
                result.appendToBuilder(emptyMap, elementOutput, 3);
                writer.write(elementOutput.toString());
                if (payloads != null) writer.write(payloads);
                writer.write("</message></forwarded></result>\n");
                final HashMultimap<String, Reaction> aggregatedReactions = HashMultimap.create();
                final var reactions = Reaction.fromString(cursor.getString(cursor.getColumnIndexOrThrow(Message.REACTIONS)));
                for (final var reaction : reactions) {
                    aggregatedReactions.put(reaction.occupantId == null ? (reaction.trueJid == null ? reaction.from.toString() : reaction.trueJid.toString()) : reaction.occupantId, reaction);
                }
                for (final var reactionSet : aggregatedReactions.asMap().values()) {
                    final var reaction = reactionSet.iterator().next();
                    result = new Element("result", "urn:xmpp:mam:2");
                    forwarded = new Element("forwarded", "urn:xmpp:forward:0");
                    message = new Element("message", "jabber:client").setAttribute("type", conversationMode == Conversation.MODE_MULTI && mType != Message.TYPE_PRIVATE && mType != Message.TYPE_PRIVATE_FILE ? "groupchat" : "chat");
                    message.setAttribute("from", reaction.from).setAttribute("to", reaction.received && conversationMode != Conversation.MODE_MULTI ? accountJid : counterpart);
                    if (reaction.envelopeId != null) message.setAttribute("id", reaction.envelopeId);
                    final var reactionsEl = new Element("reactions", "urn:xmpp:reactions:0");
                    reactionsEl.setAttribute("id", "groupchat".equals(message.getAttribute("type")) ? serverMsgId : outerId);
                    for (final var r : reactionSet) {
                        reactionsEl.addChild("reaction", "urn:xmpp:reactions:0").setContent(r.reaction);
                    }
                    message.addChild(reactionsEl);
                    if (conversationMode == Conversation.MODE_MULTI) {
                        final var x = new Element("x", "http://jabber.org/protocol/muc#user");
                        if (reaction.trueJid != null) x.addChild("item", "http://jabber.org/protocol/muc#user").setAttribute("jid", reaction.trueJid);
                        message.addChild(x);
                        if (reaction.occupantId != null) message.addChild("occupant-id", "urn:xmpp:occupant-id:0").setAttribute("id", reaction.occupantId);
                    }
                    forwarded.addChild(message);
                    result.addChild(forwarded);
                    writer.write(result.toString());
                }
            } catch (final Exception e) {
                Log.e(Config.LOGTAG, "message export error: " + e);
            }
            exported++;
            if (exported % 100 == 0 && total > 0) {
                final int p = exported * 100 / total;
                Log.d(Config.LOGTAG, "Message export progress: " + p + " (" + exported + ")");
                notificationManager.notify(NOTIFICATION_ID, progress.build(p));
            }
        }
        // TODO: need to separate webxdc updates per MUC etc like we do messages
        // messageExportCheogram(db, account, writer, progress);
        writer.write(archive.endTag().toString());
        return exported;
    }

    private int messageCount(final SQLiteDatabase db, final Account account) {
        try (final Cursor cursor =
                db.rawQuery(
                        "select count(*) from messages join conversations on "
                                + "conversations.uuid=messages.conversationUuid "
                                + "where conversations.accountUuid=?",
                        new String[] {account.getUuid()})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private int directMessageExport(
            final SQLiteDatabase db,
            final Account account,
            final PrintWriter writer,
            final Progress progress,
            final int total,
            final int exported) {
        try (final Cursor cursor =
                db.rawQuery(
                        "select "
                                + MESSAGE_EXPORT_COLUMNS
                                + " from messages left join cheogram.messages cheogram_messages "
                                + "using (uuid) "
                                + "join conversations on conversations.uuid=messages.conversationUuid "
                                + "where conversations.accountUuid=? and conversations.mode=? "
                                + "order by messages.timeSent",
                        new String[] {
                            account.getUuid(), String.valueOf(Conversation.MODE_SINGLE)
                        })) {
            return messageExport(cursor, account, writer, progress, total, exported);
        }
    }

    private int mucMessageExport(
            final SQLiteDatabase db,
            final Account account,
            final PrintWriter writer,
            final Progress progress,
            final int total,
            int exported) {
        Element component = null;
        Element user = null;
        try (final Cursor conversations =
                db.rawQuery(
                        "select conversations.uuid, conversations.contactJid from conversations "
                                + "where conversations.accountUuid=? and conversations.mode=? "
                                + "and exists (select 1 from messages where "
                                + "messages.conversationUuid=conversations.uuid) "
                                + "order by CASE WHEN mode<>0 THEN "
                                + "SUBSTR(contactJid, INSTR(contactJid, '@') + 1) END, "
                                + "CASE WHEN mode<>0 THEN contactJid END",
                        new String[] {
                            account.getUuid(), String.valueOf(Conversation.MODE_MULTI)
                        })) {
            while (conversations.moveToNext()) {
                final String conversationUuid =
                        conversations.getString(
                                conversations.getColumnIndexOrThrow(Conversation.UUID));
                final var jid =
                        Jid.ofOrInvalid(
                                conversations.getString(
                                        conversations.getColumnIndexOrThrow(
                                                Conversation.CONTACTJID)));
                Log.d(Config.LOGTAG, "Export MUC chunk: " + jid);
                final var domain = jid.getDomain().toString();
                if (component == null || !component.getAttribute("jid").equals(domain)) {
                    if (user != null) {
                        writer.write("\n");
                        writer.write(user.endTag().toString());
                        user = null;
                    }
                    if (component != null) {
                        writer.write("\n");
                        writer.write(component.endTag().toString());
                    }
                    component =
                            new Element("component")
                                    .setAttribute("jid", domain)
                                    .setAttribute("type", "muc");
                    writer.write("\n");
                    writer.write(component.startTag().toString());
                }
                if (user != null) {
                    writer.write("\n");
                    writer.write(user.endTag().toString());
                }
                user = new Element("user").setAttribute("name", jid.getLocal());
                writer.write("\n");
                writer.write(user.startTag().toString());
                try (final Cursor messages =
                        db.rawQuery(
                                "select "
                                        + MESSAGE_EXPORT_COLUMNS
                                        + " from messages left join cheogram.messages "
                                        + "cheogram_messages using (uuid) "
                                        + "join conversations on "
                                        + "conversations.uuid=messages.conversationUuid "
                                        + "where messages.conversationUuid=? "
                                        + "order by messages.timeSent",
                                new String[] {conversationUuid})) {
                    exported = messageExport(messages, account, writer, progress, total, exported);
                }
            }
        } finally {
            writer.write("\n");
            if (user != null) {
                writer.write(user.endTag().toString());
            }
            if (component != null) {
                writer.write(component.endTag().toString());
            }
        }
        return exported;
    }

    private void messageExportCheogram(SQLiteDatabase db, Account account, PrintWriter writer, Progress progress) {
        final var notificationManager = getApplicationContext().getSystemService(NotificationManager.class);
        int i = 0;
        Cursor cursor = db.rawQuery("select conversations.*,webxdc_updates.* from " + Conversation.TABLENAME + " join cheogram.webxdc_updates webxdc_updates on " + Conversation.TABLENAME + ".uuid=webxdc_updates." + Message.CONVERSATION + " where conversations.accountUuid=?", new String[]{account.getUuid()});
        int size = cursor != null ? cursor.getCount() : 0;
        Log.d(Config.LOGTAG, "exporting " + size + " WebXDC updates for account " + account.getUuid());
        while (cursor != null && cursor.moveToNext()) {
            final Conversation conversation = Conversation.fromCursor(cursor);
            Element result = new Element("result", "urn:xmpp:mam:2");
            result.setAttribute("id", "webxdc-serial:" + cursor.getString(cursor.getColumnIndex("serial")));
            Element forwarded = new Element("forwarded", "urn:xmpp:forward:0");
            Element message = new Element("message", "jabber:client").setAttribute("type", conversation.getMode() == Conversation.MODE_MULTI ? "groupchat" : "chat");
            final var sender = cursor.getString(cursor.getColumnIndex("sender"));
            message.setAttribute("from", sender);
            message.setAttribute("to", !account.getJid().toString().equals(sender) && conversation.getMode() != Conversation.MODE_MULTI ? account.getJid() : conversation.getJid());
            final var info = cursor.getString(cursor.getColumnIndex("info"));
            if (info != null) message.addChild(new Element("body").setContent(info));
            final var thread = cursor.getString(cursor.getColumnIndex("thread"));
            if (thread != null) {
                final var threadParent = cursor.getString(cursor.getColumnIndex("threadParent"));
                final var threadEl = new Element("thread").setContent(thread);
                if (threadParent != null) threadEl.setAttribute("parent", threadParent);
                message.addChild(threadEl);
            }
            final var x = new Element("x", "urn:xmpp:webxdc:0");
            final var document = cursor.getString(cursor.getColumnIndex("document"));
            if (document != null) x.addChild("document", "urn:xmpp:webxdc:0").setContent(document);
            final var summary = cursor.getString(cursor.getColumnIndex("summary"));
            if (summary != null) x.addChild("document", "urn:xmpp:webxdc:0").setContent(summary);
            final var payload = cursor.getString(cursor.getColumnIndex("payload"));
            if (payload != null) x.addChild("json", "urn:xmpp:json:0").setContent(payload);
            message.addChild(x);
            forwarded.addChild(message);
            result.addChild(forwarded);
            writer.write(result.toString());

            i++;
            final int p = i * 100 / size;
            notificationManager.notify(NOTIFICATION_ID, progress.build(p));
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private List<File> export() throws IOException, PGPException {
        final Context context = getApplicationContext();
        final var database = DatabaseBackend.getInstance(context);
        final var accounts = database.getAccounts();
        final var notification = getNotification();
        int count = 0;
        final int max = accounts.size();
        final List<File> files = new ArrayList<>();
        Log.d(Config.LOGTAG, "starting backup for " + max + " accounts");
        for (final Account account : accounts) {
            final String password = account.getPassword();
            if (Strings.nullToEmpty(password).trim().isEmpty()) {
                Log.d(Config.LOGTAG, String.format("skipping backup for %s because password is empty. unable to encrypt", account.getJid().asBareJid()));
                continue;
            }
            Log.d(Config.LOGTAG, String.format("exporting data for account %s (%s)", account.getJid().asBareJid(), account.getUuid()));
            final Progress progress = new Progress(notification, max, count);
            final File file = new File(FileBackend.getBackupDirectory(context), account.getJid().asBareJid().toString() + ".xml.pgp");
            files.add(file);
            final File directory = file.getParentFile();
            if (directory != null && directory.mkdirs()) {
                Log.d(Config.LOGTAG, "created backup directory " + directory.getAbsolutePath());
            }
            final FileOutputStream fileOutputStream = new FileOutputStream(file);

            PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
            PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(PGPCompressedDataGenerator.ZLIB);
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(PGPEncryptedDataGenerator.AES_256).setUseV6AEAD().setWithAEAD(PGPEncryptedData.GCM, 16)
            );
            encGen.setForceSessionKey(true);
            encGen.addMethod(new BcPBEKeyEncryptionMethodGenerator(
                password.toCharArray(),
                Argon2Params.memoryConstrainedParameters()
            ).setSecureRandom(new SecureRandom()));

            PrintWriter writer = new PrintWriter(lData.open(
                comData.open(
                    encGen.open(fileOutputStream, new byte[4096]),
                    new byte[4096]
                ),
                PGPLiteralDataGenerator.UTF8,
                account.getJid().asBareJid().toString() + ".xml",
                PGPLiteralDataGenerator.NOW,
                new byte[4096]
            ));

            Element serverData = new Element("server-data", "urn:xmpp:pie:0");
            Element host = new Element("host").setAttribute("jid", account.getDomain());
            Element user = new Element("user").setAttribute("name", account.getUsername());

            writer.write(serverData.startTag().toString());
            writer.write(host.startTag().toString());
            writer.write(user.startTag().toString());
            writer.write("\n");

            Element roster = new Element("query", "jabber:iq:roster");
            if (!"".equals(account.getRosterVersion())) roster.setAttribute("ver", account.getRosterVersion());
            for (final var contact : database.readRoster(account).values()) {
                 if (contact.showInContactList()) roster.addChild(contact.asElement(true));
            }
            writer.write(roster.toString());

            final var bookmarks = new Element("pubsub", "http://jabber.org/protocol/pubsub#owner");
            final var bookmarksItems = new Element("items").setAttribute("node", "urn:xmpp:bookmarks:1");
            for (final var bookmark : account.getBookmarks()) {
                 bookmark.setAttribute("xmlns", "urn:xmpp:bookmarks:1");
                 final var item = new Element("item").setAttribute("id", bookmark.getJid().toString());
                 item.addChild(bookmark);
                 bookmarksItems.addChild(item);
            }
            bookmarks.addChild(bookmarksItems);
            writer.write(bookmarks.toString());

            if (account.getDisplayName() != null && !"".equals(account.getDisplayName())) {
                Element nickname = new Element("pubsub", "http://jabber.org/protocol/pubsub#owner");
                Element nickItems = new Element("items").setAttribute("node", "http://jabber.org/protocol/nick");
                Element nickItem = new Element("item");
                nickItem.addChild(new Element("nick", "http://jabber.org/protocol/nick").setContent(account.getDisplayName()));
                nickItems.addChild(nickItem);
                nickname.addChild(nickItems);
                writer.write(nickname.toString());
            }

            if (account.getAvatar() != null) {
                Element avatar = new Element("pubsub", "http://jabber.org/protocol/pubsub#owner");
                Element avatarItems = new Element("items").setAttribute("node", "urn:xmpp:avatar:metadata");
                Element avatarItem = new Element("item").setAttribute("id", account.getAvatar());
                Element avatarMeta = new Element("metadata", "urn:xmpp:avatar:metadata");
                avatarMeta.addChild(new Element("info").setAttribute("id", account.getAvatar()));
                avatarItem.addChild(avatarMeta);
                avatarItems.addChild(avatarItem);
                avatar.addChild(avatarItems);
                writer.write(avatar.toString());
                final var f = new File(context.getCacheDir(), "/avatars/" + account.getAvatar());
                if (f.canRead()) {
                    final var byteArrayOutputStream = new ByteArrayOutputStream();
                    final var base64OutputStream = new Base64OutputStream(byteArrayOutputStream, Base64.DEFAULT);
                    ByteStreams.copy(new FileInputStream(f), base64OutputStream);
                    base64OutputStream.flush();
                    base64OutputStream.close();

                    Element avatar2 = new Element("pubsub", "http://jabber.org/protocol/pubsub#owner");
                    Element avatarItems2 = new Element("items").setAttribute("node", "urn:xmpp:avatar:data");
                    Element avatarItem2 = new Element("item").setAttribute("id", account.getAvatar());
                    Element avatarData = new Element("data", "urn:xmpp:avatar:data");
                    avatarData.setContent(new String(byteArrayOutputStream.toByteArray()));
                    avatarItem2.addChild(avatarData);
                    avatarItems2.addChild(avatarItem2);
                    avatar2.addChild(avatarItems2);
                    writer.write(avatar2.toString());
                }
            }

            SQLiteDatabase db = database.getReadableDatabase();
            final int totalMessages = messageCount(db, account);
            Log.d(
                    Config.LOGTAG,
                    "exporting " + totalMessages + " messages for account " + account.getUuid());
            final int exportedMessages =
                    directMessageExport(db, account, writer, progress, totalMessages, 0);

            writer.write("\n");
            writer.write(user.endTag().toString());
            writer.write(host.endTag().toString());

            mucMessageExport(db, account, writer, progress, totalMessages, exportedMessages);

            writer.write("\n");
            writer.write(serverData.endTag().toString());

            writer.flush();
            writer.close();
            lData.close();
            comData.close();
            encGen.close();
            fileOutputStream.flush();
            fileOutputStream.close();
            Log.d(Config.LOGTAG, "written backup to " + file.getAbsoluteFile());
            count++;
        }
        return files;
    }

    private void notifySuccess(final List<File> files) {
        final var context = getApplicationContext();
        final String path = FileBackend.getBackupDirectory(context).getAbsolutePath();

        final var openFolderIntent = getOpenFolderIntent(path);

        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        final ArrayList<Uri> uris = new ArrayList<>();
        for (final File file : files) {
            uris.add(FileBackend.getUriForFile(context, file, file.getName()));
        }
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("application/pgp-encrypted");
        final Intent chooser =
                Intent.createChooser(intent, context.getString(R.string.share_backup_files));
        final var shareFilesIntent =
                PendingIntent.getActivity(context, 190, chooser, PENDING_INTENT_FLAGS);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, "backup");
        mBuilder.setContentTitle(context.getString(R.string.notification_backup_created_title))
                .setContentText(
                        context.getString(R.string.notification_backup_created_subtitle, path))
                .setStyle(
                        new NotificationCompat.BigTextStyle()
                                .bigText(
                                        context.getString(
                                                R.string.notification_backup_created_subtitle,
                                                FileBackend.getBackupDirectory(context)
                                                        .getAbsolutePath())))
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_archive_24dp);

        if (openFolderIntent.isPresent()) {
            mBuilder.setContentIntent(openFolderIntent.get());
        } else {
            Log.w(Config.LOGTAG, "no app can display folders");
        }

        mBuilder.addAction(
                R.drawable.ic_share_24dp,
                context.getString(R.string.share_backup_files),
                shareFilesIntent);
        final var notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.notify(BACKUP_CREATED_NOTIFICATION_ID, mBuilder.build());
    }

    private Optional<PendingIntent> getOpenFolderIntent(final String path) {
        final var context = getApplicationContext();
        for (final Intent intent : getPossibleFileOpenIntents(context, path)) {
            if (intent.resolveActivityInfo(context.getPackageManager(), 0) != null) {
                return Optional.of(
                        PendingIntent.getActivity(context, 189, intent, PENDING_INTENT_FLAGS));
            }
        }
        return Optional.absent();
    }

    private NotificationCompat.Builder getNotification() {
        final var context = getApplicationContext();
        final NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, "backup");
        notification
                .setContentTitle(context.getString(R.string.notification_create_backup_title))
                .setSmallIcon(R.drawable.ic_archive_24dp)
                .setProgress(1, 0, false);
        notification.setOngoing(true);
        notification.setLocalOnly(true);
        return notification;
    }

    private static class Progress {
        private final NotificationCompat.Builder builder;
        private final int max;
        private final int count;

        private Progress(NotificationCompat.Builder builder, int max, int count) {
            this.builder = builder;
            this.max = max;
            this.count = count;
        }

        private Notification build(int percentage) {
            builder.setProgress(max * 100, count * 100 + percentage, false);
            return builder.build();
        }
    }
}
