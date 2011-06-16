/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.android;

import android.hardware.Sensors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Notifies listeners of changes a device's orientation sensor.
 * The sensors are queried a given number of times per second that can be determined
 * on creation of the view.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class OrientationSensorNotifier {
	/**
	 * Unless otherwise specified we query the sensors every 100 ms
	 */
	public final static long DEFAULT_SENSOR_PERIOD = 100;
	/**
	 * the period we wait until the sensor is polled the next time
	 */
	private final long delay;
	/**
	 * background thread querying the orientation sensor
	 */
	@SuppressWarnings("unused")
	private Thread thread;
	/**
	 * is the notifier running?
	 */
	private boolean isRunning = false;
	/**
	 * list of registered listeners
	 */
	private final List<OrientationSensorListener> listeners = new ArrayList<OrientationSensorListener>();
	
	
	/**
	 * Create a new OrientationSensorNotifier
	 * 
	 * @param	delay	the number of milliseconds between consecutive checks of the sensor
	 * @throws	IllegalStateException if no orientation sensor is supported by the device
	 */
	public OrientationSensorNotifier(final long delay) {
		// verify whether we have an orientation sensor available
		if(!isSupported()) {
			throw new IllegalStateException("No orientation sensor available!");
		}
		
		Sensors.enableSensor(Sensors.SENSOR_ORIENTATION);
		
		this.delay = delay;
	}
	
	/**
	 * Create a new OrientationSensorNotifier
	 */
	public OrientationSensorNotifier() {
		this(DEFAULT_SENSOR_PERIOD);
	}
	
	
	/**
	 * Check whether this device supports an orientation sensor
	 * 
	 * @return	true if an orientation sensor is supported
	 */
	public static boolean isSupported() {
		return Arrays.asList(Sensors.getSupportedSensors()).contains(Sensors.SENSOR_ORIENTATION);
	}
	
	
	/**
	 * Add a new OrientationSensorListener to this notifier
	 * 
	 * @param	listener	the new listener to inform
	 */
	public void addListener(final OrientationSensorListener listener) {
		this.listeners.add(listener);
	}
	
	/**
	 * Remove an OrientationSensorListener from this notifier
	 * 
	 * @param	listener	the listener who no longer wants to be informed
	 */
	public void removeListener(final OrientationSensorListener listener) {
		this.listeners.remove(listener);
	}
	
	
	/**
	 * We start a thread which regularly polls the orientation sensor
	 */
	public void start() {
		if(!this.isRunning) {
			this.isRunning = true;
			this.thread = new Thread() { 
				@Override
				public void run() {
					while(isRunning) {
						// wait for some time until the next sensor read
						try { sleep(delay); } catch(InterruptedException e) {}
						
						// read sensor values
						final int num = Sensors.getNumSensorValues(Sensors.SENSOR_ORIENTATION);
						final float[] sensorValues = new float[num];
						
						Sensors.readSensor(Sensors.SENSOR_ORIENTATION, sensorValues);
						
						// pass these values on to all listeners
						for(int i = 0, to = listeners.size() ; i < to ; ++i) {
							listeners.get(i).onOrientationChange(sensorValues);
						}
					}
				}
			};
			this.thread.start();
		}
	}
	
	/**
	 * We stop the thread which regularly polls the orientation sensor
	 */
	public void stop() {
		this.isRunning = false;
	}
}
