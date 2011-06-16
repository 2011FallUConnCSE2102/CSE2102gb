/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.emulation.FrequencyDataProducer;
import de.joergjahnke.common.emulation.FrequencyDataProducerOwner;
import de.joergjahnke.common.emulation.WaveDataProducer;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultObservable;
import de.joergjahnke.common.util.Observer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the Gameboy's sound chip with its four sound channels
 * 
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SoundChip extends DefaultObservable implements WaveDataProducer, Serializable, Observer, FrequencyDataProducerOwner {

    /**
     * sound chip updates per second
     */
    public final static int UPDATES_PER_SECOND = 128;
    /**
     * the four sound channels of the Gameboy
     */
    private final SoundChannel[] soundChannels = new SoundChannel[4];
    /**
     * device the CPU works for
     */
    private final Gameboy gameboy;
    /**
     * sample rate, e.g. 44100 for 44.1 kHz
     */
    private final int sampleRate;
    /**
     * the CPU cycle when the next update of the sound chip is due
     */
    private long nextUpdate;
    /**
     * CPU cycles until the next update
     */
    private long updateCycles = (long) Gameboy.ORIGINAL_SPEED_CLASSIC / UPDATES_PER_SECOND;
    /**
     * buffer for mixing sound data
     */
    private final byte[] buffer;

    /**
     * Create a new sound chip working with a given sample rate
     * 
     * @param   gameboy gameboy the sound chip is connected to
     */
    public SoundChip(final Gameboy gameboy) {
        // store Gameboy instance and determine sample rate
        this.gameboy = gameboy;
        this.sampleRate = gameboy.getSoundSampleRate();
        // create sound channels
        this.soundChannels[0] = new SquareWaveChannel(this);
        this.soundChannels[1] = new SquareWaveChannel(this);
        this.soundChannels[2] = new VoluntaryWaveChannel(this);
        this.soundChannels[3] = new WhiteNoiseChannel(this);

        // determine the number of samples we have to create
        // we update every frame i.e. 256 times per second
        final int samples = getSampleRate() / UPDATES_PER_SECOND;

        this.buffer = new byte[samples * 2];
    }

    /**
     * Get the Gameboy's four sound channels
     * 
     * @return  sound channels
     */
    public final SoundChannel[] getSoundChannels() {
        return this.soundChannels;
    }

    /**
     * Get the CPU cycle when the next sound chip update is due
     * 
     * @return  CPU cycle
     */
    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    /**
     * Update the sound chip
     * 
     * @param   cycles  current CPU cycles
     */
    public void update(final long cycles) {
        // clear old contents of the buffer
        final byte[] buffer_ = this.buffer;

        for (int i = 0, to = buffer_.length; i < to; ++i) {
            buffer_[i] = 0;
        }

        // create the samples
        final SoundChannel[] soundChannels_ = this.soundChannels;

        for (int c = 0, to = soundChannels_.length; c < to; ++c) {
            final SoundChannel soundChannel = soundChannels_[c];

            if (soundChannel.isActive()) {
                soundChannel.update();
                soundChannel.mix(buffer_);
            }
        }

        // determine next update time
        this.nextUpdate = cycles + this.updateCycles;

        // notify observers to play the sample data
        setChanged(true);
        notifyObservers(buffer_);
    }

    // implementation of the WaveDataProducer interface
    public int getSampleRate() {
        return this.sampleRate;
    }

    public int getBitsPerSample() {
        return 8;
    }

    public int getChannels() {
        return 2;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.sampleRate);
        SerializationUtils.serialize(out, this.soundChannels);
        out.writeLong(this.nextUpdate);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        if (this.sampleRate != in.readInt()) {
            throw new IllegalStateException("Sample rate of the emulator does not match the saved sample rate!");
        }

        SerializationUtils.deserialize(in, this.soundChannels);
        this.nextUpdate = in.readLong();
    }

    // implementation of the Observer interface
    public void update(final Object observed, final Object arg) {
        // we get informed about a new CPU speed?
        if (observed == this.gameboy.getCPU() && arg instanceof Long) {
            // then re-calculate the number of cycles that pass between updates of the sound chip
            final long newSpeed = ((Long) arg).longValue();

            this.updateCycles = newSpeed / UPDATES_PER_SECOND;
        }
    }

    // implementation of the FrequencyDataProducerOwner interface
    public int getFrequencyDataProducerCount() {
        return this.soundChannels.length;
    }

    public FrequencyDataProducer getFrequencyDataProducers(final int n) {
        return this.soundChannels[n];
    }
}
