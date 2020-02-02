package org.metafetish.buttplug.server.bluetooth.devices;

import androidx.annotation.NonNull;

import org.metafetish.buttplug.core.IButtplugDevice;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInfo;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class YoucupsBluetoothInfo implements IBluetoothDeviceInfo {
    public enum Chrs {
        Tx
    }

    private List<String> names = new ArrayList<String>() {{
        // Warrior II
        add("Youcups");
    }};

    @NonNull
    public List<String> getNames() {
        return this.names;
    }


    private List<UUID> services = new ArrayList<UUID>() {{
        add(UUID.fromString("0000fee9-0000-1000-8000-00805f9b34fb"));
    }};

    @NonNull
    public List<UUID> getServices() {
        return this.services;
    }

    private List<UUID> characteristics = new ArrayList<UUID>() {{
        // tx
        add(UUID.fromString("d44bc439-abfd-45a2-b575-925416129600"));
    }};

    @NonNull
    public List<UUID> getCharacteristics() {
        return this.characteristics;
    }

    @NonNull
    public IButtplugDevice createDevice(@NonNull IBluetoothDeviceInterface iface) {
        return new Youcups(iface, this);
    }
}
