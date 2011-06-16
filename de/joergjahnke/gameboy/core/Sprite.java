/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.io.Serializable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A sprite, which is a moveable tile
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Sprite implements Serializable {

    /**
     * video chip we belong to
     */
    protected final VideoChip video;
    /**
     * tile number to use
     */
    private int tileId = 0;
    /**
     * the tile we paint
     */
    private Tile tile = null;
    /**
     * the sprite attributes
     */
    private int attributes;
    /**
     * do the attributes indicate that this sprite has priority?
     */
    private boolean hasPriority = true;
    /**
     * sprite coordinates
     */
    private int x = -8,  y = -16;
    /**
     * is the sprite within the screen bounds?
     */
    private boolean isVisible = false;
    /**
     * does the sprite affect other sprites priorities?
     */
    private boolean isDisplayable = false;

    /**
     * Create a new sprite
     * 
     * @param   video   the video chip we belong to
     */
    public Sprite(final VideoChip video) {
        this.video = video;
    }

    /**
     * Modify the sprite's pixel data
     * 
     * @param   n   tile number to use for the sprite
     */
    public final void setTile(final int n) {
        this.tileId = n + ((this.attributes & 0x08) != 0 ? VideoChip.NUM_TILES / 2 : 0);
        this.tile = this.video.getTiles()[this.tileId];
    }

    /**
     * Update the sprite's tile.
     * This method should be used after changes to the tile's number, attributes or after the
     * general sprite size has been changed.
     */
    public final void updateTile() {
        setTile(this.tileId & (this.video.getSpriteHeight() > VideoChip.TILE_HEIGHT ? 0xfe : 0xff));
    }

    /**
     * Modify the sprite's attributes
     * 
     * @param   attributes  new sprite attributes
     */
    public final void setAttributes(final int attributes) {
        if (attributes != this.attributes) {
            this.attributes = attributes;
            this.hasPriority = (this.attributes & 0x80) == 0;
            // the tile might have changed due to a different tile memory area being used
            updateTile();
        }
    }

    /**
     * Get the sprites x-coordinate
     * 
     * @return  pixel position
     */
    public final int getX() {
        return this.x;
    }

    /**
     * Set the sprites x-coordinate
     * 
     * @param   x   new pixel position
     */
    public final void setX(final int x) {
        this.x = x;
        checkVisible();
    }

    /**
     * Get the sprites y-coordinate
     * 
     * @return  pixel position
     */
    public final int getY() {
        return this.y;
    }

    /**
     * Set the sprites y-coordinate
     * 
     * @param   y   new pixel position
     */
    public final void setY(final int y) {
        this.y = y;
        checkVisible();
    }

    /**
     * Get the sprites x-coordinate when scaled
     * 
     * @return  pixel position
     */
    public final int getScaledX() {
        return (getX() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * Get the sprites y-coordinate when scaled
     * 
     * @return  pixel position
     */
    public final int getScaledY() {
        return (getY() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * Get the tile width
     * 
     * @return  width in pixels
     */
    public final int getWidth() {
        return this.tile.getWidth();
    }

    /**
     * Get the tile height
     * 
     * @return  height in pixels
     */
    public final int getHeight() {
        return this.video.getSpriteHeight();
    }

    /**
     * Get the scaled tile width
     * 
     * @return  width in pixels
     */
    public final int getScaledWidth() {
        return (getWidth() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * Get the scaled tile height
     * 
     * @return  height in pixels
     */
    public final int getScaledHeight() {
        return (getHeight() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * does the sprite have priority over the background?
     * 
     * @return  true if the sprite has priority over the background and window
     */
    public final boolean hasPriority() {
        return this.hasPriority;
    }

    /**
     * Check whether the sprite is within the visible coordinates
     * 
     * @return  true if the sprite is visible
     */
    public final boolean isVisible() {
        return this.isVisible;
    }

    /**
     * Does the sprite affect other sprites priority by requesting to be displayed?
     * 
     * @return  true if the sprite is within the vertical bounds of the screen
     */
    public final boolean isDisplayable() {
        return this.isDisplayable;
    }

    /**
     * Check whether the sprite is within the visible cordinates
     */
    private final void checkVisible() {
        this.isDisplayable = this.y + VideoChip.TILE_HEIGHT * 2 >= 0 && this.y < VideoChip.SCREEN_HEIGHT;
        this.isVisible = this.isDisplayable && this.y + getHeight() >= 0 && this.x + getWidth() >= 0 && this.x < VideoChip.SCREEN_WIDTH;
    }

    /**
     * Draw (portions of) a sprite
     * 
     * @param   sline   (unscaled) sprite line to drawLine
     */
    public final void drawLine(final int sline) {
        // cache some variables for better performance
        final VideoChip video_ = this.video;
        final int scalingMult = video_.getScaling();
        final int[] videoPixels = video_.getRGBData();
        final byte[] backgroundPriorities_ = video_.backgroundPriorities;
        final boolean hasPriority_ = hasPriority() || video_.isHaveSpritesPriority();
        final int ssw = getScaledWidth();

        // determine sprite pixel data and an offset to the given line to draw, if we access the second tile of a large sprite
        final int[] spritePixels = sline < VideoChip.TILE_HEIGHT ? this.tile.getPixels(this.attributes, true) : video_.getTiles()[this.tileId + 1].getPixels(this.attributes, true);
        final int lineOffset = sline < VideoChip.TILE_HEIGHT ? 0 : VideoChip.TILE_HEIGHT;

        // determine scaled start and stop positions
        final int ssx = getScaledX();
        final int ssxstart = ssx < 0 ? -ssx : 0,  ssystart = ((sline - lineOffset) * scalingMult) >> VideoChip.SCALING_MULTIPLIER_BITS;
        final int svw = video_.getScaledWidth();
        final int svystart = ((getY() + sline) * scalingMult) >> VideoChip.SCALING_MULTIPLIER_BITS;
        final int svystop = ((getY() + sline + 1) * scalingMult) >> VideoChip.SCALING_MULTIPLIER_BITS;

        // iterate over all sprite pixels
        for (int ssyi = ssystart, svyi = svystart; svyi < svystop; ++ssyi, ++svyi) {
            final int offset = svyi * svw;

            for (int ssxi = ssxstart, svx = ssx + ssxstart, vx = Math.max(0, getX()) << VideoChip.SCALING_MULTIPLIER_BITS, vxinc = VideoChip.SCALING_MULTIPLIER / scalingMult; ssxi < ssw && svx < svw; ++ssxi, ++svx, vx += vxinc) {
                // get the sprite pixel color
                final int col = spritePixels[ssyi * ssw + ssxi];

                // we don't copy the transparent pixels of a sprite and we only paint if we have priority over the background already painted
                if (col != Tile.TRANSPARENT && (hasPriority_ || backgroundPriorities_[vx >> VideoChip.SCALING_MULTIPLIER_BITS] == 0)) {
                    videoPixels[offset + svx] = col;
                }
            }
        }
    }

    /**
     * Reset the sprite, making it invisible
     */
    public void reset() {
        this.x = -8;
        this.y = -16;
        this.isVisible = false;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.tileId);
        out.writeInt(this.attributes);
        out.writeBoolean(this.hasPriority);
        out.writeInt(this.x);
        out.writeInt(this.y);
        out.writeBoolean(this.isVisible);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        setTile(in.readInt());
        this.attributes = in.readInt();
        this.hasPriority = in.readBoolean();
        this.x = in.readInt();
        this.y = in.readInt();
        this.isVisible = in.readBoolean();
    }
}
