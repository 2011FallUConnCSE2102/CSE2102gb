/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.common.jme;

import java.io.IOException;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.CustomItem;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * A button with an image instead of a text
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public abstract class ImageButton extends CustomItem {

    /**
     * padding to next object
     */
    private final static int PADDING = 1;
    /**
     * image to display
     */
    private final Image image;
    /**
     * do we display a border?
     */
    private boolean hasFocus = false;
    /**
     * is the button currently pressed?
     */
    private boolean isPressed = false;

    /**
     * Create a new ImageButton
     * 
     * @param   imageName   image name
     * @throws java.io.IOException if the image cannot be loaded
     */
    public ImageButton(final String imageName) throws IOException {
        super("");

        this.image = Image.createImage(imageName);
    }

    /**
     * Is the button currently being pressed?
     * 
     * @return  true if the button is pressed, otherwise false
     */
    public boolean isPressed() {
        return this.isPressed;
    }

    /**
     * Set whether the button is pressed or released
     * 
     * @param isPressed true if the button is now pressed, false if it has been released
     */
    private void setPressed(final boolean isPressed) {
        if (this.isPressed != isPressed) {
            this.isPressed = isPressed;
            if (isPressed) {
                onButtonPressed();
            }
        }
    }

    protected int getMinContentWidth() {
        return this.image.getWidth();
    }

    protected int getMinContentHeight() {
        return this.image.getHeight();
    }

    protected int getPrefContentWidth(final int width) {
        return getMinContentWidth() + 2 * PADDING;
    }

    protected int getPrefContentHeight(final int height) {
        return getMinContentHeight() + 2 * PADDING;
    }

    protected void paint(final Graphics g, final int w, final int h) {
        g.setColor(this.hasFocus ? 0x00e0e0e0 : 0x00c0c0c0);
        g.fillRect(PADDING, PADDING, w - 2 * PADDING, h - 2 * PADDING);
        g.drawImage(this.image, w / 2 + PADDING, h / 2 + PADDING, Graphics.HCENTER | Graphics.VCENTER);
    }

    protected void keyPressed(final int keyCode) {
        if (getGameAction(keyCode) == Canvas.FIRE) {
            setPressed(true);
        } else {
            super.keyPressed(keyCode);
        }
    }

    protected void keyReleased(final int keyCode) {
        if (getGameAction(keyCode) == Canvas.FIRE) {
            setPressed(false);
        } else {
            super.keyPressed(keyCode);
        }
    }

    protected void pointerPressed(final int x, final int y) {
        setPressed(true);
    }

    protected void pointerReleased(final int x, final int y) {
        setPressed(false);
    }

    protected boolean traverse(final int dir, final int w, final int h, final int[] visRect_inout) {
        this.hasFocus = true;
        repaint();
        return false;
    }

    protected void traverseOut() {
        this.hasFocus = false;
        repaint();
    }

    /**
     * This method is called when the button gets pressed.
     * The default implementation does nothing. Subclasses need to override this
     * method to react to button clicks.
     */
    public abstract void onButtonPressed();
}
