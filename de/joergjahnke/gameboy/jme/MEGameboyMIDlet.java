/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.jme;

import de.joergjahnke.common.jme.ButtonAssignmentCanvas;
import de.joergjahnke.common.jme.CollectionUtils;
import de.joergjahnke.common.jme.FileBrowser;
import de.joergjahnke.common.jme.FileSystemHandler;
import de.joergjahnke.common.jme.FormattedTextForm;
import de.joergjahnke.common.jme.ImageButton;
import de.joergjahnke.common.jme.LocalizationSupport;
import de.joergjahnke.common.jme.ProgressForm;
import de.joergjahnke.common.jme.PCMtoMIDIPlayer;
import de.joergjahnke.common.jme.Settings;
import de.joergjahnke.common.jme.WavePlayer;
import de.joergjahnke.gameboy.core.Cartridge;
import de.joergjahnke.gameboy.core.Gameboy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.io.Connector;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.ChoiceGroup;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.ImageItem;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.List;
import javax.microedition.lcdui.Spacer;
import javax.microedition.lcdui.StringItem;
import javax.microedition.midlet.MIDlet;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;

/**
 * Midlet for the J2ME Gameboy emulator
 *
 * @author  Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class MEGameboyMIDlet extends MIDlet implements CommandListener {

    /**
     * Setting name for screen scaling
     */
    public final static String SETTING_SCALING = "Scaling";
    /**
     * Setting name for frame-skip rate
     */
    public final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * Setting name for switching sound on/off
     */
    public final static String SETTING_SOUNDACTIVE = "SoundActive";
    /**
     * Setting name for the used sound sample rate
     */
    public final static String SETTING_SAMPLERATE = "SampleRate";
    /**
     * Setting name for file search starting directory
     */
    public final static String SETTING_FILESEARCH_STARTDIR = "FileSearchStartDir";
    /**
     * Setting name for switching orientation sensor usage on/off
     */
    public final static String SETTING_ACCELEROMETER = "Accelerometer";
    /**
     * Setting name for switching orientation sensor usage on/off
     */
    public final static String SETTING_AUTO_ROTATE = "AutoRotateScreen";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_LANGUAGE = "Language";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_SHOW_BUTTONS = "ShowButtons";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_SHOW_DPAD = "ShowDPad";
    /**
     * Setting name for UI language
     */
    public final static String SETTING_SNAPSHOTS = "Snapshots";
    /**
     * Prefix for the setting for custom key assignments
     */
    public final static String SETTING_PREFIX_KEYS = "Key_";
    /**
     * Suffix for the record store name for the suspend data
     */
    private final static String SUSPENDDATA_SUFFIX = "_SuspendData";
    /**
     * Suffix for the record store name for the ROM bank data
     */
    private final static String ROMDATA_SUFFIX = "_ROMs";
    /**
     * delimiter for the names inside the string containing the list of snapshots
     */
    private static final char SNAPSHOT_NAME_DELIMITER = '\n';
    /**
     * default frame-skip value
     */
    private static final int DEFAULT_FRAME_SKIP = 5;
    /**
     * list of joypad keys
     */
    private final static String[] JOYPAD_KEYS = {"Up", "Left", "Right", "Down", "A", "B", "Select", "Start"};
    /**
     * supported emulator languages
     */
    private final static String[] SUPPORTED_LOCALES = {"Device default", "de", "en", "es", "pt_BR", "ru", "sv", "zh_CN"};
    /**
     * URL of the project's main web page
     */
    private final static String PROJECT_PAGE_URL = "https://sourceforge.net/projects/javagb/";
    /**
     * Status code when loading an emulator state worked fine
     */
    private final static int STATUS_LOAD_OK = 0;
    /**
     * Status code when loading an emulator state failed
     */
    private final static int STATUS_LOAD_FAILED = 1;
    /**
     * Status code when the given emulator state did not exist
     */
    private final static int STATUS_NOTHING_LOADED = 2;
    /**
     * length of the program name component for a snapshot
     */
    private static final int SNAPSHOT_PROGRAMNAME_LENGTH = 18;
    /**
     * a cartridge image
     */
    private static Image cartridgeImage = null;
    /**
     * a snapshot image
     */
    private static Image snapshotImage = null;
    /**
     * main frame
     */
    private GameboyCanvas gbCanvas = null;
    /**
     * Gameboy instance
     */
    private Gameboy gameboy = null;
    /**
     * the main form to show when no game is running
     */
    private final Form mainForm = new Form("JMEBoy");
    /**
     * handler for loading files from the local file system
     */
    private FileSystemHandler fsHandler;
    /**
     * starting directory when doing a file search
     */
    private String fileSearchStartDir;
    /**
     * programs that are available as selections
     */
    private final Hashtable programs = new Hashtable();
    /**
     * emulator settings
     */
    private Settings settings = null;
    /**
     * currently loaded cartridge
     */
    private String currentImage = null;
    /**
     * points to the previously active diplay
     */
    private Displayable previous = null;
    /**
     * OK command
     */
    private final Command okCommand;
    /**
     * back command, leads back to the main form
     */
    private final Command backCommand;
    /**
     * command to pause a running game
     */
    private final Command pauseCommand;
    /**
     * command to rseume a paused game
     */
    private final Command resumeCommand;
    /**
     * command to start a new game
     */
    private final Command playCommand;
    /**
     * command to search for programs in the local file system
     */
    private final Command searchProgramsCommand;
    /**
     * command to suspend and exit
     */
    private final Command suspendCommand;
    /**
     * command to quit the current game
     */
    private final Command quitPlayCommand;
    /**
     * command to show the settings dialog
     */
    private final Command editSettingsCommand;
    /**
     * command to assign joypad keys
     */
    private final Command assignKeysCommand;
    /**
     * 'show log' command
     */
    private final Command showLogCommand;
    /**
     * about message command
     */
    private final Command aboutCommand;
    /**
     * help command
     */
    private final Command helpCommand;
    /**
     * command to exit the application
     */
    private final Command exitCommand;
    /**
     * Browse command
     */
    private final Command browseCommand;
    /**
     * Snapshots command
     */
    private final Command snapshotCommand;
    /**
     * Remove command
     */
    private final Command removeCommand;

    /**
     * Create new Gameboy MIDlet
     */
    public MEGameboyMIDlet() {
        // create Settings instance to load and store emulator settings
        try {
            this.settings = new Settings(getAppProperty("MIDlet-Name"));
        } catch (Exception e) {
            // we have to work without loading and storing settings, that's OK
        }

        // initialize L10N support
        final String locale = getLocale();

        LocalizationSupport.initLocalizationSupport(locale, LocalizationSupport.COMMON_MESSAGES);
        LocalizationSupport.initLocalizationSupport(locale, "/res/l10n/gameboyEmulatorMessages.properties");

        // initialize commands
        this.okCommand = new Command(LocalizationSupport.getMessage("OK"), Command.OK, 1);
        this.backCommand = new Command(LocalizationSupport.getMessage("Back"), Command.BACK, 99);
        this.pauseCommand = new Command(LocalizationSupport.getMessage("Pause"), LocalizationSupport.getMessage("Pause"), Command.ITEM, 2);
        this.resumeCommand = new Command(LocalizationSupport.getMessage("Resume"), LocalizationSupport.getMessage("Resume"), Command.ITEM, 3);
        this.playCommand = new Command(LocalizationSupport.getMessage("Play"), LocalizationSupport.getMessage("PlayGame"), Command.ITEM, 4);
        this.searchProgramsCommand = new Command(LocalizationSupport.getMessage("SearchPrograms"), LocalizationSupport.getMessage("SearchProgramsInFileSystem"), Command.ITEM, 6);
        this.suspendCommand = new Command(LocalizationSupport.getMessage("Suspend"), LocalizationSupport.getMessage("SuspendAndExit"), Command.EXIT, 98);
        this.quitPlayCommand = new Command(LocalizationSupport.getMessage("Quit"), LocalizationSupport.getMessage("QuitCurrentGame"), Command.EXIT, 99);
        this.editSettingsCommand = new Command(LocalizationSupport.getMessage("Settings"), LocalizationSupport.getMessage("EditSettings"), Command.ITEM, 9);
        this.assignKeysCommand = new Command(LocalizationSupport.getMessage("Assign"), LocalizationSupport.getMessage("AssignKeys"), Command.ITEM, 10);
        this.showLogCommand = new Command(LocalizationSupport.getMessage("ShowLog"), Command.ITEM, 11);
        this.aboutCommand = new Command(LocalizationSupport.getMessage("About"), Command.HELP, 12);
        this.helpCommand = new Command(LocalizationSupport.getMessage("Help"), Command.HELP, 13);
        this.exitCommand = new Command(LocalizationSupport.getMessage("Exit"), Command.EXIT, 99);
        this.browseCommand = new Command(LocalizationSupport.getMessage("Browse"), Command.ITEM, 2);
        this.snapshotCommand = new Command(LocalizationSupport.getMessage("SaveSnapshot"), Command.ITEM, 4);
        this.removeCommand = new Command(LocalizationSupport.getMessage("Remove"), Command.ITEM, 2);

        // create form that we show when no game is running
        final Display display = Display.getDisplay(this);

        try {
            final ImageButton playImageButton = new ImageButton("/res/drawable/play.png") {

                public void onButtonPressed() {
                    showSelectGameForm();
                }
            };

            playImageButton.setLayout(ImageItem.LAYOUT_CENTER | Item.BUTTON);

            final ImageButton settingsImageButton = new ImageButton("/res/drawable/settings.png") {

                public void onButtonPressed() {
                    showSettingsForm();
                }
            };

            settingsImageButton.setLayout(ImageItem.LAYOUT_CENTER | Item.BUTTON);

            final ImageButton exitButton = new ImageButton("/res/drawable/exit.png") {

                public void onButtonPressed() {
                    exit();
                }
            };

            exitButton.setLayout(ImageItem.LAYOUT_CENTER | Item.BUTTON);

            this.mainForm.append(playImageButton);
            this.mainForm.append(settingsImageButton);
            this.mainForm.append(exitButton);
        } catch (Exception e) {
            // we couldn't create the form with the image buttons, so we have to go with the menu alone
        }
        display.setCurrent(this.mainForm);

        // create menu
        this.mainForm.addCommand(playCommand);
        if (supportsFileConnectionAPI()) {
            this.mainForm.addCommand(searchProgramsCommand);
        }
        this.mainForm.addCommand(editSettingsCommand);
        this.mainForm.addCommand(assignKeysCommand);
        this.mainForm.addCommand(aboutCommand);
        this.mainForm.addCommand(helpCommand);
        this.mainForm.addCommand(showLogCommand);
        this.mainForm.addCommand(exitCommand);
        this.mainForm.setCommandListener(this);

        // create Gameboy and a canvas to display its content
        try {
            // create the Gameboy
            this.gameboy = new Gameboy();

            // initialize the display
            this.gbCanvas = new GameboyCanvas(this);
            this.gbCanvas.setGameboy(gameboy);
            this.gbCanvas.addCommand(pauseCommand);
            this.gbCanvas.addCommand(showLogCommand);
            this.gbCanvas.addCommand(quitPlayCommand);
            this.gbCanvas.addCommand(suspendCommand);
            this.gbCanvas.addCommand(snapshotCommand);
            this.gbCanvas.setCommandListener(this);
            this.gbCanvas.calculateScreenSize();

            // register the display as observer for the Gameboy's video chip
            this.gameboy.getVideoChip().addObserver(this.gbCanvas);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException(LocalizationSupport.getMessage("CouldNotInitialize") + t);
        }

        // apply some settings
        try {
            this.gameboy.getVideoChip().setFrameSkip(this.settings.getInteger(SETTING_FRAMESKIP, DEFAULT_FRAME_SKIP));
            this.gbCanvas.setShowButtons(this.settings.getBoolean(SETTING_SHOW_BUTTONS, this.gbCanvas.isShowButtons()));
            this.gbCanvas.setShowDirectionButtons(this.settings.getBoolean(SETTING_SHOW_DPAD, this.gbCanvas.isShowDirectionButtons()));
            this.gbCanvas.setAutoChangeOrientation(this.settings.getBoolean(SETTING_AUTO_ROTATE, this.gbCanvas.isAutoChangeOrientation()));
            this.gbCanvas.setUseAccelerometer(this.settings.getBoolean(SETTING_ACCELEROMETER, this.gbCanvas.isUseAccelerometer()));
            if (this.settings.exists(SETTING_PREFIX_KEYS + JOYPAD_KEYS[0])) {
                final Hashtable assignments = new Hashtable();

                for (int i = 0; i < JOYPAD_KEYS.length; ++i) {
                    assignments.put(new Integer(this.settings.getInteger(SETTING_PREFIX_KEYS + JOYPAD_KEYS[i])), JOYPAD_KEYS[i]);
                }
                this.gbCanvas.setButtonAssignments(assignments);
            }
        } catch (Exception e) {
            // we could not apply the settings and work with defaults
        }

        // determine available Gameboy programs
        this.programs.clear();
        try {
            CollectionUtils.putAll(this.programs, readProgramListFromTextFile());
        } catch (Exception e) {
            // we could not read the program list, this is no harm, we just don't have programs from the jar file available
        }
        try {
            this.fsHandler = new FileSystemHandler(this.gameboy.getLogger(), Cartridge.SUPPORTED_EXTENSIONS, this.settings);
            CollectionUtils.putAll(this.programs, this.fsHandler.getCachedProgramList());
        } catch (Exception e) {
            // we could not create the file system handler, that's OK
        }

        // try to load the cartridge image
        if (null == cartridgeImage) {
            try {
                cartridgeImage = Image.createImage("/res/drawable/cartridge.png");
            } catch (Exception e) {
                // we can work without the image
            }
        }
        if (null == snapshotImage) {
            try {
                snapshotImage = Image.createImage("/res/drawable/snapshot.png");
            } catch (Exception e) {
                // we can work without the image
            }
        }

        // we try to resume a previous session
        resume();
    }

    /**
     * Get the program settings
     * 
     * @return  settings instance, null if no settings instance could be created
     */
    public Settings getSettings() {
        return this.settings;
    }

    /**
     * Get the locale for the emulator.
     * The default locale is taken from the system property "microedition.locale"
     * but can be overriden via the settings.
     * 
     * @return  locale string e.g. "sv" or "de"
     */
    private String getLocale() {
        // initialize L10N support
        String locale = System.getProperty("microedition.locale");

        try {
            locale = this.settings.getString(SETTING_LANGUAGE, locale);
        } catch (Exception e) {
            // we could not determine the locale setting and will use the system default
        }

        return locale;
    }

    /**
     * Create the canvas displaying the C64 and the C64 instance and show the former
     */
    public void startApp() {
        this.gameboy.resume();
    }

    /**
     * Pause the C64 if the application is inactive
     */
    public void pauseApp() {
        this.gameboy.pause();
        notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_PAUSE);
    }

    /**
     * Destroy must cleanup everything not handled by the garbage collector.
     * In this case there is nothing to cleanup.
     */
    public void destroyApp(boolean unconditional) {
        quitPlay();
    }

    /**
     * Send a signal to the sound player to stop it from working
     *
     * @param signal    signal to send
     */
    private void notifySoundPlayer(final Object signal) {
        if (null != this.gameboy.getSoundChip() && this.gameboy.getSoundChip().countObservers() > 0) {
            this.gameboy.getSoundChip().setChanged(true);
            this.gameboy.getSoundChip().notifyObservers(signal);
        }
    }

    // implementation of the CommandListener interface
    /**
     * Respond to commands, including exit
     */
    public void commandAction(final Command c, final Displayable s) {
        // an OK or Back command has been entered?
        if (c == okCommand || c == backCommand) {
            Display.getDisplay(this).setCurrent(this.previous);
            this.previous = null;
        // we want to play a new game?
        } else if (c == playCommand) {
            showSelectGameForm();
        // we want to pause a running game?
        } else if (c == pauseCommand) {
            this.gameboy.pause();
            notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_PAUSE);
            this.gbCanvas.addCommand(resumeCommand);
            this.gbCanvas.removeCommand(pauseCommand);
        // we want to resume a paused game?
        } else if (c == resumeCommand) {
            this.gameboy.resume();
            this.gbCanvas.addCommand(pauseCommand);
            this.gbCanvas.removeCommand(resumeCommand);
        } else if (c == quitPlayCommand) {
            quitPlay();
            Display.getDisplay(this).setCurrent(this.mainForm);
        // we want to show the about dialog?
        } else if (c == aboutCommand) {
            showAboutForm();
        // we want to display the program help?
        } else if (c == helpCommand) {
            showHelpForm();
        // we want to display the log?
        } else if (c == showLogCommand) {
            showLogForm();
        // we want to edit the emulator settings?
        } else if (c == editSettingsCommand) {
            showSettingsForm();
        // we want to search the local file system for Gameboy images?
        } else if (c == searchProgramsCommand) {
            CollectionUtils.removeAll(this.programs, this.fsHandler.getCachedProgramList());
            showSelectDirectoryForm();
        // we want to exit the emulator?
        } else if (c == exitCommand) {
            exit();
        // we want to suspend the current state and exit?
        } else if (c == suspendCommand) {
            suspend();
        } else if (c == assignKeysCommand) {
            showAssignButtonsCanvas();
        } else if (c == snapshotCommand) {
            saveSnapshot();
        }
    }

    /**
     * Retrieve the list of saved snapshots from the settings.
     * This method assumes that all elements, even the last in the list, are followed by the snapshot string delimiter.
     *
     * @return  list of snapshot names
     */
    private Vector getSnapshots() {
        final Vector result = new Vector();
        final String snapshots = getSettings().getString(SETTING_SNAPSHOTS, null);

        if (null != snapshots) {
            for (int index = 0, newIndex; index < snapshots.length(); index = newIndex + 1) {
                newIndex = snapshots.indexOf(SNAPSHOT_NAME_DELIMITER, index);
                result.addElement(snapshots.substring(index, newIndex));
            }
        }

        return result;
    }

    /**
     * Get all snapshots associated with a given program
     *
     * @param program   program name
     * @param snapshotList  list containing all saved snapshots
     * @return  list of snapshots associated with the given program
     */
    private Vector getSnapshots(final String program, final Vector snapshotList) {
        final Vector result = new Vector();
        final String search = program.substring(0, Math.min(program.length(), SNAPSHOT_PROGRAMNAME_LENGTH));

        for (int i = 0, to = snapshotList == null ? 0 : snapshotList.size(); i < to; ++i) {
            final String name = snapshotList.elementAt(i).toString();

            if (name.startsWith(search)) {
                result.addElement(name);
            }
        }

        return result;
    }

    /**
     * Save a given list of snapshots in the settings.
     * This method stores the list of names in one string, adding a delimiter after each string, even the last one.
     *
     * @param snapshotList  list of snapshot names
     * @throws RecordStoreException if saving in the settings fails
     */
    private void setSnapshots(final Vector snapshotList) throws RecordStoreException {
        if (null != snapshotList) {
            final StringBuffer snapshots = new StringBuffer();

            for (int i = 0, to = snapshotList.size(); i < to; ++i) {
                snapshots.append(snapshotList.elementAt(i));
                snapshots.append(SNAPSHOT_NAME_DELIMITER);
            }

            getSettings().setString(SETTING_SNAPSHOTS, snapshots.toString());
        } else {
            getSettings().remove(SETTING_SNAPSHOTS);
        }
    }

    /**
     * Save the current emulator state in a record store with the given name
     *
     * @param name  name of the record store to use
     * @throws IOException  if the snapshot of the current state cannot be created
     * @throws RecordStoreException if the current state can't be saved inside the record store
     */
    private void saveState(final String name) throws IOException, RecordStoreException {
        // create suspend "file"
        final RecordStore rs = RecordStore.openRecordStore(name, true);
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(os);
        // save the list of attached images
        out.writeUTF(this.currentImage);
        // save current emulator state
        this.gameboy.serialize(out);
        out.flush();
        rs.addRecord(os.toByteArray(), 0, os.size());
        out.close();
        os.close();
        rs.closeRecordStore();
    }

    /**
     * Save a snapshot of the current state
     */
    private void saveSnapshot() {
        this.gameboy.pause();

        // generate a name for the snapshot
        final Calendar cal = Calendar.getInstance();

        cal.setTime(new java.util.Date());

        final String name = this.currentImage.substring(0, Math.min(this.currentImage.length(), SNAPSHOT_PROGRAMNAME_LENGTH)) + "@" + formatForSnapshot(cal.get(Calendar.YEAR)) + formatForSnapshot(cal.get(Calendar.MONTH) + 1) + formatForSnapshot(cal.get(Calendar.DAY_OF_MONTH)) + "-" + formatForSnapshot(cal.get(Calendar.HOUR_OF_DAY)) + formatForSnapshot(cal.get(Calendar.MINUTE)) + formatForSnapshot(cal.get(Calendar.SECOND));

        try {
            // save the current state under that name
            saveState(name);

            // add the name to the list of snapshots and save this in the settings
            final Vector snapshots = getSnapshots();

            snapshots.addElement(name);
            setSnapshots(snapshots);
        } catch (Throwable t) {
            try {
                RecordStore.deleteRecordStore(name);
            } catch (Throwable t2) {
                // the snapshot probably was not saved at all, so we ignore this
            }
            // show the cause of the error
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("SavingSnapshotFailed"), LocalizationSupport.getMessage("FailedToStoreState"), null, AlertType.WARNING));
            t.printStackTrace();
        }

        this.gameboy.resume();
    }

    /**
     * Return an integer as two-digit string with training zero if necessary
     *
     * @param n number to format
     * @return  formatted string
     */
    private String formatForSnapshot(final int n) {
        final String s = "0" + n;

        return s.substring(s.length() - 2);
    }

    /**
     * Save the emulator state and exit
     */
    private void suspend() {
        this.gameboy.pause();

        boolean reallyExit = true;

        try {
            saveState(getAppProperty("MIDlet-Name") + SUSPENDDATA_SUFFIX);
        } catch (Throwable t) {
            // show the cause of the error
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("SuspendFailed"), LocalizationSupport.getMessage("FailedToStoreState"), null, AlertType.WARNING));
            t.printStackTrace();
            // we don't exit, the user might want to continue now that the suspend failed
            reallyExit = false;
            this.gameboy.resume();
        }

        if (reallyExit) {
            exit();
        }
    }

    /**
     * Load an emulator state from a record store with a given name.
     * A progress bar will be displayed while loading.
     *
     * @param name  name of the record store to use
     * @return  status of the load operation
     */
    private int loadState(final String name) {
        int status = STATUS_LOAD_OK;
        final Display display = Display.getDisplay(this);

        // create a screen that displays a progress bar
        final ProgressForm progressForm = new ProgressForm(LocalizationSupport.getMessage("Resuming"));

        this.gameboy.getCartridge().deleteObservers();
        this.gameboy.getCartridge().addObserver(progressForm);

        boolean hasSuspendData = false;
        RecordStore rs = null;

        try {
            // open the suspend "file"
            rs = RecordStore.openRecordStore(name, false);

            byte[] bytes = rs.getRecord(1);

            hasSuspendData = true;

            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));

            bytes = null;
            // display the prepared progress form
            display.setCurrent(progressForm);

            // reload the attached image
            final String program = in.readUTF();

            loadGame(program);

            // load saved emulator state
            this.gameboy.deserialize(in);

            in.close();
            rs.closeRecordStore();
        } catch (Throwable t) {
            if (hasSuspendData) {
                status = STATUS_LOAD_FAILED;
                t.printStackTrace();
            } else {
                status = STATUS_NOTHING_LOADED;
            }
        } finally {
            if (hasSuspendData) {
                try {
                    rs.closeRecordStore();
                } catch (Exception e) {
                    // we could not close the record store, that hopefully does not hinder other actions on it and it was closed anyway...
                }
            }
            display.setCurrent(this.mainForm);
        }

        return status;
    }

    /**
     * Load and start a given emulator snapshot
     *
     * @param   name    name of the snapshot to load
     */
    private void runSnapshot(final String name) {
        final Display display = Display.getDisplay(this);
        final Thread loader = new Thread() {

            public void run() {
                switch (loadState(name)) {
                    case STATUS_LOAD_FAILED:
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("LoadSnapshotFailed"), LocalizationSupport.getMessage("FailedToRestoreState"), null, AlertType.WARNING), gbCanvas);
                        break;
                    case STATUS_LOAD_OK:
                        startGame();
                }
            }
        };
        loader.start();
    }

    /**
     * Load suspended emulator state.
     * The emulator needs to be in the paused state for this method to work correctly.
     */
    private void resume() {
        final Display display = Display.getDisplay(this);
        final String name = getAppProperty("MIDlet-Name") + SUSPENDDATA_SUFFIX;
        final Thread loader = new Thread() {

            public void run() {
                // try to load a saved state
                final int status = loadState(name);

                // first remove the suspend file, so that we don't load this old state again
                if (status == STATUS_LOAD_FAILED || status == STATUS_LOAD_OK) {
                    try {
                        RecordStore.deleteRecordStore(name);
                    } catch (Exception e) {
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("CouldNotRemoveSuspendData"), LocalizationSupport.getMessage("FailedToRemoveSuspendData"), null, AlertType.WARNING));
                        e.printStackTrace();
                    }
                }

                // react according to the outcome of the attempt at loading an old state
                switch (status) {
                    case STATUS_LOAD_FAILED:
                        // show a message that resuming the game failed
                        display.setCurrent(new Alert(LocalizationSupport.getMessage("ResumeFailed"), LocalizationSupport.getMessage("FailedToRestoreState"), null, AlertType.WARNING), gbCanvas);
                        break;
                    case STATUS_LOAD_OK:
                        // start the game
                        startGame();
                        break;
                    default:
                        // nothing loaded, this happens when no hibernation file was written
                        ;
                }
            }
        };
        loader.start();
    }

    /**
     * Load a ROM file
     *
     * @param program   game to load
     * @throws java.io.IOException if the game cannot be loaded
     */
    private synchronized void loadGame(final String program) throws IOException {
        // it might happen on some phones that this method is entered twice, so we try to detect this and then return
        if (program.equals(this.currentImage)) {
            return;
        }

        // determine image file
        final String image = this.programs.get(program).toString();

        // load the cartridge
        final InputStream romStream = image.indexOf("://") > 0 ? Connector.openInputStream(image) : getClass().getResourceAsStream(image);

        this.gameboy.load(romStream);
        romStream.close();

        // check whether we have saved data from a previous game
        final String save = getSettings().getString(program, null);

        if (save != null) {
            final InputStream saveStream = new ByteArrayInputStream(save.getBytes());

            this.gameboy.getCartridge().loadData(saveStream);
            saveStream.close();
        }

        // refresh sound setting
        setSound(getSettings().getBoolean(SETTING_SOUNDACTIVE, false));

        this.currentImage = program;
    }

    /**
     * Load and start a given ROM file
     *
     * @param name  filename of the ROM to load
     */
    private void runGame(final String name) {
        // create a screen that displays a progress bar
        final Display display = Display.getDisplay(this);
        final ProgressForm progressForm = new ProgressForm(LocalizationSupport.getMessage("Loading"));

        this.gameboy.getCartridge().deleteObservers();
        this.gameboy.getCartridge().addObserver(progressForm);

        display.setCurrent(progressForm);

        final Thread loader = new Thread() {

            public void run() {
                try {
                    // load game and start the emulation
                    loadGame(name);
                    gameboy.getCPU().powerUp();
                    startGame();
                } catch (Throwable t) {
                    gameboy.stop();
                    display.setCurrent(new Alert(LocalizationSupport.getMessage("FailedToLoadImage"), LocalizationSupport.getMessage("FailedToLoadImage2") + name, null, AlertType.WARNING), previous);
                    t.printStackTrace();
                }
            }
        };
        loader.start();
    }

    /**
     * Exit without saving
     */
    private void exit() {
        this.gameboy.stop();
        destroyApp(false);
        notifyDestroyed();
    }

    /**
     * Stop and save, if necessary, a running game
     */
    private void quitPlay() {
        // stop the Gameboy
        this.gameboy.stop();
        // stop the MIDI sound player, if one is active
        notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_STOP);
        // we have to save the cartridge RAM?
        if (null != this.gameboy.getCartridge() && this.gameboy.getCartridge().hasBatterySupport()) {
            // save the game
            try {
                // save cartridge data in the settings
                final ByteArrayOutputStream saveStream = new ByteArrayOutputStream();

                this.gameboy.getCartridge().saveData(saveStream);
                this.settings.setString(this.currentImage, saveStream.toString());
            } catch (Exception e) {
                // we could not save the file
                Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("FailedToSaveGame"), LocalizationSupport.getMessage("FailedToSaveGameDataFor") + this.currentImage, null, AlertType.WARNING));
            }
        }
        // no image is loaded
        this.currentImage = null;
    }

    /**
     * Show a dialog that lets the user assign the device's buttons to joypad buttons
     */
    private void showAssignButtonsCanvas() {
        final Display display = Display.getDisplay(this);
        final Vector buttons = new Vector();

        for (int i = 0; i < JOYPAD_KEYS.length; ++i) {
            buttons.addElement(JOYPAD_KEYS[i]);
        }

        final ButtonAssignmentCanvas buttonAssignmentCanvas = new ButtonAssignmentCanvas(display, buttons) {

            /**
             * Assign the buttons and save them to settings when finishing the dialog
             */
            public void onFinished() {
                super.onFinished();
                if (getState() == Command.OK) {
                    gbCanvas.setButtonAssignments(getAssignments());
                    try {
                        for (int i = 0; i < buttons.size(); ++i) {
                            try {
                                settings.remove(SETTING_PREFIX_KEYS + buttons.elementAt(i).toString());
                            } catch (IllegalArgumentException e) {
                                // this happens if the key was not assigned, no problem
                            }
                        }
                        for (final Enumeration en = getAssignments().keys(); en.hasMoreElements();) {
                            final Integer key = (Integer) en.nextElement();

                            settings.setInteger(SETTING_PREFIX_KEYS + getAssignments().get(key).toString(), key.intValue());
                        }
                    } catch (Exception e) {
                        // we could not save the key settings, that's OK
                    }
                }
            }
        };

        display.setCurrent(buttonAssignmentCanvas);
    }

    /**
     * Select and start a new game
     */
    private void showSelectGameForm() {
        // get list of cached programs or search the local filesystem if that list is empty
        if (this.programs.isEmpty()) {
            this.fsHandler.readProgramListFromFileSystem(this.fileSearchStartDir, this.programs, Display.getDisplay(this));
        }

        // show programs for selection
        final Display display = Display.getDisplay(this);
        final List imageList = new List(LocalizationSupport.getMessage("SelectCartridge"), List.IMPLICIT);

        imageList.addCommand(okCommand);
        imageList.addCommand(removeCommand);
        imageList.addCommand(backCommand);
        imageList.setSelectCommand(okCommand);

        // sort program names
        final Vector listItems = new Vector();

        for (final Enumeration en = this.programs.keys(); en.hasMoreElements();) {
            final String program = en.nextElement().toString();
            int p = 0;
            final int n = listItems.size();

            while (p < n && program.compareTo(listItems.elementAt(p).toString()) > 0) {
                ++p;
            }
            listItems.insertElementAt(program, p);
        }

        // add program names plus associated snapshots to listbox
        final Vector snapshots = getSnapshots();

        for (int i = 0; i < listItems.size(); ++i) {
            final String name = listItems.elementAt(i).toString();

            imageList.append(name, cartridgeImage);

            final Vector programSnapshots = getSnapshots(name, snapshots);

            for (int j = 0, to = programSnapshots.size(); j < to; ++j) {
                imageList.append(programSnapshots.elementAt(j).toString(), snapshotImage);
            }
        }

        imageList.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        display.setCurrent(mainForm);

                        // retrieve name of the selected program or snapshot
                        final String name = imageList.getString(imageList.getSelectedIndex());

                        // the entry was selected for running it?
                        if (c == okCommand) {
                            // load game or snapshot and start the emulation
                            if (programs.containsKey(name)) {
                                runGame(name);
                            } else {
                                runSnapshot(name);
                            }
                        // the entry was selected for removal?
                        } else if (c == removeCommand) {
                            // a program was selected?
                            if (programs.containsKey(name)) {
                                // this is not allowed
                                display.setCurrent(new Alert(LocalizationSupport.getMessage("NotASnapshot"), LocalizationSupport.getMessage("OnlySnapshotsCanBeRemoved"), null, AlertType.INFO), previous);
                            } else {
                                try {
                                    // remove the snapshot from the device...
                                    RecordStore.deleteRecordStore(name);

                                    // ...and also from the list of snapshots
                                    final Vector snapshots = getSnapshots();

                                    snapshots.removeElement(name);
                                    setSnapshots(snapshots);

                                    // inform the user about the successful removal
                                    display.setCurrent(new Alert(LocalizationSupport.getMessage("RemovedSnapshotData"), LocalizationSupport.getMessage("RemovedSnapshotDataFor") + " " + name, null, AlertType.INFO), previous);
                                } catch (Exception e) {
                                    display.setCurrent(new Alert(LocalizationSupport.getMessage("CouldNotRemoveSnapshotData"), LocalizationSupport.getMessage("FailedToRemoveSnapshotDataFor") + " " + name, null, AlertType.WARNING), previous);
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                });

        // show listbox
        this.previous = display.getCurrent();
        display.setCurrent(imageList);
    }

    /**
     * Start the currently loaded game
     */
    private void startGame() {
        if (!this.gameboy.isRunning()) {
            new Thread(this.gameboy).start();
            this.gameboy.getJoypad().deleteObserver(this.gbCanvas);
            this.gameboy.getJoypad().addObserver(this.gbCanvas);
            Display.getDisplay(this).setCurrent(this.gbCanvas);
        }
    }

    /**
     * Show an about message form
     */
    private void showAboutForm() {
        final Form about = new Form(LocalizationSupport.getMessage("About"));
        // create a StringItem that calls the project url in a browser when clicked
        final StringItem projectUrl = new StringItem(null, PROJECT_PAGE_URL, Item.HYPERLINK);

        projectUrl.addCommand(this.browseCommand);
        projectUrl.setItemCommandListener(new ItemCommandListener() {

            public void commandAction(final Command c, final Item item) {
                try {
                    platformRequest(((StringItem) item).getText());
                } catch (Exception e) {
                    // we could not invoke the browser, that's a pity but we can live with it
                }
            }
        });

        // get the About box text
        String text = LocalizationSupport.getMessage("AboutText1") + getAppProperty("MIDlet-Version") + LocalizationSupport.getMessage("AboutText2") + LocalizationSupport.getMessage("AboutText3");
        // replace the project page place holder with the project url
        final String pageStr = "#PROJECTPAGE#";
        final int index1 = text.indexOf(pageStr);

        about.append(text.substring(0, index1));
        about.append(projectUrl);
        about.append(text.substring(index1 + pageStr.length(), text.length()));
        about.addCommand(backCommand);
        about.setCommandListener(this);

        // display the created form
        final Display display = Display.getDisplay(this);

        this.previous = display.getCurrent();
        display.setCurrent(about);
    }

    /**
     * Show help content form
     */
    private void showHelpForm() {
        final InputStream helpContent = LocalizationSupport.loadLocalizedFile("/docs/help.txt", getLocale());

        try {
            final Form helpForm = new FormattedTextForm(this, LocalizationSupport.getMessage("Help"), helpContent);

            helpForm.addCommand(backCommand);
            helpForm.setCommandListener(this);

            final Display display = Display.getDisplay(this);

            this.previous = display.getCurrent();
            display.setCurrent(helpForm);
        } catch (Exception e) {
            Display.getDisplay(this).setCurrent(new Alert(LocalizationSupport.getMessage("FailedToLoadHelp"), LocalizationSupport.getMessage("FailedToLoadHelpFile"), null, AlertType.WARNING));
            e.printStackTrace();
        }
    }

    /**
     * Show contents of the log
     */
    private void showLogForm() {
        final Form log = new Form(LocalizationSupport.getMessage("LogMessages"));
        final StringItem logItem = new StringItem("", (this.gameboy.getLogger()).dumpAll());

        logItem.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL));
        log.append(logItem);

        log.addCommand(backCommand);
        log.setCommandListener(this);

        final Display display = Display.getDisplay(this);

        this.previous = display.getCurrent();
        display.setCurrent(log);
    }

    /**
     * Show the possible selections for the file system search start directory
     */
    private void showSelectDirectoryForm() {
        final Display display = Display.getDisplay(this);
        final String defaultDir = this.settings.getString(SETTING_FILESEARCH_STARTDIR, "");
        final Vector filters = new Vector();

        filters.addElement("/");

        final FileBrowser fileBrowser = new FileBrowser(display, defaultDir, filters) {

            /**
             * We start the file search when a directory was selected
             */
            public void onSelect() {
                super.onSelect();
                // save the new default directory
                fileSearchStartDir = getSelectedFile();
                try {
                    settings.setString(SETTING_FILESEARCH_STARTDIR, fileSearchStartDir);
                } catch (Exception e) {
                    // we couldn't save the settings, that's OK
                }
                // search for files in this directory and return to the main form
                fsHandler.readProgramListFromFileSystem(fileSearchStartDir, programs, display);
                display.setCurrent(mainForm);
            }

            /**
             * We remove the previous start directory, so that the root directory is used on the next attempt
             */
            public void onError(final Throwable t) {
                super.onError(t);
                try {
                    settings.remove(SETTING_FILESEARCH_STARTDIR);
                } catch (Exception e) {
                    // we couldn't save the settings, that's OK
                }
            }
        };

        fileBrowser.show();
    }

    /**
     * Show a form with the emulator settings
     */
    private void showSettingsForm() {
        final Display display = Display.getDisplay(this);
        final Form settingsForm = new Form(LocalizationSupport.getMessage("EmulatorSettings"));
        // show sound settings
        final String[] sound = {LocalizationSupport.getMessage("On"), LocalizationSupport.getMessage("Off")};
        final ChoiceGroup soundChoice = new ChoiceGroup(LocalizationSupport.getMessage("Sound"), ChoiceGroup.EXCLUSIVE, sound, null);

        soundChoice.setSelectedIndex(this.settings.getBoolean(SETTING_SOUNDACTIVE, false) ? 0 : 1, true);
        settingsForm.append(soundChoice);

        // show screen settings
        settingsForm.append(new Spacer(0, 2));

        final String[] scalingOptions = {LocalizationSupport.getMessage("FullScreen"), LocalizationSupport.getMessage("NoScaling")};
        final ChoiceGroup scalingChoice = new ChoiceGroup(LocalizationSupport.getMessage("VideoScaling"), ChoiceGroup.EXCLUSIVE, scalingOptions, null);

        scalingChoice.setSelectedIndex(this.settings.getBoolean(SETTING_SCALING, true) ? 0 : 1, true);
        settingsForm.append(scalingChoice);

        settingsForm.append(new Spacer(0, 2));

        final String[] frameSkipOptions = {"1", "2", "3", "4", "5"};
        final ChoiceGroup frameSkipChoice = new ChoiceGroup(LocalizationSupport.getMessage("ShowEveryNthFrame"), ChoiceGroup.EXCLUSIVE, frameSkipOptions, null);

        frameSkipChoice.setSelectedIndex(this.gameboy.getVideoChip().getFrameSkip() - 1, true);
        settingsForm.append(frameSkipChoice);

        final String[] showButtonsOptions = {LocalizationSupport.getMessage("On"), LocalizationSupport.getMessage("Off")};
        final ChoiceGroup showButtonsChoice = new ChoiceGroup(LocalizationSupport.getMessage("ShowButtons"), ChoiceGroup.EXCLUSIVE, showButtonsOptions, null);

        final String[] showDPadOptions = {LocalizationSupport.getMessage("On"), LocalizationSupport.getMessage("Off")};
        final ChoiceGroup showDPadChoice = new ChoiceGroup(LocalizationSupport.getMessage("ShowDPad"), ChoiceGroup.EXCLUSIVE, showDPadOptions, null);

        if (this.gbCanvas.hasPointerEvents()) {
            showButtonsChoice.setSelectedIndex(this.settings.getBoolean(SETTING_SHOW_BUTTONS, true) ? 0 : 1, true);
            settingsForm.append(showButtonsChoice);


            showDPadChoice.setSelectedIndex(this.settings.getBoolean(SETTING_SHOW_DPAD, true) ? 0 : 1, true);
            settingsForm.append(showDPadChoice);

            settingsForm.append(new Spacer(0, 2));
        }

        // show miscellaneous settings
        final ChoiceGroup languageChoice = new ChoiceGroup(LocalizationSupport.getMessage("Language"), ChoiceGroup.EXCLUSIVE, SUPPORTED_LOCALES, null);
        final String activeLanguage = this.settings.getString(SETTING_LANGUAGE, SUPPORTED_LOCALES[0]);
        int activeLanguageIndex = 0;

        for (int i = 0; i < SUPPORTED_LOCALES.length; ++i) {
            if (activeLanguage.equals(SUPPORTED_LOCALES[i])) {
                activeLanguageIndex = i;
                break;
            }
        }
        languageChoice.setSelectedIndex(activeLanguageIndex, true);
        settingsForm.append(new Spacer(0, 2));

        settingsForm.append(languageChoice);

        final String[] accelerometerOptions = {LocalizationSupport.getMessage("ForJoystickEmulation"), LocalizationSupport.getMessage("AutoRotateScreen")};
        final ChoiceGroup accelerometerChoice = new ChoiceGroup(LocalizationSupport.getMessage("UseAccelerometer"), ChoiceGroup.MULTIPLE, accelerometerOptions, null);
        final boolean isUseAccelerometer = this.gbCanvas.isUseAccelerometer();
        final boolean isRotateScreen = this.gbCanvas.isAutoChangeOrientation();

        accelerometerChoice.setSelectedIndex(0, isUseAccelerometer);
        accelerometerChoice.setSelectedIndex(1, isRotateScreen);
        try {
            if (de.joergjahnke.common.jme.OrientationSensitiveCanvasHelper.supportsAccelerometer()) {
                settingsForm.append(new Spacer(0, 2));

                settingsForm.append(accelerometerChoice);
            }
        } catch (Throwable t) {
            // this might happen if the Sensors API is not supported, but that's OK because then we don't need to display the accelerometer options
        }

        // add OK and Cancel button
        settingsForm.addCommand(okCommand);
        settingsForm.addCommand(backCommand);
        settingsForm.setCommandListener(
                new CommandListener() {

                    public void commandAction(Command c, Displayable d) {
                        boolean isRestartRequired = false;

                        if (c == okCommand) {
                            // apply video settings
                            gameboy.getVideoChip().setFrameSkip(frameSkipChoice.getSelectedIndex() + 1);
                            gbCanvas.setShowButtons(showButtonsChoice.getSelectedIndex() == 0);
                            gbCanvas.setShowDirectionButtons(showDPadChoice.getSelectedIndex() == 0);
                            gbCanvas.calculateScreenSize();
                            // apply misc settings
                            gbCanvas.setUseAccelerometer(accelerometerChoice.isSelected(0));
                            gbCanvas.setAutoChangeOrientation(accelerometerChoice.isSelected(1));

                            // save settings
                            try {
                                settings.setBoolean(SETTING_SOUNDACTIVE, soundChoice.getSelectedIndex() == 0);
                                settings.setInteger(SETTING_SAMPLERATE, gameboy.getSoundSampleRate());
                                settings.setInteger(SETTING_FRAMESKIP, gameboy.getVideoChip().getFrameSkip());
                                isRestartRequired |= (settings.getBoolean(SETTING_SCALING, true) ? 0 : 1) != scalingChoice.getSelectedIndex();
                                settings.setBoolean(SETTING_SCALING, scalingChoice.getSelectedIndex() == 0);
                                settings.setBoolean(SETTING_ACCELEROMETER, gbCanvas.isUseAccelerometer());
                                settings.setBoolean(SETTING_AUTO_ROTATE, gbCanvas.isAutoChangeOrientation());
                                settings.setBoolean(SETTING_SHOW_BUTTONS, showButtonsChoice.getSelectedIndex() == 0);
                                settings.setBoolean(SETTING_SHOW_DPAD, showDPadChoice.getSelectedIndex() == 0);

                                final String newLanguage = SUPPORTED_LOCALES[languageChoice.getSelectedIndex()];

                                isRestartRequired |= !activeLanguage.equals(newLanguage);
                                if (languageChoice.getSelectedIndex() == 0 && settings.exists(SETTING_LANGUAGE)) {
                                    settings.remove(SETTING_LANGUAGE);
                                } else {
                                    settings.setString(SETTING_LANGUAGE, newLanguage);
                                }
                            } catch (Exception e) {
                                // we couldn't save the settings, that's OK
                                e.printStackTrace();
                            }
                        }
                        display.setCurrent(mainForm);

                        // some settings might require a restart of the emulator, we tell the user about this
                        if (isRestartRequired) {
                            display.callSerially(
                                    new Runnable() {

                                        public void run() {
                                            display.setCurrent(new Alert(LocalizationSupport.getMessage("RestartRequired"), LocalizationSupport.getMessage("SomeSettingsRequireRestart"), null, AlertType.INFO));
                                        }
                                    });
                        }
                    }
                });

        this.previous = display.getCurrent();
        display.setCurrent(settingsForm);
    }

    /**
     * Switch the sound on/off
     *
     * @param   active  true to switch the sound on, false to switch it off
     */
    protected void setSound(final boolean active) {
        if (active) {
            if (this.gameboy.getSoundChip().countObservers() == 0) {
                // first try to initialize the PCMtoMIDIPlayer
                try {
                    this.gameboy.getSoundChip().addObserver(new PCMtoMIDIPlayer(this.gameboy.getSoundChip()));
                } catch (Throwable t) {
                    // if that does not work we try the WavePlayer
                    try {
                        this.gameboy.getSoundChip().addObserver(new WavePlayer(this.gameboy.getSoundChip()));
                    } catch (Throwable t2) {
                        // we could not add a player, that's OK
                        this.gameboy.getLogger().warning(LocalizationSupport.getMessage("CouldNotCreateSoundPlayer"));
                        t2.printStackTrace();
                    }
                }
            }
        } else {
            if (this.gameboy.getSoundChip().countObservers() > 0) {
                notifySoundPlayer(PCMtoMIDIPlayer.SIGNAL_STOP);
                this.gameboy.getSoundChip().deleteObservers();
            }
        }
        try {
            this.settings.setBoolean(SETTING_SOUNDACTIVE, active);
        } catch (Exception e) {
            // we couldn't store the setting, that's OK
        }
    }

    /**
     * Read the list of available programs from the archive file programs.txt
     * 
     * @return  map containing the filenames and locations of the available C64 programs
     */
    private Hashtable readProgramListFromTextFile() throws IOException {
        final Hashtable result = new Hashtable();
        final InputStream is = getClass().getResourceAsStream("/programs/programs.txt");
        int c = 0;

        while (c >= 0) {
            // read a line from the text file
            final StringBuffer line = new StringBuffer();

            while ((c = is.read()) > 0 && c != '\n' && c != '\r') {
                line.append((char) c);
            }

            // we don't have a comment line?
            if (!line.toString().startsWith("#") && line.length() > 0) {
                // then this must be a program name to add to the programs list
                result.put(line.toString(), "/programs/" + line.toString());
            }
        }

        return result;
    }

    /**
     * Check if the FileConnection API is supported by the device
     *
     * @return  true if the API is supported
     */
    public final boolean supportsFileConnectionAPI() {
        return null != System.getProperty("microedition.io.file.FileConnection.version");
    }
}
