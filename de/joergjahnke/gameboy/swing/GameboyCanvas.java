/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.swing;

import de.joergjahnke.common.util.Observer;
import de.joergjahnke.gameboy.core.Gameboy;
import de.joergjahnke.gameboy.core.Joypad;
import de.joergjahnke.gameboy.core.VideoChip;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * The actual Swing canvas that shows the Gameboy Screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class GameboyCanvas extends JPanel implements Observer, KeyListener {
    // image with Gameboy screen to display
    private BufferedImage screenImage;
    // Gameboy to display in the canvas
    private Gameboy gameboy;
    // position to paint the screen
    private int sx,  sy;
    // size of painted screen
    private int swidth,  sheight;
    // preferred size
    private Dimension preferredSize;

    /**
     * Create a new Gameboy canvas.
     */
    public GameboyCanvas() {
        addKeyListener(this);
        setFocusable(true);
    }

    /**
     * We'd like to have the size of the Gameboy screen
     */
    @Override
    public Dimension getPreferredSize() {
        return this.preferredSize;
    }

    @Override
    public void setPreferredSize(final Dimension size) {
        // the original Gameboy size is the minimum
        if (size.getHeight() < VideoChip.SCREEN_HEIGHT) {
            size.width = VideoChip.SCREEN_WIDTH;
            size.height = VideoChip.SCREEN_HEIGHT;
        }

        // set new size
        this.preferredSize = size;
    }

    /**
     * Scale the screen size with a given factor
     *
     * @param   scaling scaling factor, 1 equals 160x144 pixels i.e. the Gameboy's screen size
     */
    public void setScaling(final int scaling) {
        // set component size to fit new desired Gameboy screen size
        final Dimension newSize = new Dimension(VideoChip.SCREEN_WIDTH * scaling, VideoChip.SCREEN_HEIGHT * scaling);

        setPreferredSize(newSize);
        setSize(getPreferredSize());

        // determine screen rectangle
        this.swidth = VideoChip.SCREEN_WIDTH * scaling;
        this.sheight = VideoChip.SCREEN_HEIGHT * scaling;
        this.sx = 0;
        this.sy = 0;
    }

    /**
     * Get the instance being displayed
     */
    public final Gameboy getGameboy() {
        return this.gameboy;
    }

    /**
     * Set the instance being displayed
     */
    public void setGameboy(final Gameboy instance) {
        // store instance
        this.gameboy = instance;

        // register as observer for screen refresh
        final VideoChip vic = this.gameboy.getVideoChip();

        vic.addObserver(this);

        // create image where the Gameboy screen content is copied to
        this.screenImage = new BufferedImage(VideoChip.SCREEN_WIDTH, VideoChip.SCREEN_HEIGHT, BufferedImage.TYPE_INT_ARGB);

        // set the preferred size
        setScaling(1);
    }

    /**
     * Repaint the screen.
     * If the Gameboy is running then its contents are displayed here.
     *
     * @param   g   graphics context to use
     */
    @Override
    public void paint(final Graphics g) {
        if (this.gameboy != null) {
            this.screenImage.setRGB(0, 0, VideoChip.SCREEN_WIDTH, VideoChip.SCREEN_HEIGHT, this.gameboy.getVideoChip().getRGBData(), 0, VideoChip.SCREEN_WIDTH);
            g.drawImage(this.screenImage, this.sx, this.sy, this.swidth, this.sheight, this);
        }
    }

    // implementation of the KeyListener interface
    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    public void keyPressed(final KeyEvent event) {
        int pressedDirection = 0, pressedButton = 0;

        switch (event.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                pressedDirection = Joypad.LEFT;
                break;
            case KeyEvent.VK_RIGHT:
                pressedDirection = Joypad.RIGHT;
                break;
            case KeyEvent.VK_UP:
                pressedDirection = Joypad.UP;
                break;
            case KeyEvent.VK_DOWN:
                pressedDirection = Joypad.DOWN;
                break;
            case KeyEvent.VK_A:
                pressedButton = Joypad.A;
                break;
            case KeyEvent.VK_B:
                pressedButton = Joypad.B;
                break;
            case KeyEvent.VK_ENTER:
                pressedButton = Joypad.SELECT;
                break;
            case KeyEvent.VK_SPACE:
                pressedButton = Joypad.START;
                break;
            default:
                ;
        }

        this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | pressedDirection);
        this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | pressedButton);
    }

    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
    public void keyReleased(final KeyEvent event) {
        int pressedDirection = 0, pressedButton = 0;

        switch (event.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                pressedDirection = Joypad.LEFT;
                break;
            case KeyEvent.VK_RIGHT:
                pressedDirection = Joypad.RIGHT;
                break;
            case KeyEvent.VK_UP:
                pressedDirection = Joypad.UP;
                break;
            case KeyEvent.VK_DOWN:
                pressedDirection = Joypad.DOWN;
                break;
            case KeyEvent.VK_A:
                pressedButton = Joypad.A;
                break;
            case KeyEvent.VK_B:
                pressedButton = Joypad.B;
                break;
            case KeyEvent.VK_ENTER:
                pressedButton = Joypad.SELECT;
                break;
            case KeyEvent.VK_SPACE:
                pressedButton = Joypad.START;
                break;
            default:
                ;
        }

        this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - pressedDirection));
        this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - pressedButton));
    }

    public void keyTyped(final KeyEvent e) {
        // we do nothing as keyPressed and keyReleased already did all the work
    }

    // implementation of the Observer interface
    /**
     * Initialize screen update
     */
    public final void update(final Object observable, final Object event) {
        // the notification comes from the video chip?
        if (observable instanceof VideoChip) {
            // repaint the screen
            repaint();
        }
    }
}

