/**
 *
 */
package org.janelia.saalfeldlab.bdv.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class XTouchMiniMCUControl {

	private static final String DEFAULT_DEVICE_DESCRIPTION = "X-TOUCH MINI";

	private MidiDevice transDev = null;
	private Transmitter trans = null;
	private MidiDevice recDev = null;
	private Receiver rec = null;

	@SuppressWarnings("resource")
	public XTouchMiniMCUControl(final String deviceDescription)
			throws InvalidMidiDataException,
			MidiUnavailableException,
			InterruptedException {

		for (final Info info : MidiSystem.getMidiDeviceInfo()) {

			final MidiDevice device = MidiSystem.getMidiDevice(info);
			System.out.println(info.getName() + " : " + info.getDescription());
			if (info.getDescription().contains(deviceDescription)) {
//				device.open();
//				System.out.println("name : " + info.getName());
//				System.out.println("vendor : " + info.getVendor());
//				System.out.println("version : " + info.getVersion());

				if (device.getMaxTransmitters() != 0) {
					transDev = device;
					trans = device.getTransmitter();
				}
				if (device.getMaxReceivers() != 0) {
					recDev = device;
					rec = device.getReceiver();
				}
			}
		}

		if (!(trans == null || rec == null)) {
			transDev.open();
			recDev.open();
			trans.setReceiver(new InputReceiver());

			reset();

		} else {
			if (transDev != null)
				transDev.close();
			if (recDev != null)
				recDev.close();
			throw new MidiUnavailableException("No X TOUCH mini MIDI controller found.");
		}
	}

	public XTouchMiniMCUControl() throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		this(DEFAULT_DEVICE_DESCRIPTION);
	}

	/**
	 * Reset all controls.
	 *
	 * @throws InterruptedException
	 * @throws InvalidMidiDataException
	 */
	public void reset() throws InterruptedException, InvalidMidiDataException {

		final ShortMessage msg = new ShortMessage(ShortMessage.SYSTEM_RESET);
		send(msg);

		/* MC mode */
		msg.setMessage(ShortMessage.CONTROL_CHANGE, 127, 1);
		send(ShortMessage.CONTROL_CHANGE, 127, 1);

		Thread.sleep(500);

//		send(0xb0, 0x0f, 0x01);
//		send(0xb0, 0x2f, 0x40);
		send(0x90, 0x00, 0x00);

		Thread.sleep(500);

		send(0x90, 0x00, 0x00);

//		send(0xe8, 0x00, 0x01);

//		final byte[] data = new byte[3];

//		msg.setMessage(0xb0, 0x41, 0x13);
//		rec.send(msg, System.currentTimeMillis());
//		System.out.println(
//				String.format("%02X %02X %02X", msg.getStatus(), msg.getData1(), msg.getData2()));
//		recDev.close();
//		transDev.close();
//		if (true) return;

//		for (int d1 = 0xe7; d1 < 256; ++d1) {
//			for (int d2 = 0x00; d2 < 128; ++d2) {
//				for (int d3 = 0; d3 < 128; ++d3) {
//					System.out.println(
//							String.format("%02X %02X %02X", (byte)d1, (byte)d2, (byte)d3));
//					msg.setMessage(d1, d2, d3);
//					Thread.sleep(1);
//					rec.send(msg, System.currentTimeMillis());
//				}
//			}
//		}
	}

	private void send(final ShortMessage msg) throws InvalidMidiDataException {

		rec.send(msg, System.currentTimeMillis());
	}

	private void send(final byte status, final byte data1, final byte data2) throws InvalidMidiDataException {

		send(new ShortMessage(status, data1, data2));
	}

	private void send(final int status, final int data1, final int data2) throws InvalidMidiDataException {

		send((byte)status, (byte)data1, (byte)data2);
	}

	public class InputReceiver implements Receiver {

		@Override
		public void send(final MidiMessage msg, final long timeStamp) {

			System.out.println(timeStamp);
			final byte[] bytes = msg.getMessage();
			System.out.println("received : " + String.format("%02x %02x %02x", bytes[0], bytes[1], bytes[2]));

			final ShortMessage sm = (ShortMessage)msg;
			System.out.println(
					"CMD " + String.format("%02x", sm.getCommand()) +
					"  CH " + String.format("%02x", sm.getChannel()) +
					"  DAT1 " + String.format("%02x", sm.getData1()) +
					"  DAT2 " + String.format("%02x", sm.getData2()));
		}

		@Override
		public void close() {

			transDev.close();
			recDev.close();
		}
	}

	public static void main(final String... args) throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		new XTouchMiniMCUControl();
	}
}
