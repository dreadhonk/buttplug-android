package org.metafetish.buttplug.server.bluetooth.devices;


import android.support.annotation.NonNull;

import org.metafetish.buttplug.core.ButtplugConsts;
import org.metafetish.buttplug.core.ButtplugDeviceMessage;
import org.metafetish.buttplug.core.ButtplugMessage;
import org.metafetish.buttplug.core.IButtplugLogManager;
import org.metafetish.buttplug.core.IButtplugMessageCallback;
import org.metafetish.buttplug.core.Messages.FleshlightLaunchFW12Cmd;
import org.metafetish.buttplug.core.Messages.LinearCmd;
import org.metafetish.buttplug.core.Messages.MessageAttributes;
import org.metafetish.buttplug.core.Messages.Error;
import org.metafetish.buttplug.core.Messages.Ok;
import org.metafetish.buttplug.core.Messages.StopDeviceCmd;
import org.metafetish.buttplug.server.bluetooth.ButtplugBluetoothDevice;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInfo;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInterface;
import org.metafetish.buttplug.server.util.FleshlightHelper;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.concurrent.ExecutionException;

public class FleshlightLaunch extends ButtplugBluetoothDevice {
    private double lastPosition;

    public FleshlightLaunch(@NonNull IButtplugLogManager aLogManager,
                            @NonNull IBluetoothDeviceInterface aInterface,
                            @NonNull IBluetoothDeviceInfo aInfo) {
        super(aLogManager, "Fleshlight Launch", aInterface, aInfo);
        // Setup message function array
        msgFuncs.put(FleshlightLaunchFW12Cmd.class, new ButtplugDeviceWrapper(this.handleFleshlightLaunchRawCmd));
        msgFuncs.put(LinearCmd.class, new ButtplugDeviceWrapper(this.handleLinearCmd, new MessageAttributes(1)));
        msgFuncs.put(StopDeviceCmd.class, new ButtplugDeviceWrapper(this.handleStopDeviceCmd));
    }

    public ListenableFuture<ButtplugMessage> initialize() {
        return iface.writeValue(
                ButtplugConsts.SystemMsgId,
                info.characteristics.get(FleshlightLaunchBluetoothInfo.Chrs.Cmd.ordinal()),
                new byte[]{0},
                true
        );
    }

    private IButtplugMessageCallback handleStopDeviceCmd = new IButtplugMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugMessage aMsg) {
            // This probably shouldn't be a nop, but right now we don't have a good way to know
            // if the launch is moving or not, and surprisingly enough, setting speed to 0 does not
            // actually stop movement. It just makes it move really slow.
            // However, since each move it makes is finite (unlike setting vibration on some devices),
            // so we can assume it will be a short move, similar to what we do for the Kiiroo toys.
            FleshlightLaunch.this.bpLogger.debug("Stopping Device " + FleshlightLaunch.this.getName());
            return new Ok(aMsg.id);
        }
    };

    private IButtplugMessageCallback handleLinearCmd = new IButtplugMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugMessage aMsg) {
            LinearCmd cmdMsg = (LinearCmd) aMsg;
            if (cmdMsg == null) {
                return FleshlightLaunch.this.bpLogger.logErrorMsg(aMsg.id, Error.ErrorClass.ERROR_DEVICE, "Wrong Handler");
            }

            if (cmdMsg.vectors.size() != 1) {
                return new Error("LinearCmd requires 1 vector for this device.", Error.ErrorClass.ERROR_DEVICE, cmdMsg.id);
            }

            for (LinearCmd.VectorSubcommands vector : cmdMsg.vectors) {
                if (vector.index != 0) {
                    return new Error("Index " + vector.index + " is out of bounds for LinearCmd for this device.", Error.ErrorClass.ERROR_DEVICE, cmdMsg.id);
                }

                return FleshlightLaunch.this.handleFleshlightLaunchRawCmd.invoke(new FleshlightLaunchFW12Cmd(
                        cmdMsg.deviceIndex,
                        (int) FleshlightHelper.GetSpeed(Math.abs(FleshlightLaunch.this.lastPosition - vector.getPosition()), vector.duration) * 99,
                        (int) vector.getPosition() * 99,
                        cmdMsg.id
                ));
            }

            return new Ok(aMsg.id);
        }
    };

    private IButtplugMessageCallback handleFleshlightLaunchRawCmd = new IButtplugMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugMessage aMsg) {

            // TODO: Split into Command message and Control message? (Issue #17)
            FleshlightLaunchFW12Cmd cmdMsg = (FleshlightLaunchFW12Cmd) aMsg;
            if (cmdMsg == null) {
                return FleshlightLaunch.this.bpLogger.logErrorMsg(aMsg.id, Error.ErrorClass.ERROR_DEVICE, "Wrong Handler");
            }

            FleshlightLaunch.this.lastPosition = cmdMsg.GetPosition() / 99;

            try {
                return FleshlightLaunch.this.iface.writeValue(
                        cmdMsg.id,
                        FleshlightLaunch.this.info.characteristics.get(FleshlightLaunchBluetoothInfo.Chrs.Tx.ordinal()),
                        new byte[]{(byte) cmdMsg.GetPosition(), (byte) cmdMsg.GetSpeed()}
                ).get();
            } catch (InterruptedException | ExecutionException e) {
                return FleshlightLaunch.this.bpLogger.logErrorMsg(aMsg.id, Error.ErrorClass.ERROR_DEVICE, "Exception writing value");
            }
        }
    };
}