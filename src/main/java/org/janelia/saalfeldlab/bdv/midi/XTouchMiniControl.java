/**
 *
 */
package org.janelia.saalfeldlab.bdv.midi;

import java.util.Arrays;
import java.util.function.Consumer;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class XTouchMiniControl {

	private static final String DEFAULT_DEVICE_DESCRIPTION = "X-TOUCH MINI";

	private static final int CHANNEL = 10; // uses percussion channel

	private static final int CONTROL_CENTER = 64;

	private static final int LED_RING_SINGLE = 0;
	private static final int LED_RING_PAN = 1;
	private static final int LED_RING_FAN = 2;
	private static final int LED_RING_SPREAD = 3;
	private static final int LED_RING_TRIM = 4;

	public enum ControlType {
		ABSOLUTE,
		RELATIVE
	}

	/**
	 * sliders and knobs that generate control messages
	 */
	private static final TIntHashSet CONTROLS = new TIntHashSet(
			new int[]{
					1, 2, 3, 4, 5, 6, 7, 8, 9,
					10, 11, 12, 13, 14, 15, 16, 17, 18});

	/**
	 * LED rings
	 */
	private static final TIntHashSet LEDS = new TIntHashSet(
			new int[]{1, 2, 3, 4, 5, 6, 7, 8});

	/**
	 * control leds
	 */
	private static final TIntIntHashMap CONTROL_LEDS = new TIntIntHashMap(
			new int[]{
					1, 2, 3, 4, 5, 6, 7, 8,
					10, 11, 12, 13, 14, 15, 16, 17},
			new int[]{
					1, 2, 3, 4, 5, 6, 7, 8,
					1, 2, 3, 4, 5, 6, 7, 8});


	/**
	 * controls that reset to center after each event, so they can be used for
	 * relative/ infinite range controls, this makes sense only for digital
	 * controls like knobs that act like mouse wheels, has no meaningful effect
	 * on analog controls like sliders
	 *
	 * digital knobs on this device are
	 * 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 14, 15, 16, 17, 18
	 */
	private final TIntHashSet relativeControls = new TIntHashSet();

	/**
	 * keys that generate note on and off messages, including knobs that can be
	 * pressed
	 */
	private static final TIntHashSet KEYS = new TIntHashSet(
			new int[]{
					0, 1, 2, 3, 4, 5, 6, 7,
					8, 9, 10, 11, 12, 13, 14, 15,
					16, 17, 18, 19, 20, 21, 22, 23,
					24, 25, 26, 27, 28, 29, 30, 31,
					32, 33, 34, 35, 36, 37, 38, 39,
					40, 41, 42, 43, 44, 45, 46, 47});

	/**
	 * keys that are associated with controls, here knobs you can press,
	 * there is no association between the ids other than that the numbers
	 * come up in the same order, so you figure
	 */
	private static final TIntHashSet CONTROL_KEYS = new TIntHashSet(
			new int[]{
					0, 1, 2, 3, 4, 5, 6, 7,
					24, 25, 26, 27, 28, 29, 30, 31});

	/**
	 * keys that always return to on state after a note off message, this is
	 * used to make keys light up, the messages come in unchanged
	 */
	private static final TIntHashSet onKeys = new TIntHashSet();

	private Transmitter trans = null;
	private Receiver rec = null;

	private final TIntObjectHashMap<Consumer<ShortMessage>> controlChangeHandlers = new TIntObjectHashMap<>();

	private final TIntObjectHashMap<Consumer<ShortMessage>> noteHandlers = new TIntObjectHashMap<>();

	@SuppressWarnings("resource")
	public XTouchMiniControl(final String deviceDescription)
			throws InvalidMidiDataException,
			MidiUnavailableException,
			InterruptedException {

		MidiDevice transDev = null, recDev = null;

		for (final Info info : MidiSystem.getMidiDeviceInfo()) {

			final MidiDevice device = MidiSystem.getMidiDevice(info);
			System.out.println(info.getDescription());
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

	public XTouchMiniControl() throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

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
		rec.send(msg, System.currentTimeMillis());

		/* normal mode because LEDs cannot be used in MC mode */
		msg.setMessage(ShortMessage.CONTROL_CHANGE, 127, 0);
		rec.send(msg, System.currentTimeMillis());

		/* activate layer A */
		msg.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0);
		rec.send(msg, System.currentTimeMillis());

		/* set LED rings to pan mode */
		for (final int id : CONTROL_LEDS.values()) {
			msg.setMessage(ShortMessage.CONTROL_CHANGE, id, LED_RING_PAN);
			rec.send(msg, System.currentTimeMillis());
		}

		/* reset all controls */
		for (final int id : CONTROLS.toArray()) {
			msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, id, 0);
			rec.send(msg, System.currentTimeMillis());
		}
	}

	/**
	 * Reset controls and play some fancy visuals that indicate that the device
	 * has been initialized. This is entirely unnecessary, but it looks very
	 * cool.
	 *
	 * @throws InterruptedException
	 * @throws InvalidMidiDataException
	 */
	public void blink() throws InterruptedException, InvalidMidiDataException {

		/* initialize */
		final ShortMessage msg = new ShortMessage();

		final int[] controls = CONTROLS.toArray();
		for (int i = 0; i < 128; i += 8) {
			for (final int id : controls) {
				msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, id, i);
				rec.send(msg, System.currentTimeMillis());
			}
			Thread.sleep(20);
		}
		for (int i = 127; i >= CONTROL_CENTER; i -= 8) {
			for (final int knob : controls) {
				msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, knob, i);
				rec.send(msg, System.currentTimeMillis());
			}
			Thread.sleep(20);
		}
		for (final int knob : controls) {
			msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, knob, CONTROL_CENTER);
			rec.send(msg, System.currentTimeMillis());
		}

		final int[] keysArray = KEYS.toArray();
		for (int i = 0; i < 3; ++i) {
			Thread.sleep(50);
			for (final int key : keysArray) {
				msg.setMessage(ShortMessage.NOTE_ON, CHANNEL, key, 127);
				rec.send(msg, System.currentTimeMillis());
			}
			Thread.sleep(50);
			for (final int key : keysArray) {
				msg.setMessage(ShortMessage.NOTE_OFF, CHANNEL, key, 0);
				rec.send(msg, System.currentTimeMillis());
			}
		}
		for (final int key : onKeys.toArray()) {
			msg.setMessage(ShortMessage.NOTE_ON, CHANNEL, key, 127);
			rec.send(msg, System.currentTimeMillis());
		}
	}

	public class InputReceiver implements Receiver {

		@Override
		public void send(final MidiMessage msg, final long timeStamp) {

			System.out.println(timeStamp);

			System.out.println("received : " + Arrays.toString(msg.getMessage()));

			if (msg instanceof ShortMessage) {
				final ShortMessage in = (ShortMessage)msg;

				final int cmd = in.getCommand();
				System.out.println(cmd);
				switch (cmd) {
				case ShortMessage.CONTROL_CHANGE: {
					final int id = in.getData1();
					final Consumer<ShortMessage> handler = controlChangeHandlers.get(id);
					if (relativeControls.contains(id)) {
						final int pos = in.getData2();
						final int d = pos - CONTROL_CENTER;
						try {
							final long t = System.currentTimeMillis();
							final ShortMessage out = new ShortMessage(cmd, CHANNEL, id, CONTROL_CENTER);
							out.setMessage(cmd, CHANNEL, id, CONTROL_CENTER + 8 * (byte)d);
							rec.send(out, t);
							out.setMessage(cmd, CHANNEL, id, CONTROL_CENTER);
							rec.send(out, t + 1);
							in.setMessage(cmd, CHANNEL, id, d);
						} catch (final Exception e) {
							System.err.println(e);
						}
					}
					if (handler != null) {
						handler.accept(in);
					}
				}
					break;
				case ShortMessage.NOTE_ON:
				case ShortMessage.NOTE_OFF: {
					final int id = in.getData1();
					final Consumer<ShortMessage> handler = noteHandlers.get(id);
					if (onKeys.contains(id)) {
						try {
							rec
									.send(
											new ShortMessage(ShortMessage.NOTE_ON, CHANNEL, id, 127),
											System.currentTimeMillis());
						} catch (final Exception e) {
							System.err.println(e);
						}
					}
					if (handler != null) {
						handler.accept(in);
					}
				}
					break;
				}
				System.out
						.println(
								in.getChannel() + " " +
										in.getCommand() + " " +
										in.getData1() + " " +
										in.getData2());
			}
		}

		@Override
		public void close() {

			trans.close();
			rec.close();
		}
	}

	public Consumer<ShortMessage> addControlHandler(final int id, final Consumer<ShortMessage> handler) {

		return controlChangeHandlers.put(id, handler);
	}

	public Consumer<ShortMessage> addNoteHandler(final int id, final Consumer<ShortMessage> handler) {

		return noteHandlers.put(id, handler);
	}

	public Consumer<ShortMessage> remove(final int id) {

		return controlChangeHandlers.remove(id);
	}

	public Consumer<ShortMessage> removeNoteHandler(final int id) {

		return noteHandlers.remove(id);
	}

	public void addRelativeControls(final int... ids) {

		relativeControls.addAll(ids);
		final ShortMessage msg = new ShortMessage();

		for (final int id : ids) {
			try {
				msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, id, CONTROL_CENTER);
				rec.send(msg, System.currentTimeMillis());
				final int ledId = CONTROL_LEDS.get(id);
				if (ledId != CONTROL_LEDS.getNoEntryValue()) {
					msg.setMessage(ShortMessage.CONTROL_CHANGE, ledId, LED_RING_TRIM);
					rec.send(msg, System.currentTimeMillis());
				}
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace(System.err);
			}
		}
	}

	public void removeRelativeControls(final int... ids) {

		relativeControls.removeAll(ids);
		final ShortMessage msg = new ShortMessage();

		for (final int id : ids) {
			try {
				msg.setMessage(ShortMessage.CONTROL_CHANGE, CHANNEL, id, 0);
				rec.send(msg, System.currentTimeMillis());
				final int ledId = CONTROL_LEDS.get(id);
				if (ledId != CONTROL_LEDS.getNoEntryValue()) {
					msg.setMessage(ShortMessage.CONTROL_CHANGE, ledId, LED_RING_PAN);
					rec.send(msg, System.currentTimeMillis());
				}
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace(System.err);
			}
		}
	}

	public void addOnKeys(final int... ids) {

		onKeys.addAll(ids);
		final ShortMessage msg = new ShortMessage();

		for (final int id : ids) {
			try {
				msg.setMessage(ShortMessage.NOTE_ON, CHANNEL, id, 127);
				rec.send(msg, System.currentTimeMillis());
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace(System.err);
			}
		}
	}

	public void removeOnKeys(final int... ids) {

		onKeys.removeAll(ids);
		final ShortMessage msg = new ShortMessage();

		for (final int id : ids) {
			try {
				msg.setMessage(ShortMessage.NOTE_OFF, CHANNEL, id, 0);
				rec.send(msg, System.currentTimeMillis());
			} catch (final InvalidMidiDataException e) {
				e.printStackTrace(System.err);
			}

		}
	}
}
