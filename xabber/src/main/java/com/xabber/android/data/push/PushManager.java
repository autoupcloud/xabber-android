package com.xabber.android.data.push;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.xabber.android.data.Application;
import com.xabber.android.data.account.AccountItem;
import com.xabber.android.data.account.AccountManager;
import com.xabber.android.data.connection.ConnectionItem;
import com.xabber.android.data.connection.listeners.OnConnectedListener;
import com.xabber.android.data.entity.AccountJid;
import com.xabber.android.data.http.PushApiClient;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.push_notifications.PushNotificationsManager;

import java.util.Collection;

import okhttp3.ResponseBody;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class PushManager implements OnConnectedListener {

    private static final String LOG_TAG = PushManager.class.getSimpleName();

    private static PushManager instance;

    public static PushManager getInstance() {
        if (instance == null)
            instance = new PushManager();
        return instance;
    }

    private CompositeSubscription compositeSubscription = new CompositeSubscription();

    @Override
    public void onConnected(final ConnectionItem connection) {
        Application.getInstance().runInBackground(new Runnable() {
            @Override
            public void run() {
                AccountJid accountJid = connection.getAccount();

                // TODO: 02.04.19 check push support

                registerEndpoint(accountJid);
            }
        });
    }

    public void onEndpointRegistered(String jid, String node) {
        AccountJid accountJid = null;
        Collection<AccountJid> accounts = AccountManager.getInstance().getEnabledAccounts();
        for (AccountJid account : accounts) {
            if (account.getFullJid().asBareJid().equals(jid)) {
                accountJid = account;
                break;
            }
        }

        if (accountJid != null) {
            AccountItem account = AccountManager.getInstance().getAccount(accountJid);
            if (account != null) {

                // save node to account
                AccountManager.getInstance().setPushNode(account, node);

                // enable push on XMPP-server
                sendEnablePushIQ(account, accountJid, node);
            }
        }
    }

    public void onNewMessagePush(String node) {

    }

    public void registerEndpoint(AccountJid accountJid) {
        compositeSubscription.add(
            PushApiClient.registerEndpoint(
                    FirebaseInstanceId.getInstance().getToken(), accountJid.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<ResponseBody>() {
                    @Override
                    public void call(ResponseBody responseBody) {
                        Log.d(LOG_TAG, "Endpoint successfully registered");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.d(LOG_TAG, "Endpoint register failed: " + throwable.toString());
                    }
                }));
    }

    public void deleteEndpoint(AccountJid accountJid) {
        compositeSubscription.add(
            PushApiClient.deleteEndpoint(
                    FirebaseInstanceId.getInstance().getToken(), accountJid.toString())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<ResponseBody>() {
                    @Override
                    public void call(ResponseBody responseBody) {
                        Log.d(LOG_TAG, "Endpoint successfully unregistered");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.d(LOG_TAG, "Endpoint unregister failed: " + throwable.toString());
                    }
                }));
    }

    private void sendEnablePushIQ(final AccountItem accountItem, final AccountJid jid, final String node) {
        Application.getInstance().runInBackground(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    PushNotificationsManager.getInstanceFor(accountItem.getConnection())
                            .enable(jid.getFullJid().asBareJid(), node);
                } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                        | SmackException.NotConnectedException | InterruptedException e) {
                    Log.d(LOG_TAG, "Push notification enabling failed: " + e.toString());
                }
            }
        });
    }

}