/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the Gameboy's sound channel 4, which offers voluntary wave patterns from wave RAM
 * 
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class VoluntaryWaveChannel extends SoundChannel {

    /**
     * wave pattern buffer size
     */
    private final static int BUFFER_SIZE = 32;
    /**
     * current output level in percent of the maximum
     */
    private int volumePercent = 0;
    /**
     * is the sound switched on?
     */
    private boolean active = true;
    /**
     * 32 4-bit samples to play
     */
    private int[] wavePatterns = new int[BUFFER_SIZE];

    /**
     * Create a new voluntary wave channel for the given sound chip
     * 
     * @param   sound   the sound chip we work for
     */
    public VoluntaryWaveChannel(final SoundChip sound) {
        super(sound);
    }

    public boolean isActive() {
        return super.isActive() && this.active;
    }

    /**
     * Activate or deactivate the channel
     * 
     * @param   active  true to active the channel, false to deactivate it
     */
    public void setActive(final boolean active) {
        this.active = active;
    }

    /**
     * Set volume
     * 
     * @param   volume  new volume in percent of the maximum volume, e.g. 100 for maximum
     */
    public void setOutputLevel(final int volume) {
        this.volumePercent = volume;
        this.volume = MAX_VOLUME * volume / 100;
    }

    /**
     * Set the sound frequency
     * 
     * @param   frequencyGB frequency in Gameboy format (0-2047)
     */
    public void setFrequency(final int frequencyGB) {
        this.frequency = 65536 / (2048 - frequencyGB);
    }

    /**
     * Store a wave pattern
     * 
     * @param   idx pattern id (0-15)
     * @param   value   contains 2 4-bit samples, the higher 4 bits for the left channel, the lower bits for the right channel
     */
    public void setWavePattern(final int idx, final byte value) {
        this.wavePatterns[idx << 1] = value >> 4;
        this.wavePatterns[(idx << 1) + 1] = value & 0x0f;
    }

    // implementation of the abstract methods of class SoundChannel
    public void update() {
        --this.length;
    }

    public void mix(final byte[] buffer) {
        final int sampleRate = this.sound.getSampleRate();
        final boolean isLeftActive = isTerminalActive(LEFT);
        final boolean isRightActive = isTerminalActive(RIGHT);

        for (int i = 0, to = buffer.length; i < to; i += 2) {
            // determine sample
            final int idx = (this.audioIndex % sampleRate) * BUFFER_SIZE / sampleRate;
            final int sample = this.active ? (this.wavePatterns[idx] * this.volumePercent / 100) << 1 : 0;

            // apply sample to relevant output terminals
            if (isLeftActive) {
                buffer[i + LEFT] += sample;
            }
            if (isRightActive) {
                buffer[i + RIGHT] += sample;
            }

            // proceed to next sample
            this.audioIndex += this.frequency;
            this.audioIndex %= sampleRate;
        }
    }

    // implementation of the FrequencyDataProducer interface
    public final int getFrequency() {
        return Math.min(12544, this.startFrequency);
    }

    public final int getVolume() {
        return hasSound() && this.length > 0 ? this.startVolume * 100 / MAX_VOLUME : 0;
    }

    public final int getType() {
        return 0;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.volumePercent);
        out.writeBoolean(this.active);
        SerializationUtils.serialize(out, wavePatterns);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.volumePercent = in.readInt();
        this.active = in.readBoolean();
        SerializationUtils.deserialize(in, wavePatterns);
    }
}
