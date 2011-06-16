/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.emulation.FrequencyDataProducer;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base class for a single sound channel of the Gameboy
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class SoundChannel implements Serializable, FrequencyDataProducer {

    /**
     * left output terminal
     */
    public final static int LEFT = 0;
    /**
     * right output terminal
     */
    public final static int RIGHT = 1;
    /**
     * maximum output volume
     */
    protected final static int MAX_VOLUME = 0x0f;
    /**
     * sound chip we work for
     */
    protected final SoundChip sound;
    /**
     * indicates whether the left & right channel are active;
     */
    private boolean active[] = new boolean[2];
    /**
     * the length of the current sound
     */
    protected int length = 0;
    /**
     * counts the number of created samples
     */
    protected int samples = 0;
    /**
     * do we repeat the sound after its length has expired?
     */
    protected boolean isRepeat = false;
    /**
     * sound frequency in Hz
     */
    protected int frequency;
    /**
     * initial frequency when the sound was started
     */
    protected int startFrequency;
    /**
     * current wave amplitude
     */
    protected int volume = 0;
    /**
     * current wave amplitude
     */
    protected int startVolume = 0;
    /**
     * internal counter we need to determine the wave form
     */
    protected int audioIndex = 0;

    /**
     * Create a new sound channel for a given sound chip
     * 
     * @param   sound   sound chip we work for
     */
    protected SoundChannel(final SoundChip sound) {
        this.sound = sound;
    }

    /**
     * Is the sound channel still active?
     * 
     * @return  true if the channel is active
     */
    public boolean isActive() {
        return this.length > 0 || this.isRepeat;
    }

    /**
     * Does the sound channel produce sound on either of its terminals?
     *
     * @return  true if the channel is active and at least one of its two terminals is active
     */
    public final boolean hasSound() {
        return isActive() && (isTerminalActive(LEFT) || isTerminalActive(RIGHT));
    }

    /**
     * Is a given output terminal active?
     * 
     * @param   terminal    LEFT or RIGHT output terminal
     * @return  true if the output terminal is active
     */
    public final boolean isTerminalActive(final int terminal) {
        return this.active[terminal];
    }

    /**
     * Activate output for a given output terminal
     * 
     * @param   terminal    LEFT or RIGHT output terminal
     * @param   active  true to activate the sound, false to deactivate
     */
    public final void setTerminalActive(final int terminal, final boolean active) {
        this.active[terminal] = active;
    }

    /**
     * Get the sound length
     * 
     * @return  sound length in 1/256th seconds
     */
    public final int getLength() {
        return (this.length << 8) / SoundChip.UPDATES_PER_SECOND;
    }

    /**
     * Set the sound length
     * 
     * @param   length  new length in 1/256th seconds
     */
    public final void setLength(final int length) {
        this.length = (length * SoundChip.UPDATES_PER_SECOND) >> 8;
        this.startFrequency = this.frequency;
        this.startVolume = this.volume;
    }

    /**
     * Does the sound get repeated after its length has expired?
     * 
     * @return  true if the sound gets repeated
     */
    public final boolean isRepeat() {
        return this.isRepeat;
    }

    /**
     * Set whether the sound gets repeated when its length has expired
     * 
     * @param   isRepeat    true to repeat the sound
     */
    public final void setRepeat(final boolean isRepeat) {
        this.isRepeat = isRepeat;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.active);
        out.writeInt(this.audioIndex);
        out.writeInt(this.volume);
        out.writeInt(this.startVolume);
        out.writeInt(this.frequency);
        out.writeInt(this.startFrequency);
        out.writeInt(this.length);
        out.writeInt(this.samples);
        out.writeBoolean(this.isRepeat);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.active);
        this.audioIndex = in.readInt();
        this.volume = in.readInt();
        this.startVolume = in.readInt();
        this.frequency = in.readInt();
        this.startVolume = in.readInt();
        this.length = in.readInt();
        this.samples = in.readInt();
        this.isRepeat = in.readBoolean();
    }

    // to be implemented by subclasses
    /**
     * Update the sound channel
     */
    public abstract void update();

    /**
     * Mix audio data from this channel in the given buffer
     * 
     * @param   buffer  buffer to add data to
     */
    public abstract void mix(final byte[] buffer);
}
