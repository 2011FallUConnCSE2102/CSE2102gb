package de.joergjahnke.gameboy.android;

import de.joergjahnke.common.android.FileDialog;
import java.util.Arrays;
import java.util.List;

/**
 * Class displaying a dialog for selecting a gameboy cartridge to load into the emulator
 * 
 * @author Jörg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class LoadCartridgeDialog extends FileDialog {

    @Override
    public List<String> getAcceptedFileTypes() {
        final String[] types = {"cgb", "gbc", "gb"};

        return Arrays.asList(types);
    }

    @Override
    public int getFileImage() {
        return R.drawable.cartridge;
    }

    @Override
    public int getFolderImage() {
        return R.drawable.folder;
    }

    @Override
    public int getParentFolderImage() {
        return R.drawable.parent;
    }

    @Override
    public int getTextView() {
        return R.layout.listactivities_textview1;
    }
}
