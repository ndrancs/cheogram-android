package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.services.XmppConnectionService;

public abstract class AbstractManager extends XmppConnection.Delegate {

    protected AbstractManager(final XmppConnectionService context, final XmppConnection connection) {
        super(context, connection);
    }
}
