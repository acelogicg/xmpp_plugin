package org.xrstudio.xmpp.flutter_xmpp.managers;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.mam.MamManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.xrstudio.xmpp.flutter_xmpp.Connection.FlutterXmppConnection;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Utils;

import java.util.Date;
import java.util.List;

public class MAMManager {

    public static void requestMAM(String userJid, String requestBefore, String requestSince, String limit) {
        try {
            XMPPTCPConnection connection = FlutterXmppConnection.getConnection();
            if (connection == null || !connection.isAuthenticated()) return;

            MamManager mamManager = MamManager.getInstanceFor(connection);
            MamManager.MamQueryArgs.Builder queryArgs = MamManager.MamQueryArgs.builder();

            // before
            if (requestBefore != null && !requestBefore.isEmpty()) {
                long ts = Long.parseLong(requestBefore);
                if (ts > 0) queryArgs.limitResultsBefore(new Date(ts));
            }

            // since
            if (requestSince != null && !requestSince.isEmpty()) {
                long ts = Long.parseLong(requestSince);
                if (ts > 0) queryArgs.limitResultsSince(new Date(ts));
            }

            // limit / page size
            if (limit != null && !limit.isEmpty()) {
                int pageSize = Integer.parseInt(limit);
                if (pageSize > 0) queryArgs.setResultPageSizeTo(pageSize);
            }

            // ✅ withJid filter (optional)
            // - kalau kosong -> jangan set filter (ambil semua)
            // - kalau sama dengan jid sendiri -> jangan set filter
            userJid = Utils.getValidJid(userJid);

            BareJid myBare = null;
            try {
                if (connection.getUser() != null && connection.getUser().asBareJid() != null) {
                    myBare = connection.getUser().asBareJid();
                }
            } catch (Exception ignored) {}

            if (userJid != null && !userJid.trim().isEmpty()) {
                Jid jid = Utils.getFullJid(userJid);

                if (jid != null) {
                    BareJid withBare = jid.asBareJid();

                    boolean sameAsMe = (myBare != null && withBare != null && myBare.equals(withBare));

                    if (!sameAsMe) {
                        // ✅ Smack 4.4.x
                        queryArgs.limitResultsToJid(jid);
                        Utils.printLog("MAM: using withJid filter=" + jid.toString());
                    } else {
                        Utils.printLog("MAM: withJid == myBare, ignore filter (catchup all)");
                    }
                }
            } else {
                Utils.printLog("MAM: empty userJid, catchup all (no withJid filter)");
            }

            MamManager.MamQuery query = mamManager.queryArchive(queryArgs.build());
            List<Message> messageList = query.getMessages();

            for (Message message : messageList) {
                Utils.printLog("Received MAM message: " + message.toXML());
                Utils.broadcastMessageToFlutter(FlutterXmppConnection.getApplicationContext(), message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
