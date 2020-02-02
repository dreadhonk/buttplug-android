package org.metafetish.buttplug.core;

import androidx.annotation.NonNull;

import org.metafetish.buttplug.core.Messages.MessageAttributes;

import java.util.concurrent.Future;

public interface IButtplugDevice {
    @NonNull
    String getName();

    @NonNull
    String getIdentifier();

    @NonNull
    Long getIndex();

    void setIndex(long index);

    @NonNull
    Boolean isConnected();

    @NonNull
    ButtplugEventHandler getDeviceRemoved();

    @NonNull
    ButtplugEventHandler getMessageEmitted();

    @NonNull
    Iterable<String> getAllowedMessageTypes();

    @NonNull
    Future<ButtplugMessage> parseMessage(ButtplugDeviceMessage msg);

    @NonNull
    Future<ButtplugMessage> initialize();

    void disconnect();

    @NonNull
    MessageAttributes getMessageAttrs(String msgType);
}
