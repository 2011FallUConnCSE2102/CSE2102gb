/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.lcdui;

import javax.microedition.lcdui.Image;

/**
 * Utility methods for image manipulation with the MIDP toolkit
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class ImageUtils {

    /**
     * Align the image to the left
     */
    public final static int ALIGN_LEFT = 1;
    /**
     * Align the image to the right
     */
    public final static int ALIGN_RIGHT = 2;
    /**
     * Align the image horizontally centered
     */
    public final static int ALIGN_HORIZ_CENTER = ALIGN_LEFT & ALIGN_RIGHT;
    /**
     * Align the image to the top
     */
    public final static int ALIGN_TOP = 4;
    /**
     * Align the image to the bottom
     */
    public final static int ALIGN_BOTTOM = 8;
    /**
     * Align the image vertically centered
     */
    public final static int ALIGN_VERT_CENTER = ALIGN_TOP & ALIGN_BOTTOM;
    /**
     * Align the image horizontally and vertically centered
     */
    public final static int ALIGN_CENTER = ALIGN_HORIZ_CENTER & ALIGN_VERT_CENTER;
    // bit mask for horizontal alignment
    private final static int ALIGN_MASK_HORIZ = ALIGN_LEFT & ALIGN_RIGHT;
    // bit mask for horizontal alignment
    private final static int ALIGN_MASK_VERT = ALIGN_TOP & ALIGN_BOTTOM;

    /** 
     * A new instance cannot be created
     */
    private ImageUtils() {
    }

    /**
     * Scale image to a given size
     *
     * @param   src image to scale
     * @param   width   target width
     * @param   height  target height
     * @param   useAntialiasing true to use antialiasing, which lets the result usually look better but takes some performance
     * @return  scaled image
     */
    public static Image scale(final Image src, final int width, final int height, final boolean useAntialiasing) {
        // get source dimensions
        final int srcw = src.getWidth();
        final int srch = src.getHeight();

        // no scaling to be done?
        if (srcw == width && srch == height) {
            // then return source image
            return src;
        } else {
            // initialize source buffer
            int buf[] = new int[srcw * srch];
            // initialize target buffer
            final int buf2[] = new int[width * height];

            // copy image data to source buffer
            src.getRGB(buf, 0, srcw, 0, 0, srcw, srch);

            // determine scaling
            final int scaleX = (srcw << 10) / width,  scaleY = (srch << 10) / height;

            // iterate over all pixels in the target image and determine pixel color
            for (int y = 0; y < height; ++y) {
                final int c1 = y * width;
                final int startY = y * scaleY,  endY = startY + scaleY;
                final int ceilY = Math.min((endY + 1023) >> 10, srch);

                for (int x = 0; x < width; ++x) {
                    if (useAntialiasing) {
                        final int startX = x * scaleX,  endX = startX + scaleX;
                        final int ceilX = Math.min((endX + 1023) >> 10, srcw);
                        int alpha = 0, r = 0, g = 0, b = 0;
                        int pixCount = 0;

                        for (int yy = startY >> 10; yy < ceilY; ++yy) {
                            final int c2 = yy * srcw;
                            final int factorY = yy << 10 < startY ? startY - (yy << 10) : yy > ceilY ? (yy - ceilY) << 10 : 1024;

                            for (int xx = startX >> 10; xx < ceilX; ++xx) {
                                final int source = buf[c2 + xx];
                                final int factorX = xx << 10 < startX ? startX - (xx << 10) : xx > ceilX ? (xx - ceilX) << 10 : 1024;
                                final int factor = Math.max(1, (factorX * factorY) >> 10);

                                alpha += ((source >> 24) & 255) * factor;
                                r += ((source >> 16) & 255) * factor;
                                g += ((source >> 8) & 255) * factor;
                                b += (source & 255) * factor;
                                pixCount += factor;
                            }
                        }

                        alpha /= pixCount;
                        r /= pixCount;
                        g /= pixCount;
                        b /= pixCount;

                        buf2[c1 + x] = (alpha << 24) + (r << 16) + (g << 8) + b;
                    } else {
                        buf2[c1 + x] = buf[(startY >> 10) * srcw + (x * (scaleX >> 10))];
                    }
                }
            }

            buf = null;

            return Image.createRGBImage(buf2, width, height, true);
        }
    }

    /**
     * Scale image in size according to a given factor
     *
     * @param   src image to scale
     * @param   factorWidth  factor to increase/ descrease the image in size. E.g.
     *              a factor of .5 halves the image width
     * @param   factorHeight factor to increase/ descrease the image in size. E.g.
     *              a factor of .5 halves the image height
     * @param   useAntialiasing true to use antialiasing, which lets the result usually look better but takes some performance
     * @return  scaled image
     */
    public static Image scale(final Image src, final double factorWidth, final double factorHeight, final boolean useAntialiasing) {
        final int width = (int) (src.getWidth() * factorWidth);
        final int height = (int) (src.getHeight() * factorHeight);

        return scale(src, width <= 0 ? 1 : width, height <= 0 ? 1 : height, useAntialiasing);
    }

    /**
     * Extend image to a new size and insert pixels in a given color into the new space
     *
     * @param   src image to scale
     * @param   width   target width
     * @param   height  target height
     * @param   alpha   alpha-channel of fill-pixel
     * @param   r       red value of fill-pixel
     * @param   g       green value of fill-pixel
     * @param   b       blue value of fill-pixel
     * @param   alignment   alignment e.g. ALIGN_HORIZ_CENTER that defines where the
     *              original image gets placed inside the new image
     * @return  scaled image
     */
    public static Image expand(final Image src, final int width, final int height, final int alpha, final int r, final int g, final int b, final int alignment) {
        // get source dimensions
        final int srcw = src.getWidth();
        final int srch = src.getHeight();
        // initialize source buffer
        int buf[] = new int[srcw * srch];
        // initialize target buffer
        final int buf2[] = new int[width * height];

        // copy image data to source buffer
        src.getRGB(buf, 0, srcw, 0, 0, srcw, srch);

        // calculate placement of the original image
        int xstart, xend, ystart, yend;

        if ((alignment & ALIGN_MASK_HORIZ) == ALIGN_LEFT) {
            xstart = 0;
        } else if ((alignment & ALIGN_MASK_HORIZ) == ALIGN_RIGHT) {
            xstart = width - srcw;
        } else {
            xstart = (width - srcw) / 2;
        }
        xend = xstart + srcw;
        if ((alignment & ALIGN_MASK_VERT) == ALIGN_TOP) {
            ystart = 0;
        } else if ((alignment & ALIGN_MASK_HORIZ) == ALIGN_BOTTOM) {
            ystart = height - srch;
        } else {
            ystart = (height - srcw) / 2;
        }
        yend = ystart + srch;

        // determine surrounding color
        final int col = (alpha << 24) + (r << 16) + (g << 8) + b;

        // copy image and fill space around it in defined color
        for (int y = 0; y < height; ++y) {
            final int c1 = y * width;
            final int c2 = (y - ystart) * srcw;

            for (int x = 0; x < width; ++x) {
                if (y < ystart || y >= yend || x < xstart || x >= xend) {
                    buf2[c1 + x] = col;
                } else {
                    buf2[c1 + x] = buf[c2 + x - xstart];
                }
            }
        }

        buf = null;

        return Image.createRGBImage(buf2, width, height, true);
    }

    /**
     * Change the brightness of a given image
     *
     * @param   src source image
     * @param   percentage  percentage of brightness change, e.g. a percentage of 0.8 darkens all pixels by 20%, must be >= 0
     * @return  new image with modified brightness
     */
    public static Image adjustBrightness(final Image src, final double percentage) {
        // percentage must not be negative
        if (percentage < 0) {
            throw new IllegalArgumentException("Percentage value for brightness change must not be < 0!");
        }

        // get source dimensions
        final int srcw = src.getWidth();
        final int srch = src.getHeight();
        // initialize source buffer
        int buf[] = new int[srcw * srch];
        // initialize target buffer
        final int buf2[] = new int[srcw * srch];

        // copy image data to source buffer
        src.getRGB(buf, 0, srcw, 0, 0, srcw, srch);

        // change brightness of all pixels according to the given percentage
        for (int y = 0; y < srch; ++y) {
            final int idx = y * srcw;

            for (int x = 0; x < srcw; ++x) {
                final int col = buf[idx + x];
                final int a = col & 0xff000000;
                final int r = Math.min(255, (int) (((col & 0x00ff0000) >> 16) * percentage)) << 16;
                final int g = Math.min(255, (int) (((col & 0x0000ff00) >> 8) * percentage)) << 8;
                final int b = Math.min(255, (int) ((col & 0x000000ff) * percentage));

                buf2[idx + x] = a | r | g | b;
            }
        }

        buf = null;

        return Image.createRGBImage(buf2, srcw, srch, true);
    }
}
