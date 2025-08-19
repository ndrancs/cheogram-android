package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.model.stanza.Presence;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(Account account, Presence.Availability status) {
        return selfPresence(account, status, true, null);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(
            final Account account, final Presence.Availability status, final boolean personal, final String nickname) {
        final var connection = account.getXmppConnection();
        if (connection == null) {
            return new Presence();
        }
        final var packet = connection.getManager(PresenceManager.class).getPresence(status, personal);
        if (nickname != null) {
            final var nick = packet.addChild("nick", "http://jabber.org/protocol/nick");
            nick.setContent(nickname);
        }
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence leave(final MucOptions mucOptions) {
        im.conversations.android.xmpp.model.stanza.Presence presence =
                new im.conversations.android.xmpp.model.stanza.Presence();
        presence.setTo(mucOptions.getSelf().getFullJid());
        presence.setFrom(mucOptions.getAccount().getJid());
        presence.setAttribute("type", "unavailable");
        return presence;
    }

    public im.conversations.android.xmpp.model.stanza.Presence sendOfflinePresence(
            Account account) {
        im.conversations.android.xmpp.model.stanza.Presence packet =
                new im.conversations.android.xmpp.model.stanza.Presence();
        packet.setFrom(account.getJid());
        packet.setAttribute("type", "unavailable");
        return packet;
    }
}
