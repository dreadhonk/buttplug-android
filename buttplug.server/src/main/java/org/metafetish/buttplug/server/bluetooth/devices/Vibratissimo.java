package org.metafetish.buttplug.server.bluetooth.devices;

import androidx.annotation.NonNull;

import org.metafetish.buttplug.core.ButtplugDeviceMessage;
import org.metafetish.buttplug.core.ButtplugMessage;
import org.metafetish.buttplug.core.IButtplugDeviceMessageCallback;
import org.metafetish.buttplug.core.Messages.Error;
import org.metafetish.buttplug.core.Messages.MessageAttributes;
import org.metafetish.buttplug.core.Messages.Ok;
import org.metafetish.buttplug.core.Messages.SingleMotorVibrateCmd;
import org.metafetish.buttplug.core.Messages.StopDeviceCmd;
import org.metafetish.buttplug.core.Messages.VibrateCmd;
import org.metafetish.buttplug.server.bluetooth.ButtplugBluetoothDevice;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInfo;
import org.metafetish.buttplug.server.bluetooth.IBluetoothDeviceInterface;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;


public class Vibratissimo extends ButtplugBluetoothDevice {
    private double vibratorSpeed;

    public Vibratissimo(@NonNull IBluetoothDeviceInterface iface,
                  @NonNull IBluetoothDeviceInfo info) {
        super(String.format("Vibratissimo %s", iface.getName()), iface, info);
        msgFuncs.put(SingleMotorVibrateCmd.class.getSimpleName(), new ButtplugDeviceWrapper(
                this.handleSingleMotorVibrateCmd, new MessageAttributes(1)));
        msgFuncs.put(VibrateCmd.class.getSimpleName(), new ButtplugDeviceWrapper(this.handleVibrateCmd,
                new MessageAttributes(1)));
        msgFuncs.put(StopDeviceCmd.class.getSimpleName(), new ButtplugDeviceWrapper(this.handleStopDeviceCmd));
    }

    private IButtplugDeviceMessageCallback handleStopDeviceCmd = new IButtplugDeviceMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugDeviceMessage msg) {
            Vibratissimo.this.bpLogger.debug(
                    String.format("Stopping Device %s", Vibratissimo.this.getName()));
            return Vibratissimo.this.handleSingleMotorVibrateCmd.invoke(
                    new SingleMotorVibrateCmd(msg.deviceIndex, 0, msg.id));
        }
    };

    private IButtplugDeviceMessageCallback handleSingleMotorVibrateCmd = new IButtplugDeviceMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugDeviceMessage msg) {
            if (!(msg instanceof SingleMotorVibrateCmd)) {
                return Vibratissimo.this.bpLogger.logErrorMsg(msg.id, Error.ErrorClass.ERROR_DEVICE,
                        "Wrong Handler");
            }
            SingleMotorVibrateCmd cmdMsg = (SingleMotorVibrateCmd) msg;

            VibrateCmd vibrateCmd = new VibrateCmd(cmdMsg.deviceIndex, null, cmdMsg.id);
            ArrayList<VibrateCmd.VibrateSubcommand> speeds = new ArrayList<>();
            speeds.add(vibrateCmd.new VibrateSubcommand(0, cmdMsg.getSpeed()));
            vibrateCmd.speeds = speeds;
            return Vibratissimo.this.handleVibrateCmd.invoke(vibrateCmd);
        }
    };

    private IButtplugDeviceMessageCallback handleVibrateCmd = new IButtplugDeviceMessageCallback() {
        @Override
        public ButtplugMessage invoke(ButtplugDeviceMessage msg) {
            if (!(msg instanceof VibrateCmd)) {
                return Vibratissimo.this.bpLogger.logErrorMsg(msg.id, Error.ErrorClass.ERROR_DEVICE,
                        "Wrong Handler");

            }
            VibrateCmd cmdMsg = (VibrateCmd) msg;

            if (cmdMsg.speeds.size() != 1) {
                return new Error("VibrateCmd requires 1 vector for this device.", Error
                        .ErrorClass.ERROR_DEVICE, cmdMsg.id);
            }


            for (VibrateCmd.VibrateSubcommand speed : cmdMsg.speeds) {
                if (speed.index != 1) {
                    return new Error(String.format("Index %s is out of bounds for VibrateCmd for this device.",
                            speed.index), Error.ErrorClass.ERROR_DEVICE, cmdMsg.id);
                }

                if (Math.abs(Vibratissimo.this.vibratorSpeed - speed.getSpeed()) < 0.001) {
                    return new Ok(cmdMsg.id);
                }

                Vibratissimo.this.vibratorSpeed = speed.getSpeed();
            }

            byte[] data = new byte[] {0x03, (byte) 0xff};

            try {
                Vibratissimo.this.iface.writeValue(
                        cmdMsg.id,
                        Vibratissimo.this.info.getCharacteristics().get(
                                VibratissimoBluetoothInfo.Chrs.TxMode.ordinal()),
                        data
                ).get();
            } catch (InterruptedException | ExecutionException e) {
                return Vibratissimo.this.bpLogger.logErrorMsg(msg.id, Error.ErrorClass.ERROR_DEVICE,
                        "Exception writing value");
            }

            data[0] = (byte) (Vibratissimo.this.vibratorSpeed * 0xff);
            data[1] = 0x00;

            try {
                return Vibratissimo.this.iface.writeValue(
                        cmdMsg.id,
                        Vibratissimo.this.info.getCharacteristics().get(
                                VibratissimoBluetoothInfo.Chrs.TxSpeed.ordinal()),
                        data
                ).get();
            } catch (InterruptedException | ExecutionException e) {
                return Vibratissimo.this.bpLogger.logErrorMsg(msg.id, Error.ErrorClass.ERROR_DEVICE,
                        "Exception writing value");
            }
        }
    };
}
