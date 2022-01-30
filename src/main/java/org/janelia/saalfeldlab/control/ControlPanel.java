/**
 *
 */
package org.janelia.saalfeldlab.control;


/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public interface ControlPanel {

	public int getNumControls();
	public IntControl getVPotControl(int i);
}
