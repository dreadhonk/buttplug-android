package org.metafetish.buttplug.core;

import androidx.annotation.NonNull;

public interface IButtplugLogManager {
    @NonNull
    ButtplugEventHandler getLogMessageReceived();

    @NonNull
    IButtplugLog getLogger(String className);

    void setButtplugLogLevel(ButtplugLogLevel level);
}
