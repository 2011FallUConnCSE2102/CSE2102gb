/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.jme;

import de.joergjahnke.common.jme.ButtonAssignmentCanvas;
import de.joergjahnke.common.jme.LocalizationSupport;
import de.joergjahnke.common.jme.OrientationSensitiveCanvas;
import de.joergjahnke.common.lcdui.ImageUtils;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.gameboy.core.Gameboy;
import de.joergjahnke.gameboy.core.Joypad;
import de.joergjahnke.gameboy.core.VideoChip;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

/**
 * The actual MIDP canvas that shows the Gameboy Screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class GameboyCanvas extends OrientationSensitiveCanvas implements Observer {

    /**
     * image names for the on-screen buttons
     */
    private final static String[] BUTTON_IMAGES = {"/res/drawable/button_a", "/res/drawable/button_b", "/res/drawable/button_select", "/res/drawable/button_start"};
    /**
     * Joypad buttons assigned to the Gameboy buttons
     */
    private final static int[] BUTTON_JOYPAD_MAPPING = {Joypad.A, Joypad.B, Joypad.SELECT, Joypad.START};
    /**
     * image names for the on-screen direction buttons
     */
    private final static String[] DIRECTION_IMAGES = {"/res/drawable/button_up", "/res/drawable/button_down", "/res/drawable/button_left", "/res/drawable/button_right"};
    /**
     * Joypad direction buttons assigned to the on-screen direction buttons
     */
    private final static int[] DIRECTION_JOYPAD_MAPPING = {Joypad.UP, Joypad.DOWN, Joypad.LEFT, Joypad.RIGHT};
    /**
     * one pixel-sized image, used for collision detection
     */
    private final static String ONEPIXEL_IMAGE = "/res/jme/pixel.png";
    /**
     * the minimum percentage in relation to the screen size for accepting a pointer drag event as pointer movement
     */
    private final static int MIN_PERCENTAGE_POINTER_MOVEMENT = 5;
    /**
     * number of milliseconds when we automatically release a pressed key if keyRelease is not fired for the key
     */
    private final static long AUTOMATIC_KEY_RELEASE_TIME = 200;
    /**
     * Midlet the canvas belongs to
     */
    protected final MEGameboyMIDlet midlet;
    /**
     * Gameboy to display in the canvas
     */
    protected Gameboy gameboy;
    /**
     * do we display emulator errors? We do this only once until a reset
     */
    protected boolean showEmulatorExceptions = true;
    /**
     * x-coordinate where to paint the screen
     */
    private int x;
    /**
     * x-coordinate where to paint the screen
     */
    private int y;
    /**
     * we pre-calculate the dimensions to paint
     */
    private int paintWidth,  paintHeight;
    /**
     * on-screen buttons
     */
    private Sprite[] buttons = null;
    /**
     * button images
     */
    private Image[] buttonImages = null;
    /**
     * on-screen direction buttons
     */
    private Sprite[] directions = null;
    /**
     * direction button images
     */
    private Image[] directionImages = null;
    /**
     * one pixel image, used for collision detection of the buttons
     */
    private Image onePixel = null;
    /**
     * cache the Graphics object for better performance
     */
    protected final Graphics graphics;
    /**
     * pointer starting position, used when the pointer is dragged
     */
    private int pStartX = -1,  pStartY = -1;
    /**
     * if we have a custom button assignment then this value is not null
     */
    private Hashtable buttonAssignments = new Hashtable();
    /**
     * we use these timers to automatically release buttons after some time
     */
    private Hashtable buttonReleaseTimers = new Hashtable();
    /**
     * do we show on-screen buttons?
     */
    private boolean isShowButtons = false;
    /**
     * do we show the on-screen D-Pad?
     */
    private boolean isShowDPad = false;
    /**
     * copy of the RGB data array, we use this because the paint operations might be executed asynchronously and the RGB array might change while painting
     */
    private int[] rgbDataCopy = null;

    /**
     * Create a new Gameboy canvas
     *
     * @param   midlet  MIDlet this canvas is displayed in
     */
    public GameboyCanvas(final MEGameboyMIDlet midlet) {
        super(midlet);

        this.midlet = midlet;

        // switch to full screen mode, we need as much of the screen as possible
        setFullScreenMode(true);

        // load on-screen button images if a pointer can be used
        if (hasPointerEvents()) {
            this.isShowButtons = this.isShowDPad = true;
            this.buttons = new Sprite[BUTTON_IMAGES.length];
            this.buttonImages = new Image[BUTTON_IMAGES.length * 2];
            this.directions = new Sprite[DIRECTION_IMAGES.length];
            this.directionImages = new Image[DIRECTION_IMAGES.length * 2];

            try {
                // load images
                this.onePixel = Image.createImage(ONEPIXEL_IMAGE);
                for (int i = 0; i < BUTTON_IMAGES.length; ++i) {
                    this.buttonImages[i] = Image.createImage(BUTTON_IMAGES[i] + ".png");
                    this.buttonImages[i + BUTTON_IMAGES.length] = ImageUtils.adjustBrightness(this.buttonImages[i], 0.75);
                    this.buttons[i] = new Sprite(this.buttonImages[i]);
                }
                for (int i = 0; i < DIRECTION_IMAGES.length; ++i) {
                    this.directionImages[i] = Image.createImage(DIRECTION_IMAGES[i] + ".png");
                    this.directionImages[i + BUTTON_IMAGES.length] = ImageUtils.adjustBrightness(this.directionImages[i], 0.75);
                    this.directions[i] = new Sprite(this.directionImages[i]);
                }

                // check whether the additional images are too large for the screen
                int dim, bdim, ddim;

                if (getWidth() < getHeight()) {
                    dim = getWidth();
                    bdim = this.buttons[0].getWidth();
                    ddim = this.directions[0].getWidth();
                } else {
                    dim = getHeight();
                    bdim = this.buttons[0].getHeight();
                    ddim = this.directions[0].getHeight();
                }

                if (dim < bdim * this.buttons.length + ddim) {
                    this.directions = null;
                    if (dim < bdim * this.buttons.length) {
                        this.buttons = null;
                    }
                }
            } catch (IOException e) {
                // we could not load all buttons, so we clear the buttons array and display none
                this.buttons = null;
                this.directions = null;
            }
        }

        // cache the graphics object for better performance
        this.graphics = getGraphics();

        // set default buttons
        this.buttonAssignments.put(new Integer(KEY_NUM1), "A");
        this.buttonAssignments.put(new Integer(KEY_NUM3), "B");
        this.buttonAssignments.put(new Integer(KEY_NUM7), "Select");
        this.buttonAssignments.put(new Integer(KEY_NUM9), "Start");
    }

    /**
     * Get the Gameboy instance being displayed
     *
     * @return  the gameboy instance which is display in this canvas
     */
    public final Gameboy getGameboy() {
        return this.gameboy;
    }

    /**
     * Set the Gameboy instance to be displayed
     *
     * @param   gameboy the gameboy instance to be displayed in this canvas
     */
    public void setGameboy(final Gameboy gameboy) {
        this.gameboy = gameboy;
    }

    /**
     * Do we show on-screen buttons?
     *
     * @return  true if the buttons should be displayed
     */
    public boolean isShowButtons() {
        return this.isShowButtons && this.buttons != null;
    }

    /**
     * Define whether we show on-screen buttons
     *
     * @param isShowButtons true to show the buttons, false to hide them
     */
    public void setShowButtons(final boolean isShowButtons) {
        this.isShowButtons = isShowButtons;
    }

    /**
     * Do we show on-screen direction buttons?
     *
     * @return  true if the buttons should be displayed
     */
    public boolean isShowDirectionButtons() {
        return this.isShowDPad && this.directions != null;
    }

    /**
     * Define whether we show on-screen direction buttons
     *
     * @param isShowDPad true to show the buttons, false to hide them
     */
    public void setShowDirectionButtons(final boolean isShowDPad) {
        this.isShowDPad = isShowDPad;
    }

    /**
     * Get the currently set custom buttons
     * 
     * @return  map of key codes and joypad button names
     */
    public Hashtable getButtonAssignments() {
        return this.buttonAssignments;
    }

    /**
     * Set custom button assignment
     * 
     * @param buttonAssignments map of key codes and joypad button names
     */
    public void setButtonAssignments(final Hashtable buttonAssignments) {
        this.buttonAssignments = buttonAssignments;
    }

    /**
     * Paint the Gameboy screen and on-screen buttons
     */
    private void paint() {
        // first clear the screen
        this.graphics.setColor(0, 0, 0);
        this.graphics.fillRect(0, 0, getWidth(), getHeight());

        // copy the current Gameboy screen since the painting might run asynchronously and the screen contents might change while painting
        System.arraycopy(this.gameboy.getVideoChip().getRGBData(), 0, this.rgbDataCopy, 0, this.rgbDataCopy.length);

        // afterwards draw the Gameboy display
        if (!isAutoChangeOrientation() || this.transform == Sprite.TRANS_NONE) {
            // this is the fast and simple way of drawing
            this.graphics.drawRGB(this.rgbDataCopy, 0, this.paintWidth, this.x, this.y, this.paintWidth, this.paintHeight, false);
        } else {
            // creating an image for every repaint it quite costly, but it allows us to apply the rotation transformations
            Image image = Image.createRGBImage(this.rgbDataCopy, this.paintWidth, this.paintHeight, false);

            this.graphics.drawRegion(image, 0, 0, image.getWidth(), image.getHeight(), this.transform, this.x, this.y, Graphics.TOP | Graphics.LEFT);

            image = null;
            System.gc();
        }

        // draw the on-screen buttons if necessary
        if (isShowButtons()) {
            for (int i = 0; i < this.buttons.length; ++i) {
                this.buttons[i].setTransform(this.transform);
                this.buttons[i].paint(this.graphics);
            }
        }
        if (isShowDirectionButtons()) {
            for (int i = 0; i < this.directions.length; ++i) {
                this.directions[i].setTransform(this.transform);
                this.directions[i].paint(this.graphics);
            }
        }

        flushGraphics();
    }

    /**
     * Calculate the screen size of the Gameboy screen
     *
     * @throws NullPointerException if the Gameboy instance has not yet been set
     */
    public void calculateScreenSize() {
        // determine screen scaling factor
        final int w = getWidth(),  h = isAutoChangeOrientation() ? getWidth() : getHeight();
        double scalingW = w * 1.0 / VideoChip.SCREEN_WIDTH;
        double scalingH = h * 1.0 / VideoChip.SCREEN_HEIGHT;

        // if necessary, adjust scaling so that the on-screen buttons fit onto the screen
        final int buttonsWidth = isShowDirectionButtons() ? this.directions[0].getWidth() : isShowButtons() ? this.buttons[0].getWidth() : 0;
        final int buttonsHeight = isShowDirectionButtons() ? this.directions[0].getHeight() : isShowButtons() ? this.buttons[0].getHeight() : 0;

        if (isShowButtons() || isShowDirectionButtons()) {
            if (scalingH >= scalingW) {
                scalingH = (h - buttonsHeight) * 1.0 / VideoChip.SCREEN_HEIGHT;
            } else {
                scalingW = (w - buttonsWidth) * 1.0 / VideoChip.SCREEN_WIDTH;
            }
        }

        // set screen scaling
        final VideoChip video = gameboy.getVideoChip();
        double scaling = Math.min(scalingW, scalingH);

        try {
            final boolean useScaling = this.midlet.getSettings().getBoolean(MEGameboyMIDlet.SETTING_SCALING, true);

            if (!useScaling) {
                scaling = 1.0;
            }
        } catch (Exception e) {
            // setting could not be read, no problem we use default settings
        } finally {
            video.setScaling((int) (scaling * VideoChip.SCALING_MULTIPLIER));
        }

        // register as observer for screen refresh, joypad and emulator exceptions
        video.deleteObserver(this);
        video.addObserver(this);
        this.gameboy.deleteObserver(this);
        this.gameboy.addObserver(this);

        // determine size of the paintable area
        this.paintWidth = video.getScaledWidth();
        this.paintHeight = video.getScaledHeight();

        // reserve memory for a copy of the RGB data
        this.rgbDataCopy = new int[this.paintWidth * this.paintHeight];

        // determine position to start painting the screen
        if (isShowButtons() || isShowDirectionButtons()) {
            final int n1 = this.buttons.length;
            final int bw = this.buttons[0].getWidth();
            final int bh = this.buttons[0].getHeight();
            final int n2 = isShowDirectionButtons() ? 1 + n1 : n1;
            final int dw = this.directions == null ? 0 : this.directions[0].getWidth();
            final int dh = this.directions == null ? 0 : this.directions[0].getHeight();

            if (isShowButtons()) {
                // place buttons below the Gameboy screen?
                if (h - this.paintHeight >= bh) {
                    this.x = (w - this.paintWidth) >> 1;
                    this.y = (h - this.paintHeight - buttonsHeight) >> 1;

                    final int xinc = (w - n1 * bw - (isShowDirectionButtons() ? dw : 0)) / (n2 - 1) + bw;

                    for (int i = 0, xx = 0, yy = h - bh; i < n1; ++i, xx += xinc) {
                        this.buttons[i].setPosition(xx, yy);
                    }
                } else {
                    // no, place them to the right
                    this.x = (w - this.paintWidth - buttonsWidth) >> 1;
                    this.y = (h - this.paintHeight) >> 1;

                    final int yinc = (h - n1 * bh - (isShowDirectionButtons() ? dh : 0)) / (n2 - 1) + bh;

                    for (int i = 0, xx = w - bw, yy = 0; i < n1; ++i, yy += yinc) {
                        this.buttons[i].setPosition(xx, yy);
                    }
                }
            }

            // also show direction buttons
            if (isShowDirectionButtons()) {
                // these are always in the bottom right corner
                for (int i = 0; i < this.directions.length; ++i) {
                    this.directions[i].setPosition(w - dw, h - dh);
                }
            }
        } else {
            this.x = (w - this.paintWidth) >> 1;
            this.y = (h - this.paintHeight) >> 1;
        }
    }

    /**
     * Adjust joypad direction according to the current screen rotation
     *
     * @param   direction   normal joypad direction
     * @return  adjusted joypad direction
     */
    private int adjustJoypadDirection(final int direction) {
        switch (this.transform) {
            case Sprite.TRANS_ROT90:
                return direction < Joypad.UP ? direction << 2 : direction == Joypad.UP ? Joypad.LEFT : Joypad.RIGHT;
            case Sprite.TRANS_ROT180:
                return direction >= Joypad.UP ? direction ^ (Joypad.UP | Joypad.DOWN) : direction ^ (Joypad.LEFT | Joypad.RIGHT);
            case Sprite.TRANS_ROT270:
                return direction >= Joypad.UP ? direction >> 2 : direction == Joypad.RIGHT ? Joypad.DOWN : Joypad.UP;
            default:
                return direction;
        }
    }

    /**
     * Get joypad changes depending on the pressed or released key
     * 
     * @param keyCode   pressed or released key
     * @return  affected joypad directions + affected joypad buttons << 16
     */
    private int getJoypadChanges(final int keyCode) {
        int pressedDirections = this.gameboy.getJoypad().getDirections(), pressedButtons = this.gameboy.getJoypad().getButtons();
        int repeatMask = 0;
        final int gameAction = this.buttonAssignments.contains("Up") ? 0 : getGameAction(keyCode);
        String buttonName = null;

        if (this.buttonAssignments.containsKey(new Integer(keyCode))) {
            buttonName = this.buttonAssignments.get(new Integer(keyCode)).toString();
        } else if (this.buttonAssignments.containsKey(new Integer(keyCode + ButtonAssignmentCanvas.MASK_REPEAT_KEY))) {
            buttonName = this.buttonAssignments.get(new Integer(keyCode + ButtonAssignmentCanvas.MASK_REPEAT_KEY)).toString();
            repeatMask = ButtonAssignmentCanvas.MASK_REPEAT_KEY;
        }

        if ("Up".equals(buttonName) || gameAction == UP) {
            pressedDirections |= adjustJoypadDirection(Joypad.UP);
        }
        if ("Down".equals(buttonName) || gameAction == DOWN) {
            pressedDirections |= adjustJoypadDirection(Joypad.DOWN);
        }
        if ("Left".equals(buttonName) || gameAction == LEFT) {
            pressedDirections |= adjustJoypadDirection(Joypad.LEFT);
        }
        if ("Right".equals(buttonName) || gameAction == RIGHT) {
            pressedDirections |= adjustJoypadDirection(Joypad.RIGHT);
        }
        if ("A".equals(buttonName) || gameAction == FIRE) {
            pressedButtons |= Joypad.A;
        }
        if ("B".equals(buttonName)) {
            pressedButtons |= Joypad.B;
        }
        if ("Select".equals(buttonName)) {
            pressedButtons |= Joypad.SELECT;
        }
        if ("Start".equals(buttonName)) {
            pressedButtons |= Joypad.START;
        }

        return pressedDirections + (pressedButtons << 16) + repeatMask;
    }

    /**
     * Pass key events to Keyboard class or Joystick after converting the key code
     */
    protected void keyPressed(final int keyCode) {
        // trigger joypad movement if cursor keys or button keys are pressed
        final int changes = getJoypadChanges(keyCode);
        final int pressedDirections = changes & 0x0000ffff;
        final int pressedButtons = (changes & (0xffff0000 - ButtonAssignmentCanvas.MASK_REPEAT_KEY)) >> 16;

        // install a timer that releases the key after some time, if necessary
        final boolean needReleaseTimer = (changes & ButtonAssignmentCanvas.MASK_REPEAT_KEY) != 0;

        if (needReleaseTimer) {
            final Integer key = new Integer(keyCode);
            Timer buttonReleaseTimer = (Timer) this.buttonReleaseTimers.get(key);

            if (buttonReleaseTimer != null) {
                buttonReleaseTimer.cancel();
            }
            buttonReleaseTimer = new Timer();
            buttonReleaseTimer.schedule(
                    new TimerTask() {

                        public void run() {
                            keyReleased(keyCode);
                        }
                    }, AUTOMATIC_KEY_RELEASE_TIME);
            this.buttonReleaseTimers.put(key, buttonReleaseTimer);
        }

        // apply new directions and buttons
        this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | pressedDirections);
        this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | pressedButtons);
    }

    /**
     * Pass key events to Keyboard class after converting the key code
     */
    protected void keyReleased(final int keyCode) {
        // end joypad movement if cursor keys or button keys are released
        final int changes = getJoypadChanges(keyCode);
        final int pressedDirections = changes & 0x0000ffff;
        final int pressedButtons = (changes & (0xffff0000 - ButtonAssignmentCanvas.MASK_REPEAT_KEY)) >> 16;

        this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - pressedDirections));
        this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - pressedButtons));
    }

    /**
     * A repeated key gets continuously pressed
     */
    protected void keyRepeated(final int keyCode) {
        keyPressed(keyCode);
    }

    /**
     * Check if an on-screen button has been pressed or if a pointer movement starts
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerPressed(final int x, final int y) {
        boolean wasEventProcessed = false;

        // check whether an on-screen button was pressed, if these are active
        if (isShowButtons()) {
            for (int i = 0; !wasEventProcessed && i < this.buttons.length; ++i) {
                if (this.buttons[i].collidesWith(this.onePixel, x, y, true)) {
                    this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | BUTTON_JOYPAD_MAPPING[i]);
                    wasEventProcessed = true;
                }
            }
        }
        if (isShowDirectionButtons()) {
            for (int i = 0; !wasEventProcessed && i < this.directions.length; ++i) {
                if (this.directions[i].collidesWith(this.onePixel, x, y, true)) {
                    this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | DIRECTION_JOYPAD_MAPPING[i]);
                    wasEventProcessed = true;
                }
            }
        }

        // if it was not an on-screen button then we record the position as possible start of a pointer drag operation
        if (!wasEventProcessed && x >= this.x && x < this.x + this.paintWidth && y >= this.y && y < this.y + this.paintHeight) {
            this.pStartX = x;
            this.pStartY = y;
        }
    }

    /**
     * Check if an on-screen button has been released or if a pointer movement was ended
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerReleased(final int x, final int y) {
        boolean wasEventProcessed = false;

        // check for the on-screen buttons if they are active
        if (isShowButtons()) {
            for (int i = 0; !wasEventProcessed && i < this.buttons.length; ++i) {
                if (this.buttons[i].collidesWith(this.onePixel, x, y, true)) {
                    this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - BUTTON_JOYPAD_MAPPING[i]));
                    wasEventProcessed = true;
                }
            }
        }
        if (isShowDirectionButtons()) {
            for (int i = 0; !wasEventProcessed && i < this.directions.length; ++i) {
                if (this.directions[i].collidesWith(this.onePixel, x, y, true)) {
                    this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - DIRECTION_JOYPAD_MAPPING[i]));
                    wasEventProcessed = true;
                }
            }
        }

        // if it was not an on-screen button that was released then it might be the end of a drag operation
        if (!wasEventProcessed && x >= this.x && x < this.x + this.paintWidth && y >= this.y && y < this.y + this.paintHeight) {
            // in this case we cease all joypad movement
            this.gameboy.getJoypad().setDirections(0);
        }

        // no pointer drag operation currently running
        this.pStartX = this.pStartY = -1;
    }

    /**
     * Check if pointer movement is used to simulate joypad input
     * 
     * @param   x   x-position of the event
     * @param   y   y-position of the event
     */
    protected void pointerDragged(final int x, final int y) {
        // the event is within the gameboy screen area?
        if (x >= this.x && x < this.x + this.paintWidth && y >= this.y && y < this.y + this.paintHeight) {
            // a pointer drag operation is running?
            if (this.pStartX > 0) {
                // check distance to last movement
                final int distX = x - this.pStartX;
                final int distY = y - this.pStartY;

                // the direction with more movement is the one we consider
                // a minimum percentage of the screen the pointer must have been moved, then the joypad is triggered accordingly
                if (Math.abs(distX) > Math.abs(distY)) {
                    if (Math.abs(distX) > this.paintWidth * MIN_PERCENTAGE_POINTER_MOVEMENT / 100) {
                        this.gameboy.getJoypad().setDirections(distX < 0 ? adjustJoypadDirection(Joypad.LEFT) : adjustJoypadDirection(Joypad.RIGHT));
                    }
                } else {
                    if (Math.abs(distY) > this.paintHeight * MIN_PERCENTAGE_POINTER_MOVEMENT / 100) {
                        this.gameboy.getJoypad().setDirections(distY < 0 ? adjustJoypadDirection(Joypad.UP) : adjustJoypadDirection(Joypad.DOWN));
                    }
                }
            }
        }
    }

    /**
     * Set joystick according to accelerometer changes
     */
    public void onAccelerometerChange(final double x, final double y, final double z) {
        final Joypad joypad = this.gameboy.getJoypad();

        if (x > 200) {
            joypad.setDirections(joypad.getDirections() | adjustJoypadDirection(Joypad.LEFT));
        } else if (x < -200) {
            joypad.setDirections(joypad.getDirections() | adjustJoypadDirection(Joypad.RIGHT));
        } else {
            joypad.setDirections(joypad.getDirections() & (0x0f - adjustJoypadDirection(Joypad.LEFT) - adjustJoypadDirection(Joypad.RIGHT)));
        }
        if (y < -200) {
            joypad.setDirections(joypad.getDirections() | adjustJoypadDirection(Joypad.UP));
        } else if (y > 200) {
            joypad.setDirections(joypad.getDirections() | adjustJoypadDirection(Joypad.DOWN));
        } else {
            joypad.setDirections(joypad.getDirections() & (0x0f - adjustJoypadDirection(Joypad.UP) - adjustJoypadDirection(Joypad.DOWN)));
        }

        // activate the backlight because with accelerometer usage the keys don't get pressed often, so that many device switch off the backlight
        try {
            de.joergjahnke.common.jme.Backlight.setLevel(75);
        } catch(Throwable t) {
            // the API required for this method might not be available
        }
    }
    // implementation of the Observer interface

    /**
     * Initialize screen update
     */
    public void update(final Object observable, final Object event) {
        // the notification comes from the video chip?
        if (observable instanceof VideoChip && event == VideoChip.SIGNAL_NEW_FRAME) {
            // then repaint the screen
            paint();
        // the emulator encountered an exception?
        } else if (event instanceof Throwable && this.showEmulatorExceptions) {
            // we show the first of such exceptions on the screen, all are in the log anyway
            this.showEmulatorExceptions = false;
            this.display.setCurrent(new Alert(LocalizationSupport.getMessage("AnErrorHasOccurred"), LocalizationSupport.getMessage("ErrorWas") + ((Throwable) event).getMessage() + LocalizationSupport.getMessage("NoFurtherMessages"), null, AlertType.WARNING));
        } else if (observable instanceof Joypad) {
            // toggle on-screen keys if necessary
            if (isShowButtons()) {
                final int pressedButtons = ((Joypad) observable).getButtons();

                for (int i = 0; i < BUTTON_JOYPAD_MAPPING.length; ++i) {
                    this.buttons[i].setImage(this.buttonImages[(pressedButtons & BUTTON_JOYPAD_MAPPING[i]) != 0 ? i + BUTTON_IMAGES.length : i], this.buttons[i].getWidth(), this.buttons[i].getHeight());
                }
            }

            if (isShowDirectionButtons()) {
                final int selectedDirections = ((Joypad) observable).getDirections();

                for (int i = 0; i < DIRECTION_JOYPAD_MAPPING.length; ++i) {
                    this.directions[i].setImage(this.directionImages[(selectedDirections & DIRECTION_JOYPAD_MAPPING[i]) != 0 ? i + BUTTON_IMAGES.length : i], this.directions[i].getWidth(), this.directions[i].getHeight());
                }
            }

            if (isShowButtons() || isShowDirectionButtons()) {
                paint();
            }
        }
    }
}
