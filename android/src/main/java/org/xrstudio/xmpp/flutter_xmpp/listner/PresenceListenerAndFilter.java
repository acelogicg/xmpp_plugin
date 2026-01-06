package org.xrstudio.xmpp.flutter_xmpp.listner;

import android.content.Context;
import android.content.Intent;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.xrstudio.xmpp.flutter_xmpp.Enum.SuccessState;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Constants;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Utils;

public class PresenceListenerAndFilter implements StanzaListener {

    private static Context mApplicationContext;

    public PresenceListenerAndFilter(Context context) {
        mApplicationContext = context;
    }

    @Override
    public void processStanza(Stanza packet) {
        try {
            if (!(packet instanceof Presence)) return;

            Presence presence = (Presence) packet;

            String jid = "";
            if (presence.getFrom() != null) jid = presence.getFrom().toString();

            Presence.Type type = presence.getType(); // biasanya gak null
            Presence.Mode mode = presence.getMode(); // bisa null

            if (presence.hasExtension(MUCUser.ELEMENT, MUCUser.NAMESPACE)) {
                MUCUser mucUser = MUCUser.from(presence);

                if (mucUser != null) {
                    if (mucUser.getStatus().contains(MUCUser.Status.ROOM_CREATED_201)) {
                        Utils.broadcastSuccessMessageToFlutter(
                                mApplicationContext,
                                SuccessState.GROUP_CREATED_SUCCESS,
                                jid
                        );
                        return;
                    } else if (mucUser.getStatus().contains(MUCUser.Status.PRESENCE_TO_SELF_110)) {
                        Utils.broadcastSuccessMessageToFlutter(
                                mApplicationContext,
                                SuccessState.GROUP_JOINED_SUCCESS,
                                jid
                        );
                        return;
                    }
                }
            }

            Intent intent = new Intent(Constants.PRESENCE_MESSAGE);
            intent.setPackage(mApplicationContext.getPackageName());
            intent.putExtra(Constants.BUNDLE_FROM_JID, jid);
            intent.putExtra(Constants.BUNDLE_PRESENCE_TYPE,
                    (type != null ? type.toString().toLowerCase() : "available"));
            intent.putExtra(Constants.BUNDLE_PRESENCE_MODE,
                    (mode != null ? mode.toString().toLowerCase() : ""));

            mApplicationContext.sendBroadcast(intent);

        } catch (Exception e) {
            Utils.addLogInStorage("PresenceListener error: " + e);
        }
    }
}
