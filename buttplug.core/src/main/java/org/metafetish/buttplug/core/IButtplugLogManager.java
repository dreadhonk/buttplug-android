package org.metafetish.buttplug.core;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface IButtplugLogManager {
    @Nullable
    ButtplugEventHandler logMessageReceived = null;

    @NonNull
    IButtplugLog getLogger(Class aClass);

    void setButtplugLogLevel(ButtplugLogLevel level);
}