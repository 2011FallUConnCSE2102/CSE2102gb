/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.android;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.Menu.Item;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import de.joergjahnke.common.android.OrientationSensorNotifier;
import de.joergjahnke.common.extendeddevices.WavePlayer;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.gameboy.core.Gameboy;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Main class for the Android version of the emulator
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class AndroidGB extends Activity implements Observer {

    /**
     * File extension for the file with saved game data
     */
    public final static String SAVED_GAME_FILE_EXTENSION = ".sav";
    /**
     * Name of the application preferences
     */
    public final static String PREFS_NAME = "GameboyPreferences";
    /**
     * Setting for frame-skip
     */
    private final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * Setting name switching antialiasing on/off
     */
    private final static String SETTING_ANTIALIASING = "Antialiasing";
    /**
     * Setting name for setting sound on/off
     */
    protected final static String SETTING_SOUNDACTIVE = "SoundActive";
    /**
     * Setting name for setting orientation sensor support on/off
     */
    protected final static String SETTING_ORIENTATIONSENSORACTIVE = "OrientationSensorActive";
    /**
     * Setting name for file search starting directory
     */
    private final static String SETTING_FILESEARCH_STARTDIR = "FileSearchStartDir";

    // menu item IDs
    private final static int MENU_LOADCARTRIDGE = 1;
    private final static int MENU_QUITGAME = 2;
    private final static int MENU_PAUSE = 3;
    private final static int MENU_RESUME = 4;
    private final static int MENU_SETTINGS = 9;
    private final static int MENU_SHOWLOG = 10;
    private final static int MENU_ABOUT = 11;
    private final static int MENU_HELP = 12;
    private final static int MENU_EXIT = 14;
    /**
     * URL of the online help page
     */
    private final static String URL_ONLINE_HELP = "http://javagb.wiki.sourceforge.net/AndroidBoy+online+help?f=print";
    /**
     * message we send to repaint the status icon
     */
    protected final static int MSG_REPAINT_STATUS_ICON = 1;
    /**
     * view for the Gameboy screen
     */
    private GameboyView gameboyView = null;
    /**
     * Gameboy instance
     */
    private Gameboy gameboy = null;
    /**
     * the main menu
     */
    private Menu mainmenu = null;
    /**
     * emulator preferences
     */
    private SharedPreferences prefs = null;
    /**
     * last attached file
     */
    private File currentlyAttachedFile = null;
    /**
     * status icon
     */
    private LayerDrawable statusIcon = null;
    private Drawable[] statusIconLayers = new Drawable[3];
    /**
     * message handler for this thread
     */
    protected Handler handler = null;
    /**
     * notifies about orientation sensor changes
     */
    private OrientationSensorNotifier orientationSensorNotifier = null;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);

        // request some display features depending on the display size
        if (getResources().getDisplayMetrics().heightPixels >= 180) {
            requestWindowFeature(Window.FEATURE_LEFT_ICON);
            requestWindowFeature(Window.FEATURE_RIGHT_ICON);
        } else {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        // create the gameboy and a view for the Gameboy screen
        this.gameboy = new Gameboy();
        this.gameboy.addObserver(this);
        this.gameboyView = new GameboyView(this);
        this.gameboyView.setGameboy(this.gameboy);

        // show the main view containing the action buttons
        showMainView();

        try {
            // retrieve the application preferences
            this.prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            this.gameboy.getVideoChip().setFrameSkip(this.prefs.getInt(SETTING_FRAMESKIP, this.gameboy.getVideoChip().getFrameSkip()));
            this.gameboyView.setUseAntialiasing(this.prefs.getBoolean(SETTING_ANTIALIASING, this.gameboyView.isUseAntialiasing()));

            // add title and status icons
            try {
                // add title icon
                setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.androidgb_sm);

                // add status icons as LayerDrawable
                final int[] resIds = {R.drawable.speedometer_green, R.drawable.speedometer_yellow, R.drawable.speedometer_red};

                for (int i = 0; i < this.statusIconLayers.length; ++i) {
                    this.statusIconLayers[i] = new BitmapDrawable(BitmapFactory.decodeResource(getResources(), resIds[i]));
                }
                this.statusIcon = new LayerDrawable(this.statusIconLayers);
                setFeatureDrawable(Window.FEATURE_RIGHT_ICON, this.statusIcon);
            	for( int i = 0 ; i < this.statusIconLayers.length ; ++i ) {
            		this.statusIconLayers[ i ].setAlpha(0);
            	}

                // initialize a handler which we can use to process messages from other threads
                this.handler = new Handler() {

                    @Override
                    public void handleMessage(final Message msg) {
                        if (msg.what == MSG_REPAINT_STATUS_ICON) {
                            statusIcon.invalidateSelf();
                        }
                    }
                };
            } catch (Exception e) {
            // we could not add the icons, that's OK
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(getResources().getString(R.string.msg_couldNotInitialize) + t);
        }
    }

    /**
     * Pause the emulator when another activity is used
     */
    @Override
    public void onPause() {
        this.gameboy.pause();

        super.onPause();
    }

    /**
     * Resume the emulator when the user returns back to it
     */
    @Override
    public void onResume() {
        this.gameboy.resume();

        super.onResume();
    }

    /**
     * We stop the emulator thread when stopping the activity
     */
    @Override
    public void onDestroy() {
        this.gameboy.stop();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final boolean result = super.onCreateOptionsMenu(menu);

        menu.add(2, MENU_LOADCARTRIDGE, R.string.menu_loadCartridge);
        menu.add(3, MENU_QUITGAME, R.string.menu_quitGame);
        menu.add(4, MENU_PAUSE, R.string.menu_pause);
        menu.add(5, MENU_RESUME, R.string.menu_resume);
        //menu.addSeparator(7, -1);
        menu.add(9, MENU_SETTINGS, R.string.menu_settings);
        menu.add(10, MENU_SHOWLOG, R.string.menu_showLog);
        menu.add(11, MENU_ABOUT, R.string.menu_about);
        menu.add(12, MENU_HELP, R.string.menu_help);
        //menu.addSeparator(14, -1);
        menu.add(15, MENU_EXIT, R.string.menu_exit);

        this.mainmenu = menu;

        return result;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final boolean result = super.onPrepareOptionsMenu(menu);

        if (this.gameboy.isRunning()) {
            this.mainmenu.findItem(MENU_QUITGAME).setShown(true);
            this.mainmenu.findItem(MENU_PAUSE).setShown(!this.gameboy.isPaused());
            this.mainmenu.findItem(MENU_RESUME).setShown(this.gameboy.isPaused());
            this.mainmenu.findItem(MENU_LOADCARTRIDGE).setShown(false);
            this.mainmenu.findItem(MENU_SETTINGS).setShown(false);
        } else {
        	this.mainmenu.setItemShown(MENU_QUITGAME, false);
            this.mainmenu.findItem(MENU_PAUSE).setShown(false);
            this.mainmenu.findItem(MENU_RESUME).setShown(false);
            this.mainmenu.findItem(MENU_LOADCARTRIDGE).setShown(true);
            this.mainmenu.findItem(MENU_SETTINGS).setShown(true);
        }
        
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(final Item item) {
        switch (item.getId()) {
            case MENU_LOADCARTRIDGE:
                showLoadCartridgeDialog();
                break;
            case MENU_QUITGAME:
            	// stop emulation
                this.gameboy.stop();
                // we have to save the cartridge RAM?
                if(this.gameboy.getCartridge().hasBatterySupport()) {
                	// save the game
                	final String cartridgeName = this.currentlyAttachedFile.getAbsolutePath();
                	final String saveName = cartridgeName.substring(0, cartridgeName.lastIndexOf('.')) + SAVED_GAME_FILE_EXTENSION;
                	
                	try {
                		final OutputStream saveStream = new BufferedOutputStream(new FileOutputStream(saveName)); 
                	
                		for(int i = 0 ; i < this.gameboy.getCartridge().getRAMBanks().length ; ++i) {
                			saveStream.write(this.gameboy.getCartridge().getRAMBanks()[i]);
                		}
                		saveStream.close();
                	} catch(IOException e ) {
                		// we could not save the file
                        showAlert(getResources().getString(R.string.title_warning), 0, getResources().getString(R.string.msg_gameNotSave), "OK", true);
                	}
                }
                // return to main view
                showMainView();
                break;
            case MENU_PAUSE:
                this.gameboy.pause();
                break;
            case MENU_RESUME:
                this.gameboy.resume();
                break;
            
            case MENU_SETTINGS:
	            showSettingsDialog();
	            break;
            case MENU_SHOWLOG:
                if (null == this.gameboy) {
                    showAlert(getResources().getString(R.string.title_logMessages), 0, "-", "OK", true);
                } else {
                    showAlert(getResources().getString(R.string.title_logMessages), 0, this.gameboy.getLogger().dumpAll(), "OK", true);
                }
                break;
            case MENU_ABOUT:
                showAlert(getResources().getString(R.string.title_about), 0, getResources().getString(R.string.msg_about), "OK", true);
                break;
            case MENU_HELP:
            {
	            // try to open the online help page in a new browser window
	            try {
	            	final Intent help = new Intent( "android.intent.action.VIEW", Uri.parse( URL_ONLINE_HELP ) );
	            	
		            startActivity( help );
	            } catch( Exception e ) {
	                showAlert(getResources().getString(R.string.title_warning), 0, getResources().getString(R.string.msg_helpNotDisplayed), "OK", true);
	            }
	            break;
            }
            case MENU_EXIT:
                finish();
                break;

            default:
                showAlert(getResources().getString(R.string.title_warning), 0, getResources().getString(R.string.msg_notImplemented), "OK", true);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final String data, final Bundle extras) {
        super.onActivityResult(requestCode, resultCode, data, extras);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case MENU_LOADCARTRIDGE: {
                    try {
                        // show success
                        showTimedAlert(getResources().getString(R.string.title_cartridgeLoaded), getResources().getString(R.string.msg_cartridgeLoaded).replaceFirst("\\#", data), "OK");

                        // save directory we selected from, so that this directory appears initially when attaching the next file
                        final Editor prefsEditor = this.prefs.edit();

                        this.currentlyAttachedFile = new File(data);
                        prefsEditor.putString(SETTING_FILESEARCH_STARTDIR, this.currentlyAttachedFile.getParent());
                        prefsEditor.commit();
                        
                        // check whether we have saved data from a previous game
                        InputStream saveStream = null;
                        final int index = data.lastIndexOf('.');
                        final String save = data.substring(0, index) + SAVED_GAME_FILE_EXTENSION;
                        
                        if(new File(save).exists()) {
                        	saveStream = new BufferedInputStream(new FileInputStream(save));
                        }

                        // load cartridge, start emulation
                        final InputStream romStream = new BufferedInputStream(new FileInputStream(data));

                        gameboy.load(romStream, saveStream);
                        romStream.close();
                        if(saveStream != null) saveStream.close();
                        
                        // start emulation and switch to Gameboy view
                        setSound( this.prefs.getBoolean( SETTING_SOUNDACTIVE, false ) );
    	                activateOrientationSensorNotifier( this.prefs.getBoolean( SETTING_ORIENTATIONSENSORACTIVE, false ) );
                        new Thread(gameboy).start();
                        setContentView(this.gameboyView);
                    } catch (Exception e) {
                        showAlert(getResources().getString(R.string.title_warning), 0, getResources().getString(R.string.msg_cartridgeNotLoaded).replaceFirst("\\#", data) + e, "OK", true);
                    }
                    break;
                }
                case MENU_SETTINGS:
                {
	                final Editor prefsEditor = this.prefs.edit(); 
	                final int frameSkip = extras.getInt( "de.joergjahnke.gameboy.android.frameSkip" );
	                
	                this.gameboy.getVideoChip().setFrameSkip( frameSkip );
	                prefsEditor.putInt( SETTING_FRAMESKIP, frameSkip );
	                
	                final boolean useAntialiasing = extras.getBoolean( "de.joergjahnke.gameboy.android.antialiasing" );
	                
	                this.gameboyView.setUseAntialiasing(useAntialiasing);
	                prefsEditor.putBoolean( SETTING_ANTIALIASING, useAntialiasing );
	                
	                final boolean soundActive = extras.getBoolean( "de.joergjahnke.gameboy.android.soundActive" );
	                
	                prefsEditor.putBoolean( SETTING_SOUNDACTIVE, soundActive );
	                
	                final boolean orientationSensorActive = extras.getBoolean( "de.joergjahnke.gameboy.android.orientationSensorActive" );
	                
	                prefsEditor.putBoolean( SETTING_ORIENTATIONSENSORACTIVE, orientationSensorActive );
	                prefsEditor.commit();
	                break;
                }
            }
        }
    }

    /**
     * Display the dialog for loading a cartridge
     */
    private void showLoadCartridgeDialog() {
        final Intent loadFileIntent = new Intent();

        loadFileIntent.setClass(this, LoadCartridgeDialog.class);
        loadFileIntent.putExtra("de.joergjahnke.c64.android.prgdir", this.prefs.getString(SETTING_FILESEARCH_STARTDIR, "/"));
        startSubActivity(loadFileIntent, MENU_LOADCARTRIDGE);
    }

    /**
     * Display the dialog for editing the application settings
     */
	private void showSettingsDialog() {
		final Intent settingsIntent = new Intent();
		
		settingsIntent.setClass( this, EditSettingsDialog.class );
		settingsIntent.putExtra( "de.joergjahnke.gameboy.android.frameSkip", this.gameboy.getVideoChip().getFrameSkip() );
		settingsIntent.putExtra( "de.joergjahnke.gameboy.android.antialiasing", this.gameboyView.isUseAntialiasing() );
		settingsIntent.putExtra( "de.joergjahnke.gameboy.android.soundActive", this.prefs.getBoolean(SETTING_SOUNDACTIVE, false) );
		settingsIntent.putExtra( "de.joergjahnke.gameboy.android.orientationSensorActive", this.prefs.getBoolean(SETTING_ORIENTATIONSENSORACTIVE, false) );
		startSubActivity( settingsIntent, MENU_SETTINGS );
	}

    /**
     * Activate the main view containing the action buttons
     */
    private void showMainView() {
        setContentView(R.layout.main);

        final ImageButton loadButton = (ImageButton) findViewById(R.id.load);

        loadButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                showLoadCartridgeDialog();
            }
        });

        final ImageButton settingsButton = (ImageButton) findViewById(R.id.settings);

        settingsButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                showSettingsDialog();
            }
        });

        final ImageButton exitButton = (ImageButton) findViewById(R.id.exit);

        exitButton.setOnClickListener(new OnClickListener() {

            public void onClick(View arg0) {
                finish();
            }
        });
    }

    /**
     * Switch the sound on/off
     *
     * @param   active  true to switch the sound on, false to switch it off
     */
    protected void setSound(final boolean active) {
        if (active) {
            if (this.gameboy.getSoundChip().countObservers() == 0) {
                try {
                    this.gameboy.getSoundChip().addObserver(new WavePlayer(this.gameboy.getSoundChip()));
                } catch (Throwable t) {
                    // we could not add a player, that's OK
                    this.gameboy.getLogger().warning("Could not create sound player! Sound output remains deactivated.");
                    t.printStackTrace();
                }
            }
        } else {
            if (this.gameboy.getSoundChip().countObservers() > 0) {
                this.gameboy.getSoundChip().deleteObservers();
            }
        }
    }
	
	/**
	 * We try to attach/detach an orientation sensor notifier
	 * 
	 * @param	active	true to activate, false to deactivate the notifier
	 */
	protected void activateOrientationSensorNotifier(final boolean active) {
		if(active) {
			if(this.orientationSensorNotifier == null) {
				try {
					this.orientationSensorNotifier = new OrientationSensorNotifier();
					this.orientationSensorNotifier.addListener(this.gameboyView);
					this.orientationSensorNotifier.start();
				} catch( Exception e ) {
					// we could not attach the notifier, most probably no orientation sensor is available
				}
			}
		} else {
			if(this.orientationSensorNotifier != null) {
				this.orientationSensorNotifier.stop();
				this.orientationSensorNotifier = null;
			}
		}
	}

    /**
     * Show and automatically dismiss an alert message
     * 
     * @param	title	message title
     * @param	message	message text
     * @param	buttonText	button text of the dialog's button
     * @return	created dialog
     */
    private DialogInterface showTimedAlert(final CharSequence title, final CharSequence message, final CharSequence buttonText) {
        final DialogInterface result = showAlert(title, 0, message, buttonText, false);

        this.handler.postDelayed(new Runnable() {

            public void run() {
                result.dismiss();
            }
        }, 2000);

        return result;
    }

    // implementation of the Observer interface
    @Override
    public void update(Object observed, Object arg) {
        // we have a new performance measurement result?
        if (observed == this.gameboy && arg == Gameboy.SIGNAL_NEW_PERFORMANCE_MEASUREMENT) {
            // then display this with the corresponding color
            final int performance = this.gameboy.getPerformanceMeter().getLastPerformance();
            final int background = performance >= 120
                    ? Color.CYAN
                    : performance >= 90
                    ? Color.GREEN
                    : performance >= 60
                    ? Color.YELLOW
                    : Color.RED;

            switch (background) {
                case Color.CYAN:
                case Color.GREEN:
                    this.statusIconLayers[0].setAlpha(0xff);
                    this.statusIconLayers[1].setAlpha(0);
                    this.statusIconLayers[2].setAlpha(0);
                    break;
                case Color.YELLOW:
                    this.statusIconLayers[0].setAlpha(0);
                    this.statusIconLayers[1].setAlpha(0xff);
                    this.statusIconLayers[2].setAlpha(0);
                    break;
                case Color.RED:
                    this.statusIconLayers[0].setAlpha(0);
                    this.statusIconLayers[1].setAlpha(0);
                    this.statusIconLayers[2].setAlpha(0xff);
                    break;
                default:
                    ;
            }

            this.handler.sendMessage(Message.obtain(this.handler, MSG_REPAINT_STATUS_ICON));
        }
    }
}

