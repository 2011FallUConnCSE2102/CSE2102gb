/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.emulation.PerformanceMeter;
import de.joergjahnke.common.emulation.RunnableDevice;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultLogger;
import de.joergjahnke.common.util.Observer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Gameboy main class
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Gameboy extends RunnableDevice implements Observer, Serializable {

    /**
     * signal we send when we have a new performance measurement
     */
    public final static Integer SIGNAL_NEW_PERFORMANCE_MEASUREMENT = new Integer(1);
    /**
     * CPU speed of the Classic Gameboy
     */
    public final static int ORIGINAL_SPEED_CLASSIC = 4194304;
    /**
     * CPU speed of the Gameboy Color
     */
    public final static int ORIGINAL_SPEED_COLOR = 8388000;
    /**
     * the default sound sample rate is 8000 Hz
     */
    public final static int DEFAULT_SAMPLE_RATE = 8000;
    /**
     * performance meter used to measure and throttle, if necessary, the performance
     */
    private PerformanceMeter performanceMeter = null;
    /**
     * logs messages
     */
    private DefaultLogger logger = new DefaultLogger(100);
    /**
     * the Gameboy CPU
     */
    private CPU cpu;
    /**
     * Gameboy cartridge instance
     */
    private final Cartridge cartridge;
    /**
     * the Gameboy's video chip
     */
    private final VideoChip video;
    /**
     * the Gameboy's sound chip
     */
    private SoundChip sound;
    /**
     * the Gameboy's joypad
     */
    private Joypad joypad;
    /**
     * sample rate for the sound chip, default is 8000 Hz
     */
    private int soundSampleRate = DEFAULT_SAMPLE_RATE;

    /**
     * Create a Classic Gameboy or Gameboy Color instance
     */
    public Gameboy() {
        this.cartridge = new Cartridge(this);
        this.video = new VideoChip(this);
        // we register as observer for the video chip, so that we get regular updates which we use to trigger performance measurements
        this.video.addObserver(this);
    }

    /**
     * Load a Gameboy cartridge
     * 
     * @param romStream stream with ROM content
     * @throws java.io.IOException  if the cartridge could not be loaded
     */
    public void load(final InputStream romStream) throws IOException {
        // free some memory before loading
        Tile.resetCache();

        // load cartridge and initialize some Gameboy components
        this.cartridge.load(romStream);
        this.cpu = new CPU(this, cartridge);
        this.cpu.addObserver(this.video);
        this.cpu.addObserver(cartridge);
        this.sound = new SoundChip(this);
        this.joypad = new Joypad(this);
        this.performanceMeter = new PerformanceMeter(this.cpu, ORIGINAL_SPEED_CLASSIC);
        this.performanceMeter.addObserver(this);
        this.video.reset();

        // determine best tile cache size
        Tile.initializeCache();

        getLogger().info("Loaded cartridge '" + cartridge.getTitle().trim() + "' (" + cartridge.getCartridgeTypeName() + ")");
    }

    /**
     * Get the logger of this instance
     *
     * @return  the central logger
     */
    public final DefaultLogger getLogger() {
        return this.logger;
    }

    /**
     * Set the logger of this instance
     * 
     * @param	logger	the new logger
     */
    public final void setLogger(final DefaultLogger logger) {
        this.logger = logger;
    }

    /**
     * Get the emulator's CPU
     * 
     * @return	CPU instance
     */
    public final CPU getCPU() {
        return this.cpu;
    }

    /**
     * Get the emulator's cartridge
     *
     * @return	cartridge instance
     */
    public final Cartridge getCartridge() {
        return this.cartridge;
    }

    /**
     * Get the emulator's video chip
     * 
     * @return	video chip instance
     */
    public final VideoChip getVideoChip() {
        return this.video;
    }

    /**
     * Get the emulator's sound chip
     * 
     * @return	sound chip instance
     */
    public final SoundChip getSoundChip() {
        return this.sound;
    }

    /**
     * Get the emulator's joypad
     * 
     * @return	joypad instance
     */
    public final Joypad getJoypad() {
        return this.joypad;
    }

    /**
     * Get the performance meter
     * 
     * @return	PerformanceMeter instance
     */
    public PerformanceMeter getPerformanceMeter() {
        return this.performanceMeter;
    }

    /**
     * Get the sample rate of the sound chip
     * 
     * @return  sample rate in Hz
     */
    public int getSoundSampleRate() {
        return this.soundSampleRate;
    }

    /**
     * Set the sample rate of the sound chip
     * 
     * @param   soundSampleRate new sample rate in Hz, will be effective when the next cartridge gets loaded
     */
    public void setSoundSampleRate(final int soundSampleRate) {
        this.soundSampleRate = soundSampleRate;
    }

    /**
     * Run the emulator
     */
    public void run() {
        if (!isRunning()) {
            this.logger.info("Gameboy starting");
            super.run();
            // we use this thread to run the CPU
            try {
                this.cpu.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Stop the CPU
     */
    public void stop() {
        if (isRunning()) {
            this.logger.info("Gameboy stopping");
            this.cpu.stop();
            super.stop();
        }
    }

    /**
     * Pause the emulation
     */
    public void pause() {
        if (isRunning() && !isPaused()) {
            this.cpu.pause();
            super.pause();
            this.logger.info("Gameboy paused");
        }
    }

    /**
     * Continue the emulation
     */
    public void resume() {
        if (isRunning() && isPaused()) {
            this.cpu.resume();
            this.performanceMeter.setupNextMeasurement(this.cpu.getCycles());
            super.resume();
            this.logger.info("Gameboy resumed");
        }
    }

    public void update(final Object observed, final Object arg) {
        // a new frame wa created by the videochip?
        if (observed == this.video && arg == VideoChip.SIGNAL_NEW_FRAME) {
            // we use this regular update to initiate performance measurements
            this.performanceMeter.measure(this.cpu.getCycles());
        // a message from the performance meter?
        } else if (observed == this.performanceMeter) {
            // log this message
            getLogger().info(arg.toString());
            // propagate the change to observers of the Gameboy
            setChanged(true);
            notifyObservers(SIGNAL_NEW_PERFORMANCE_MEASUREMENT);
        }
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        this.cpu.serialize(out);
        SerializationUtils.setMarker(out);
        this.video.serialize(out);
        SerializationUtils.setMarker(out);
        this.sound.serialize(out);
        SerializationUtils.setMarker(out);
        this.joypad.serialize(out);
        SerializationUtils.setMarker(out);
        out.writeInt(this.soundSampleRate);
        SerializationUtils.setMarker(out);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.cpu.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.video.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.sound.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.joypad.deserialize(in);
        SerializationUtils.verifyMarker(in);
        this.soundSampleRate = in.readInt();
        SerializationUtils.verifyMarker(in);
    }
}
