/**
 *
 */
package org.janelia.saalfeldlab.control;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface VPotControl extends AdjustableClippingIntControl {

	public static final int DISPLAY_NONE = 0;
	public static final int DISPLAY_PAN = 1;
	public static final int DISPLAY_TRIM = 2;
	public static final int DISPLAY_FAN = 3;
	public static final int DISPLAY_SPREAD = 4;

	/*
	 * Returns whether this VPot control tracks an absolute value
	 * or reports relative changes.
	 *
	 * @return true if the control tracks an absolute value
	 *         false if this control reports relative changes
	 */
	public boolean isAbsolute();
	public void setAbsolute(final boolean absolute);

	public void setDisplayType(final int display);
}
