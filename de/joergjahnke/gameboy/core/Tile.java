/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.ui.Color;
import de.joergjahnke.common.util.LRUCache;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements a tile displayed by the Gameboy's video chip.
 * Each tile has 64 variants for the 16 color palettes times the 4 possible
 * alignments of a tile.
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Tile implements Serializable {

    /**
     * transparent pixel color
     */
    public final static int TRANSPARENT = 0x00000000;
    /**
     * tile variants (16 color palettes x 2 horizontal variants x 2 vertical variants)
     */
    private final static int VARIANTS = 64;
    /**
     * We also recalculate the tile size when the tile gets invalidated?
     * If we want to dynamically change the tile size while running the emulator then we need to set this to true.
     */
    private final static boolean RECALCULATE_SIZE_ON_INVALIDATE = false;
    /**
     * do we use RGB data caching?
     */
    private static boolean useCache = false;
    /**
     * size of the RGB data cache
     */
    private static int cacheSize = 0;
    /**
     * here we cache some pre-calculated tile RGB data
     */
    private static LRUCache tileCache = null;
    /**
     * video chip we belong to
     */
    protected final VideoChip video;
    /**
     * RGB data of the 64 tile variants
     */
    private final int[][] pixels = new int[VARIANTS][];
    /**
     * raw tile data without colors or other attributes
     */
    private final TileData tileData;
    /**
     * scaled width
     */
    private int scaledWidth;
    /**
     * scaled height
     */
    private int scaledHeight;
    /**
     * are all variants of the tile invalid?
     */
    private boolean areAllVariantsInvalid = true;


    static {
        // we initialize the cache on start, the results may be overwritten by a later call to initializeCache
        initializeCache();
    }

    /**
     * Create a new tile
     * 
     * @param   video   the video chip we belong to
     * @param   tileData    tile data instance
     */
    public Tile(final VideoChip video, final TileData tileData) {
        this.video = video;
        this.tileData = tileData;
        recalculateScaledWidth();
        recalculateScaledHeight();
    }

    /**
     * Create a new tile
     * 
     * @param   video   the video chip we belong to
     * @param   tileDataAdr memory address of the tile data
     */
    public Tile(final VideoChip video, final int tileDataAdr) {
        this(video, new TileData(video, tileDataAdr));
    }

    /**
     * Clear the current contents of the tile cache
     */
    public static void resetCache() {
        if (null != tileCache) {
            tileCache.clear();
        }
    }

    /**
     * Initialize the tile cache, determining its best size based on the free memory
     */
    public static void initializeCache() {
        final long freeMem = Runtime.getRuntime().freeMemory();

        useCache = freeMem >= 1 << 18;
        cacheSize = freeMem < 1 << 18 ? 0 : freeMem < 1 << 19 ? 100 : freeMem < 2 << 20 ? 500 : 1000;
        tileCache = cacheSize > 1 ? new LRUCache(cacheSize) : null;
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
        return VideoChip.TILE_HEIGHT;
    }

    /**
     * Get the scaled tile width
     * 
     * @return  scaled width in pixels
     */
    public final int getScaledWidth() {
        return this.scaledWidth;
    }

    /**
     * Get the scaled tile height
     * 
     * @return  scaled height in pixels
     */
    public final int getScaledHeight() {
        return this.scaledHeight;
    }

    /**
     * Recalculate the scaled tile width
     */
    private void recalculateScaledWidth() {
        this.scaledWidth = (getWidth() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * Recalculate the scaled tile height
     */
    private void recalculateScaledHeight() {
        this.scaledHeight = (getHeight() * this.video.getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
    }

    /**
     * Set the tile with all its variants as invalid
     */
    public final void invalidate() {
        tileData.invalidate();

        if (!this.areAllVariantsInvalid) {
            // we also have to recalculate the tile's RGB data
            final int[][] pixels_ = this.pixels;

            for (int i = 0, to = pixels_.length; i < to; ++i) {
                pixels_[i] = null;
            }

            this.areAllVariantsInvalid = true;
        }
        if (RECALCULATE_SIZE_ON_INVALIDATE) {
            recalculateScaledWidth();
            recalculateScaledHeight();
        }
    }

    /**
     * Invalidate a palette of this tile
     * 
     * @param   palette palette (0-7) to invalidate
     */
    public final void invalidatePalette(final int palette) {
        for (int i = 0, p = palette; i < 4; ++i, p += 16) {
            this.pixels[p] = null;
        }
    }

    /**
     * Get pixel data of the tile
     * 
     * @param   attributes  attributes of the tile variant
     * @param   isSprite    true if we want to retrieve sprite pixels, fals for normal tiles
     * @return  rgb data 
     */
    public final int[] getPixels(final int attributes, final boolean isSprite) {
        // get pre-calculated pixels for this variant
        final int variant = (isSprite ? 8 : 0) + (attributes & 0x07) + ((attributes & 0x60) >> 1);
        final int[] variantPixels = this.pixels[variant];

        // calculate pixels if necessary
        return variantPixels != null ? variantPixels : (this.pixels[variant] = createPixels(attributes, isSprite));
    }

    /**
     * Create pixels for the tile
     * 
     * @param   attributes  attributes of the tile variant
     * @param   isSprite    true if we want to retrieve sprite pixels, fals for normal tiles
     * @return  rgb data 
     */
    private int[] createPixels(final int attributes, final boolean isSprite) {
        // check whether we can use cached data
        final ColorPalette palette = this.video.getColorPalettes()[(attributes & 0x07) + (isSprite ? VideoChip.PALETTE_SPRITES : VideoChip.PALETTE_BACKGROUND)];
        int[] result = null;
        Integer hc = null;

        if (useCache) {
            hc = new Integer(palette.hashCode() ^ tileData.hashCode() ^ attributes);
            result = (int[]) tileCache.get(hc);
        }

        if (result == null) {
            // we cache some variables locally for better performance
            final int height_ = getHeight(),  width_ = getWidth();
            final byte[][] colorIdxs_ = tileData.getColorIndexes();

            // create new empty RGB data space
            final int[] rgbData_ = new int[height_ * width_];

            // get the colors for all pixels
            final boolean isFlipHorizontally = (attributes & (1 << 5)) != 0;
            final boolean isFlipVertically = (attributes & (1 << 6)) != 0;
            final int cyAdd = (isFlipVertically ? -1 : 1),  cxAdd = isFlipHorizontally ? -1 : 1;

            for (int y = 0, cy = isFlipVertically ? height_ - 1 : 0, yidx = 0; y < height_; ++y, cy += cyAdd, yidx += width_) {
                for (int x = 0, cx = isFlipHorizontally ? width_ - 1 : 0; x < width_; ++x, cx += cxAdd) {
                    final int colIdx = colorIdxs_[cy][cx];

                    if (isSprite && colIdx == 0) {
                        rgbData_[yidx + x] = TRANSPARENT;
                    } else {
                        rgbData_[yidx + x] = palette.getColor(colIdx);
                    }
                }
            }

            result = scale(rgbData_);

            // cache the result for later use
            if (useCache) {
                tileCache.put(hc, result);
            }
        }

        // at least the requested variant is valid afterwards
        this.areAllVariantsInvalid = false;

        return result;
    }

    /**
     * Get a scaled version of the source image
     * 
     * @param   source  source pixels
     * @return  scaled version
     */
    private int[] scale(final int[] source) {
        final int sh = getScaledHeight();
        final int h = getHeight();

        if (sh == h) {
            return source;
        } else {
            final int scalingType = this.video.getScalingType();
            final int sw = getScaledWidth();
            final int w = getWidth();
            final int[] scaled = new int[sh * sw];
            final int inc1024 = (1 << (2 * VideoChip.SCALING_MULTIPLIER_BITS)) / this.video.getScaling();

            for (int y = 0, sY1024 = inc1024 >> 4, maxSY = h - 1, yidx = 0; y < sh; ++y, sY1024 += inc1024, yidx += sw) {
                for (int x = 0, sX1024 = inc1024 >> 4, maxSX = w - 1; x < sw; ++x, sX1024 += inc1024) {
                    // the next pixel position to get colors from
                    final int sX1024Next = sX1024 + inc1024;
                    final int sY1024Next = sY1024 + inc1024;

                    switch (scalingType) {
                        case VideoChip.SCALING_QUALITY: {
                            final int x1 = (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS);
                            final int x2 = Math.min(maxSX, sX1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                            final int y2 = Math.min(maxSY, sY1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);

                            // get four pixel colors from the source array to mix
                            final int offsetY1 = (sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w;
                            final int offsetY2 = y2 * w;
                            int col11 = source[offsetY1 + x1];
                            int col12 = source[offsetY1 + x2];
                            int col21 = source[offsetY2 + x1];
                            int col22 = source[offsetY2 + x2];

                            // we need a special handling for transparent (sprite) pixels
                            if (col11 == TRANSPARENT) {
                                col11 = col12;
                            } else if (col12 == TRANSPARENT) {
                                col12 = col11;
                            }
                            if (col21 == TRANSPARENT) {
                                col21 = col22;
                            } else if (col22 == TRANSPARENT) {
                                col22 = col21;
                            }
                            if (col11 == TRANSPARENT) {
                                col11 = col21;
                            } else if (col21 == TRANSPARENT) {
                                col21 = col11;
                            }

                            // determine fractions of these colors
                            final int fracX = (VideoChip.SCALING_MULTIPLIER - (sX1024 % VideoChip.SCALING_MULTIPLIER));
                            final int fracY = (VideoChip.SCALING_MULTIPLIER - (sY1024 % VideoChip.SCALING_MULTIPLIER));
                            final int fraction11 = (fracX * fracY) >> VideoChip.SCALING_MULTIPLIER_BITS;
                            final int fraction12 = ((VideoChip.SCALING_MULTIPLIER - fracX) * fracY) >> VideoChip.SCALING_MULTIPLIER_BITS;
                            final int fraction21 = (fracX * (VideoChip.SCALING_MULTIPLIER - fracY)) >> VideoChip.SCALING_MULTIPLIER_BITS;
                            final int fraction22 = ((VideoChip.SCALING_MULTIPLIER - fracX) * (VideoChip.SCALING_MULTIPLIER - fracY)) >> VideoChip.SCALING_MULTIPLIER_BITS;

                            // mix the colors and set the mixed color as new pixel
                            scaled[yidx + x] = Color.mix(col11, fraction11, col12, fraction12, col21, fraction21, col22, fraction22);
                            break;
                        }
                        case VideoChip.SCALING_AVERAGING: {
                            final int x1 = (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS);
                            final int x2 = Math.min(maxSX, sX1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                            final int y2 = Math.min(maxSY, sY1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                            final int offsetY1 = (sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w;
                            final int offsetY2 = y2 * w;
                            int col11 = source[offsetY1 + x1];
                            int col12 = source[offsetY1 + x2];
                            int col21 = source[offsetY2 + x1];
                            int col22 = source[offsetY2 + x2];

                            // we need a special handling for transparent (sprite) pixels
                            if (col11 == TRANSPARENT) {
                                col11 = col12;
                            } else if (col12 == TRANSPARENT) {
                                col12 = col11;
                            }
                            if (col21 == TRANSPARENT) {
                                col21 = col22;
                            } else if (col22 == TRANSPARENT) {
                                col22 = col21;
                            }
                            if (col11 == TRANSPARENT) {
                                col11 = col21;
                            } else if (col21 == TRANSPARENT) {
                                col21 = col11;
                            }

                            scaled[yidx + x] = Color.mix(col11, col12, col21, col22);
                            break;
                        }
                        case VideoChip.SCALING_PLUS50PERCENT: {
                            if (x % 3 == 1 || y % 3 == 1) {
                                if (x % 3 == 1 && y % 3 == 1) {
                                    final int x1 = (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int x2 = Math.min(maxSX, sX1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int y2 = Math.min(maxSY, sY1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int offsetY1 = (sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w;
                                    final int offsetY2 = y2 * w;
                                    int col11 = source[offsetY1 + x1];
                                    int col12 = source[offsetY1 + x2];
                                    int col21 = source[offsetY2 + x1];
                                    int col22 = source[offsetY2 + x2];

                                    // we need a special handling for transparent (sprite) pixels
                                    if (col11 == TRANSPARENT) {
                                        col11 = col12;
                                    } else if (col12 == TRANSPARENT) {
                                        col12 = col11;
                                    }
                                    if (col21 == TRANSPARENT) {
                                        col21 = col22;
                                    } else if (col22 == TRANSPARENT) {
                                        col22 = col21;
                                    }
                                    if (col11 == TRANSPARENT) {
                                        col11 = col21;
                                    } else if (col21 == TRANSPARENT) {
                                        col21 = col11;
                                    }

                                    scaled[yidx + x] = Color.mix(col11, col12, col21, col22);
                                } else if (x % 3 == 1) {
                                    final int x1 = (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int x2 = Math.min(maxSX, sX1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int offsetY1 = (sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w;
                                    final int col11 = source[offsetY1 + x1];
                                    final int col12 = source[offsetY1 + x2];

                                    if (col11 == TRANSPARENT) {
                                        scaled[yidx + x] = col12;
                                    } else if (col12 == TRANSPARENT) {
                                        scaled[yidx + x] = col11;
                                    } else {
                                        scaled[yidx + x] = Color.mix(col11, col12);
                                    }
                                } else {
                                    final int x1 = (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int y2 = Math.min(maxSY, sY1024Next >> VideoChip.SCALING_MULTIPLIER_BITS);
                                    final int offsetY1 = (sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w;
                                    final int offsetY2 = y2 * w;
                                    final int col11 = source[offsetY1 + x1];
                                    final int col21 = source[offsetY2 + x1];

                                    if (col11 == TRANSPARENT) {
                                        scaled[yidx + x] = col21;
                                    } else if (col21 == TRANSPARENT) {
                                        scaled[yidx + x] = col11;
                                    } else {
                                        scaled[yidx + x] = Color.mix(col11, col21);
                                    }
                                }
                            } else {
                                scaled[yidx + x] = source[(sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w + (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS)];
                            }
                            break;
                        }
                        default:
                            scaled[yidx + x] = source[(sY1024 >> VideoChip.SCALING_MULTIPLIER_BITS) * w + (sX1024 >> VideoChip.SCALING_MULTIPLIER_BITS)];
                    }
                }
            }

            return scaled;
        }
    }

    /**
     * Draw (portions of) a tile
     * 
     * @param   tline   (unscaled) tile line to draw
     * @param   vx  (unscaled) x-position to draw at
     * @param   vy  (unscaled) y-position to draw at
     * @param   attributes  attributes of the tile variant to draw
     */
    public final void drawLine(final int tline, final int vx, final int vy, final int attributes) {
        // get pixels to draw
        final int[] tilePixels = getPixels(attributes, false);
        // cache some variables for better performance
        final VideoChip video_ = this.video;
        final int[] videoPixels = video_.pixels;
        final int scaling1024 = video_.scalingMult;
        final int svw = video_.scaledWidth;
        final int stw = this.scaledWidth;
        // determine scaled start and stop positions
        final int svx = (vx * scaling1024) >> VideoChip.SCALING_MULTIPLIER_BITS;
        final int svy = (vy * scaling1024) >> VideoChip.SCALING_MULTIPLIER_BITS;
        final int svystop = ((vy + 1) * scaling1024) >> VideoChip.SCALING_MULTIPLIER_BITS;

        // determine number of pixels we copy per line, number of lines plus position we start in the tile and target array
        int columns = VideoChip.TILE_WIDTH, bppos = vx, bpstart = 0;
        int scolumns = stw, stpos = ((tline * scaling1024) >> VideoChip.SCALING_MULTIPLIER_BITS) * stw, svpos = svy * svw + svx;

        if (vx < 0) {
            columns += vx;
            scolumns += svx;
            bppos -= vx;
            bpstart = -vx;
            stpos -= svx;
            svpos -= svx;
        } else if (vx > VideoChip.SCREEN_WIDTH - VideoChip.TILE_WIDTH) {
            columns = VideoChip.SCREEN_WIDTH - vx;
            scolumns = svw - svx;
        }

        // copy tile data
        for (int svyi = svy; svyi < svystop; ++svyi) {
            System.arraycopy(tilePixels, stpos, videoPixels, svpos, scolumns);
            stpos += stw;
            svpos += svw;
        }

        // update background priorities
        System.arraycopy(this.tileData.colorsIdxs[tline], bpstart, video_.backgroundPriorities, bppos, columns);
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeInt(this.scaledWidth);
        out.writeInt(this.scaledHeight);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.scaledWidth = in.readInt();
        this.scaledHeight = in.readInt();
        // have tile data recalculated
        this.areAllVariantsInvalid = false;
        invalidate();
    }
}
