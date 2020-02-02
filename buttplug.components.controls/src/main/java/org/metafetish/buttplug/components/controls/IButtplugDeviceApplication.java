package org.metafetish.buttplug.components.controls;

import androidx.annotation.NonNull;

import org.metafetish.buttplug.core.ButtplugEventHandler;

public interface IButtplugDeviceApplication {

    @NonNull
    ButtplugEventHandler getStartScanning();

    @NonNull
    ButtplugEventHandler getStopScanning();

    @NonNull
    ButtplugEventHandler getDeviceAdded();

    @NonNull
    ButtplugEventHandler getDeviceRemoved();

    @NonNull
    ButtplugEventHandler getDevicesReset();
}
