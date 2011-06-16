/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.android;

/**
 * A listener that gets notified on orientation sensor changes
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public interface OrientationSensorListener {
	/**
	 * Implement this method to handle orientation sensor changes.
	 * 
	 * @param	sensorValues	sensor values
	 * @return	true if the event was handled, otherwise false
	 */
	public boolean onOrientationChange(final float[] sensorValues);
}
