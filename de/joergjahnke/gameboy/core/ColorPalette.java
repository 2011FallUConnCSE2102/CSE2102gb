/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One of the Gameboy's color palette.
 * Each palette consists of four colors.
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ColorPalette implements Serializable {

    /**
     * number of colors per palette
     */
    public final static int COLORS_PER_PALETTE = 4;
    /**
     * the video chip this palette is attached to
     */
    private final VideoChip video;
    /**
     * the colors of this palette
     */
    private final int[] colors = new int[4];
    /**
     * pre-calculated hashcode
     */
    private int colorsCode = Integer.MAX_VALUE;

    /**
     * Create a new color palette.
     * All colors are initially set to white.
     * 
     * @param   video   the video chip the palette is attached to
     */
    public ColorPalette(final VideoChip video) {
        // initialize all colors as white
        this(video, 0xffffffff);
    }

    /**
     * Create a new color palette which is initialized with a given color value.
     * 
     * @param   video   the video chip the palette is attached to
     * @param   color   the RGB color to initialize all colors of the palette with
     */
    public ColorPalette(final VideoChip video, final int color) {
        this.video = video;
        // initialize all colors with the given color
        for (int i = 0; i < this.colors.length; ++i) {
            this.colors[i] = color;
        }
    }

    /**
     * Get a color from this palette
     * 
     * @param   n   color index
     * @return  RGB value
     */
    public final int getColor(final int n) {
        return this.colors[n];
    }

    /**
     * Set a color in this palette
     * 
     * @param   n   color index
     * @param   col RGB value of the new color
     */
    public final void setColor(final int n, final int col) {
        if (col != this.colors[n]) {
            this.colors[n] = col;

            // the palette hashcode is now invalid
            this.colorsCode = Integer.MAX_VALUE;

            // ensure that tiles get recalculated due to the color change and all lines repainted
            this.video.invalidateTiles();
            this.video.invalidateLines();
        }
    }

    /**
     * Get a unique code for this palette
     * 
     * @return  unique code
     */
    public final int hashCode() {
        if (this.colorsCode == Integer.MAX_VALUE) {
            int hc = 0;

            for (int i = 0, shift = 0; i < COLORS_PER_PALETTE; ++i, shift += 2) {
                hc ^= (this.colors[i] << shift);
            }

            this.colorsCode = hc;
        }

        return this.colorsCode;
    }

    /**
     * Check whether this ColorPalette equals another one
     *
     * @return  true if the given object is another ColorPalette object with the same hash code as this one
     */
    public boolean equals(final Object obj) {
        return obj != null && getClass() == obj.getClass() && hashCode() == ((ColorPalette) obj).hashCode();
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.colors);
        out.writeInt(this.colorsCode);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.colors);
        this.colorsCode = in.readInt();
    }
}
