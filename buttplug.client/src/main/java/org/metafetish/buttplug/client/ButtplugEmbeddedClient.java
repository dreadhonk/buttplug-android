package org.metafetish.buttplug.client;

import com.google.common.util.concurrent.SettableFuture;

import org.metafetish.buttplug.core.ButtplugConsts;
import org.metafetish.buttplug.core.ButtplugEvent;
import org.metafetish.buttplug.core.ButtplugMessage;
import org.metafetish.buttplug.core.IButtplugCallback;
import org.metafetish.buttplug.core.Messages.Error;
import org.metafetish.buttplug.server.ButtplugServer;
import org.metafetish.buttplug.server.IButtplugServerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ButtplugEmbeddedClient extends ButtplugClient {
    private IButtplugServerFactory serverFactory;
    private ButtplugServer server;

    public ButtplugEmbeddedClient(String clientName, IButtplugServerFactory serverFactory) {
        super(clientName);
        this.serverFactory = serverFactory;
    }


    public void connect() throws Exception {
        super.connect();
        this.server = this.serverFactory.getServer();
        this.server.getMessageReceived().addCallback(this.messageReceivedCallback);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ButtplugEmbeddedClient.this.onConnect();
                } catch (Exception e) {
                    ButtplugEmbeddedClient.this.bpLogger.logException(e);
                }
            }
        }).start();
    }

    @Override
    public void disconnect() {
        this.server = null;
        super.disconnect();
    }

    @Override
    protected Future<ButtplugMessage> sendMessage(final ButtplugMessage msg) {
        final SettableFuture<ButtplugMessage> promise = SettableFuture.create();
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                ButtplugEmbeddedClient.this.waitingMsgs.put(msg.id, promise);
                if (ButtplugEmbeddedClient.this.server == null) {
                    promise.set(new Error("Bad server state!", Error.ErrorClass.ERROR_UNKNOWN,
                            ButtplugConsts.SystemMsgId));
                }
                try {
                    promise.set(ButtplugEmbeddedClient.this.server.sendMessage(msg).get());
                } catch (InterruptedException | ExecutionException e) {
                    ButtplugEmbeddedClient.this.bpLogger.debug("exception sending message");
                    promise.set(new Error(e.getMessage(), Error.ErrorClass.ERROR_UNKNOWN, msg.id));
                }
            }
        });
        return promise;
    }

    private IButtplugCallback messageReceivedCallback = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
            ButtplugMessage msg = event.getMessage();
            if (msg == null) {
                return;
            }
            ButtplugEmbeddedClient.this.onMessage(msg);
        }
    };
}
