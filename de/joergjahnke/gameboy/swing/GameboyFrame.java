/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.swing;

import de.joergjahnke.common.extendeddevices.WavePlayer;
import de.joergjahnke.common.util.Observer;
import de.joergjahnke.gameboy.core.Gameboy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * The emulator's main frame.
 */
public class GameboyFrame extends JFrame implements Observer {

    /**
     * Current program version
     */
    private final static String VERSION = "1.2.11";
    /**
     * URL of the online help page
     */
    private final static String URL_ONLINE_HELP = "http://sourceforge.net/apps/mediawiki/javagb/index.php?title=JSwingBoy_Online_Help";
    /**
     * Name of the settings file
     */
    private final static String PROPERTIES_NAME = "JSwingBoy.properties.xml";
    /**
     * Name of the suspend file
     */
    private final static String SUSPENDFILE_NAME = "JSwingBoy.suspend";
    /**
     * Name of the program icon file
     */
    private final static String ICON_NAME = "/res/jme/jmeboy_sm.png";
    /**
     * Setting for window scaling
     */
    private final static String SETTING_WINDOW_SCALING = "WindowScaling";
    /**
     * Setting for the directory of the last loaded image
     */
    private final static String SETTING_IMAGE_DIRECTORY = "ImageDirectory";
    /**
     * Setting for frame-skip
     */
    private final static String SETTING_FRAMESKIP = "FrameSkip";
    /**
     * URL of the project's main web page
     */
    private final static String PROJECT_PAGE_URL = "https://sourceforge.net/projects/javagb/";
    /**
     * file extension we use for saved games
     */
    private static final String SAVE_EXTENSION = ".sav";
    /**
     * file extension we use for snapshot files
     */
    private static final String SNAPSHOT_EXTENSION = ".snapshot";
    /**
     * status code when loading a saved emulator state was not necessary
     */
    private static final int STATUS_NOTHING_LOADED = 0;
    /**
     * status code when loading a saved emulator state succeeded
     */
    private static final int STATUS_LOAD_OK = 1;
    /**
     * status code when loading a saved emulator state failed
     */
    private static final int STATUS_LOAD_FAILED = 2;
    /**
     * main canvas
     */
    private final GameboyCanvas canvas;
    /**
     * Gameboy instance
     */
    private final Gameboy gameboy;
    /**
     * last selected file, used to point to the same directory as before with the file dialog
     */
    private File lastFile = null;
    /**
     * timer we install to display status bar messages only for a short time
     */
    private Timer statusMessageTimer = null;
    /**
     * emulator settings
     */
    private final Properties settings = new Properties();
    /**
     * current wave player
     */
    private WavePlayer currentWavePlayer = null;
    /**
     * localized string resources specific for the Gameboy emulator
     */
    private final ResourceBundle gbResources = ResourceBundle.getBundle("res/l10n/gameboyEmulatorMessages");
    /**
     * common localized string resources
     */
    private final ResourceBundle commonResources = ResourceBundle.getBundle("res/l10n/commonMessages");

    /**
     * Creates a new Gameboy frame
     */
    public GameboyFrame() {
        // use the system L&F if we can
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // we continue without setting the UI style
        }

        // load settings, if available
        try {
            this.settings.loadFromXML(new FileInputStream(new File(PROPERTIES_NAME)));
        } catch (Exception e) {
            // we could not load settings, that's OK, we just use the defaults
        }

        // set the window icon
        try {
            setIconImage(getToolkit().getImage(getClass().getResource(ICON_NAME)));
        } catch (SecurityException e) {
            // we can work without the icon having been set
        }

        initComponents();

        // create a GameboyCanvas for displaying the emulator
        this.canvas = new GameboyCanvas();

        try {
            // create Gameboy instance and inform the canvas about this instance
            this.gameboy = new Gameboy();
            this.canvas.setGameboy(gameboy);

            // get frameskip value from setting
            final int frameskip = Integer.parseInt(this.settings.getProperty(SETTING_FRAMESKIP, "2"));

            switch (frameskip) {
                case 1:
                    jMenuItemFrameSkip1ActionPerformed(null);
                    break;
                case 2:
                    jMenuItemFrameSkip2ActionPerformed(null);
                    break;
                case 3:
                    jMenuItemFrameSkip3ActionPerformed(null);
                    break;
                case 4:
                    jMenuItemFrameSkip4ActionPerformed(null);
                    break;
                default:
                    throw new RuntimeException(this.commonResources.getString("IllegalFrameskipValue") + " " + frameskip + "!");
            }

            // add canvas to this window and resize accordingly
            getContentPane().add(this.canvas, BorderLayout.CENTER);

            // apply size from settings
            final int scaling = Integer.parseInt(this.settings.getProperty(SETTING_WINDOW_SCALING, "2"));

            switch (scaling) {
                case 1:
                    jMenuItemSizeX1ActionPerformed(null);
                    break;
                case 2:
                    jMenuItemSizeX2ActionPerformed(null);
                    break;
                case 3:
                    jMenuItemSizeX3ActionPerformed(null);
                    break;
                case 4:
                    jMenuItemSizeX4ActionPerformed(null);
                    break;
            }

            // center the window of the screen
            final Dimension d = Toolkit.getDefaultToolkit().getScreenSize();

            setLocation((d.width - getSize().width) / 2, (d.height - getSize().height) / 2);
            setResizable(false);

            // we register as observer for the Gameboy's logger and the Gameboy instance to show log messages in the status bar
            this.gameboy.getLogger().addObserver(this);
            this.gameboy.addObserver(this);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(this.commonResources.getString("CouldNotInitialize") + e);
        }

        // install a window listener which saves the settings on exit
        addWindowListener(
                new WindowAdapter() {

                    @Override
                    public void windowClosing(final WindowEvent evt) {
                        try {
                            settings.storeToXML(new FileOutputStream(new File(PROPERTIES_NAME)), "Properties for JSwingBoy");
                            stopGame();
                        } catch (Exception e) {
                            // we can't save the settings, that's a pity, but we'll just use defaults on next startup
                        }
                    }
                });

        resume();
    }

    /**
     * Save the emulator state to a given file
     *
     * @param   filename    name of the file to save the state in
     */
    private boolean saveState(final String filename) {
        this.gameboy.pause();

        final File suspend = new File(filename);
        DataOutputStream out = null;
        boolean wasSuccessful = false;

        try {
            out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(suspend)));

            // save the currently loaded ROM name
            out.writeUTF(this.lastFile.getAbsolutePath());
            // save current emulator state
            this.gameboy.serialize(out);
            wasSuccessful = true;
        } catch (Throwable t) {
            // show the cause of the error
            t.printStackTrace();
            // delete the suspend file
            suspend.delete();
            // we don't exit, the user might want to continue now that the suspend failed
            wasSuccessful = false;
            this.gameboy.resume();
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
        }

        return wasSuccessful;
    }

    /**
     * Save the emulator state to a file and exit
     */
    private void suspend() {
        final boolean wasSuspended = saveState(SUSPENDFILE_NAME);

        if (wasSuspended) {
            setVisible(false);
            System.exit(0);
        } else {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToStoreState"), this.commonResources.getString("SuspendFailed"), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Save a snapshot and then continue with the game
     */
    private void saveSnapshot() {
        // generate name of file to save
        final DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
        String filename = this.lastFile.getAbsolutePath();

        filename = filename.substring(0, filename.lastIndexOf('.'));
        filename += " ";
        filename += format.format(new Date()).replaceAll("\\:", "");
        filename += SNAPSHOT_EXTENSION;

        // save snapshot
        final boolean wasSaved = saveState(filename);

        // continue with the game or show an error, depending on whether saving succeeded
        if (wasSaved) {
            this.gameboy.resume();
        } else {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToStoreState"), this.commonResources.getString("SavingSnapshotFailed"), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Load saved emulator state
     *
     * @param   filename    name of the file with the saved state
     * @return  status of the action
     */
    private int loadState(final String filename) {
        this.gameboy.pause();
        this.gameboy.stop();

        boolean hasSuspendData = false;
        int status = STATUS_NOTHING_LOADED;
        final File suspend = new File(filename);
        DataInputStream in = null;

        try {
            in = new DataInputStream(new BufferedInputStream(new FileInputStream(suspend)));

            hasSuspendData = true;

            // reload the old ROM file
            loadROM(in.readUTF());
            // load the emulator state
            this.gameboy.deserialize(in);

            status = STATUS_LOAD_OK;
        } catch (Throwable t) {
            if (hasSuspendData) {
                status = STATUS_LOAD_FAILED;
                t.printStackTrace();
            }
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }

        return status;
    }

    /**
     * Resume the old emulator state
     */
    private void resume() {
        // try to load a saved state
        final int status = loadState(SUSPENDFILE_NAME);

        // remove the suspend file if necessary
        if (status == STATUS_LOAD_OK || status == STATUS_LOAD_FAILED) {
            try {
                new File(SUSPENDFILE_NAME).delete();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Could not remove the suspend file! Please remove the suspend file '" + SUSPENDFILE_NAME + "' manually.", this.commonResources.getString("CouldNotRemoveSuspendData"), JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }

        // start emulation or show an error message, depending on the outcome of the resume operation
        switch (status) {
            case STATUS_LOAD_OK:
                // start the emulation
                new Thread(this.gameboy).start();
                break;
            case STATUS_LOAD_FAILED:
                JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToRestoreState"), this.commonResources.getString("ResumeFailed"), JOptionPane.ERROR_MESSAGE);
                break;
        }
    }

    /**
     * Load and start a given emulator snapshot
     *
     * @param   filename    name of the snapshot to load
     */
    private void runSnapshot(final String filename) {
        // try to load a saved state
        final int status = loadState(filename);

        // start emulation or show an error message, depending on the outcome of the resume operation
        switch (status) {
            case STATUS_LOAD_OK:
                // start the emulation
                new Thread(this.gameboy).start();
                break;
            case STATUS_LOAD_FAILED:
                JOptionPane.showMessageDialog(this, this.commonResources.getString("FailedToRestoreState"), this.commonResources.getString("LoadSnapshotFailed"), JOptionPane.ERROR_MESSAGE);
                break;
        }
    }

    /**
     * Load a given ROM file into the emulator
     * 
     * @param romFilename   path of the file to load
     */
    private void loadROM(final String romFilename) {
        this.lastFile = new File(romFilename);
        this.settings.setProperty(SETTING_IMAGE_DIRECTORY, this.lastFile.getParentFile().getAbsolutePath());

        // load the cartridge
        InputStream romStream = null;

        try {
            romStream = new BufferedInputStream(new FileInputStream(this.lastFile));

            gameboy.stop();
            gameboy.load(romStream);

            // check whether we have saved data from a previous game
            InputStream saveStream = null;

            try {
                final String saveFilename = romFilename.substring(0, romFilename.lastIndexOf('.')) + SAVE_EXTENSION;

                saveStream = new BufferedInputStream(new FileInputStream(saveFilename));

                this.gameboy.getCartridge().loadData(saveStream);
                saveStream.close();
            } catch (Exception e) {
                // no saved game was found
            } finally {
                try {
                    saveStream.close();
                } catch (Exception e) {
                }
            }

            // create a player that observes the sound chip and plays its sound
            if (null != this.currentWavePlayer) {
                this.currentWavePlayer.stop();
                this.gameboy.getSoundChip().deleteObservers();
            }
            this.currentWavePlayer = new WavePlayer(this.gameboy.getSoundChip());
            this.gameboy.getSoundChip().addObserver(this.currentWavePlayer);

            // enable the suspend and the save snapshot menu items
            this.jMenuItemSuspend.setEnabled(true);
            this.jMenuItemSaveSnapshot.setEnabled(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, this.gbResources.getString("CouldNotStartGameWMessage") + e, this.gbResources.getString("CouldNotStartGame"), JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                romStream.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Stop a game and ensure that its state gets saved in case of a ROM with battery support
     */
    private void stopGame() {
        this.gameboy.stop();
        // we have to save the cartridge RAM?
        if (null != this.gameboy.getCartridge() && this.gameboy.getCartridge().hasBatterySupport()) {
            // save the game
            try {
                // save cartridge data to file
                final String filename = this.lastFile.getAbsolutePath().substring(0, this.lastFile.getAbsolutePath().lastIndexOf('.')) + SAVE_EXTENSION;
                final FileOutputStream fout = new FileOutputStream(filename);

                this.gameboy.getCartridge().saveData(fout);
                fout.close();
            } catch (Exception e) {
                // we could not save the file
                JOptionPane.showMessageDialog(this, this.gbResources.getString("FailedToSaveGameDataFor") + this.lastFile, this.gbResources.getString("FailedToSaveGame"), JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {

            public void run() {
                new GameboyFrame().setVisible(true);
            }
        });
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroupScreenSizes = new javax.swing.ButtonGroup();
        buttonGroupFrameSkipSelections = new javax.swing.ButtonGroup();
        jPanelStatus = new javax.swing.JPanel();
        jLabelPerformance = new javax.swing.JLabel();
        jLabelMessages = new javax.swing.JLabel();
        jMenuBar = new javax.swing.JMenuBar();
        jMenuFile = new javax.swing.JMenu();
        jMenuItemAttachImage = new javax.swing.JMenuItem();
        jMenuItemSaveSnapshot = new javax.swing.JMenuItem();
        jSeparatorFileMenu = new javax.swing.JSeparator();
        jMenuItemExit = new javax.swing.JMenuItem();
        jMenuItemSuspend = new javax.swing.JMenuItem();
        jMenuEmulation = new javax.swing.JMenu();
        jSeparatorEmulationMenu1 = new javax.swing.JSeparator();
        jMenuSize = new javax.swing.JMenu();
        jMenuItemSizeX1 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemSizeX2 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemSizeX3 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemSizeX4 = new javax.swing.JRadioButtonMenuItem();
        jMenuFrameskip = new javax.swing.JMenu();
        jMenuItemFrameSkip1 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip2 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip3 = new javax.swing.JRadioButtonMenuItem();
        jMenuItemFrameSkip4 = new javax.swing.JRadioButtonMenuItem();
        jSeparatorEmulationMenu2 = new javax.swing.JSeparator();
        jMenuItemShowLog = new javax.swing.JMenuItem();
        jMenuHelp = new javax.swing.JMenu();
        jMenuItemAbout = new javax.swing.JMenuItem();
        jMenuItemContents = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("JSwingBoy");

        jPanelStatus.setLayout(new java.awt.BorderLayout());

        jLabelPerformance.setFont(new java.awt.Font("Arial", 0, 10));
        jLabelPerformance.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabelPerformance.setText("      ");
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("res/l10n/gameboyEmulatorMessages"); // NOI18N
        jLabelPerformance.setToolTipText(bundle.getString("PerformancePanelTooltip")); // NOI18N
        jLabelPerformance.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jLabelPerformance.setOpaque(true);
        jLabelPerformance.setPreferredSize(new java.awt.Dimension(45, 17));
        jPanelStatus.add(jLabelPerformance, java.awt.BorderLayout.WEST);

        jLabelMessages.setFont(new java.awt.Font("Arial", 0, 10));
        jLabelMessages.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabelMessages.setToolTipText(bundle.getString("MessagePanelTooltip")); // NOI18N
        jLabelMessages.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jPanelStatus.add(jLabelMessages, java.awt.BorderLayout.CENTER);

        getContentPane().add(jPanelStatus, java.awt.BorderLayout.SOUTH);

        java.util.ResourceBundle bundle1 = java.util.ResourceBundle.getBundle("res/l10n/commonMessages"); // NOI18N
        jMenuFile.setText(bundle1.getString("File")); // NOI18N

        jMenuItemAttachImage.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_L, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemAttachImage.setText(bundle.getString("LoadGame")); // NOI18N
        jMenuItemAttachImage.setToolTipText(bundle.getString("LoadGameTooltip")); // NOI18N
        jMenuItemAttachImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAttachImageActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemAttachImage);

        jMenuItemSaveSnapshot.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemSaveSnapshot.setText(bundle1.getString("SaveSnapshot")); // NOI18N
        jMenuItemSaveSnapshot.setToolTipText(bundle1.getString("SaveSnapshotTooltip")); // NOI18N
        jMenuItemSaveSnapshot.setEnabled(false);
        jMenuItemSaveSnapshot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSaveSnapshotActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemSaveSnapshot);
        jMenuFile.add(jSeparatorFileMenu);

        jMenuItemExit.setText(bundle1.getString("Exit")); // NOI18N
        jMenuItemExit.setToolTipText(bundle1.getString("ExitTooltip")); // NOI18N
        jMenuItemExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemExitActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemExit);

        jMenuItemSuspend.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemSuspend.setText(bundle1.getString("Suspend")); // NOI18N
        jMenuItemSuspend.setToolTipText(bundle1.getString("SuspendTooltip")); // NOI18N
        jMenuItemSuspend.setEnabled(false);
        jMenuItemSuspend.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSuspendActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemSuspend);

        jMenuBar.add(jMenuFile);

        jMenuEmulation.setText(bundle1.getString("Emulation")); // NOI18N
        jMenuEmulation.add(jSeparatorEmulationMenu1);

        jMenuSize.setText(bundle1.getString("Size")); // NOI18N
        jMenuSize.setToolTipText(bundle1.getString("SizeTooltip")); // NOI18N

        jMenuItemSizeX1.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX1);
        jMenuItemSizeX1.setText("100%");
        jMenuItemSizeX1.setToolTipText(bundle.getString("Size100Tooltip")); // NOI18N
        jMenuItemSizeX1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX1ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX1);

        jMenuItemSizeX2.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_2, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX2);
        jMenuItemSizeX2.setText("200%");
        jMenuItemSizeX2.setToolTipText(bundle.getString("Size200Tooltip")); // NOI18N
        jMenuItemSizeX2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX2ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX2);

        jMenuItemSizeX3.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_3, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX3);
        jMenuItemSizeX3.setText("300%");
        jMenuItemSizeX3.setToolTipText(bundle.getString("Size300Tooltip")); // NOI18N
        jMenuItemSizeX3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX3ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX3);

        jMenuItemSizeX4.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.SHIFT_MASK));
        buttonGroupScreenSizes.add(jMenuItemSizeX4);
        jMenuItemSizeX4.setText("400%");
        jMenuItemSizeX4.setToolTipText(bundle.getString("Size400Tooltip")); // NOI18N
        jMenuItemSizeX4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSizeX4ActionPerformed(evt);
            }
        });
        jMenuSize.add(jMenuItemSizeX4);

        jMenuEmulation.add(jMenuSize);

        jMenuFrameskip.setText(bundle1.getString("SkipFrames")); // NOI18N
        jMenuFrameskip.setToolTipText(bundle1.getString("SkipFramesTooltip")); // NOI18N

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip1);
        jMenuItemFrameSkip1.setText("1");
        jMenuItemFrameSkip1.setToolTipText(bundle1.getString("SkipFrames1Tooltip")); // NOI18N
        jMenuItemFrameSkip1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip1ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip1);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip2);
        jMenuItemFrameSkip2.setText("2");
        jMenuItemFrameSkip2.setToolTipText(bundle1.getString("SkipFrames2Tooltip")); // NOI18N
        jMenuItemFrameSkip2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip2ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip2);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip3);
        jMenuItemFrameSkip3.setText("3");
        jMenuItemFrameSkip3.setToolTipText(bundle1.getString("SkipFrames3Tooltip")); // NOI18N
        jMenuItemFrameSkip3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip3ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip3);

        buttonGroupFrameSkipSelections.add(jMenuItemFrameSkip4);
        jMenuItemFrameSkip4.setText("4");
        jMenuItemFrameSkip4.setToolTipText(bundle1.getString("SkipFrames4Tooltip")); // NOI18N
        jMenuItemFrameSkip4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemFrameSkip4ActionPerformed(evt);
            }
        });
        jMenuFrameskip.add(jMenuItemFrameSkip4);

        jMenuEmulation.add(jMenuFrameskip);
        jMenuEmulation.add(jSeparatorEmulationMenu2);

        jMenuItemShowLog.setText(bundle1.getString("ShowLog")); // NOI18N
        jMenuItemShowLog.setToolTipText(bundle1.getString("ShowLogTooltip")); // NOI18N
        jMenuItemShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemShowLogActionPerformed(evt);
            }
        });
        jMenuEmulation.add(jMenuItemShowLog);

        jMenuBar.add(jMenuEmulation);

        jMenuHelp.setText(bundle1.getString("Help")); // NOI18N

        jMenuItemAbout.setText(bundle1.getString("About")); // NOI18N
        jMenuItemAbout.setToolTipText(bundle1.getString("AboutTooltip")); // NOI18N
        jMenuItemAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAboutActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemAbout);

        jMenuItemContents.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemContents.setText(bundle1.getString("Contents")); // NOI18N
        jMenuItemContents.setToolTipText(bundle1.getString("ContentsTooltip")); // NOI18N
        jMenuItemContents.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemContentsActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemContents);

        jMenuBar.add(jMenuHelp);

        setJMenuBar(jMenuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents
    private void jMenuItemAttachImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAttachImageActionPerformed
        // show a dialog to select the C64 file to attach
        final JFileChooser fileChooser = new JFileChooser() {

            @Override
            public boolean accept(final File f) {
                return f.getName().toLowerCase().endsWith(".gb") || f.getName().toLowerCase().endsWith(".gbc") || f.getName().toLowerCase().endsWith(".cgb") || f.getName().toLowerCase().endsWith(SNAPSHOT_EXTENSION) || f.isDirectory();
            }
        };

        if (this.lastFile != null) {
            fileChooser.setCurrentDirectory(this.lastFile.getParentFile());
        } else if (this.settings.getProperty(SETTING_IMAGE_DIRECTORY) != null) {
            fileChooser.setCurrentDirectory(new File(this.settings.getProperty(SETTING_IMAGE_DIRECTORY)));
        }

        // a file was selected?
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // stop (possible) running old game
            stopGame();

            // load and start new game
            final String filename = fileChooser.getSelectedFile().getAbsolutePath();

            if (filename.endsWith(SNAPSHOT_EXTENSION)) {
                runSnapshot(filename);
            } else {
                loadROM(filename);
                // start the emulation
                new Thread(this.gameboy).start();
            }
        }
    }//GEN-LAST:event_jMenuItemAttachImageActionPerformed

    private void jMenuItemExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemExitActionPerformed
        setVisible(false);
        System.exit(0);
    }//GEN-LAST:event_jMenuItemExitActionPerformed

    private void jMenuItemSizeX1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX1ActionPerformed
        this.canvas.setScaling(1);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "1");
        pack();
        this.jMenuItemSizeX1.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX1ActionPerformed

    private void jMenuItemSizeX2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX2ActionPerformed
        this.canvas.setScaling(2);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "2");
        pack();
        this.jMenuItemSizeX2.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX2ActionPerformed

    private void jMenuItemSizeX3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX3ActionPerformed
        this.canvas.setScaling(3);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "3");
        pack();
        this.jMenuItemSizeX3.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX3ActionPerformed

    private void jMenuItemFrameSkip1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip1ActionPerformed
        this.gameboy.getVideoChip().setFrameSkip(1);
        this.settings.setProperty(SETTING_FRAMESKIP, "1");
        this.jMenuItemFrameSkip1.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip1ActionPerformed

    private void jMenuItemFrameSkip2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip2ActionPerformed
        this.gameboy.getVideoChip().setFrameSkip(2);
        this.settings.setProperty(SETTING_FRAMESKIP, "2");
        this.jMenuItemFrameSkip2.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip2ActionPerformed

    private void jMenuItemFrameSkip3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip3ActionPerformed
        this.gameboy.getVideoChip().setFrameSkip(3);
        this.settings.setProperty(SETTING_FRAMESKIP, "3");
        this.jMenuItemFrameSkip3.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip3ActionPerformed

    private void jMenuItemFrameSkip4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemFrameSkip4ActionPerformed
        this.gameboy.getVideoChip().setFrameSkip(4);
        this.settings.setProperty(SETTING_FRAMESKIP, "4");
        this.jMenuItemFrameSkip4.setSelected(true);
    }//GEN-LAST:event_jMenuItemFrameSkip4ActionPerformed

    private void jMenuItemShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemShowLogActionPerformed
        JOptionPane.showMessageDialog(this, this.gameboy.getLogger().dumpAll(), this.commonResources.getString("LogMessages"), JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_jMenuItemShowLogActionPerformed

    private void jMenuItemAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAboutActionPerformed
        // get About box text and convert it to HTML, inserting the link to the project page
        String text = this.gbResources.getString("AboutText1") + VERSION + this.gbResources.getString("AboutText2") + this.gbResources.getString("AboutText3");

        text = text.replaceAll("\\n", "<br>");
        text = text.replaceAll("\\#PROJECTPAGE\\#", "\\<a href\\=\\'" + PROJECT_PAGE_URL + "\\'\\>" + PROJECT_PAGE_URL + "\\<\\/a\\>");
        text = "<html><body>" + text + "</body></html>";

        // create an editor pane that displays this text and add a listener that uses the system browser to display any hyperlinks activated by the user
        JEditorPane messagePane = new JEditorPane("text/html", text);

        messagePane.setBackground(getBackground());
        messagePane.setEditable(false);
        messagePane.addHyperlinkListener(new HyperlinkListener() {

            @Override
            public void hyperlinkUpdate(final HyperlinkEvent evt) {
                if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(evt.getURL().toURI());
                    } catch (Throwable t) {
                        // could not display the web page, what to do now???
                        System.err.println("Could not browse to page " + evt.getURL());
                    }
                }
            }
        });

        JOptionPane.showMessageDialog(this, messagePane);
    }//GEN-LAST:event_jMenuItemAboutActionPerformed

    private void jMenuItemContentsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemContentsActionPerformed
        try {
            java.awt.Desktop.getDesktop().browse(new URI(URL_ONLINE_HELP));
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, this.commonResources.getString("CouldNotDisplayOnlineHelp") + " '" + URL_ONLINE_HELP + "'", this.commonResources.getString("CouldNotStartBrowser"), JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_jMenuItemContentsActionPerformed

    private void jMenuItemSizeX4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSizeX4ActionPerformed
        this.canvas.setScaling(4);
        this.settings.setProperty(SETTING_WINDOW_SCALING, "4");
        pack();
        this.jMenuItemSizeX4.setSelected(true);
    }//GEN-LAST:event_jMenuItemSizeX4ActionPerformed

    private void jMenuItemSuspendActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSuspendActionPerformed
        suspend();
    }//GEN-LAST:event_jMenuItemSuspendActionPerformed

    private void jMenuItemSaveSnapshotActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSaveSnapshotActionPerformed
        saveSnapshot();
    }//GEN-LAST:event_jMenuItemSaveSnapshotActionPerformed

    // implementation of the Observer interface
    /**
     * We show log messages in the status bar and save changes to modified emulator images
     */
    public void update(final Object observed, final Object arg) {
        // this update is from the Gameboy's logger?
        if (observed == this.gameboy.getLogger()) {
            final String message = arg.toString();

            // do not display a performance info here
            if (!(message.startsWith("Emulator working at ") && message.indexOf("performance") > 0)) {
                // otherwise we have a normal message and display this for a short time
                this.jLabelMessages.setText(message);
                // clear the message after 5 seconds
                if (null != this.statusMessageTimer) {
                    this.statusMessageTimer.cancel();
                }
                this.statusMessageTimer = new Timer();
                this.statusMessageTimer.schedule(
                        new TimerTask() {

                            public void run() {
                                jLabelMessages.setText("");
                                statusMessageTimer = null;
                            }
                        }, 5000);
            }
            // we have a new performance measurement result?
        } else if (observed == this.gameboy) {
            // then display this with the corresponding color
            final int performance = this.gameboy.getPerformanceMeter().getLastPerformance();
            final Color background = performance >= 120
                    ? Color.CYAN
                    : performance >= 90
                    ? Color.GREEN
                    : performance >= 80
                    ? Color.YELLOW
                    : Color.RED;
            final String performanceText = "   " + performance + "% ";

            this.jLabelPerformance.setBackground(background);
            this.jLabelPerformance.setText(performanceText.substring(performanceText.length() - 6, performanceText.length()));
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroupFrameSkipSelections;
    private javax.swing.ButtonGroup buttonGroupScreenSizes;
    private javax.swing.JLabel jLabelMessages;
    private javax.swing.JLabel jLabelPerformance;
    private javax.swing.JMenuBar jMenuBar;
    private javax.swing.JMenu jMenuEmulation;
    private javax.swing.JMenu jMenuFile;
    private javax.swing.JMenu jMenuFrameskip;
    private javax.swing.JMenu jMenuHelp;
    private javax.swing.JMenuItem jMenuItemAbout;
    private javax.swing.JMenuItem jMenuItemAttachImage;
    private javax.swing.JMenuItem jMenuItemContents;
    private javax.swing.JMenuItem jMenuItemExit;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip1;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip2;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip3;
    private javax.swing.JRadioButtonMenuItem jMenuItemFrameSkip4;
    private javax.swing.JMenuItem jMenuItemSaveSnapshot;
    private javax.swing.JMenuItem jMenuItemShowLog;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX1;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX2;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX3;
    private javax.swing.JRadioButtonMenuItem jMenuItemSizeX4;
    private javax.swing.JMenuItem jMenuItemSuspend;
    private javax.swing.JMenu jMenuSize;
    private javax.swing.JPanel jPanelStatus;
    private javax.swing.JSeparator jSeparatorEmulationMenu1;
    private javax.swing.JSeparator jSeparatorEmulationMenu2;
    private javax.swing.JSeparator jSeparatorFileMenu;
    // End of variables declaration//GEN-END:variables
}
