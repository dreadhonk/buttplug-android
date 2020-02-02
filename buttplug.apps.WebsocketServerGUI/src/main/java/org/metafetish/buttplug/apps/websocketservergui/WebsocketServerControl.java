package org.metafetish.buttplug.apps.websocketservergui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import org.metafetish.buttplug.components.controls.ButtplugTabControl;
import org.metafetish.buttplug.components.websocketserver.ButtplugWebsocketServer;
import org.metafetish.buttplug.core.ButtplugEvent;
import org.metafetish.buttplug.core.ButtplugLogManager;
import org.metafetish.buttplug.core.Events.Connection;
import org.metafetish.buttplug.core.IButtplugCallback;
import org.metafetish.buttplug.core.IButtplugLog;
import org.metafetish.buttplug.server.IButtplugServerFactory;

import java.util.Map;
import java.util.concurrent.ExecutionException;


public class WebsocketServerControl extends Fragment {
    private AppCompatActivity activity;
    private View view;

    private ButtplugWebsocketServer ws;
    private IButtplugServerFactory bpFactory;
    //TODO: Implement ButtplugConfig?
    private ButtplugLogManager bpLogManager = new ButtplugLogManager();
    private IButtplugLog bpLogger = this.bpLogManager.getLogger(this.getClass().getSimpleName());
    private boolean loopback;
    private long port;
    private boolean secure;

    private SharedPreferences sharedPreferences;

    private boolean serverStarted = false;
    private boolean clientConnected = false;

    private Map<String, String> connUrls;
    private String remoteId = "";
    private String currentExceptionMessage = "";

    public WebsocketServerControl() {
        // Required empty public constructor
    }

    //TODO: Switch to getParentFragment()
    @SuppressLint("ValidFragment")
    public WebsocketServerControl(ButtplugTabControl bpTabControl) {
        this.bpFactory = bpTabControl;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (this.activity != null) {
            this.sharedPreferences = this.activity.getPreferences(Context.MODE_PRIVATE);
            this.ws = new ButtplugWebsocketServer(this.activity);

            //TODO: Why doesn't this work?
            //this.bpFactory = (ButtplugTabControl) getParentFragment();

//              this._config = new ButtplugConfig("Buttplug");
//              this._connUrls = new ConnUrlList();

            this.port = this.sharedPreferences.getLong("port", Long.parseLong(this.getString(R.string.default_port)));
            this.loopback = this.sharedPreferences.getBoolean("loopback", false);
            this.secure = this.sharedPreferences.getBoolean("secure", false);

            this.ws.getOnException().addCallback(this.websocketException);
            this.ws.getConnectionAccepted().addCallback(this.websocketConnectionAccepted);
            this.ws.getConnectionUpdated().addCallback(this.websocketConnectionAccepted);
            this.ws.getConnectionClosed().addCallback(this.websocketConnectionClosed);
            this.bpLogger.getOnLogException().addCallback(this.exceptionLogged);
            this.startServer();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_websocket_server_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.view = view;
        if (this.activity != null) {
            EditText portText = this.activity.findViewById(R.id.port);
            portText.setText(String.valueOf(this.port));
            portText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    WebsocketServerControl.this.port = Long.valueOf(charSequence.toString());
                    SharedPreferences.Editor editor = WebsocketServerControl.this.sharedPreferences.edit();
                    editor.putLong("port", WebsocketServerControl.this.port);
                    editor.apply();
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });

            Switch loopbackSwitch = this.activity.findViewById(R.id.loopback);
            loopbackSwitch.setChecked(this.loopback);
            loopbackSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WebsocketServerControl.this.loopback = isChecked;
                    SharedPreferences.Editor editor = WebsocketServerControl.this
                            .sharedPreferences.edit();
                    editor.putBoolean("loopback", WebsocketServerControl.this.loopback);
                    editor.apply();
                }
            });

            Switch secureSwitch = this.activity.findViewById(R.id.secure);
            secureSwitch.setChecked(this.secure);
            secureSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WebsocketServerControl.this.secure = isChecked;
                    SharedPreferences.Editor editor = WebsocketServerControl.this
                            .sharedPreferences.edit();
                    editor.putBoolean("secure", WebsocketServerControl.this.secure);
                    editor.apply();
                }
            });

            Button serverToggle = this.activity.findViewById(R.id.server_toggle);
            serverToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Button) view).getText().toString().equals(
                            WebsocketServerControl.this.getString(R.string.server_start))) {
                        WebsocketServerControl.this.startServer();
                    } else if (((Button) view).getText().toString().equals(
                            WebsocketServerControl.this.getString(R.string.server_stop))) {
                        WebsocketServerControl.this.stopServer();
                    }
                }
            });

            Button clientToggle = this.activity.findViewById(R.id.client_toggle);
            clientToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    WebsocketServerControl.this.bpLogger.trace("Clicked disconnect");
                    WebsocketServerControl.this.ws.disconnect();
                }
            });

            if (this.serverStarted) {
                this.onServerStart();
                if (this.clientConnected) {
                    this.onClientConnected();
                }
            }
            if (!this.currentExceptionMessage.isEmpty()) {
                this.onError();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.view = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.ws.disconnect();
        this.ws.stopServer();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            this.activity = (AppCompatActivity) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    public boolean isUiReady() {
        return this.activity != null && this.view != null;
    }

    private IButtplugCallback websocketException = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
//            _toastTimer.Enabled = true;
            Exception exception = event.getException();
            String errorMessage = exception.getMessage();

            WebsocketServerControl.this.bpLogger.trace(errorMessage);
            if (WebsocketServerControl.this.secure && errorMessage.contains("Not GET request") &&
                    WebsocketServerControl.this.ws != null) {  // && !ex.IsTerminating)
                WebsocketServerControl.this.bpLogger.logException(exception, true, errorMessage);
                return;
            }

            if (WebsocketServerControl.this.secure) {
                if (errorMessage.contains("The handshake failed due to an unexpected packet format")) {
                    errorMessage += "\n\nThis usually means that the client/browser tried to " +
                            "connect without SSL. Make sure the client is set use the wss:// URI scheme.";
                }
                //TODO: When should this be used?
//                else {
//                    errorMessage += "\n\nIf your connection is working, you can ignore this " +
//                            "message. Otherwise, this could mean that the client/browser has not " +
//                            "accepted our SSL certificate. Try hitting the test button on the " +
//                            "\"Websocket Server\" tab.";
//                }
            }

            WebsocketServerControl.this.currentExceptionMessage = errorMessage;

            WebsocketServerControl.this.bpLogger.logException(exception, true, errorMessage);

            //TODO: Implement isTerminating
            WebsocketServerControl.this.stopServer();
        }
    };

    private IButtplugCallback websocketConnectionAccepted = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
            WebsocketServerControl.this.clientConnected = true;
            if (event instanceof Connection) {
                WebsocketServerControl.this.remoteId = ((Connection) event).clientName;
            } else {
                WebsocketServerControl.this.remoteId = event.getString();
            }
            if (WebsocketServerControl.this.isUiReady()) {
                WebsocketServerControl.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebsocketServerControl.this.onClientConnected();
                    }
                });
            }
        }
    };

    private IButtplugCallback websocketConnectionClosed = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
            WebsocketServerControl.this.clientConnected = false;
            if (WebsocketServerControl.this.isUiReady()) {
                WebsocketServerControl.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebsocketServerControl.this.onClientDisonnected();
                    }
                });
            }
        }
    };

    private IButtplugCallback exceptionLogged = new IButtplugCallback() {
        @Override
        public void invoke(ButtplugEvent event) {
            if (WebsocketServerControl.this.isUiReady()) {
                WebsocketServerControl.this.activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebsocketServerControl.this.onError();
                    }
                });
            }
        }
    };

    private void startServer() {
        try {
            this.serverStarted = true;
            this.currentExceptionMessage = "";
            this.connUrls = this.ws.getHostPairs(this.loopback);
            this.ws.startServer(this.bpFactory, this.loopback, (int) this.port, this.secure ?
                    connUrls : null);
            WebsocketServerControl.this.activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    WebsocketServerControl.this.onServerStart();
                }
            });
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void stopServer() {
        this.serverStarted = false;
        this.ws.stopServer();
        WebsocketServerControl.this.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WebsocketServerControl.this.onClientDisonnected();
                WebsocketServerControl.this.onServerStop();
            }
        });
    }

    private void onServerStart() {
        if (this.isUiReady()) {
            ((Button) this.activity.findViewById(R.id.server_toggle)).setText(R.string.server_stop);
            this.activity.findViewById(R.id.port).setEnabled(false);
            this.activity.findViewById(R.id.loopback).setEnabled(false);
            this.activity.findViewById(R.id.secure).setEnabled(false);
            ((TextView) this.activity.findViewById(R.id.addresses)).setText(TextUtils.join("\n",
                    connUrls.keySet()));
            ((TextView) this.activity.findViewById(R.id.last_error)).setText("");
        }
    }

    private void onServerStop() {
        if (this.isUiReady()) {
            ((Button) this.activity.findViewById(R.id.server_toggle)).setText(R.string
                    .server_start);
            this.activity.findViewById(R.id.port).setEnabled(true);
            this.activity.findViewById(R.id.loopback).setEnabled(true);
            this.activity.findViewById(R.id.secure).setEnabled(true);
            ((TextView) this.activity.findViewById(R.id.addresses)).setText("");
        }
    }

    private void onClientConnected() {
        if (this.isUiReady()) {
            ((TextView) this.activity.findViewById(R.id.status)).setText(
                    this.getString(R.string.status_connected, this.remoteId));
            this.activity.findViewById(R.id.client_toggle).setEnabled(true);
        }
    }

    private void onClientDisonnected() {
        if (this.isUiReady()) {
            ((TextView) this.activity.findViewById(R.id.status)).setText(R.string
                    .status_not_connected);
            this.activity.findViewById(R.id.client_toggle).setEnabled(false);
        }
    }

    private void onError() {
        if (this.isUiReady()) {
            ((TextView) this.activity.findViewById(R.id.last_error)).setText(this.currentExceptionMessage);
        }
    }

}
