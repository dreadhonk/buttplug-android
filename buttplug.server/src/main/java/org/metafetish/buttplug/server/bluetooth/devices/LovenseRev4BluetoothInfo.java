package org.metafetish.buttplug.server.bluetooth.devices;

import androidx.annotation.NonNull;

import org.metafetish.buttplug.core.IButtplugDevice;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInfo;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class LovenseRev4BluetoothInfo implements IBluetoothDeviceInfo {
    public enum Chrs {
        Tx,
        Rx
    }

    private List<String> names = new ArrayList<String>() {{
        // Domi
        add("LVS-Domi37");
        add("LVS-Domi38");
        add("LVS-Domi39");
    }};

    @NonNull
    public List<String> getNames() {
        return this.names;
    }

    private List<UUID> services = new ArrayList<UUID>() {{
        add(UUID.fromString("57300001-0023-4bd4-bbd5-a6920e4c5653"));
    }};

    @NonNull
    public List<UUID> getServices() {
        return this.services;
    }

    private List<UUID> characteristics = new ArrayList<UUID>() {{
        // tx
        add(UUID.fromString("57300002-0023-4bd4-bbd5-a6920e4c5653"));
        // rx
        add(UUID.fromString("57300003-0023-4bd4-bbd5-a6920e4c5653"));
    }};

    @NonNull
    public List<UUID> getCharacteristics() {
        return this.characteristics;
    }

    @NonNull
    public IButtplugDevice createDevice(@NonNull IBluetoothDeviceInterface iface) {
        return new Lovense(iface, this);
    }
}
