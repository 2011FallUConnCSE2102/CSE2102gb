/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

/**
 * The raw pixel data of a tile, without colors or attributes
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class TileData {

    /**
     * video chip we belong to
     */
    protected final VideoChip video;
    /**
     * tile data memory address
     */
    protected final int tileDataAdr;
    /**
     * tile height in pixels
     */
    private final int height;
    /**
     * indexed color per pixel inside the palette
     */
    protected byte[][] colorsIdxs;
    /**
     * hashcode over the color indexes
     */
    private int pixelsCode = Integer.MAX_VALUE;

    /**
     * Create a new tile data
     * 
     * @param   video   the video chip we belong to
     * @param   tileDataAdr memory address of the tile data
     */
    public TileData(final VideoChip video, final int tileDataAdr) {
        this(video, tileDataAdr, VideoChip.TILE_HEIGHT);
    }

    /**
     * Create a new tile data
     *
     * @param   video   the video chip we belong to
     * @param   tileDataAdr memory address of the tile data
     * @param   height  tile height in pixels, normally 8, but can be 16 for double-height sprites
     */
    public TileData(final VideoChip video, final int tileDataAdr, final int height) {
        this.video = video;
        this.tileDataAdr = tileDataAdr;
        this.height = height;
    }

    /**
     * Get the address where the tile data starts
     * 
     * @return  VRAM memory address
     */
    private int getTileDataAddress() {
        return this.tileDataAdr;
    }

    /**
     * Get the tile width
     * 
     * @return  width in pixels
     */
    public final int getWidth() {
        return VideoChip.TILE_WIDTH;
    }

    /**
     * Get the tile height
     * 
     * @return  height in pixels
     */
    public final int getHeight() {
        return this.height;
    }

    /**
     * Set the tile data as invalid
     */
    public final void invalidate() {
        // we have to recalculate the color indexes...
        this.colorsIdxs = null;
        this.pixelsCode = Integer.MAX_VALUE;
    }

    /**
     * Get color indexes of all pixels of the TileData
     * 
     * @return  array of colors
     */
    protected final byte[][] getColorIndexes() {
        // we can use the current pixels and don't have to recalculate?
        if (this.colorsIdxs == null) {
            // no, we have to reclaculate anew
            this.colorsIdxs = createColorIndexes();
        }

        return this.colorsIdxs;
    }

    /**
     * Create the color indexes of all pixels of the TileData
     * 
     * @return  color indexes of the pixels
     */
    private byte[][] createColorIndexes() {
        // cache some variables locally for faster access
        final byte[] vRAM = this.video.vRAM;
        final int height_ = getHeight();
        final int width_ = getWidth();

        // create new pixel array
        final byte[][] colorIdxs_ = new byte[height_][width_];

        // get the colors for all pixels
        int adr = getTileDataAddress() & (height_ == VideoChip.TILE_HEIGHT ? 0x3fff : 0x3ffe);

        for (int y = 0; y < height_; ++y) {
            final int data1 = vRAM[adr++] & 0xff;
            final int data2 = vRAM[adr++] & 0xff;

            for (int x = 0, mask = 0x80; x < width_; ++x, mask >>= 1) {
                colorIdxs_[y][x] = (byte) (((data1 & mask) != 0 ? 1 : 0) + ((data2 & mask) != 0 ? 2 : 0));
            }
        }

        return colorIdxs_;
    }

    /**
     * Get a unique code for this tile data
     * 
     * @return  unique code
     */
    public final int hashCode() {
        if (this.pixelsCode == Integer.MAX_VALUE) {
            int hc = 0;
            final byte[][] colorsIdxs_ = getColorIndexes();

            for (int y = 0, y2 = 0, toY = this.colorsIdxs.length; y < toY; ++y, y2 += 7) {
                final byte[] colorsLine = colorsIdxs_[y];

                for (int x = 0, x2 = 0, toX = colorsLine.length; x < toX; ++x, x2 += 19) {
                    hc ^= (colorsLine[x] << ((x2 + y2) % 20));
                }
            }

            this.pixelsCode = hc;
        }

        return this.pixelsCode;
    }

    /**
     * Check whether this TileData equals another one
     * 
     * @return  true if the given object is another TileData object with the same hash code as this one
     */
    public boolean equals(final Object obj) {
        return obj != null && getClass() == obj.getClass() && hashCode() == ((TileData) obj).hashCode();
    }
}
