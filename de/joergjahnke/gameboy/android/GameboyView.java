/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.android;

import android.content.Context;
import android.content.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import de.joergjahnke.common.android.OrientationSensorListener;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.gameboy.core.Gameboy;
import de.joergjahnke.gameboy.core.Joypad;
import de.joergjahnke.gameboy.core.VideoChip;
import java.util.Map;

/**
 * The actual Android view that shows the Gameboy screen.
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class GameboyView extends View implements Observer, OrientationSensorListener {

    /**
     * message we send to repaint the screen
     */
    protected final static int MSG_REPAINT = 1;
    /**
     * resource ids of the Gameboy buttons
     */
    private final static int[] BUTTON_RESOURCE_IDS = { R.drawable.button_a, R.drawable.button_b, R.drawable.button_select, R.drawable.button_start };
    /**
     * keys assigned to the Gameboy buttons
     */
    private final static int[] BUTTON_KEYS = { KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_NEWLINE, KeyEvent.KEYCODE_SPACE };
    /**
     * The currently active gameboy instance
     */
    private Gameboy gameboy = null;
    /**
     * bitmap used when painting the Gameboy screen 
     */
    protected Bitmap screenBitmap = null;
    /**
     * message handler for this thread
     */
    protected final Handler handler;
    /**
     * rectangle where we paint the Gameboy screen
     */
    private Rect screenRect = null;
    /**
     * Gameboy buttons to be displayed when we have a touch screen available
     */
    private final BitmapDrawable buttons[];
    /**
     * do we use antialiasing to improve the Gameboy screen content?
     */
    private boolean useAntialiasing = true;

    /**
     * Create a new Gameboy
     * 
     * @param	context	AndroidBoy instance	
     */
    public GameboyView(final Context context) {
        super(context);
        
        // we need to get key events
        setFocusable(true);

        // initialize a handler which we can use to process messages from other threads
        this.handler = new Handler() {

            @Override
            public void handleMessage(final Message msg) {
                if (msg.what == MSG_REPAINT) {
                    invalidate();
                }
            }
        };
        
        // initialize Gameboy buttons if we have a touch screen available
        final Resources.Configuration config = context.getResources().getConfiguration();
        
        config.setToDefaults();
        if(config.touchscreen != Resources.Configuration.TOUCHSCREEN_NOTOUCH) {
        	this.buttons = new BitmapDrawable[BUTTON_RESOURCE_IDS.length];
        	for(int i = 0 ; i < this.buttons.length ; ++i) {
        		this.buttons[i] = new BitmapDrawable(BitmapFactory.decodeResource(getResources(), BUTTON_RESOURCE_IDS[i]));
        	}
        } else {
        	this.buttons = null;
        }
    }

    /**
     * Create a new Gameboy
     * 
     * @param	context	AndroidBoy instance
     * @param	attrs	not used
     * @param	inflateParams	not used	
     */
    @SuppressWarnings("unchecked")
	public GameboyView(final Context context, final AttributeSet attrs, final Map inflateParams){
    	this(context);
    }

    /**
     * Get the currently active Gameboy instance
     * 
     * @return	Gameboy instance or null if no Gameboy is currently being emulated
     */
    public Gameboy getGameboy() {
        return this.gameboy;
    }

    /**
     * Set the Gameboy instance to be displayed
     */
    public void setGameboy(final Gameboy gameboy) {
        // store instance
        this.gameboy = gameboy;

        // register as observer for screen refresh and emulator exceptions
        VideoChip video = gameboy.getVideoChip();

        video.addObserver(this);

        // create a bitmap used when painting the Gameboy screen
        this.screenBitmap = Bitmap.createBitmap(video.getScaledWidth(), video.getScaledHeight(), false);
    }

	/**
	 * Check whether antialiasing is used for the Gameboy screen
	 * 
	 * @return	true if antialiasing is used
	 */
	public boolean isUseAntialiasing() {
		return this.useAntialiasing;
	}

    /**
     * Set whether to use antialiasing for the Gameboy screen
     * 
	 * @param	useAntialiasing	true to enable antialiasing, false to disable
	 */
	public void setUseAntialiasing(boolean useAntialiasing) {
		this.useAntialiasing = useAntialiasing;
	}

	/**
     * Paint the Gameboy screen
     *
     * @param   canvas	canvas to paint on
     */
    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        if (this.gameboy != null) {
            // set black as screen background
            final Paint paint = new Paint();

            paint.setColor(0);
            paint.setAlpha(255);
            canvas.drawPaint(paint);

            // show Gameboy screen
            final VideoChip video = this.gameboy.getVideoChip();
            final Paint paint2 = isUseAntialiasing() ? new Paint(Paint.ANTI_ALIAS_FLAG + Paint.FILTER_BITMAP_FLAG) : null; 

            this.screenBitmap.setPixels(video.getRGBData(), 0, video.getScaledWidth(), 0, 0, this.screenBitmap.width(), this.screenBitmap.height());
            canvas.drawBitmap(this.screenBitmap, null, this.screenRect, paint2);
            
            // show Gameboy buttons if these were created
            if(this.buttons != null) {
            	for(int i = 0, to = this.buttons.length; i < to; ++i) {
            		this.buttons[i].draw(canvas);
            	}
            }
        }
    }

    @Override
    public void onSizeChanged(int w, int h, final int oldw, final int oldh) {
    	// determine optimal size of the screen
    	this.screenRect = determineScreenRect(w, h);
    	
    	// we need to display additional Gameboy buttons?
    	if(this.buttons != null) {
    		// ensure that the screen leaves enough space
    		final int bw = this.buttons[0].getBitmap().getWidth();
    		final int bh = this.buttons[0].getBitmap().getHeight();
    		
    		if(w - this.screenRect.width() < bw && h - this.screenRect.height() < bh) {
    			// we have to adjust the screen size
    			if(w - this.screenRect.width() > h - this.screenRect.height()) {
    				this.screenRect = determineScreenRect(w - bw, h);
    			} else {
    				this.screenRect = determineScreenRect(w, h - bh);
    			}
    		}
    		
    		// reposition the screen if necessary
    		if(w - this.screenRect.right < bw && h - this.screenRect.bottom < bh) {
  				this.screenRect.offsetTo(0, 0);
    		}
    		
    		// determine the button positions
    		final int n = this.buttons.length;
    		
			if(w - this.screenRect.width() > h - this.screenRect.height()) {
				// place buttons at the size of the screen
				final int yinc = (h - n * bh) / (n - 1) + bh;
				
				for(int i = 0, x = w - bw, y = 0 ; i < n ; ++i, y += yinc) {
					this.buttons[i].setBounds(x, y, x + bw, y + bh);
				}
			} else {
				// place buttons at the bottom of the screen
				final int xinc = (w - n * bw) / (n - 1) + bw;
				
				for(int i = 0, x = 0, y = h - bh ; i < n ; ++i, x += xinc) {
					this.buttons[i].setBounds(x, y, x + bw, y + bh);
				}
			}
    	}
    }
    
    /**
     * Determine the best size for the Gameboy screen for a given display size
     * 
     * @param	w	display width
     * @param	h	display height
     * @return	screen rectangle
     */
    public Rect determineScreenRect(final int w, final int h) {
        final VideoChip video = this.gameboy.getVideoChip();
        // scale to fit the screen while keeping the aspect ratio
        final double scaling = Math.min(w * 1.0 / video.getScaledWidth(), h * 1.0 / video.getScaledHeight());
        final int paintWidth = (int) (video.getScaledWidth() * scaling);
        final int paintHeight = (int) (video.getScaledHeight() * scaling);
        final int x = (w - paintWidth) / 2;
        final int y = (h - paintHeight) / 2;
        
        return new Rect(x, y, x + paintWidth, y + paintHeight);
    }
    
    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
	@Override
	public boolean onKeyDown( final int keyCode, final KeyEvent event ) {
        switch(keyCode) {
	        case KeyEvent.KEYCODE_DPAD_LEFT:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | Joypad.LEFT);
	            break;
	        case KeyEvent.KEYCODE_DPAD_RIGHT:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | Joypad.RIGHT);
	            break;
	        case KeyEvent.KEYCODE_DPAD_UP:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | Joypad.UP);
	            break;
	        case KeyEvent.KEYCODE_DPAD_DOWN:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() | Joypad.DOWN);
	            break;
	        case KeyEvent.KEYCODE_DPAD_CENTER:
	        case KeyEvent.KEYCODE_A:
	        case KeyEvent.KEYCODE_1:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | Joypad.A);
	            if(this.buttons != null ) this.buttons[0].setAlpha(0x80);
	            break;
	        case KeyEvent.KEYCODE_B:
	        case KeyEvent.KEYCODE_3:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | Joypad.B);
	            if(this.buttons != null ) this.buttons[1].setAlpha(0x80);
	            break;
	        case KeyEvent.KEYCODE_NEWLINE:
	        case KeyEvent.KEYCODE_7:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | Joypad.SELECT);
	            if(this.buttons != null ) this.buttons[2].setAlpha(0x80);
	            break;
	        case KeyEvent.KEYCODE_SPACE:
	        case KeyEvent.KEYCODE_9:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() | Joypad.START);
	            if(this.buttons != null ) this.buttons[3].setAlpha(0x80);
	            break;
	        default:
	            ;
	    }
        
        return super.onKeyDown( keyCode, event );
    }
    
    /**
     * Convert key event to key selection suitable for the Keyboard class and pass it to that class
     */
	@Override
	public boolean onKeyUp( final int keyCode, final KeyEvent event ) {
        switch(keyCode) {
	        case KeyEvent.KEYCODE_DPAD_LEFT:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - Joypad.LEFT));
	            break;
	        case KeyEvent.KEYCODE_DPAD_RIGHT:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - Joypad.RIGHT));
	            break;
	        case KeyEvent.KEYCODE_DPAD_UP:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - Joypad.UP));
	            break;
	        case KeyEvent.KEYCODE_DPAD_DOWN:
	            this.gameboy.getJoypad().setDirections(this.gameboy.getJoypad().getDirections() & (0x0f - Joypad.DOWN));
	            break;
	        case KeyEvent.KEYCODE_DPAD_CENTER:
	        case KeyEvent.KEYCODE_A:
	        case KeyEvent.KEYCODE_1:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - Joypad.A));
	            if(this.buttons != null ) this.buttons[0].setAlpha(0xff);
	            break;
	        case KeyEvent.KEYCODE_B:
	        case KeyEvent.KEYCODE_3:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - Joypad.B));
	            if(this.buttons != null ) this.buttons[1].setAlpha(0xff);
	            break;
	        case KeyEvent.KEYCODE_NEWLINE:
	        case KeyEvent.KEYCODE_7:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - Joypad.SELECT));
	            if(this.buttons != null ) this.buttons[2].setAlpha(0xff);
	            break;
	        case KeyEvent.KEYCODE_SPACE:
	        case KeyEvent.KEYCODE_9:
	            this.gameboy.getJoypad().setButtons(this.gameboy.getJoypad().getButtons() & (0x0f - Joypad.START));
	            if(this.buttons != null ) this.buttons[3].setAlpha(0xff);
	            break;
	        default:
	            ;
	    }
        
        return super.onKeyUp( keyCode, event );
    }
	
	@Override
    public boolean onTouchEvent(final MotionEvent event) {
		// check whether a button was pressed or released
		if(this.buttons != null) {
			for(int i = 0 ; i < this.buttons.length ; ++i) {
				// the event belongs to this button?
				if(this.buttons[i].getBounds().contains((int)event.getX(), (int)event.getY())) {
					// simulate a key event that corresponds to the button
					if(event.getAction() == MotionEvent.ACTION_DOWN) {
						onKeyDown(BUTTON_KEYS[i], null);
					} else if(event.getAction() == MotionEvent.ACTION_UP) {
						onKeyUp(BUTTON_KEYS[i], null);
					}
				}
			}
		}
		
		// check whether we had a move event
		if(event.getAction() == MotionEvent.ACTION_MOVE && event.getHistorySize() > 0) {
			final int hist = event.getHistorySize();
			final float xmove = event.getX() - event.getHistoricalX(hist - 1);
			final float ymove = event.getY() - event.getHistoricalY(hist - 1);
			
			// horizontal movement?
			if(Math.abs(xmove) > Math.abs(ymove)) {
				if(xmove < 0) {
					onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, null);
				} else {
					onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, null);
				}
			// no, vertical movement
			} else {
				if(ymove < 0) {
					onKeyDown(KeyEvent.KEYCODE_DPAD_UP, null);
				} else {
					onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, null);
				}
			}
			
			// stop the event shortly after
			postDelayed(
				new Runnable() {
					public void run() {
						gameboy.getJoypad().setDirections(0);
					}
				}, 500
			);
		}
		
		return true;
	}
	
    // implementation of the Observer interface
    /**
     * Initialize screen update
     */
    public final void update(final Object observed, final Object arg) {
        // a new frame was created by the video chip
        if (observed instanceof VideoChip && arg == VideoChip.SIGNAL_NEW_FRAME) {
            // repaint the screen
            handler.sendMessage(Message.obtain(this.handler, MSG_REPAINT));
        }
    }

    // implementation of the OrientationSensorListener interface
    /**
     * Translate orientation sensor changes into key events
     */
	@Override
	public boolean onOrientationChange(final float[] sensorValues) {
		final float pitch = sensorValues[1];
		
		if(pitch < -20) {
			onKeyDown(KeyEvent.KEYCODE_DPAD_UP, null);
		} else if(pitch > 20) {
			onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN, null);
		} else {
			onKeyUp(KeyEvent.KEYCODE_DPAD_UP, null);
			onKeyUp(KeyEvent.KEYCODE_DPAD_DOWN, null);
		}
		
		final float roll = sensorValues[2];
		
		if(roll < -20) {
			onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, null);
		} else if(roll > 20) {
			onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, null);
		} else {
			onKeyUp(KeyEvent.KEYCODE_DPAD_LEFT, null);
			onKeyUp(KeyEvent.KEYCODE_DPAD_RIGHT, null);
		}
		
		return true;
	}
}

