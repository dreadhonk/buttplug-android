package org.metafetish.buttplug.components.websocketserver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.util.Pair;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import org.metafetish.buttplug.core.ButtplugConsts;
import org.metafetish.buttplug.core.ButtplugEvent;
import org.metafetish.buttplug.core.ButtplugEventHandler;
import org.metafetish.buttplug.core.ButtplugJsonMessageParser;
import org.metafetish.buttplug.core.ButtplugLogManager;
import org.metafetish.buttplug.core.ButtplugMessage;
import org.metafetish.buttplug.core.Events.Connection;
import org.metafetish.buttplug.core.IButtplugCallback;
import org.metafetish.buttplug.core.IButtplugLog;
import org.metafetish.buttplug.core.IButtplugLogManager;
import org.metafetish.buttplug.core.Messages.Error;
import org.metafetish.buttplug.core.Messages.RequestServerInfo;
import org.metafetish.buttplug.server.ButtplugServer;
import org.metafetish.buttplug.server.IButtplugServerFactory;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class ButtplugWebsocketServer {
    @NonNull
    private WebSocketServer wsServer;

    @NonNull
    private IButtplugServerFactory serverFactory;

    @NonNull
    private IButtplugLogManager bpLogManager = new ButtplugLogManager();

    @NonNull
    private IButtplugLog bpLogger = this.bpLogManager.getLogger(this.getClass().getSimpleName());

    private ButtplugEventHandler onException = new ButtplugEventHandler();

    @NonNull
    public ButtplugEventHandler getOnException() {
        return this.onException;
    }

    public ButtplugEventHandler connectionAccepted = new ButtplugEventHandler();

    @NonNull
    public ButtplugEventHandler getConnectionAccepted() {
        return this.connectionAccepted;
    }

    public ButtplugEventHandler connectionUpdated = new ButtplugEventHandler();

    @NonNull
    public ButtplugEventHandler getConnectionUpdated() {
        return this.connectionUpdated;
    }

    public ButtplugEventHandler connectionClosed = new ButtplugEventHandler();

    @NonNull
    public ButtplugEventHandler getConnectionClosed() {
        return this.connectionClosed;
    }

    //TODO: Does this need to be a map, given we only allow the one?
    @NonNull
    private ConcurrentHashMap<String, Pair<WebSocket, ButtplugServer>> connections = new
            ConcurrentHashMap<>();

    private boolean connected = false;

    public boolean isConnected() {
        return this.connected;
    }

    private Context context;

    public ButtplugWebsocketServer(Context context) {
        this.context = context;
    }

    public void startServer(@NonNull IButtplugServerFactory factory) throws ExecutionException,
            InterruptedException {
        startServer(factory, true, 12345, null);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, boolean loopback) throws
            ExecutionException, InterruptedException {
        startServer(factory, loopback, 12345, null);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, int port) throws
            ExecutionException, InterruptedException {
        startServer(factory, true, port, null);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, Map<String, String>
            secureHostPairs)
            throws ExecutionException, InterruptedException {
        startServer(factory, true, 12345, secureHostPairs);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, boolean loopback, int port)
            throws ExecutionException, InterruptedException {
        startServer(factory, loopback, port, null);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, boolean loopback,
                            Map<String, String> secureHostPairs) throws ExecutionException,
            InterruptedException {
        startServer(factory, loopback, 12345, secureHostPairs);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, int port, Map<String,
            String> secureHostPairs) throws ExecutionException, InterruptedException {
        startServer(factory, true, port, secureHostPairs);
    }

    public void startServer(@NonNull IButtplugServerFactory factory, boolean loopback, int port,
                            Map<String, String> secureHostPairs) throws ExecutionException,
            InterruptedException {
        this.serverFactory = factory;

        InetSocketAddress address;
        if (loopback) {
            address = new LoopbackTask().execute(port).get();
        } else {
            address = new InetSocketAddress(port);
        }
        this.bpLogger.trace(address.toString());

        this.wsServer = new WebSocketServer(address) {
            @Override
            public void onOpen(WebSocket ws, ClientHandshake handshake) {
                ButtplugWebsocketServer.this.bpLogger.trace("onOpen()");
                if (!ButtplugWebsocketServer.this.connections.isEmpty()) {
                    try {
                        ws.send((new ButtplugJsonMessageParser()).serialize
                                (ButtplugWebsocketServer.this.bpLogger.logErrorMsg(
                                        ButtplugConsts.SystemMsgId, Error.ErrorClass.ERROR_INIT,
                                        "WebSocketServer already in use!"

                                ), 0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ws.close();
                }
                String remoteId = ws.getRemoteSocketAddress().getHostName();
                ButtplugWebsocketServer.this.connectionAccepted.invoke(new ButtplugEvent(remoteId));
                ButtplugServer buttplug = ButtplugWebsocketServer.this.serverFactory.getServer();
                ButtplugWebsocketServer.this.connections.put(remoteId, new Pair<>(ws, buttplug));

                buttplug.getMessageReceived().addCallback(ButtplugWebsocketServer.this
                        .messageReceivedCallback);

                buttplug.getClientConnected().addCallback(new IButtplugCallback() {
                    @Override
                    public void invoke(ButtplugEvent event) {
                        ButtplugMessage msg = event.getMessage();
                        if (msg != null && msg instanceof RequestServerInfo) {
                            String remoteId = ButtplugWebsocketServer.this.connections.keys()
                                    .nextElement();
                            String clientName = ((RequestServerInfo) msg).clientName;
                            ButtplugWebsocketServer.this.connectionUpdated.invoke(new Connection(
                                    remoteId,
                                    clientName
                            ));
                        }
                    }
                });
            }

            @Override
            public void onClose(WebSocket ws, int code, String reason, boolean remote) {
                String remoteId = ButtplugWebsocketServer.this.connections.keys().nextElement();
                ButtplugServer buttplug = ButtplugWebsocketServer.this.connections.get(remoteId).second;
                buttplug.getMessageReceived().removeCallback(ButtplugWebsocketServer.this.messageReceivedCallback);

                try {
                    //TODO: Figure out why this sometimes dies on disconnect
                    buttplug.shutdown().get();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
                ButtplugWebsocketServer.this.connections.remove(remoteId);
                ButtplugWebsocketServer.this.connectionClosed.invoke(new ButtplugEvent(remoteId));
            }

            @Override
            public void onMessage(WebSocket ws, String msg) {
                if (msg != null) {

                    String remoteId = ButtplugWebsocketServer.this.connections.keys()
                            .nextElement();
                    ButtplugServer buttplug = ButtplugWebsocketServer.this.connections.get
                            (remoteId).second;
                    List<ButtplugMessage> respMsgs;
                    try {
                        respMsgs = buttplug.sendMessage(msg).get();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
                    String respJson = null;
                    try {
                        ButtplugWebsocketServer.this.bpLogger.trace("Preparing to format");
                        respJson = buttplug.serialize(respMsgs);
                        ButtplugWebsocketServer.this.bpLogger.trace(respJson);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (respJson == null) {
                        ButtplugWebsocketServer.this.bpLogger.trace("null");
                        return;
                    }

                    ws.send(respJson);
                    ButtplugWebsocketServer.this.bpLogger.trace("Sent");


                    for (ButtplugMessage respMsg : respMsgs) {
                        if (respMsg instanceof Error && ((Error) respMsg).errorCode == Error
                                .ErrorClass
                                .ERROR_PING) {
                            ws.close();
                            break;
                        }
                    }
                }
            }

            @Override
            public void onError(WebSocket ws, Exception ex) {
                ButtplugWebsocketServer.this.bpLogger.trace("onError()");
                ButtplugWebsocketServer.this.bpLogger.logException(ex);
                ButtplugWebsocketServer.this.onException.invoke(new ButtplugEvent(ex));
            }

            @Override
            public void onStart() {
                ButtplugWebsocketServer.this.bpLogger.trace("onStart()");
                ButtplugWebsocketServer.this.connected = true;
            }
        };
        if (secureHostPairs != null) {
            KeyStore keyStore = CertUtils.getKeystore(this.context, secureHostPairs);
            if (keyStore != null) {
                KeyManagerFactory keyManagerFactory = null;
                TrustManagerFactory trustManagerFactory = null;
                try {
                    this.bpLogger.trace(String.format("Getting key manager factory (%s).",
                            KeyManagerFactory.getDefaultAlgorithm()));
                    keyManagerFactory = KeyManagerFactory.getInstance(
                            KeyManagerFactory.getDefaultAlgorithm());
                    keyManagerFactory.init(keyStore, null);
                    this.bpLogger.trace(String.format("Getting trust manager factory (%s).",
                            TrustManagerFactory.getDefaultAlgorithm()));
                    trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory
                            .getDefaultAlgorithm());
                    trustManagerFactory.init(keyStore);
                } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException
                        e) {
                    this.bpLogger.trace("Failed to get key manager factory.");
                    e.printStackTrace();
                }
                if (keyManagerFactory != null && trustManagerFactory != null) {
                    SSLContext sslContext = null;
                    try {
                        this.bpLogger.trace("Getting ssl context.");
                        sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory
                                .getTrustManagers(), null);
                    } catch (NoSuchAlgorithmException | KeyManagementException e) {
                        this.bpLogger.trace("Failed to get ssl context.");
                        e.printStackTrace();
                    }
                    if (sslContext != null) {
                        this.bpLogger.trace("Setting ssl factory.");
                        this.wsServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory
                                (sslContext));
                    } else {
                        this.bpLogger.trace("Ssl context is null.");
                    }
                } else {
                    this.bpLogger.trace("Key manager factory is null.");
                }
            } else {
                this.bpLogger.trace("Failed to read keystore.");
            }
        }
        this.wsServer.start();
    }

    private IButtplugCallback messageReceivedCallback = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
            ButtplugMessage msg = event.getMessage();
            if (msg == null) {
                return;
            }

            if (!ButtplugWebsocketServer.this.connections.isEmpty()) {
                String remoteId = ButtplugWebsocketServer.this.connections.keys().nextElement();
                WebSocket ws = ButtplugWebsocketServer.this.connections.get(remoteId).first;
                try {
                    ws.send((new ButtplugJsonMessageParser()).serialize(msg, 0));
                } catch (WebsocketNotConnectedException | IOException e) {
                    e.printStackTrace();
                    ws.close();
                }
                if (msg instanceof Error && ((Error) msg).errorCode == Error.ErrorClass
                        .ERROR_PING) {
                    ws.close();
                }
            }
        }
    };

    public void stopServer() {
        try {
            this.disconnect();
            this.wsServer.stop();
            this.connected = false;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        this.disconnect(null);
    }

    public void disconnect(String remoteId) {
        if (remoteId == null && !ButtplugWebsocketServer.this.connections.isEmpty()) {
            remoteId = ButtplugWebsocketServer.this.connections.keys().nextElement();
        }
        if (remoteId != null) {
            WebSocket ws = this.connections.get(remoteId).first;
            ws.close();
        }
    }

    public Map<String, String> getHostPairs(boolean loopback) throws ExecutionException,
            InterruptedException {
        return new HostnamesTask().execute(loopback).get();
    }

    @SuppressLint("StaticFieldLeak")
    public class HostnamesTask extends AsyncTask<Boolean, Void, Map<String, String>> {
        @Override
        protected Map<String, String> doInBackground(Boolean... booleans) {
            Boolean loopback = booleans[0];
            Map<String, String> addresses = new HashMap<>();
            try {
                for (NetworkInterface networkInterface : Collections.list(NetworkInterface
                        .getNetworkInterfaces())) {
                    if (loopback && !networkInterface.isLoopback()) {
                        continue;
                    }
                    Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                    for (InetAddress inetAddress : Collections.list(networkInterface
                            .getInetAddresses())) {
                        if (inetAddress instanceof Inet4Address) {
                            ButtplugWebsocketServer.this.bpLogger.trace(String.format("%s, %s",
                                    inetAddress.getHostAddress(),
                                    inetAddress.getCanonicalHostName()));
                            addresses.put(
                                    inetAddress.getHostAddress(),
                                    inetAddress.getCanonicalHostName());
                        }
                    }
                }
            } catch (SocketException ex) {
                return null;
            }
            return addresses;
        }
    }

    public static class LoopbackTask extends AsyncTask<Integer, Void, InetSocketAddress> {
        @Override
        protected InetSocketAddress doInBackground(Integer... integers) {
            int port = integers[0];
            try {
                return new InetSocketAddress(InetAddress.getLocalHost(), port);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
