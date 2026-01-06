// android/src/main/java/org/xrstudio/xmpp/flutter_xmpp/Connection/FlutterXmppConnection.java
package org.xrstudio.xmpp.flutter_xmpp.Connection;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StandardExtensionElement;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smack.util.TLSUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.muc.Affiliate;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jivesoftware.smackx.xdata.form.FillableForm;
import org.jivesoftware.smackx.xdata.form.Form;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.xrstudio.xmpp.flutter_xmpp.Enum.ConnectionState;
import org.xrstudio.xmpp.flutter_xmpp.Enum.ErrorState;
import org.xrstudio.xmpp.flutter_xmpp.Enum.GroupRole;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Constants;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Utils;
import org.xrstudio.xmpp.flutter_xmpp.listner.MessageListener;
import org.xrstudio.xmpp.flutter_xmpp.listner.PresenceListenerAndFilter;
import org.xrstudio.xmpp.flutter_xmpp.listner.StanzaAckListener;

import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class FlutterXmppConnection implements ConnectionListener {

    public static String mHost;
    public static String mUsername = "";
    private static String mPassword;
    private static String mResource = "";
    private static Roster rosterConnection;
    private static String mServiceName = "";
    private static XMPPTCPConnection mConnection;
    private static MultiUserChatManager multiUserChatManager;
    private static boolean mRequireSSLConnection, mAutoDeliveryReceipt,
            mAutomaticReconnection = true, mUseStreamManagement = true;
    private static Context mApplicationContext;

    // ✅ Receiver untuk kirim message dari Flutter -> Java
    private BroadcastReceiver uiThreadMessageReceiver;

    public FlutterXmppConnection(
            Context context,
            String jid_user,
            String password,
            String host,
            Integer port,
            boolean requireSSLConnection,
            boolean autoDeliveryReceipt,
            boolean useStreamManagement,
            boolean automaticReconnection
    ) {
        Utils.printLog(" Connection Constructor called: ");
        mApplicationContext = context.getApplicationContext();
        mPassword = password;
        Constants.PORT_NUMBER = port;
        mHost = host;
        mRequireSSLConnection = requireSSLConnection;
        mAutoDeliveryReceipt = autoDeliveryReceipt;
        mUseStreamManagement = useStreamManagement;
        mAutomaticReconnection = automaticReconnection;

        if (jid_user != null && jid_user.contains(Constants.SYMBOL_COMPARE_JID)) {
            String[] jid_list = jid_user.split(Constants.SYMBOL_COMPARE_JID);
            mUsername = jid_list[0];

            if (jid_list[1].contains(Constants.SYMBOL_FORWARD_SLASH)) {
                String[] domain_resource = jid_list[1].split(Constants.SYMBOL_FORWARD_SLASH);
                mServiceName = domain_resource[0];
                mResource = domain_resource[1];
            } else {
                mServiceName = jid_list[1];
                mResource = Constants.ANDROID;
            }
        }
    }

    public static Context getApplicationContext() {
        return mApplicationContext;
    }

    public static XMPPTCPConnection getConnection() {
        return mConnection;
    }

    public static boolean isAuthenticated() {
        try {
            return mConnection != null && mConnection.isAuthenticated();
        } catch (Throwable t) {
            return false;
        }
    }

    // =========================================================
    // ✅ FIX: METHOD YANG KAMU BUTUH (sebelumnya hilang -> compile error)
    // =========================================================
    private void setupUiThreadBroadCastMessageReceiver() {
        try {
            if (mApplicationContext == null) return;

            // kalau sebelumnya sudah register, unregister dulu (hindari double)
            if (uiThreadMessageReceiver != null) {
                try {
                    mApplicationContext.unregisterReceiver(uiThreadMessageReceiver);
                } catch (Exception ignored) {}
                uiThreadMessageReceiver = null;
            }

            uiThreadMessageReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null) return;

                    String action = intent.getAction();
                    Utils.printLog(" action: " + action);

                    if (Constants.X_SEND_MESSAGE.equals(action)
                            || Constants.GROUP_SEND_MESSAGE.equals(action)) {

                        sendMessage(
                                intent.getStringExtra(Constants.BUNDLE_MESSAGE_BODY),
                                intent.getStringExtra(Constants.BUNDLE_TO),
                                intent.getStringExtra(Constants.BUNDLE_MESSAGE_PARAMS),
                                Constants.X_SEND_MESSAGE.equals(action),
                                intent.getStringExtra(Constants.BUNDLE_MESSAGE_SENDER_TIME)
                        );
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Constants.X_SEND_MESSAGE);
            filter.addAction(Constants.READ_MESSAGE);
            filter.addAction(Constants.GROUP_SEND_MESSAGE);

            if (Build.VERSION.SDK_INT >= 33) {
                mApplicationContext.registerReceiver(uiThreadMessageReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                mApplicationContext.registerReceiver(uiThreadMessageReceiver, filter);
            }

            Utils.printLog(" setupUiThreadBroadCastMessageReceiver(): registered ✅");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // SEND (custom + receipt)
    // =========================================================
    public static void sendCustomMessage(String body, String toJid, String msgId, String customText, boolean isDm, String time) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;

            Message xmppMessage = new Message();
            xmppMessage.setStanzaId(msgId);
            xmppMessage.setBody(body);
            xmppMessage.setType(isDm ? Message.Type.chat : Message.Type.groupchat);

            if (mAutoDeliveryReceipt) DeliveryReceiptRequest.addTo(xmppMessage);

            StandardExtensionElement timeElement =
                    StandardExtensionElement.builder(Constants.TIME, Constants.URN_XMPP_TIME)
                            .addElement(Constants.TS, time)
                            .build();
            xmppMessage.addExtension(timeElement);

            StandardExtensionElement element =
                    StandardExtensionElement.builder(Constants.CUSTOM, Constants.URN_XMPP_CUSTOM)
                            .addElement(Constants.custom, customText)
                            .build();
            xmppMessage.addExtension(element);

            if (isDm) {
                xmppMessage.setTo(JidCreate.from(toJid));
                mConnection.sendStanza(xmppMessage);
            } else {
                EntityBareJid jid = JidCreate.entityBareFrom(toJid);
                xmppMessage.setTo(jid);

                EntityBareJid mucJid =
                        (EntityBareJid) JidCreate.bareFrom(Utils.getRoomIdWithDomainName(toJid, mHost));
                MultiUserChat muc = multiUserChatManager.getMultiUserChat(mucJid);
                muc.sendMessage(xmppMessage);
            }

            Utils.addLogInStorage("Action: sentCustomMessageToServer, Content: " + xmppMessage.toXML().toString());
            Utils.printLog(" Sent custom message from: " + xmppMessage.toXML() + "  sent.");

        } catch (SmackException.NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send_delivery_receipt(String toJid, String msgId, String receiptId) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;

            if (!toJid.contains(mHost)) toJid = toJid + Constants.SYMBOL_COMPARE_JID + mHost;

            Message deliveryMessage = new Message();
            deliveryMessage.setStanzaId(receiptId);
            deliveryMessage.setTo(JidCreate.from(toJid));

            DeliveryReceipt deliveryReceipt = new DeliveryReceipt(msgId);
            deliveryMessage.addExtension(deliveryReceipt);

            mConnection.sendStanza(deliveryMessage);
            Utils.addLogInStorage("Action: sentDeliveryReceiptToServer, Content: " + deliveryMessage.toXML().toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

<<<<<<< HEAD
    public static void sendReadReceipt(String toJid, String msgId) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;
            if (toJid == null || toJid.trim().isEmpty()) return;
            if (msgId == null || msgId.trim().isEmpty()) return;

            if (!toJid.contains(mHost)) toJid = toJid + Constants.SYMBOL_COMPARE_JID + mHost;

            Message readMessage = new Message();
            readMessage.setStanzaId(UUID.randomUUID().toString());
            readMessage.setTo(JidCreate.from(toJid));
            readMessage.setType(Message.Type.chat);

            StandardExtensionElement readElement =
                    StandardExtensionElement.builder(Constants.READ, Constants.URN_XMPP_READ)
                            .addAttribute(Constants.ID, msgId)
                            .build();
            readMessage.addExtension(readElement);

            mConnection.sendStanza(readMessage);
            Utils.addLogInStorage("Action: sentReadReceiptToServer, Content: " + readMessage.toXML().toString());
=======
    public static void sendMessageRetraction(String toJid, String targetId, String retractId) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;
            if (targetId == null || targetId.isEmpty()) return;

            if (!toJid.contains(mHost)) toJid = toJid + Constants.SYMBOL_COMPARE_JID + mHost;

            Message xmppMessage = new Message();
            xmppMessage.setType(Message.Type.chat);
            xmppMessage.setTo(JidCreate.from(toJid));
            xmppMessage.setStanzaId(retractId);

            StandardExtensionElement retractElement =
                    StandardExtensionElement.builder("apply-to", "urn:xmpp:message-retract:0")
                            .addAttribute("id", targetId)
                            .addElement("retract", "urn:xmpp:message-retract:0")
                            .build();

            xmppMessage.addExtension(retractElement);

            mConnection.sendStanza(xmppMessage);
            Utils.addLogInStorage("Action: sentMessageRetraction, Content: " + xmppMessage.toXML().toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void enablePush(String pushJid, String node, String appId) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;
            if (pushJid == null || pushJid.isEmpty()) return;

            final String pushJidFinal = pushJid;
            final String nodeFinal = node == null ? "" : node;
            final StandardExtensionElement form = StandardExtensionElement.builder("x", "jabber:x:data")
                    .addAttribute("type", "submit")
                    .addElement(StandardExtensionElement.builder("field", null)
                            .addAttribute("var", "FORM_TYPE")
                            .addAttribute("type", "hidden")
                            .addElement("value", "urn:xmpp:push:0")
                            .build())
                    .addElement(StandardExtensionElement.builder("field", null)
                            .addAttribute("var", "appid")
                            .addElement("value", appId == null ? "" : appId)
                            .build())
                    .build();

            IQ enableIq = new IQ("enable", "urn:xmpp:push:0") {
                @Override
                protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
                    xml.attribute("jid", pushJidFinal);
                    if (!nodeFinal.isEmpty()) {
                        xml.attribute("node", nodeFinal);
                    }
                    xml.rightAngleBracket();
                    xml.append(form.toXML());
                    return xml;
                }
            };
            enableIq.setType(IQ.Type.set);
            enableIq.setTo(JidCreate.from(pushJidFinal));
            mConnection.sendStanza(enableIq);
            Utils.addLogInStorage("Action: enablePush, Content: " + enableIq.toXML().toString());
>>>>>>> fd98fea6cc1be8c553fddbce6cf8179f03b8de41
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // Roster / Group helpers (tetap sama)
    // =========================================================
    public static void manageAddMembersInGroup(GroupRole groupRole, String groupName, ArrayList<String> membersJid) {
        try {
            if (multiUserChatManager == null) return;

            List<Jid> jidList = new ArrayList<>();
            MultiUserChat muc = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );

            for (String memberJid : membersJid) {
                if (!memberJid.contains(mHost)) memberJid = memberJid + Constants.SYMBOL_COMPARE_JID + mHost;
                Jid jid = JidCreate.from(memberJid);
                jidList.add(jid);
            }

            if (groupRole == GroupRole.ADMIN) {
                muc.grantAdmin(jidList);
            } else if (groupRole == GroupRole.MEMBER) {
                muc.grantMembership(jidList);
            }

            for (Jid jid : jidList) {
                muc.invite(jid.asEntityBareJidIfPossible(), Constants.INVITE_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void manageRemoveFromGroup(GroupRole groupRole, String groupName, ArrayList<String> membersJid) {
        try {
            if (multiUserChatManager == null) return;

            List<Jid> jidList = new ArrayList<>();
            for (String memberJid : membersJid) {
                if (!memberJid.contains(mHost)) memberJid = memberJid + Constants.SYMBOL_COMPARE_JID + mHost;
                Jid jid = JidCreate.from(memberJid);
                jidList.add(jid);
            }

            MultiUserChat muc = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );

            if (groupRole == GroupRole.ADMIN) {
                for (Jid jid : jidList) {
                    muc.revokeAdmin(jid.asEntityJidOrThrow());
                }
            } else if (groupRole == GroupRole.MEMBER) {
                muc.revokeMembership(jidList);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> getMembersOrAdminsOrOwners(GroupRole groupRole, String groupName) {
        List<String> jidList = new ArrayList<>();
        try {
            if (multiUserChatManager == null) return jidList;

            List<Affiliate> affiliates;
            MultiUserChat muc = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );

            if (groupRole == GroupRole.ADMIN) affiliates = muc.getAdmins();
            else if (groupRole == GroupRole.MEMBER) affiliates = muc.getMembers();
            else if (groupRole == GroupRole.OWNER) affiliates = muc.getOwners();
            else affiliates = new ArrayList<>();

            for (Affiliate affiliate : affiliates) {
                jidList.add(affiliate.getJid().toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return jidList;
    }

    public static int getOnlineMemberCount(String groupName) {
        try {
            if (multiUserChatManager == null) return 0;
            MultiUserChat muc = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );
            return muc.getOccupants().size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static long getLastSeen(String userJid) {
        long userLastActivity = Constants.RESULT_DEFAULT;
        try {
            if (mConnection == null) return userLastActivity;
            LastActivityManager lastActivityManager = LastActivityManager.getInstanceFor(mConnection);
            LastActivity lastActivity = lastActivityManager.getLastActivity(
                    JidCreate.from(Utils.getJidWithDomainName(userJid, mHost))
            );
            userLastActivity = lastActivity.lastActivity;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return userLastActivity;
    }

    public static List<String> getMyRosters() {
        List<String> muRosterList = new ArrayList<>();
        try {
            if (rosterConnection == null) return muRosterList;
            Set<RosterEntry> allRoster = rosterConnection.getEntries();
            for (RosterEntry rosterEntry : allRoster) {
                muRosterList.add(rosterEntry.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return muRosterList;
    }

    public static void createRosterEntry(String userJid) {
        try {
            if (rosterConnection == null) return;
            rosterConnection.createItemAndRequestSubscription(
                    JidCreate.bareFrom(Utils.getJidWithDomainName(userJid, mHost)),
                    userJid,
                    null
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean createMUC(String groupName, String persistent) {
        boolean isGroupCreatedSuccessfully = false;
        try {
            if (multiUserChatManager == null) return false;

            MultiUserChat multiUserChat = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );

            multiUserChat.create(Resourcepart.from(mUsername));

            if (persistent.equals(Constants.TRUE)) {
                Form form = multiUserChat.getConfigurationForm();
                FillableForm answerForm = form.getFillableForm();
                answerForm.setAnswer(Constants.MUC_PERSISTENT_ROOM, true);
                answerForm.setAnswer(Constants.MUC_MEMBER_ONLY, true);
                multiUserChat.sendConfigurationForm(answerForm);
            }

            isGroupCreatedSuccessfully = true;
        } catch (Exception e) {
            e.printStackTrace();
            String groupCreateError = e.getLocalizedMessage();
            Utils.printLog(" createMUC : exception: " + groupCreateError);
            Utils.broadcastErrorMessageToFlutter(mApplicationContext, ErrorState.GROUP_CREATION_FAILED, groupCreateError, groupName);
        }
        return isGroupCreatedSuccessfully;
    }

    public static String joinAllGroups(ArrayList<String> allGroupsIds) {
        String response = "FAIL";
        for (String groupId : allGroupsIds) {
            try {
                if (multiUserChatManager == null) continue;

                String groupName = groupId;
                String lastMsgTime = "0";
                if (groupName.contains(",")) {
                    String[] groupData = groupName.split(",");
                    groupName = groupData[0];
                    lastMsgTime = groupData[1];
                }

                MultiUserChat multiUserChat = multiUserChatManager.getMultiUserChat(
                        (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
                );

                Resourcepart resourcepart = Resourcepart.from(mUsername);
                long currentTime = new Date().getTime();
                long lastMessageTime = Long.parseLong(lastMsgTime);
                long diff = currentTime - lastMessageTime;

                MucEnterConfiguration mucEnterConfiguration =
                        multiUserChat.getEnterConfigurationBuilder(resourcepart)
                                .requestHistorySince((int) diff)
                                .build();

                if (!multiUserChat.isJoined()) multiUserChat.join(mucEnterConfiguration);

            } catch (Exception e) {
                e.printStackTrace();
                String allGroupJoinError = e.getLocalizedMessage();
                Utils.printLog(" joinAllGroup : exception: " + allGroupJoinError);
                Utils.broadcastErrorMessageToFlutter(mApplicationContext, ErrorState.GROUP_JOINED_FAILED, allGroupJoinError, groupId);
            }
        }
        response = Constants.SUCCESS;
        return response;
    }

    public static boolean joinGroupWithResponse(String groupId) {
        boolean isJoinedSuccessfully = false;
        try {
            if (multiUserChatManager == null) return false;

            String groupName = groupId;
            String lastMsgTime = "0";
            if (groupName.contains(",")) {
                String[] groupData = groupName.split(",");
                groupName = groupData[0];
                lastMsgTime = groupData[1];
            }

            MultiUserChat multiUserChat = multiUserChatManager.getMultiUserChat(
                    (EntityBareJid) JidCreate.from(Utils.getRoomIdWithDomainName(groupName, mHost))
            );

            Resourcepart resourcepart = Resourcepart.from(mUsername);
            long currentTime = new Date().getTime();
            long lastMessageTime = Long.parseLong(lastMsgTime);
            long diff = currentTime - lastMessageTime;

            MucEnterConfiguration mucEnterConfiguration =
                    multiUserChat.getEnterConfigurationBuilder(resourcepart)
                            .requestHistorySince((int) diff)
                            .build();

            if (!multiUserChat.isJoined()) multiUserChat.join(mucEnterConfiguration);

            isJoinedSuccessfully = true;
        } catch (Exception e) {
            String groupJoinError = e.getLocalizedMessage();
            Utils.printLog(" joinGroup : exception: " + groupJoinError);
            Utils.broadcastErrorMessageToFlutter(mApplicationContext, ErrorState.GROUP_JOINED_FAILED, groupJoinError, groupId);
            e.printStackTrace();
        }
        return isJoinedSuccessfully;
    }

    public static void updateChatState(String jid, String status) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;
            Jid toJid = Utils.getFullJid(jid);
            Message message = new Message(toJid);
            ChatState chatState = ChatState.valueOf(status);
            message.addExtension(new ChatStateExtension(chatState));
            Utils.printLog("Sending Typing status " + message.toXML());
            mConnection.sendStanza(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updatePresence(String presenceType, String presenceMode) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;
            Presence.Type type = Presence.Type.valueOf(presenceType);
            Presence.Mode mode = Presence.Mode.valueOf(presenceMode);
            Presence presence = new Presence(type);
            presence.setMode(mode);
            mConnection.sendStanza(presence);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // ✅ CONNECT (sesuai request kamu)
    // =========================================================
    public void connect() throws IOException, XMPPException, SmackException {
        FlutterXmppConnectionService.sConnectionState = ConnectionState.CONNECTING;

        XMPPTCPConnectionConfiguration.Builder conf = XMPPTCPConnectionConfiguration.builder();
        conf.setXmppDomain(mServiceName);

        // ✅ jangan auto-presence saat login
        conf.setSendPresence(false);

        if (Utils.validIP(mHost)) {
            Utils.printLog(" connecting via ip: " + Utils.validIP(mHost));
            InetAddress address = InetAddress.getByName(mHost);
            conf.setHostAddress(address);
            conf.setHost(mHost);
        } else {
            Utils.printLog(" not valid host: ");
            conf.setHost(mHost);
        }

        if (Constants.PORT_NUMBER != 0) conf.setPort(Constants.PORT_NUMBER);

        conf.setUsernameAndPassword(mUsername, mPassword);
        conf.setResource(mResource);
        conf.setCompressionEnabled(true);
        conf.enableDefaultDebugger();

        if (mRequireSSLConnection) {
            SSLContext context = null;
            try {
                context = SSLContext.getInstance(Constants.TLS);
                context.init(null, new TrustManager[]{new TLSUtils.AcceptAllTrustManager()}, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
            }
            conf.setCustomSSLContext(context);
            conf.setKeystoreType(null);
            conf.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
        } else {
            conf.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        }

        Utils.printLog(" connect 1 mServiceName: " + mServiceName +
                " mHost: " + mHost +
                " mPort: " + Constants.PORT +
                " mUsername: " + mUsername +
                " mPassword: " + mPassword +
                " mResource:" + mResource);

        try {
            mConnection = new XMPPTCPConnection(conf.build());
            mConnection.addConnectionListener(this);

            // ✅ REGISTER receiver & listeners sebelum connect/login
            setupUiThreadBroadCastMessageReceiver();

            rosterConnection = Roster.getInstanceFor(mConnection);
            rosterConnection.setSubscriptionMode(Roster.SubscriptionMode.accept_all);

            mConnection.addSyncStanzaListener(new PresenceListenerAndFilter(mApplicationContext), StanzaTypeFilter.PRESENCE);
            mConnection.addStanzaAcknowledgedListener(new StanzaAckListener(mApplicationContext));
            mConnection.addSyncStanzaListener(new MessageListener(mApplicationContext), StanzaTypeFilter.MESSAGE);

            if (mUseStreamManagement) {
                mConnection.setUseStreamManagement(true);
                mConnection.setUseStreamManagementResumption(true);
            }

            if (mAutomaticReconnection) {
                ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(mConnection);
                ReconnectionManager.setEnabledPerDefault(true);
                reconnectionManager.enableAutomaticReconnection();
            }

            Utils.printLog(" Calling connect(): ");
            mConnection.connect();

            // ✅ LOGIN
            mConnection.login();

            // ✅ KIRIM PRESENCE setelah login (trigger offline delivery)
            Presence p = new Presence(Presence.Type.available);
            p.setMode(Presence.Mode.chat);
            mConnection.sendStanza(p);

            Utils.printLog(" Initial presence sent after login (available/chat)");

        } catch (InterruptedException e) {
            FlutterXmppConnectionService.sConnectionState = ConnectionState.FAILED;
            e.printStackTrace();
        } catch (SmackException.NotConnectedException e) {
            FlutterXmppConnectionService.sConnectionState = ConnectionState.FAILED;
            e.printStackTrace();
        }
    }

    private void sendMessage(String body, String toJid, String msgId, boolean isDm, String time) {
        try {
            if (mConnection == null || !mConnection.isAuthenticated()) return;

            Message xmppMessage = new Message();
            xmppMessage.setStanzaId(msgId);
            xmppMessage.setBody(body);
            xmppMessage.setType(isDm ? Message.Type.chat : Message.Type.groupchat);

            StandardExtensionElement timeElement =
                    StandardExtensionElement.builder(Constants.TIME, Constants.URN_XMPP_TIME)
                            .addElement(Constants.TS, time)
                            .build();
            xmppMessage.addExtension(timeElement);

            if (mAutoDeliveryReceipt) DeliveryReceiptRequest.addTo(xmppMessage);

            if (isDm) {
                xmppMessage.setTo(JidCreate.from(toJid));
                mConnection.sendStanza(xmppMessage);
            } else {
                EntityBareJid jid = JidCreate.entityBareFrom(toJid);
                xmppMessage.setTo(jid);

                EntityBareJid mucJid =
                        (EntityBareJid) JidCreate.bareFrom(Utils.getRoomIdWithDomainName(toJid, mHost));
                MultiUserChat muc = multiUserChatManager.getMultiUserChat(mucJid);
                muc.sendMessage(body);
            }

            Utils.addLogInStorage("Action: sentMessageToServer, Content: " + xmppMessage.toXML().toString());
            Utils.printLog(" Sent message from: " + xmppMessage.toXML() + "  sent.");

        } catch (SmackException.NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        Utils.printLog(" Disconnecting from server: " + mServiceName);

        if (mConnection != null) {
            try {
                mConnection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mConnection = null;
        }

        if (uiThreadMessageReceiver != null) {
            try {
                mApplicationContext.unregisterReceiver(uiThreadMessageReceiver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            uiThreadMessageReceiver = null;
        }

        rosterConnection = null;
        multiUserChatManager = null;
    }

    @Override
    public void connected(XMPPConnection connection) {
        Utils.printLog(" Connected Successfully: ");
        FlutterXmppConnectionService.sConnectionState = ConnectionState.CONNECTED;
        Utils.broadcastConnectionMessageToFlutter(mApplicationContext, ConnectionState.CONNECTED, "");

        Intent intent = new Intent(Constants.RECEIVE_MESSAGE);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(Constants.BUNDLE_FROM_JID, mUsername);
        intent.putExtra(Constants.BUNDLE_MESSAGE_BODY, Constants.CONNECTED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_PARAMS, Constants.CONNECTED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_TYPE, Constants.CONNECTED);
        mApplicationContext.sendBroadcast(intent);
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        Utils.printLog(" Flutter Authenticated Successfully: ");
        multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

        FlutterXmppConnectionService.sConnectionState = ConnectionState.AUTHENTICATED;
        Utils.broadcastConnectionMessageToFlutter(mApplicationContext, ConnectionState.AUTHENTICATED, "");

        Intent intent = new Intent(Constants.RECEIVE_MESSAGE);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(Constants.BUNDLE_FROM_JID, mUsername);
        intent.putExtra(Constants.BUNDLE_MESSAGE_BODY, Constants.AUTHENTICATED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_PARAMS, Constants.AUTHENTICATED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_TYPE, Constants.AUTHENTICATED);
        mApplicationContext.sendBroadcast(intent);
    }

    @Override
    public void connectionClosed() {
        Utils.printLog(" ConnectionClosed(): ");
        if (FlutterXmppConnectionService.sConnectionState == ConnectionState.FAILED) {
            connectionCloseMessageToFlutter(ConnectionState.FAILED, Constants.FAILED);
        } else {
            FlutterXmppConnectionService.sConnectionState = ConnectionState.DISCONNECTED;
            connectionCloseMessageToFlutter(ConnectionState.DISCONNECTED, Constants.DISCONNECTED);
        }
    }

    void connectionCloseMessageToFlutter(ConnectionState connectionState, String connection) {
        if (connectionState != ConnectionState.FAILED) {
            Utils.broadcastConnectionMessageToFlutter(mApplicationContext, connectionState, "");
        }

        Intent intent = new Intent(Constants.RECEIVE_MESSAGE);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(Constants.BUNDLE_FROM_JID, connection);
        intent.putExtra(Constants.BUNDLE_MESSAGE_BODY, connection);
        intent.putExtra(Constants.BUNDLE_MESSAGE_PARAMS, connection);
        intent.putExtra(Constants.BUNDLE_MESSAGE_TYPE, connection);
        mApplicationContext.sendBroadcast(intent);
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        Utils.printLog(" ConnectionClosedOnError, error:  " + e.toString());
        FlutterXmppConnectionService.sConnectionState = ConnectionState.FAILED;

        Utils.broadcastConnectionMessageToFlutter(mApplicationContext, ConnectionState.FAILED, e.getLocalizedMessage());

        Intent intent = new Intent(Constants.RECEIVE_MESSAGE);
        intent.setPackage(mApplicationContext.getPackageName());
        intent.putExtra(Constants.BUNDLE_FROM_JID, Constants.DISCONNECTED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_BODY, Constants.DISCONNECTED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_PARAMS, Constants.DISCONNECTED);
        intent.putExtra(Constants.BUNDLE_MESSAGE_TYPE, Constants.DISCONNECTED);
        mApplicationContext.sendBroadcast(intent);
    }
}
