package org.xrstudio.xmpp.flutter_xmpp.listner;

import android.content.Context;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Utils;

public class MessageListener implements StanzaListener {

    private static Context mApplicationContext;

    public MessageListener(Context context) {
        mApplicationContext = context;
    }

    @Override
    public void processStanza(Stanza packet) {
        try {
            // ✅ jangan cast buta
            if (!(packet instanceof Message)) return;

            Message message = (Message) packet;

            // ✅ 1) Skip MAM wrapper message:
            // <message to='me'><result xmlns='urn:xmpp:mam:2'>...</result></message>
            if (message.getExtension("result", "urn:xmpp:mam:2") != null) {
                return;
            }

            // ✅ 2) Null-safety (biar Utils gak NPE)
            if (message.getFrom() == null && message.getTo() == null) {
                return;
            }

            // ✅ 3) (opsional) kalau body null & bukan receipt, skip
            // biar gak spam event kosong
            // if (message.getBody() == null) return;

            Utils.broadcastMessageToFlutter(mApplicationContext, message);

        } catch (Exception e) {
            // biar gak matiin listener thread
            Utils.addLogInStorage("MessageListener error: " + e);
        }
    }
}
