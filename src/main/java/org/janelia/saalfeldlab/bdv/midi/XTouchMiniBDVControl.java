/**
 *
 */
package org.janelia.saalfeldlab.bdv.midi;

import java.util.function.Consumer;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.ShortMessage;

import bdv.viewer.Interpolation;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerFrame;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class XTouchMiniBDVControl {

	/**
	 * One step of rotation (radian).
	 */
	final private static double step = Math.PI / 180;

	private final ViewerPanel viewerPanel;
	private final XTouchMiniControl controller;

	public XTouchMiniBDVControl(final ViewerPanel viewer) throws InvalidMidiDataException, MidiUnavailableException, InterruptedException {

		this.viewerPanel = viewer;
		controller = new XTouchMiniControl();
		controller.addRelativeControls(1, 2, 3, 4, 5, 6, 7);

		controller.blink();

		/* add handlers */
		controller.addControlHandler(
				1,
				new KnobAxisShiftHandler(0));
		controller.addControlHandler(
				2,
				new KnobAxisShiftHandler(1));
		controller.addControlHandler(
				3,
				new KnobAxisShiftHandler(2));

		controller.addControlHandler(
				4,
				new KnobAxisRotationHandler(0));
		controller.addControlHandler(
				5,
				new KnobAxisRotationHandler(1));
		controller.addControlHandler(
				6,
				new KnobAxisRotationHandler(2));
		controller.addControlHandler(
				7,
				new KnobZoomHandler());
		controller.addNoteHandler(
				8,
				new InterpolationSwitcher(8));

		System.out.println((ViewerFrame)viewerPanel.getRootPane().getParent());
	}

	public class KnobAxisRotationHandler implements Consumer<ShortMessage> {

		private final int axis;

		public KnobAxisRotationHandler(final int axis) {

			this.axis = axis;
		}

		@Override
		public void accept(final ShortMessage msg) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			// center shift
			final double cX = 0.5 * viewerPanel.getWidth();
			final double cY = 0.5 * viewerPanel.getHeight();
			viewerTransform.set(viewerTransform.get( 0, 3 ) - cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) - cY, 1, 3);

			// rotate
			viewerTransform.rotate(axis, (byte)msg.getData2() * step);

			// center un-shift
			viewerTransform.set(viewerTransform.get( 0, 3 ) + cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) + cY, 1, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class KnobAxisShiftHandler implements Consumer<ShortMessage> {

		private final int axis;

		public KnobAxisShiftHandler(final int axis) {

			this.axis = axis;
		}

		@Override
		public void accept(final ShortMessage msg) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			viewerTransform.set(viewerTransform.get( axis, 3 ) + (byte)msg.getData2(), axis, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class KnobZoomHandler implements Consumer<ShortMessage> {

		@Override
		public void accept(final ShortMessage msg) {

			final SynchronizedViewerState state = viewerPanel.state();
			final AffineTransform3D viewerTransform = state.getViewerTransform();

			// center shift
			final double cX = 0.5 * viewerPanel.getWidth();
			final double cY = 0.5 * viewerPanel.getHeight();
			viewerTransform.set(viewerTransform.get( 0, 3 ) - cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) - cY, 1, 3);

			// rotate
			final double dScale = 1.0 + 0.05;
			viewerTransform.scale(Math.pow(dScale, (byte)msg.getData2()));

			// center un-shift
			viewerTransform.set(viewerTransform.get( 0, 3 ) + cX, 0, 3);
			viewerTransform.set(viewerTransform.get( 1, 3 ) + cY, 1, 3);

			state.setViewerTransform(viewerTransform);
		}
	}

	public class InterpolationSwitcher implements Consumer<ShortMessage> {

		private final int keyId;

		public InterpolationSwitcher(final int keyId) {

			this.keyId = keyId;
		}

		@Override
		public void accept(final ShortMessage msg) {

			if (msg.getCommand() == ShortMessage.NOTE_ON) {
				final SynchronizedViewerState state = viewerPanel.state();
				final Interpolation interpolation = state.getInterpolation();
				if (interpolation == Interpolation.NEARESTNEIGHBOR) {
					state.setInterpolation(Interpolation.NLINEAR);
					controller.addOnKeys(keyId);
				} else {
					state.setInterpolation(Interpolation.NEARESTNEIGHBOR);
					controller.removeOnKeys(keyId);
				}
			}
		}
	}
}
