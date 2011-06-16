/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.DefaultObservable;
import de.joergjahnke.common.util.Observer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implements the Gameboy's video chip functionality
 * 
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 * @todo    ignore VRAM writes and return $ff on reads while in mode MODE_OAM_VRAM or MODE_OAM
 * @todo    implement correct sprite prioritization for non-CGB mode
 */
public class VideoChip extends DefaultObservable implements Serializable, Observer {

    /**
     * Do we need debug information?
     */
    private final static boolean DEBUG = false;
    /**
     * signal we send when a new frame is ready
     */
    public final static Integer SIGNAL_NEW_FRAME = new Integer(1);
    /**
     * number of color palettes
     */
    public final static int NUM_PALETTES = 8;
    /**
     * number of colors for background
     */
    public final static int NUM_COLORS = NUM_PALETTES * ColorPalette.COLORS_PER_PALETTE;
    /**
     * Start index of background colors
     */
    public final static int PALETTE_BACKGROUND = 0;
    /**
     * Start index of sprites colors
     */
    public final static int PALETTE_SPRITES = NUM_PALETTES;
    /**
     * number of tiles, 384 * 2 for a Gameboy Color
     */
    public final static int NUM_TILES = 384 * 2;
    /**
     * number of sprites, 40
     */
    public final static int NUM_SPRITES = 40;
    /**
     * tile width in pixels
     */
    public final static int TILE_WIDTH = 8;
    /**
     * tile height in pixels
     */
    public final static int TILE_HEIGHT = 8;
    /**
     * tiles per line
     */
    public final static int TILES_PER_LINE = 20;
    /**
     * pixels per line
     */
    public final static int SCREEN_WIDTH = TILES_PER_LINE * TILE_WIDTH;
    /**
     * tiles per column
     */
    public final static int TILES_PER_COLUMN = 18;
    /**
     * lines per screen
     */
    public final static int SCREEN_HEIGHT = TILES_PER_COLUMN * TILE_HEIGHT;
    /**
     * V-Blank columns
     */
    public final static int VBLANK_COLUMNS = 10;
    // time periods for the different video modes
    /**
     * HBlank period on a Gameboy Classic
     */
    private final static int HBLANK_PERIOD = 200;
    /**
     * Period where OAM is accessed on a Gameboy Classic
     */
    private final static int OAM_PERIOD = 80;
    /**
     * Period where OAM and VRAM are accessed on a Gameboy Classic (start)
     */
    private final static int OAM_VRAM_PERIOD_START = 48;
    /**
     * Period where OAM and VRAM are accessed on a Gameboy Classic (end)
     */
    private final static int OAM_VRAM_PERIOD_END = 128;
    /**
     * Number of cycles on a Gameboy Classic used for one LCD line
     */
    private final static int LCD_LINE_PERIOD = HBLANK_PERIOD + OAM_PERIOD + OAM_VRAM_PERIOD_START + OAM_VRAM_PERIOD_END;
    /**
     * VBlank period per LCD line on a Gameboy Classic
     */
    private final static int VBLANK_PERIOD = LCD_LINE_PERIOD;
    /**
     * VBlank period per LCD line on a Gameboy Classic (start)
     */
    private final static int VBLANK_PERIOD_START = 32;
    /**
     * VBlank period per LCD line on a Gameboy Classic (end)
     */
    private final static int VBLANK_PERIOD_END = 4;
    // video modes
    /**
     * LCD controller is reading from OAM memory
     */
    protected final static int MODE_OAM = 2;
    /**
     * LCD controller is reading from both OAM and VRAM
     */
    protected final static int MODE_OAM_VRAM = 3;
    /**
     * LCD controller is at the end of reading from both OAM and VRAM
     */
    protected final static int MODE_OAM_VRAM_END = 3 + 4;
    /**
     * LCD contoller is at the start of the V-Blank period
     */
    protected final static int MODE_VBLANK_START = 1 + 4;
    /**
     * LCD contoller is in the V-Blank period
     */
    protected final static int MODE_VBLANK = 1;
    /**
     * LCD controller is in the H-Blank period
     */
    protected final static int MODE_HBLANK = 0;
    /**
     * Size of a GBC VRAM bank is 4k
     */
    private final static int GBCVRAM_BANK_SIZE = 0x2000;
    /**
     * number of VRAM banks in GBC mode
     */
    private final static int NUM_VRAM_BANKS = 2;
    /**
     * scaling type for fast scaling
     */
    public final static int SCALING_FAST = 0;
    /**
     * scaling type for scaling that simply averages all colors of the involved pixels
     */
    public final static int SCALING_AVERAGING = 1;
    /**
     * scaling type for best quality scaling
     */
    public final static int SCALING_QUALITY = 2;
    /**
     * scaling type for scaling up by 50% for the popular QVGA displays of mobile devices
     */
    public final static int SCALING_PLUS50PERCENT = 3;
    /**
     * we don't use floating point arithmetics but integers and a multiplier
     */
    public final static int SCALING_MULTIPLIER = 1024;
    /**
     * the multiplier log 2
     */
    public final static int SCALING_MULTIPLIER_BITS = 10;
    /**
     * maximum number of visible sprites per line
     */
    private final static int MAX_SPRITES_VISIBLE = 10;
    /**
     * do we use a cache for the background pixel data when painting sprites?
     */
    private final static boolean USE_BACKGROUND_CACHE = Runtime.getRuntime().totalMemory() >= 3000000;
    /**
     * device the video chip works for
     */
    protected final Gameboy gameboy;
    /**
     * current LCD mode
     */
    private int mode = MODE_OAM;
    /**
     * next CPU cycle when we need to update the video chip
     */
    private long nextUpdate = 0;
    /**
     * the number of frames having been painted
     */
    private int frames = 0;
    /**
     * we paint every n-th frame
     */
    private int frameSkip = 3;
    /**
     * current LCD line (0-154)
     */
    private int currentLine = 0;
    /**
     * is the display enabled?
     */
    private boolean isLCDEnabled = true;
    /**
     * is the window enabled?
     */
    private boolean isWindowEnabled = false;
    /**
     * selected tile data area
     */
    private int tileDataArea;
    /**
     * background tile map address
     */
    private int bgTileMapAdr;
    /**
     * window tile map address
     */
    private int windowTileMapAdr;
    /**
     * sprite height in pixels
     */
    private int spriteHeight = 8;
    /**
     * are sprites enabled?
     */
    private boolean areSpritesEnabled;
    /**
     * is the background painted white?
     */
    private boolean isBGBlank = false;
    /**
     * do sprites always have priority over the background and window?
     */
    private boolean haveSpritesPriority = false;
    /**
     * Color palettes
     */
    private final ColorPalette[] palettes = new ColorPalette[NUM_PALETTES * 2];
    /**
     * Colors as raw bytes
     */
    private final int[] colorBytes = new int[NUM_COLORS * 2 * 2];
    /**
     * Scroll X
     */
    private int scrollX;
    /**
     * Scroll Y
     */
    private int scrollY;
    /**
     * window x-position
     */
    private int windowX;
    /**
     * window y-position
     */
    private int windowY;
    /**
     * next window Y value, we need to keep window position until the next frame
     */
    private int nextWindowY;
    /**
     * current window line
     */
    private int windowLine;
    /**
     * is the H-Blank interrupt enabled?
     */
    private boolean isHBlankIRQEnabled;
    /**
     * is the V-Blank interrupt enabled?
     */
    private boolean isVBlankIRQEnabled;
    /**
     * is the OAM interrupt enabled?
     */
    private boolean isOAMIRQEnabled;
    /**
     * is the coincidence interrupt enabled?
     */
    private boolean isCoincidenceIRQEnabled;
    /**
     * available tiles
     */
    private final Tile[] tiles = new Tile[NUM_TILES];
    /**
     * all tiles are invalid?
     */
    private boolean areAllTilesInvalid = true;
    /**
     * available sprites
     */
    private final Sprite[] sprites = new Sprite[NUM_SPRITES];
    /**
     * 160x144 pixels with the current screen
     */
    protected int[] pixels;
    /**
     * copy of the screen's background, used when painting sprites to preserve the background pixels
     */
    private int[] backgroundPixelsBuffer;
    /**
     * a blank line, used when neither background not window are going to be displayed (GB mode) or LCD is off
     */
    private int[] blankLine;
    /**
     * Currently active 8k GBC VRAM bank in memory $8000-$9fff, stored as offset into the 16k VRAM we use internally
     */
    private int currentVRAMBank = 0;
    /**
     * Offset to the currently active VRAM bank
     */
    private int currentVRAMOffset = 0;
    /**
     * switchable GBC VRAM banks 0-1
     */
    protected final byte[] vRAM = new byte[GBCVRAM_BANK_SIZE * NUM_VRAM_BANKS];
    /**
     * scaling multiplier
     */
    protected int scalingMult;
    /**
     * the current frame gets painted?
     */
    private boolean isPaintFrame;
    /**
     * do we use the (slower) smooth scaling?
     */
    private int scalingType = SCALING_QUALITY;
    /**
     * stores whether the screen lines have been modified since the last repaint
     */
    private final boolean[] wasLineModified = new boolean[SCREEN_HEIGHT];
    /**
     * used to re-initialize wasLineModified
     */
    private final boolean[] allLinesModified = new boolean[SCREEN_HEIGHT];
    /**
     * stores whether a sprite was painted on a given line
     */
    private final boolean[] wasSpritePainted = new boolean[SCREEN_HEIGHT];
    /**
     * are all lines currently modified?
     */
    private boolean areAllLinesModified = false;
    /**
     * has a value >0 for every foreground pixel and a value of 0 for every background pixel from background and window
     */
    protected final byte[] backgroundPriorities = new byte[SCREEN_WIDTH];
    /**
     * pre-calculated scaled with
     */
    protected int scaledWidth = SCREEN_WIDTH;
    /**
     * multiplier for the CPU speed * 1024, used in calculating the number of passing CPU cycles
     * for a Gameboy Classic we use 1 * 1024, for a Gameboy Color we have 2 * 1024
     */
    private int cpuSpeedMult = 1 << 10;

    /**
     * Create a new Gameboy video
     *
     * @param   gameboy the Gameboy instance this video chip belongs to
     */
    public VideoChip(final Gameboy gameboy) {
        this.gameboy = gameboy;
        // initialize allLinesModified array
        for (int i = 0; i < this.allLinesModified.length; ++i) {
            this.allLinesModified[i] = true;
        }
        // default scaling is x1
        setScaling(SCALING_MULTIPLIER);
    }

    /**
     * Get the RGB data containing the contents of the screen
     * 
     * @return  array with RGB data
     */
    public final int[] getRGBData() {
        return this.pixels;
    }

    /**
     * Get the scaling type
     * 
     * @return  SCALING_FAST, SCALING_QUALITY, SCALING_AVERAGING
     */
    public final int getScalingType() {
        return this.scalingType;
    }

    /**
     * Set the scaling type
     * 
     * @param scalingType   the new scaling type
     */
    public final void setScalingType(final int scalingType) {
        this.scalingType = scalingType;
    }

    /**
     * Get the CPU cycle when we need to update the video chip the next time
     * 
     * @return  CPU cycle
     */
    public final long getNextUpdate() {
        return this.nextUpdate;
    }

    /**
     * Get the current video mode
     * 
     * @return  MODE_OAM, MODE_OAM_VRAM, MODE_HBLANK or MODE_VBLANK
     */
    public final int getVideoMode() {
        return this.mode & 0x03;
    }

    /**
     * Get the current LCD line
     * 
     * @return  line (0-153)
     */
    public final int getLCDLine() {
        return this.currentLine;
    }

    /**
     * Is the display enabled
     * 
     * @return  true if the display is enabled
     */
    public final boolean isLCDEnabled() {
        return this.isLCDEnabled;
    }

    /**
     * Enable/disable the display
     * 
     * @param   isLCDEnabled    true to enable the display, false to disable it
     */
    public final void setLCDEnabled(final boolean isLCDEnabled) {
        if (isLCDEnabled != this.isLCDEnabled) {
            if (DEBUG) {
                System.out.println("Set LCD enable to " + isLCDEnabled + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.isLCDEnabled = isLCDEnabled;
            this.currentLine = 0;
            setSpriteHeight(isLCDEnabled ? VideoChip.TILE_HEIGHT * 2 : VideoChip.TILE_HEIGHT);
            invalidateLines();
        }
    }

    /**
     * Check whether the display window is enabled?
     * 
     * @return  true if the window is enabled
     */
    private boolean isWindowEnabled() {
        return this.isWindowEnabled;
    }

    /**
     * Enable/disable the window
     * 
     * @param isWindowEnabled   true to enable the window, false to disable it
     */
    public final void setWindowEnabled(final boolean isWindowEnabled) {
        if (isWindowEnabled != this.isWindowEnabled) {
            if (DEBUG) {
                System.out.println("Set window enable to " + isWindowEnabled + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.isWindowEnabled = isWindowEnabled;
            if (isWindowEnabled && this.windowLine == 0 && this.currentLine > getWindowY()) {
                this.windowLine = SCREEN_HEIGHT;
            }
            invalidateLines();
        }
    }

    /**
     * Get the available tiles
     * 
     * @return  tiles array
     */
    public final Tile[] getTiles() {
        return this.tiles;
    }

    /**
     * Set the memory location of the background tile map
     * 
     * @param   bgTileMapAdr    the new memory location
     */
    public final void setBackgroundTileArea(final int bgTileMapAdr) {
        if (this.bgTileMapAdr != (bgTileMapAdr & 0x1fff)) {
            if (DEBUG) {
                System.out.println("Set background tile area to " + Integer.toHexString(bgTileMapAdr) + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.bgTileMapAdr = bgTileMapAdr & 0x1fff;
            invalidateLines();
        }
    }

    /**
     * Set the memory location of the window tile map
     * 
     * @param   windowTileMapAdr    the new memory location
     */
    public final void setWindowTileArea(final int windowTileMapAdr) {
        if (this.windowTileMapAdr != (windowTileMapAdr & 0x1fff)) {
            if (DEBUG) {
                System.out.println("Set window tile area to " + Integer.toHexString(windowTileMapAdr) + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.windowTileMapAdr = windowTileMapAdr & 0x1fff;
            invalidateLines();
        }
    }

    /**
     * Get the memory location of the tile data
     * 
     * @return  memory location $0000-$1fff, to get the actual memory address $8000 needs to be added to this value
     */
    private int getTileDataArea() {
        return this.tileDataArea;
    }

    /**
     * Set the memory location of the tile data
     * 
     * @param   tileDataArea    the new memory location
     */
    public final void setTileDataArea(final int tileDataArea) {
        if (this.tileDataArea != (tileDataArea & 0x1fff)) {
            if (DEBUG) {
                System.out.println("Set tile data area to " + Integer.toHexString(tileDataArea & 0x1fff) + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.tileDataArea = tileDataArea & 0x1fff;
            invalidateTiles();
            invalidateLines();
        }
    }

    /**
     * Set new GBC VRAM bank to be active at $8000-$9fff of the main memory
     * 
     * @param   vRAMBank    VRAM bank number to activate
     */
    public void setGBCVRAMBank(final int vRAMBank) {
        if (vRAMBank != this.currentVRAMBank) {
            if (DEBUG) {
                System.out.println("Set VRAM bank to " + vRAMBank + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.currentVRAMBank = vRAMBank;
            this.currentVRAMOffset = vRAMBank * GBCVRAM_BANK_SIZE;
        }
    }

    /**
     * Get the sprites
     * 
     * @return  array of sprites
     */
    public final Sprite[] getSprites() {
        return this.sprites;
    }

    /**
     * Get the sprite height
     * 
     * @return  height in pixels (8 or 16)
     */
    public final int getSpriteHeight() {
        return this.spriteHeight;
    }

    /**
     * Set the sprite height
     * 
     * @param   spriteSize  the new sprite height in pixels
     */
    public final void setSpriteHeight(final int spriteSize) {
        if (spriteSize != this.spriteHeight) {
            if (DEBUG) {
                System.out.println("Set sprite size to " + spriteSize + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.spriteHeight = spriteSize;
            // we have to update the sprite tiles
            for (int i = 0; i < this.sprites.length; ++i) {
                this.sprites[i].updateTile();
            }
        }
    }

    /**
     * Check whether sprites are enabled
     * 
     * @return  true if sprites are enabled
     */
    private boolean areSpritesEnabled() {
        return this.areSpritesEnabled;
    }

    /**
     * Set whether sprites are enabled
     * 
     * @param   areSpritesEnabled   true to enable sprites, false to disabled them
     */
    public final void setSpritesEnabled(final boolean areSpritesEnabled) {
        if (areSpritesEnabled != this.areSpritesEnabled) {
            if (DEBUG) {
                System.out.println("Set sprite enable to " + areSpritesEnabled + " at line " + this.currentLine + " of frame " + this.frames);
            }
            invalidateLines();
        }
        this.areSpritesEnabled = areSpritesEnabled;
    }

    /**
     * Check whether the background is blank
     * 
     * @return  true if the background is blank and to be display white
     */
    private boolean isBackgroundBlank() {
        return this.isBGBlank;
    }

    /**
     * Set whether the background is to be painted white
     * 
     * @param   isBGBlank   true to display the background blank
     */
    public final void setBackgroundBlank(final boolean isBGBlank) {
        if (isBGBlank != this.isBGBlank) {
            if (DEBUG) {
                System.out.println("Set background enable to " + !isBGBlank + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.isBGBlank = isBGBlank;
            invalidateLines();
        }
    }

    /**
     * Check whether the sprites always have priority over background and window
     * 
     * @return  true if the sprites always have priority
     */
    public boolean isHaveSpritesPriority() {
        return this.haveSpritesPriority;
    }

    /**
     * Set whether sprites always have priority over background and window
     * 
     * @param   haveSpritesPriority true to always give sprites priority over background and window
     */
    public final void setHaveSpritesPriority(final boolean haveSpritesPriority) {
        if (DEBUG && haveSpritesPriority != this.haveSpritesPriority) {
            System.out.println("Set sprite priority enable to " + haveSpritesPriority + " at line " + this.currentLine + " of frame " + this.frames);
        }
        this.haveSpritesPriority = haveSpritesPriority;
    }

    /**
     * Get a byte from the Gameboy's color palette memory
     * 
     * @param   n   palette index (0-$7f)
     * @return  palette memory data
     */
    public final int getColorByte(final int n) {
        return this.colorBytes[n];
    }

    /**
     * Write a byte to the Gameboy's palette memory
     * 
     * @param   n   color byte index (0-$7f)
     * @param   b   byte to write
     */
    public final void setColorByte(final int n, final int b) {
        // this is a color change?
        if (this.colorBytes[n] != b) {
            // then store the new color
            this.colorBytes[n] = b;

            // generate the 15bit Gameboy color value
            final int gbColor = this.colorBytes[n & 0xfe] + ((this.colorBytes[(n & 0xfe) + 1] & 0xff) << 8);

            // create 32bit color value
            this.palettes[n >> 3].setColor((n >> 1) & 0x03, 0xff000000 | ((gbColor & 0x1f) << 19) | ((gbColor & (0x1f << 5)) << 6) | ((gbColor & (0x1f << 10)) >> 7));
        }
    }

    /**
     * Get the available color palettes
     *
     * @return array of color palettes
     */
    public final ColorPalette[] getColorPalettes() {
        return this.palettes;
    }

    /**
     * Get the left position of the background map
     * 
     * @return  horizontal scroll value
     */
    private int getScrollX() {
        return this.scrollX;
    }

    /**
     * Set the left position of the background map
     * 
     * @param scrollX   new horizontal scroll value
     */
    public final void setScrollX(final int scrollX) {
        if (scrollX != this.scrollX) {
            if (DEBUG) {
                System.out.println("Set x-scroll to " + scrollX + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.scrollX = scrollX;
            invalidateLines();
        }
    }

    /**
     * Get the top position of the background map
     * 
     * @return  vertical scroll value
     */
    private int getScrollY() {
        return this.scrollY;
    }

    /**
     * Set the top position of the background map
     * 
     * @param scrollY   new vertical scroll value
     */
    public final void setScrollY(final int scrollY) {
        if (scrollY != this.scrollY) {
            if (DEBUG) {
                System.out.println("Set y-scroll to " + scrollY + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.scrollY = scrollY;
            invalidateLines();
        }
    }

    /**
     * Get the x-position of the window
     * 
     * @return  x-position
     */
    private int getWindowX() {
        return this.windowX;
    }

    /**
     * Set the x-position of the window.
     * Setting a position outside the visible area stops painting the window after the current line.
     * 
     * @param   windowX new x-position
     */
    public final void setWindowX(final int windowX) {
        if (windowX != this.windowX) {
            if (DEBUG) {
                System.out.println("Set window x-position to " + windowX + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.windowX = windowX;
            invalidateWindowLines();
        }
    }

    /**
     * Get the y-position of the window
     * 
     * @return  y-posiion
     */
    private int getWindowY() {
        return this.windowY;
    }

    /**
     * Set the y-position of the window.
     * Setting a position outside the visible area stops painting the window after the current line.
     * 
     * @param   windowY new y-position
     */
    public final void setWindowY(final int windowY) {
        if (windowY != this.windowY) {
            if (DEBUG) {
                System.out.println("Set window y-position to " + windowY + " at line " + this.currentLine + " of frame " + this.frames);
            }
            this.nextWindowY = windowY;
        }
    }

    /**
     * Get the frameskip value
     * 
     * @return  1 if every frame is shown, 2 if every second frame is shown etc.
     */
    public int getFrameSkip() {
        return this.frameSkip;
    }

    /**
     * Set the frameskip value
     * 
     * @param   frameSkip   1 to show every frame, 2 to show every second frame etc.
     */
    public void setFrameSkip(final int frameSkip) {
        if (frameSkip < 1) {
            throw new IllegalArgumentException("Frameskip value must be > 0!");
        }
        this.frameSkip = frameSkip;
    }

    /**
     * Is the H-Blank interrupt enabled?
     * 
     * @return  true if the IRQ is enabled
     */
    private boolean isHBlankIRQEnabled() {
        return this.isHBlankIRQEnabled;
    }

    /**
     * Enable/disable the H-Blank interrupt
     * 
     * @param   isHBlankIRQEnabled  true to enable the IRQ, false to disable it
     */
    public final void setHBlankIRQEnabled(final boolean isHBlankIRQEnabled) {
        if (DEBUG && isHBlankIRQEnabled != this.isHBlankIRQEnabled) {
            System.out.println("Set HBlank IRQ enable to " + isHBlankIRQEnabled + " at line " + this.currentLine + " of frame " + this.frames);
        }
        this.isHBlankIRQEnabled = isHBlankIRQEnabled;
    }

    /**
     * Is the V-Blank interrupt enabled?
     * 
     * @return  true if the IRQ is enabled
     */
    private boolean isVBlankIRQEnabled() {
        return this.isVBlankIRQEnabled;
    }

    /**
     * Enable/disable the V-Blank interrupt
     * 
     * @param   isVBlankIRQEnabled  true to enable the IRQ, false to disable it
     */
    public final void setVBlankIRQEnabled(final boolean isVBlankIRQEnabled) {
        if (DEBUG && isVBlankIRQEnabled != this.isVBlankIRQEnabled) {
            System.out.println("Set VBlank IRQ enable to " + isVBlankIRQEnabled + " at line " + this.currentLine + " of frame " + this.frames);
        }
        this.isVBlankIRQEnabled = isVBlankIRQEnabled;
    }

    /**
     * Is the OAM interrupt enabled?
     * 
     * @return  true if the IRQ is enabled
     */
    private boolean isOAMIRQEnabled() {
        return this.isOAMIRQEnabled;
    }

    /**
     * Enable/disable the OAM interrupt
     * 
     * @param   isOAMIRQEnabled true to enable the IRQ, false to disable it
     */
    public final void setOAMIRQEnabled(final boolean isOAMIRQEnabled) {
        if (DEBUG && isOAMIRQEnabled != this.isOAMIRQEnabled) {
            System.out.println("Set OAM IRQ enable to " + isOAMIRQEnabled + " at line " + this.currentLine + " of frame " + this.frames);
        }
        this.isOAMIRQEnabled = isOAMIRQEnabled;
    }

    /**
     * Is the coincidence interrupt enabled?
     * 
     * @return  true if the IRQ is enabled
     */
    private boolean isCoincidenceIRQEnabled() {
        return this.isCoincidenceIRQEnabled;
    }

    /**
     * Enable/disable the coincidence interrupt
     * 
     * @param   isCoincidenceIRQEnabled true to enable the IRQ, false to disable it
     */
    public final void setCoincidenceIRQEnabled(final boolean isCoincidenceIRQEnabled) {
        if (DEBUG && isCoincidenceIRQEnabled != this.isCoincidenceIRQEnabled) {
            System.out.println("Set Coincidence IRQ enable to " + isCoincidenceIRQEnabled + " at line " + this.currentLine + " of frame " + this.frames);
        }
        this.isCoincidenceIRQEnabled = isCoincidenceIRQEnabled;
    }

    /**
     * Get the screen scaling factor
     * 
     * @return  scaling multiplier
     */
    public final int getScaling() {
        return this.scalingMult;
    }

    /**
     * Set the screen scaling factor
     * 
     * @param   scalingMult new scaling factor << SCALING_MULTIPLIER_BITS
     */
    public final void setScaling(final int scalingMult) {
        int modScalingMult = scalingMult;
        
        // ensure that the tile size is an integer
        modScalingMult /= 128;
        modScalingMult *= 128;
        // ensure valid scaling values
        if (modScalingMult == 0) {
            throw new RuntimeException("Scaling factor cannot be 0!");
        }
        // apply new scaling value if necessary
        if (modScalingMult != this.scalingMult) {
            this.scalingMult = modScalingMult;
            this.scaledWidth = (SCREEN_WIDTH * this.scalingMult) >> 10;
            setScalingType(this.scalingMult % SCALING_MULTIPLIER == 0 ? SCALING_FAST : this.scalingMult == SCALING_MULTIPLIER * 3 / 2 ? SCALING_PLUS50PERCENT : SCALING_QUALITY);
            initializeScreen();
        }
    }

    /**
     * Get the screen width, 160 pixels for a standard, non-scaled Gameboy screen
     * 
     * @return  number of pixels
     */
    public final int getScaledWidth() {
        return this.scaledWidth;
    }

    /**
     * Get the screen height, 144 pixels for a standard, non-scaled Gameboy screen
     * 
     * @return  number of pixels
     */
    public final int getScaledHeight() {
        return (SCREEN_HEIGHT * this.scalingMult) >> 10;
    }

    /**
     * Read a byte from VRAM memory
     * 
     * @param   adr VRAM address ($0-$1fff) to read from
     * @return  data read
     */
    public final int readByte(final int adr) {
        return this.vRAM[this.currentVRAMOffset + adr] & 0xff;
    }

    /**
     * Write a byte to VRAM memory
     * 
     * @param   adr VRAM address ($0-$1fff) to write to
     * @param   data    byte to write
     */
    public final void writeByte(final int adr, final byte data) {
        // check if this really is a modification
        final int vRAMAdr = this.currentVRAMOffset + adr;

        if (data != this.vRAM[vRAMAdr]) {
            // store data
            this.vRAM[vRAMAdr] = data;

            // tile data was changed?
            if (adr < 0x1800) {
                // then invalidate the tile data, the pixels need to be recalculated anew
                this.tiles[this.currentVRAMBank * (NUM_TILES >> 1) + (adr >> 4)].invalidate();
            }
            // we also have to repaint the screen
            invalidateLines();
        }
    }

    /**
     * Update the video chip depending on the current video mode
     * 
     * @param   cycles  current CPU cycles
     */
    public final void update(final long cycles) {
        int lcdCycles;

        switch (this.mode) {
            case MODE_OAM:
                lcdCycles = onOAM();
                break;
            case MODE_OAM_VRAM:
                lcdCycles = onTransfer();
                break;
            case MODE_OAM_VRAM_END:
                lcdCycles = onOAMEnd();
                break;
            case MODE_HBLANK:
                lcdCycles = onHBlank();
                break;
            case MODE_VBLANK_START:
                lcdCycles = onVBlankStart();
                break;
            case MODE_VBLANK:
                lcdCycles = onVBlank();
                break;
            default:
                throw new IllegalStateException("Illegal video mode: " + this.mode);
        }

        this.nextUpdate = cycles + ((this.cpuSpeedMult * lcdCycles) >> 10);
    }

    /**
     * We do nothing special here
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onOAM() {
        this.mode = MODE_OAM_VRAM;

        return OAM_VRAM_PERIOD_START;
    }

    /**
     * Paint the next line of the display
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onTransfer() {
        // we have a paintable line?
        if (this.isPaintFrame) {
            if (isLCDEnabled()) {
                // paint the background, window and sprites for this line
                drawLine(this.currentLine);
            } else {
                // draw an empty line
                for (int i = (this.currentLine * getScaling()) >> 10, to = ((this.currentLine + 1) * getScaling()) >> 10; i < to; ++i) {
                    System.arraycopy(this.blankLine, 0, this.pixels, i * getScaledWidth(), getScaledWidth());
                }
            }
        }

        this.mode = MODE_OAM_VRAM_END;

        return OAM_VRAM_PERIOD_END;
    }

    /**
     * Trigger H-Blank IRQ if necessary
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onOAMEnd() {
        if (isLCDEnabled()) {
            final CPU cpu = this.gameboy.getCPU();

            if (isHBlankIRQEnabled() && (cpu.memory[0xff41] & 0x44) != 0x44) {
                cpu.requestIRQ(CPU.IRQ_LCDSTAT);
            }
        }

        this.mode = MODE_HBLANK;

        return HBLANK_PERIOD;
    }

    /**
     * Proceed to next line and check interrupts.
     * After the last line of the screen we paint the whole frame.
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onHBlank() {
        int lcdCycles = 0;

        // proceed to next line
        ++this.currentLine;

        if (isLCDEnabled()) {
            checkCoincidenceIRQ();
        }

        // we still are in the paintable area?
        if (this.currentLine < SCREEN_HEIGHT) {
            // trigger OAM IRQ if necessary
            if (isLCDEnabled()) {
                checkOAMIRQ();
            }

            lcdCycles = OAM_PERIOD;
            this.mode = MODE_OAM;
        } else {
            // determine whether to paint the next frame
            onFrameFinished();

            // update scroll values
            this.windowY = this.nextWindowY;

            this.mode = MODE_VBLANK_START;
            lcdCycles = VBLANK_PERIOD_START;
        }

        return lcdCycles;
    }

    /**
     * Trigger V-Blank IRQ if necessary
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onVBlankStart() {
        final CPU cpu = this.gameboy.getCPU();

        // trigger V-Blank interrupt
        if (isLCDEnabled()) {
            cpu.requestIRQ(CPU.IRQ_VBLANK);
            if (isVBlankIRQEnabled()) {
                cpu.requestIRQ(CPU.IRQ_LCDSTAT);
            }
        }

        this.mode = MODE_VBLANK;

        return VBLANK_PERIOD - VBLANK_PERIOD_START;
    }

    /**
     * Update screen for the next frame
     * 
     * @return  number of CPU cycles on a Gameboy classic required to perform this action
     */
    private int onVBlank() {
        int lcdCycles = 0;

        if (this.currentLine == 0) {
            if (isLCDEnabled()) {
                checkOAMIRQ();
            }

            // update window position
            if (this.windowY != this.nextWindowY) {
                final int firstInvalid = Math.max(0, Math.min(this.nextWindowY, getWindowY()));

                if (firstInvalid < SCREEN_HEIGHT) {
                    invalidateLines(firstInvalid, SCREEN_HEIGHT - firstInvalid);
                }
                this.windowY = this.nextWindowY;
            }

            lcdCycles = OAM_PERIOD;
            this.mode = MODE_OAM;
        } else {
            if (this.currentLine < SCREEN_HEIGHT + VBLANK_COLUMNS - 1) {
                ++this.currentLine;
                lcdCycles = this.currentLine == SCREEN_HEIGHT + VBLANK_COLUMNS - 1 ? VBLANK_PERIOD_END : VBLANK_PERIOD;
            } else {
                // we start with (tile) line 0 again
                this.windowLine = 0;
                this.currentLine = 0;

                lcdCycles = VBLANK_PERIOD - VBLANK_PERIOD_END;
            }

            if (isLCDEnabled()) {
                checkCoincidenceIRQ();
            }
        }

        return lcdCycles;
    }

    /**
     * Paint the newly generated frame
     */
    private void onFrameFinished() {
        // we have just finished a frame
        ++this.frames;

        // notify observers of the new frame
        if (this.isPaintFrame) {
            setChanged(true);
            notifyObservers(SIGNAL_NEW_FRAME);
        }

        // determine whether to paint the next frame
        this.isPaintFrame = this.frames % getFrameSkip() == 0;
    }

    /**
     * Trigger coincidence IRQ if necessary
     */
    protected void checkCoincidenceIRQ() {
        final CPU cpu = this.gameboy.getCPU();

        if (isCoincidenceIRQEnabled() && this.currentLine == (cpu.readIO(0xff45))) {
            cpu.memory[0xff41] |= 0x04;
            cpu.requestIRQ(CPU.IRQ_LCDSTAT);
        } else {
            cpu.memory[0xff41] &= 0xfb;
        }
    }

    /**
     * Trigger OAM IRQ if necessary
     */
    protected void checkOAMIRQ() {
        final CPU cpu = this.gameboy.getCPU();

        if (isOAMIRQEnabled() && (cpu.memory[0xff41] & 0x44) != 0x44) {
            cpu.requestIRQ(CPU.IRQ_LCDSTAT);
        }
    }

    /**
     * Draw background, window and sprites for a given screen line.
     * This methods modifies the currentLine class variable to match the given line
     * and resets it later.
     * 
     * @param line  line to paint
     */
    private void drawLine(final int line) {
        final int oldLine = this.currentLine;

        this.currentLine = line;

        // modifications to the background or window were made?
        if (this.wasLineModified[line]) {
            // paint the background for this line
            drawBackgroundLine();
            // paint the window for this line, if necessary
            drawWindowLine();
            // this line has been repainted
            this.wasLineModified[line] = false;
            this.wasSpritePainted[line] = false;
            this.areAllLinesModified = false;
        // sprites were painted over the background?
        } else if (this.wasSpritePainted[line]) {
            // then restore background from buffer
            final int svy = (line * getScaling()) >> SCALING_MULTIPLIER_BITS;
            final int svystop = ((line + 1) * getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
            final int spos = svy * getScaledWidth();

            System.arraycopy(this.backgroundPixelsBuffer, spos, this.pixels, spos, svystop * getScaledWidth() - spos);
            this.wasSpritePainted[line] = false;
        }

        // paint sprites for this line
        drawSpriteLine();

        this.currentLine = oldLine;
    }

    /**
     * Copy sprites to screen
     * 
     * @param   priorityFlag    only the sprites which have this priority will be painted
     */
    private void drawSpriteLine() {
        if (areSpritesEnabled()) {
            // cache some variables for better performance
            final Sprite[] sprites_ = this.sprites;
            final int line_ = this.currentLine;
            // we copy modified sprite lines into a background buffer instead of invalidating them
            final boolean[] wasSpritePainted_ = USE_BACKGROUND_CACHE ? this.wasSpritePainted : this.wasLineModified;
            // all sprites have the same height, so we read this only once
            final int sh = getSpriteHeight();

            // check all sprites whether they are visibible, have the desired priority and are part of the current line
            for (int i = 0, left = this.gameboy.getCartridge().isGBC() ? MAX_SPRITES_VISIBLE : NUM_SPRITES; i < NUM_SPRITES && left > 0; ++i) {
                // check for each sprite whether it should be displayed on this line
                final Sprite sprite = sprites_[i];
                final int sy = sprite.getY();

                if (sprite.isDisplayable() && line_ >= sy && line_ < sy + sh) {
                    // if the sprite is also horizontally within the visible area then we paint it
                    if (sprite.isVisible()) {
                        if (!wasSpritePainted_[line_]) {
                            if (USE_BACKGROUND_CACHE) {
                                final int svy = (line_ * getScaling()) >> SCALING_MULTIPLIER_BITS;
                                final int svystop = ((line_ + 1) * getScaling()) >> VideoChip.SCALING_MULTIPLIER_BITS;
                                final int spos = svy * getScaledWidth();

                                System.arraycopy(this.pixels, spos, this.backgroundPixelsBuffer, spos, svystop * getScaledWidth() - spos);
                            }
                            wasSpritePainted_[line_] = true;
                        }
                        sprite.drawLine(line_ - sy);
                        this.areAllTilesInvalid = false;
                    }
                    // we can display only a maximum of 10 sprites per line, so we count down
                    --left;
                }
            }
        }
    }

    /**
     * Draw the next line of the background
     */
    private void drawBackgroundLine() {
        // background is active and not covered by the window?
        if ((!isBackgroundBlank() || this.gameboy.getCartridge().isGBC()) && !(isWindowEnabled() && this.currentLine >= getWindowY() && getWindowX() == 0)) {
            // VRAM address of first tile to paint
            final int memStart = (this.bgTileMapAdr & 0x1fff) + ((((this.currentLine + getScrollY()) >> 3) & 0x1f) << 5);
            // determine start positions to paint
            final int vystart = (this.currentLine + getScrollY()) % TILE_HEIGHT;

            // drawLine the line
            drawTileMapLine(getScrollX() >> 3, -(getScrollX() & 0x07), vystart, memStart);

            this.areAllTilesInvalid = false;
        }
    }

    /**
     * Draw the next line of the window
     */
    private void drawWindowLine() {
        if (isWindowEnabled() && this.currentLine >= getWindowY() && getWindowX() < SCREEN_WIDTH && this.windowLine < SCREEN_HEIGHT) {
            // VRAM address of first tile to paint
            final int memStart = (this.windowTileMapAdr & 0x1fff) + ((this.windowLine >> 3) << 5);
            // determine start positions to paint
            final int vystart = (this.currentLine - getWindowY()) % TILE_HEIGHT;

            // drawLine the line
            drawTileMapLine(0, getWindowX(), vystart, memStart);
            ++this.windowLine;

            this.areAllTilesInvalid = false;
        }
    }

    /**
     * Draw window or background line
     * 
     * @param   txstart  vxi-position in the tile map of the first tile to paint
     * @param   vxstart (unscaled) vxi-position on the screen to start painting
     * @param   tline   (unscaled) tile line to drawLine
     * @param   memStart    memory address in the tile map
     */
    private void drawTileMapLine(final int txstart, final int vxstart, final int tline, final int memStart) {
        // cache some variables for better performance
        final Tile[] tiles_ = getTiles();
        final byte[] vRAM_ = this.vRAM;
        final boolean isGBC = this.gameboy.getCPU().cartridge.isGBC();
        final boolean isLowerTileDataArea = getTileDataArea() == 0;
        final int line_ = this.currentLine;

        // drawLine line from the given start position
        for (int txi = txstart, vxi = vxstart; vxi < SCREEN_WIDTH; ++txi, vxi += TILE_WIDTH) {
            // determine tile to paint
            final int adr = memStart + (txi & 0x1f);
            final byte data = vRAM_[adr];
            final int attributes = isGBC ? vRAM_[adr + 0x2000] & 0xff : 0;
            final int tileNumOffset = (attributes & 0x08) == 0 ? 0 : (VideoChip.NUM_TILES >> 1);
            final int tileId = tileNumOffset + (isLowerTileDataArea ? data & 0xff : 0x100 + data);

            // paint the tile
            tiles_[tileId].drawLine(tline, vxi, line_, attributes);
        }
    }

    /**
     * Invalidate all tile datas
     */
    protected final void invalidateTiles() {
        if (!this.areAllTilesInvalid) {
            final Tile[] tiles_ = this.tiles;

            for (int i = 0, to = tiles_.length; i < to; ++i) {
                tiles_[i].invalidate();
            }

            this.areAllTilesInvalid = true;
        }
    }

    /**
     * Invalidate all screen lines
     */
    protected final void invalidateLines() {
        invalidateLines(0, SCREEN_HEIGHT);
        this.areAllLinesModified = true;
    }

    /**
     * Invalidate a part of the screen lines
     * 
     * @param from  first screen line to invalidate
     * @param len   number of lines to invalidate
     */
    private void invalidateLines(final int from, final int len) {
        if (!this.areAllLinesModified) {
            System.arraycopy(this.allLinesModified, 0, this.wasLineModified, from, len);
        }
    }

    /**
     * Invalidate all screen lines where the window is being displayed
     */
    private void invalidateWindowLines() {
        final int firstInvalid = Math.max(0, getWindowY());

        if (firstInvalid < SCREEN_HEIGHT) {
            invalidateLines(firstInvalid, SCREEN_HEIGHT - firstInvalid);
        }
    }

    /**
     * Reset the video chip
     */
    public void reset() {
        // reset class variables
        this.currentLine = 0;
        this.frames = 0;
        this.mode = MODE_OAM;
        this.isLCDEnabled = true;
        this.isWindowEnabled = false;
        this.isBGBlank = false;
        this.tileDataArea = 0;
        this.bgTileMapAdr = this.windowTileMapAdr = 0;
        this.spriteHeight = 8;
        this.haveSpritesPriority = false;
        this.areSpritesEnabled = false;
        this.currentVRAMBank = 0;
        this.currentVRAMOffset = 0;
        this.nextUpdate = 0;
        this.scrollX = this.scrollY = this.windowX = this.windowY = 0;
        this.nextWindowY = 0;
        this.isCoincidenceIRQEnabled = this.isHBlankIRQEnabled = this.isOAMIRQEnabled = this.isVBlankIRQEnabled = false;
        this.cpuSpeedMult = 1 << 10;
        // reset VRAM
        for (int i = 0; i < this.vRAM.length; ++i) {
            this.vRAM[i] = 0;
        }
        // re-initializes the screen
        initializeScreen();
        // reset sprites
        for (int i = 0; i < this.sprites.length; ++i) {
            this.sprites[i].reset();
        }
    }

    /**
     * Initialize the screen
     */
    private void initializeScreen() {
        // initialize all background colors as white, all sprite colors as uninitialized
        for (int i = 0; i < this.colorBytes.length / 2; i += 2) {
            this.colorBytes[i] = 0xff;
            this.colorBytes[i + 1] = 0x7f;
        }
        for (int i = this.colorBytes.length / 2; i < this.colorBytes.length; i += 2) {
            this.colorBytes[i] = -1;
            this.colorBytes[i + 1] = -1;
        }
        // initialize color palettes
        for (int i = 0; i < this.palettes.length; ++i) {
            this.palettes[i] = new ColorPalette(this, i < PALETTE_SPRITES ? 0xffffffff : 0x00000000);
        }
        // initialize tiles
        for (int i = 0; i < this.tiles.length; ++i) {
            this.tiles[i] = new Tile(this, i < NUM_TILES / 2 ? i * 16 : GBCVRAM_BANK_SIZE + (i - NUM_TILES / 2) * 16);
        }
        // initialize sprites
        for (int i = 0; i < this.sprites.length; ++i) {
            this.sprites[i] = new Sprite(this);
            this.sprites[i].setTile(0);
        }
        // all lines need to be repainted
        invalidateLines();

        // we will re-create a lot of memory-intensive objects, so a clean-up is useful
        this.pixels = null;
        System.gc();

        // initialize screen
        this.pixels = new int[(getScaledWidth() + this.tiles[0].getScaledWidth()) * (getScaledHeight() + this.tiles[0].getScaledHeight())];
        if (USE_BACKGROUND_CACHE) {
            this.backgroundPixelsBuffer = new int[(getScaledWidth() + this.tiles[0].getScaledWidth()) * (getScaledHeight() + this.tiles[0].getScaledHeight())];
        }

        // initialize the blank line
        this.blankLine = new int[getScaledWidth()];
        for (int i = 0; i < this.blankLine.length; ++i) {
            this.blankLine[i] = 0xff000000;
        }

        // initialize background and window maps
        this.bgTileMapAdr = 0;
        this.windowTileMapAdr = 0;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        out.writeLong(this.nextUpdate);
        out.writeInt(this.mode);
        out.writeInt(this.currentLine);
        out.writeBoolean(this.isLCDEnabled);
        out.writeBoolean(this.isWindowEnabled);
        out.writeInt(this.tileDataArea);
        out.writeInt(this.spriteHeight);
        out.writeBoolean(this.areSpritesEnabled);
        out.writeBoolean(this.isBGBlank);
        out.writeBoolean(this.haveSpritesPriority);
        out.writeInt(this.scrollX);
        out.writeInt(this.scrollY);
        out.writeInt(this.windowX);
        out.writeInt(this.windowY);
        out.writeInt(this.nextWindowY);
        out.writeInt(this.windowLine);
        out.writeBoolean(this.isHBlankIRQEnabled);
        out.writeBoolean(this.isVBlankIRQEnabled);
        out.writeBoolean(this.isOAMIRQEnabled);
        out.writeBoolean(this.isCoincidenceIRQEnabled);
        out.writeInt(this.currentVRAMBank);
        SerializationUtils.serialize(out, this.vRAM);
        out.writeInt(this.scalingMult);
        out.writeBoolean(this.isPaintFrame);
        out.writeInt(this.bgTileMapAdr);
        out.writeInt(this.windowTileMapAdr);
        out.writeInt(this.cpuSpeedMult);
        SerializationUtils.serialize(out, this.colorBytes);
        SerializationUtils.serialize(out, this.palettes);
        SerializationUtils.serialize(out, this.tiles);
        SerializationUtils.serialize(out, this.sprites);
        SerializationUtils.serialize(out, this.backgroundPriorities);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.nextUpdate = in.readLong();
        this.mode = in.readInt();
        this.currentLine = in.readInt();
        this.isLCDEnabled = in.readBoolean();
        this.isWindowEnabled = in.readBoolean();
        this.tileDataArea = in.readInt();
        this.spriteHeight = in.readInt();
        this.areSpritesEnabled = in.readBoolean();
        this.isBGBlank = in.readBoolean();
        this.haveSpritesPriority = in.readBoolean();
        this.scrollX = in.readInt();
        this.scrollY = in.readInt();
        this.windowX = in.readInt();
        this.windowY = in.readInt();
        this.nextWindowY = in.readInt();
        this.windowLine = in.readInt();
        this.isHBlankIRQEnabled = in.readBoolean();
        this.isVBlankIRQEnabled = in.readBoolean();
        this.isOAMIRQEnabled = in.readBoolean();
        this.isCoincidenceIRQEnabled = in.readBoolean();
        setGBCVRAMBank(in.readInt());
        SerializationUtils.deserialize(in, this.vRAM);
        this.scalingMult = in.readInt();
        setScaling(this.scalingMult);
        this.isPaintFrame = in.readBoolean();
        this.bgTileMapAdr = in.readInt();
        this.windowTileMapAdr = in.readInt();
        this.cpuSpeedMult = in.readInt();
        SerializationUtils.deserialize(in, this.colorBytes);
        SerializationUtils.deserialize(in, this.palettes);
        SerializationUtils.deserialize(in, this.tiles);
        SerializationUtils.deserialize(in, this.sprites);
        SerializationUtils.deserialize(in, this.backgroundPriorities);
        // have all tiles and lines be repainted
        this.areAllTilesInvalid = false;
        invalidateTiles();
        invalidateLines();
    }

    // implementation of the Observer interface
    public void update(final Object observed, final Object arg) {
        // we get informed about a new CPU speed?
        if (observed == this.gameboy.getCPU() && arg instanceof Long) {
            // then calculate a new multiplier for calculations where CPU speed is relevant
            final long newSpeed = ((Long) arg).longValue();

            this.cpuSpeedMult = (int) (newSpeed * 1024 / Gameboy.ORIGINAL_SPEED_CLASSIC);
        }
    }
}
