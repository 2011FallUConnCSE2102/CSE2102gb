/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the Gameboy's sound channels 1 & 2, which offer quadrangular wave patterns with sweep and envelope functions
 * 
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class SquareWaveChannel extends SoundChannel {

    /**
     * percentage of time the amplitude has positive values in a wave
     */
    private int dutyPercent = 50;
    /**
     * sound frequency in Gameboy format (0-2047)
     */
    private int frequencyGB;
    /**
     * number of envelope steps
     */
    private int envelopeSweeps;
    /**
     * steps until next envelope sweep
     */
    private int envelopeSweepsLeft;
    /**
     * multiplier giving the direction of the envelope: -1 for decrease, +1 for increase
     */
    private int envelopeDirection = -1;
    /**
     * sweep time in audio updates
     */
    private int sweepTime;
    /**
     * steps until next sweep
     */
    private int sweepTimeLeft;
    /**
     * number of sweep shifts
     */
    private int sweepShift;
    /**
     * multiplier giving the direction of the sweep: -1 for decrease, +1 for increase
     */
    private int sweepDirection = 1;

    /**
     * Create a new square wave channel for the given sound chip
     * 
     * @param   sound   the sound chip we work for
     */
    public SquareWaveChannel(final SoundChip sound) {
        super(sound);
    }

    /**
     * Set the sweep parameters
     * 
     * @param   time    sweep time in 1/128th seconds
     * @param   decrease    true to decrease the amplitude, false to increase it
     * @param   shift   sweep shift
     */
    public void setSweep(final int time, final boolean decrease, final int shift) {
        this.sweepTime = this.sweepTimeLeft = ((time + 1) * SoundChip.UPDATES_PER_SECOND) >> 7;
        this.sweepShift = shift;
        this.sweepDirection = decrease ? -1 : 1;
    }

    /**
     * Set wave pattern duty
     * 
     * @param   duty    value between 0 and 3
     */
    public void setWavePatternDuty(final int duty) {
        this.dutyPercent = duty == 0 ? 13 : duty == 1 ? 25 : duty == 2 ? 50 : 75;
    }

    /**
     * Set envelope parameters
     * 
     * @param   initialVolume   initial sound volume (0-15)
     * @param   increase    true to increase the frequency on envelope sweeps, false to decrease
     * @param   envelopeSweeps  number of envelope sweeps in 1/64th seconds
     */
    public void setVolumeEnvelope(final int initialVolume, final boolean increase, final int envelopeSweeps) {
        this.volume = initialVolume;
        this.envelopeSweeps = this.envelopeSweepsLeft = (envelopeSweeps * SoundChip.UPDATES_PER_SECOND) >> 6;
        this.envelopeDirection = increase ? 1 : -1;
    }

    /**
     * Set the sound frequency
     * 
     * @param   frequencyGB frequency in Gameboy format (0-2047)
     */
    public void setFrequency(final int frequencyGB) {
        this.frequencyGB = frequencyGB;
        this.frequency = 131072 / (2048 - frequencyGB);
    }

    // implementation of the abstract methods of class SoundChannel
    public void update() {
        --this.length;

        // modify frequency if sweep time has passed
        if (this.sweepTime > 0) {
            --this.sweepTimeLeft;
            if (this.sweepTimeLeft <= 0) {
                setFrequency((this.frequencyGB + this.sweepDirection * (this.frequencyGB >> this.sweepShift)) & 0x7ff);
                this.sweepTimeLeft = this.sweepTime;
            }
        }

        // modify amplitude if envelope sweep time has passed
        if (this.envelopeSweeps > 0) {
            --this.envelopeSweepsLeft;
            if (this.envelopeSweepsLeft <= 0) {
                this.volume = Math.min(MAX_VOLUME, Math.max(0, this.volume + this.envelopeDirection));
                this.envelopeSweepsLeft = this.envelopeSweeps;
            }
        }
    }

    public void mix(final byte[] buffer) {
        final int sampleRate = this.sound.getSampleRate();
        final boolean isLeftActive = isTerminalActive(LEFT);
        final boolean isRightActive = isTerminalActive(RIGHT);
        final int target = this.dutyPercent * sampleRate / 100;

        for (int i = 0, to = buffer.length; i < to; i += 2) {
            // determine sample
            final byte sample = (byte) (this.audioIndex >= target ? this.volume << 1 : -this.volume << 1);

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
        return 80;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.dutyPercent);
        out.writeInt(this.frequencyGB);
        out.writeInt(this.envelopeSweeps);
        out.writeInt(this.envelopeSweepsLeft);
        out.writeInt(this.envelopeDirection);
        out.writeInt(this.sweepTime);
        out.writeInt(this.sweepTimeLeft);
        out.writeInt(this.sweepShift);
        out.writeInt(this.sweepDirection);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.dutyPercent = in.readInt();
        this.frequencyGB = in.readInt();
        this.envelopeSweeps = in.readInt();
        this.envelopeSweepsLeft = in.readInt();
        this.envelopeDirection = in.readInt();
        this.sweepTime = in.readInt();
        this.sweepTimeLeft = in.readInt();
        this.sweepShift = in.readInt();
        this.sweepDirection = in.readInt();
    }
}
