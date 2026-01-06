package org.xrstudio.xmpp.flutter_xmpp.Connection;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.xrstudio.xmpp.flutter_xmpp.Enum.ConnectionState;
import org.xrstudio.xmpp.flutter_xmpp.Enum.LoggedInState;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Constants;
import org.xrstudio.xmpp.flutter_xmpp.Utils.Utils;

import java.io.IOException;

public class FlutterXmppConnectionService extends Service {

    public static LoggedInState sLoggedInState;
    public static ConnectionState sConnectionState;

    private Integer port;
    private Thread mThread;
    private volatile boolean mActive;
    private String host = "";
    private Handler mTHandler;
    private String jid_user = "";
    private String password = "";
    private boolean requireSSLConnection = false, autoDeliveryReceipt = false, useStreamManagement = true, automaticReconnection = true;

    private FlutterXmppConnection mConnection;

    private final Object lock = new Object();

    public FlutterXmppConnectionService() {}

    public static ConnectionState getState() {
        if (sConnectionState == null) return ConnectionState.DISCONNECTED;
        return sConnectionState;
    }

    public static LoggedInState getLoggedInState() {
        if (sLoggedInState == null) return LoggedInState.LOGGED_OUT;
        return sLoggedInState;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.printLog(" onCreate(): ");
        if (sLoggedInState == null) sLoggedInState = LoggedInState.LOGGED_OUT;
        if (sConnectionState == null) sConnectionState = ConnectionState.DISCONNECTED;
    }

    private void initConnectionSafe() {
        synchronized (lock) {
            try {
                Utils.printLog(" initConnection(): jid=" + jid_user + " host=" + host + " port=" + port);

                // kalau service sudah punya connection & jid sama & masih aktif → skip
                if (mConnection != null
                        && jid_user != null
                        && jid_user.equalsIgnoreCase(FlutterXmppConnection.mUsername + "@" + FlutterXmppConnection.mHost)
                        && (sConnectionState == ConnectionState.CONNECTING
                        || sConnectionState == ConnectionState.CONNECTED
                        || sConnectionState == ConnectionState.AUTHENTICATED)) {
                    Utils.printLog(" initConnection(): already running, skip");
                    return;
                }

                if (mConnection == null) {
                    mConnection = new FlutterXmppConnection(
                            this,
                            this.jid_user,
                            this.password,
                            this.host,
                            this.port,
                            requireSSLConnection,
                            autoDeliveryReceipt,
                            useStreamManagement,
                            automaticReconnection
                    );
                }

                mConnection.connect();
                sLoggedInState = LoggedInState.LOGGED_IN;

            } catch (IOException | SmackException | XMPPException e) {
                FlutterXmppConnectionService.sConnectionState = ConnectionState.FAILED;
                sLoggedInState = LoggedInState.LOGGED_OUT;

                Utils.broadcastConnectionMessageToFlutter(
                        this,
                        ConnectionState.FAILED,
                        "Something went wrong while connecting, make sure the credentials are right and try again."
                );

                Utils.printLog(" Connection FAILED: " + e.getMessage());
                e.printStackTrace();

                stopSelf();
            }
        }
    }

    public void start() {
        synchronized (lock) {
            Utils.printLog(" Service start() called");

            if (mActive) {
                Utils.printLog(" Service already active, skip start()");
                return;
            }

            mActive = true;

            if (mThread == null || !mThread.isAlive()) {
                mThread = new Thread(() -> {
                    Looper.prepare();
                    mTHandler = new Handler(Looper.myLooper());
                    initConnectionSafe();
                    Looper.loop();
                }, "flutter_xmpp_service_thread");
                mThread.start();
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            Utils.printLog(" stop() called");
            mActive = false;

            final Handler h = mTHandler;
            if (h != null) {
                h.post(() -> {
                    try {
                        if (mConnection != null) {
                            try {
                                mConnection.disconnect();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            mConnection = null;
                        }

                        sLoggedInState = LoggedInState.LOGGED_OUT;
                        sConnectionState = ConnectionState.DISCONNECTED;

                        try {
                            Looper looper = h.getLooper();
                            if (looper != null) looper.quitSafely();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            } else {
                sLoggedInState = LoggedInState.LOGGED_OUT;
                sConnectionState = ConnectionState.DISCONNECTED;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.printLog(" onStartCommand(): ");

        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras == null) {
            Utils.printLog(" Missing User JID/Password/Host/Port");
            return Service.START_STICKY;
        }

        final String newJid = extras.getString(Constants.JID_USER, "");
        final String newHost = extras.getString(Constants.HOST, "");
        final int newPort = extras.getInt(Constants.PORT, 5222);

        // kalau sudah aktif dan startCommand datang lagi dengan config sama → skip
        if (mActive
                && newJid.equalsIgnoreCase(this.jid_user)
                && newHost.equalsIgnoreCase(this.host)
                && newPort == (this.port != null ? this.port : 5222)) {
            Utils.printLog(" onStartCommand(): duplicate start (same params) -> skip");
            return Service.START_STICKY;
        }

        // kalau ada start baru beda jid/config, stop dulu biar nggak numpuk koneksi
        if (mActive) {
            Utils.printLog(" onStartCommand(): new params detected -> stopping previous connection first");
            stop();
        }

        this.jid_user = newJid;
        this.password = extras.getString(Constants.PASSWORD, "");
        this.host = newHost;
        this.port = newPort;
        this.requireSSLConnection = extras.getBoolean(Constants.REQUIRE_SSL_CONNECTION, false);
        this.autoDeliveryReceipt = extras.getBoolean(Constants.AUTO_DELIVERY_RECEIPT, false);
        this.useStreamManagement = extras.getBoolean(Constants.USER_STREAM_MANAGEMENT, true);
        this.automaticReconnection = extras.getBoolean(Constants.AUTOMATIC_RECONNECTION, true);

        start();
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        Utils.printLog(" onDestroy(): ");
        stop();
        super.onDestroy();
    }
}
