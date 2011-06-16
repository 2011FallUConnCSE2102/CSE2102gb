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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

/**
 * Implements a Gameboy cartridge.<br>
 * For a good documentation on the Gameboy cartridge types see <a href='http://verhoeven272.nl/fruttenboel/Gameboy/pandocs.html.gz#thecartridgeheader'>http://verhoeven272.nl/fruttenboel/Gameboy/pandocs.html.gz#thecartridgeheader</a>.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class Cartridge extends DefaultObservable implements Serializable, Observer {

    /**
     * Size of a ROM bank is 16k
     */
    public final static int ROM_BANK_SIZE = 0x4000;
    /**
     * Size of a RAM bank is 8k
     */
    public final static int RAM_BANK_SIZE = 0x2000;
    /**
     * start of the ROM bank area
     */
    private static final int ROM_BANK_AREA = 0x4000;
    /**
     * start of the RAM bank area
     */
    private static final int RAM_BANK_AREA = 0xa000;
    /**
     * do we default the RTC with the system clock
     */
    private final static boolean DEFAULT_RTC_WITH_SYSTEM_CLOCK = true;
    /**
     * do we use the Gameboy CPU as input for passed time? If false, then the device's clock is used as input
     */
    private final static boolean SYNCHRONIZE_RTC_WITH_CPU = false;
    /**
     * supported file extensions for cartridge files
     */
    public final static Vector SUPPORTED_EXTENSIONS = new Vector();


    static {
        SUPPORTED_EXTENSIONS.addElement("gbc");
        SUPPORTED_EXTENSIONS.addElement("cgb");
        SUPPORTED_EXTENSIONS.addElement("gb");
    }
    /**
     * the gameboy the cartridge is attached to
     */
    private final Gameboy gameboy;
    /**
     * the cartridge title
     */
    private String title;
    /**
     * use Gameboy color mode?
     */
    private boolean isGBC;
    /**
     * cartridge type
     */
    private int cartridgeType;
    /**
     * ROM size
     */
    private int romSize;
    /**
     * RAM size
     */
    private int ramSize;
    /**
     * ROM banks
     */
    private byte[][] romBanks;
    /**
     * RAM banks
     */
    private byte[][] ramBanks;
    /**
     * handles cartridge read & write operations
     */
    private CartridgeImpl cartridgeImpl;

    /**
     * Create a new cartridge.
     * During loading the cartridge data observers get informed about the progress of
     * the load operation.
     * 
     * @param   gameboy the gameboy this cartridge is attached to
     */
    public Cartridge(final Gameboy gameboy) {
        this.gameboy = gameboy;
    }

    /**
     * Load cartridge data.
     * During loading the cartridge data observers get informed about the progress of
     * the load operation.
     *
     * @param	romStream	stream with cartridge data
     * @throws IOException	if the cartridge data cannot be read
     */
    public void load(final InputStream romStream) throws IOException {
        // notify observers that we start loading
        setChanged(true);
        notifyObservers(new Integer(0));

        // remove old cartridge data
        this.ramBanks = null;

        // read first ROM bank
        final byte[] buffer = new byte[ROM_BANK_SIZE];

        romStream.read(buffer);

        // determine the cartridge title
        final StringBuffer titleBuffer = new StringBuffer();

        for (int i = 0x134; i < 0x144 && buffer[i] != 0; ++i) {
            titleBuffer.append((char) buffer[i]);
        }
        this.title = titleBuffer.toString();

        // check whether we have a Gameboy Color cartridge
        this.isGBC = (buffer[0x143] & 0x80) != 0;

        // determine cartridge type
        this.cartridgeType = buffer[0x147] & 0xff;

        // determine the correct cartridge implementation
        final String cartridgeTypeName = getCartridgeTypeName();

        if (cartridgeTypeName.startsWith("MBC1") || cartridgeTypeName.startsWith("ROM")) {
            this.cartridgeImpl = new MBC1CartridgeImpl();
        } else if (cartridgeTypeName.startsWith("MBC2")) {
            this.cartridgeImpl = new MBC2CartridgeImpl();
        } else if (cartridgeTypeName.startsWith("MBC3")) {
            this.cartridgeImpl = new MBC3CartridgeImpl();
        } else if (cartridgeTypeName.startsWith("MBC5")) {
            this.cartridgeImpl = new MBC5CartridgeImpl();
        } else {
            this.cartridgeImpl = new MBC1CartridgeImpl();
            this.gameboy.getLogger().warning("Unsupported cartridge type: " + cartridgeTypeName + "! Trying with MBC1 cartridge handling.");
        }

        // determine ROM size
        switch (buffer[0x148]) {
            case 0x52:
                this.romSize = 72 * ROM_BANK_SIZE;
                break;
            case 0x53:
                this.romSize = 80 * ROM_BANK_SIZE;
                break;
            case 0x54:
                this.romSize = 96 * ROM_BANK_SIZE;
                break;
            default:
                this.romSize = 0x8000 << (buffer[0x148] & 0xff);
        }

        // determine RAM size
        switch (buffer[0x149]) {
            case 1:
                this.ramSize = 0x0800;
                break;
            case 2:
                this.ramSize = 0x2000;
                break;
            case 3:
                this.ramSize = 0x8000;
                break;
            case 4:
            case 5:
            case 6:
                this.ramSize = 0x20000;
                break;
            default:
                this.ramSize = 0;
        }

        // initialize RAM banks
        this.ramBanks = new byte[Math.max(1, this.ramSize / RAM_BANK_SIZE)][RAM_BANK_SIZE];

        // initialize and read ROM banks
        final int numROMBanks = this.romSize / ROM_BANK_SIZE;

        this.romBanks = new byte[this.romSize / ROM_BANK_SIZE][ROM_BANK_SIZE];
        System.arraycopy(buffer, 0, this.romBanks[ 0], 0, buffer.length);
        for (int i = 1; i < romBanks.length; ++i) {
            setChanged(true);
            notifyObservers(new Integer(i * 100 / numROMBanks));

            romStream.read(buffer);
            System.arraycopy(buffer, 0, this.romBanks[i], 0, buffer.length);
        }

        // notify observers that we finished loading
        setChanged(true);
        notifyObservers(new Integer(100));
    }

    /**
     * Get the cartridge title
     * 
     * @return	title
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Check whether this cartridge supports Gameboy Color features
     * 
     * @return	true if we have a Gameboy Color cartridge
     */
    public boolean isGBC() {
        return this.isGBC;
    }

    /**
     * Get the cartridge type
     * 
     * @return	integer defining the cartridge type
     */
    public int getCartridgeType() {
        return this.cartridgeType;
    }

    /**
     * Get the cartridge type name
     * 
     * @return  cartridge type e.g. MBC1
     */
    public String getCartridgeTypeName() {
        switch (this.cartridgeType) {
            case 0x00:
                return "ROM Only";
            case 0x01:
                return "MBC1";
            case 0x02:
                return "MBC1+RAM";
            case 0x03:
                return "MBC1+RAM+Battery";
            case 0x05:
                return "MBC2";
            case 0x06:
                return "MBC2+Battery";
            case 0x08:
                return "ROM+RAM";
            case 0x09:
                return "ROM+RAM+Battery";
            case 0x0b:
                return "MMM1";
            case 0x0c:
                return "MMM1+RAM";
            case 0x0d:
                return "MMM1+RAM+Battery";
            case 0x0f:
                return "MBC3+Timer+Battery";
            case 0x10:
                return "MBC3+Timer+RAM+Battery";
            case 0x11:
                return "MBC3";
            case 0x12:
                return "MBC3+RAM";
            case 0x13:
                return "MBC3+RAM+Battery";
            case 0x15:
                return "MBC4";
            case 0x16:
                return "MBC4+RAM";
            case 0x17:
                return "MBC4+RAM+Battery";
            case 0x19:
                return "MBC5";
            case 0x1a:
                return "MBC5+RAM";
            case 0x1b:
                return "MBC5+RAM+Battery";
            case 0x1c:
                return "MBC5+Rumble";
            case 0x1d:
                return "MBC5+Rumble+RAM";
            case 0x1e:
                return "MBC5+Rumble+RAM+Battery";
            case 0xfe:
                return "HuC3";
            case 0xff:
                return "HuC1+RAM+Battery";
            default:
                return "Unknown (" + this.cartridgeType + ")";
        }
    }

    /**
     * Check whether the cartridge contains a battery buffering the RAM data
     * 
     * @return	true if the cartridge has battery support
     */
    public boolean hasBatterySupport() {
        return getCartridgeTypeName().indexOf("Battery") >= 0;
    }

    /**
     * Get the size of the cartridge ROM
     * 
     * @return	number of bytes
     */
    public int getROMSize() {
        return this.romSize;
    }

    /**
     * Get the size of the cartridge RAM
     * 
     * @return	number of bytes
     */
    public int getRAMSize() {
        return this.ramSize;
    }

    /**
     * Get the ROM banks of the cartridge
     *
     * @return	ROM banks, each 32k in size
     */
    public byte[][] getROMBanks() {
        return this.romBanks;
    }

    /**
     * Get the RAM banks of the cartridge
     * 
     * @return	RAM banks, each 8k in size
     */
    public byte[][] getRAMBanks() {
        return this.ramBanks;
    }

    /**
     * Write to the cartridge
     * 
     * @param   adr address to write to
     * @param   data    data to write
     */
    public final void writeByte(final int adr, final byte data) {
        this.cartridgeImpl.writeByte(adr, data);
    }

    /**
     * Get output stream with data that needs to be saved.
     * In any case this will be the content of the cartridge RAM, some cartridge
     * types might add more data.
     *
     * @param   saveStream  stream to save the data to
     * @throws java.io.IOException  if data cannot be written
     */
    public void saveData(final OutputStream saveStream) throws IOException {
        final DataOutputStream out = new DataOutputStream(saveStream);

        // write RAM data
        for (int i = 0, to = getRAMBanks().length; i < to; ++i) {
            out.write(getRAMBanks()[i]);
        }

        // write additional data if necessary
        serialize(out);
        out.flush();
    }

    /**
     * Load cartridge data
     *
     * @param loadStream    stream to load from
     * @throws java.io.IOException  if the data cannot be read
     */
    public void loadData(final InputStream loadStream) throws IOException {
        final DataInputStream in = new DataInputStream(loadStream);

        // load RAM data
        for (int i = 0, to = getRAMBanks().length; i < to; ++i) {
            in.read(getRAMBanks()[i]);
        }

        // try to deserialize additional data
        try {
            deserialize(in);
        } catch (Exception e) {
            // we might have an old save file where additional data was not added, that's OK
        }
    }

    // implementation of the Serializable interface
    /**
     * Serialization for a Cartridge includes only the cartridge data that might be modified during
     * game execution. The cartridge itself needs to be loaded normally before serialization to restore
     * a running game.
     * 
     * @param out   stream to save to
     * @throws java.io.IOException if the cartridge cannot be saved
     */
    public void serialize(final DataOutputStream out) throws IOException {
        this.cartridgeImpl.serialize(out);
        for (int i = 0; i < this.ramBanks.length; ++i) {
            SerializationUtils.serialize(out, this.ramBanks[i]);
        }
    }

    public void deserialize(final DataInputStream in) throws IOException {
        this.cartridgeImpl.deserialize(in);
        for (int i = 0; i < this.ramBanks.length; ++i) {
            SerializationUtils.deserialize(in, this.ramBanks[i]);
        }
    }

    /**
     * abstract inner class implementing cartridge read & write operations
     */
    abstract class CartridgeImpl implements Serializable, Observer {

        /**
         * is writing to cartridge RAM enabled?
         */
        protected boolean areRAMWritesEnabled = true;
        /**
         * ROM/RAM banking mode
         */
        protected boolean isROMBankingMode = true;
        /**
         * Currently active 16k ROM bank in memory $4000-$7fff
         */
        protected int currentROMBank = 1;
        /**
         * Currently active 8k ROM bank in memory $a000-$bfff
         */
        protected int currentRAMBank = 0;

        /**
         * Write to the cartridge
         * 
         * @param   adr address to write to
         * @param   data    data to write
         */
        public abstract void writeByte(final int adr, final byte data);

        /**
         * Set new ROM bank to be active at $4000-$7fff of the main memory.
         * Also copies the new ROM from the cartridge to main memory.
         * 
         * @param romBank	ROM bank number to activate
         */
        protected final void setROMBank(final int romBank) {
            if (romBank != this.currentROMBank) {
                if (romBank >= getROMBanks().length) {
                    gameboy.getLogger().warning("Tried to access ROM bank " + romBank + " of only " + getROMBanks().length + " ROM banks!");
                }
                this.currentROMBank = romBank % getROMBanks().length;
                System.arraycopy(getROMBanks()[this.currentROMBank], 0, gameboy.getCPU().memory, ROM_BANK_AREA, ROM_BANK_SIZE);
            }
        }

        /**
         * Set new RAM bank to be active at $a000-$bfff of the main memory.
         * Also copies the new RAM from the cartridge to main memory.
         * Old RAM is automatically written through to the cartridge and does not
         * need to be copied back to the cartridge.
         * 
         * @param ramBank	RAM bank number to activate
         */
        protected final void setRAMBank(final int ramBank) {
            if (ramBank != this.currentRAMBank) {
                this.currentRAMBank = ramBank;
                System.arraycopy(getRAMBanks()[this.currentRAMBank], 0, gameboy.getCPU().memory, RAM_BANK_AREA, RAM_BANK_SIZE);
            }
        }

        // implementation of the Serializable interface
        public void serialize(final DataOutputStream out) throws IOException {
            out.writeBoolean(this.areRAMWritesEnabled);
            out.writeBoolean(this.isROMBankingMode);
            out.writeInt(this.currentROMBank);
            out.writeInt(this.currentRAMBank);
        }

        public void deserialize(final DataInputStream in) throws IOException {
            this.areRAMWritesEnabled = in.readBoolean();
            this.isROMBankingMode = in.readBoolean();
            this.currentROMBank = in.readInt();
            this.currentRAMBank = in.readInt();
        }

        // implementation of the Observer interface
        public void update(final Object observed, final Object arg) {
            // the default implementation does nothing
        }
    }

    /**
     * Implements cartridge writes to MBC2 type cartridges
     */
    class MBC1CartridgeImpl extends CartridgeImpl {

        public void writeByte(final int adr, final byte data) {
            switch (adr & 0xe000) {
                // writing to $0000-$1fff enables or disables cartridge RAM writes
                case 0x0000:
                    this.areRAMWritesEnabled = (data & 0x0f) == 0x0a;
                    break;
                // writing to $2000-$3fff specifies the lower 5 bits of the ROM bank number
                case 0x2000: {
                    setROMBank((this.currentROMBank & 0xe0) + Math.max(1, data & 0x1f));
                    break;
                }
                case ROM_BANK_AREA: {
                    // select a ROM bank?
                    if (this.isROMBankingMode) {
                        setROMBank((this.currentROMBank & 0x1f) + ((data & 0x03) << 5));
                    // no, select a RAM bank
                    } else {
                        setRAMBank(data & 0x03);
                    }
                    break;
                }
                case 0x6000:
                    this.isROMBankingMode = (data & 1) == 0;
                    break;
                case RAM_BANK_AREA:
                    if (this.areRAMWritesEnabled) {
                        gameboy.getCPU().memory[adr] = data;
                        ramBanks[this.currentRAMBank][adr & (RAM_BANK_SIZE - 1)] = data;
                    }
                    break;
            }
        }
    }

    /**
     * Implements cartridge writes to MBC2 type cartridges
     */
    class MBC2CartridgeImpl extends MBC1CartridgeImpl {

        public void writeByte(final int adr, final byte data) {
            switch (adr & 0xe000) {
                // RAM enabling works only if the least significant bit of the upper address byte is not set
                case 0x0000:
                    if ((adr & 0x100) == 0) {
                        super.writeByte(adr, data);
                    }
                    this.areRAMWritesEnabled = (data & 0x0f) == 0x0a;
                    break;
                // writing to $2000-$3fff specifies the lower 4 bits of the ROM bank number, but only
                // if the least significant bit of the upper address byte is set
                case 0x2000: {
                    if ((adr & 0x100) != 0) {
                        super.writeByte(adr, (byte) (data & 0x0f));
                    }
                    break;
                }
                default:
                    super.writeByte(adr, data);
            }
        }
    }

    /**
     * Implements cartridge writes to MBCC type cartridges
     */
    class MBC3CartridgeImpl extends MBC1CartridgeImpl {

        /**
         * index of the RTC seconds register
         */
        private final static int SECONDS = 0x00;
        /**
         * index of the RTC minutes register
         */
        private final static int MINUTES = 0x01;
        /**
         * index of the RTC hours register
         */
        private final static int HOURS = 0x02;
        /**
         * index of the RTC register with the lower 8 bits of the day counter
         */
        private final static int DAYS_LOW = 0x03;
        /**
         * index of the RTC register with the higher bits of the day counter
         */
        private final static int DAYS_HIGH = 0x04;
        /**
         * real time clock
         */
        private final Date clock;
        /**
         * last CPY cycle when we updated the clock
         */
        private long lastRTCUpdate = 0;
        /**
         * RTC clock counter registers
         */
        private int[] rtc = new int[5];
        /**
         * currently active RTC register
         */
        private int rtcIndex = -1;
        /**
         * latch clock data on next write to $6000-$7fff?
         */
        private boolean latchRTC = false;
        /**
         * is the clock still running?
         */
        private boolean isClockActive = true;
        /**
         * speed of the Gameboy CPU, used when updating the clock
         */
        private long cpuSpeed = Gameboy.ORIGINAL_SPEED_CLASSIC;

        /**
         * Create a new MBC3CartridgeImpl setting the RTC of the cartridge to the current date
         */
        protected MBC3CartridgeImpl() {
            // we are always in RAM-banking mode
            this.isROMBankingMode = false;

            // copy system clock time to GB clock time
            this.clock = new Date();

            // keep the system clock time?
            if (DEFAULT_RTC_WITH_SYSTEM_CLOCK) {
                // we normalize to the range of 1 year
                final Calendar calendar = Calendar.getInstance();

                calendar.set(Calendar.YEAR, 1970);
                this.clock.setTime(calendar.getTime().getTime());
            } else {
                // no, we start at 0
                this.clock.setTime(0);
            }
        }

        public void writeByte(final int adr, final byte data) {
            switch (adr & 0xe000) {
                // writing to $2000-$3fff specifies the ROM bank number
                case 0x2000: {
                    setROMBank(Math.max(1, data & 0x7f));
                    break;
                }
                // writing a value of $0-$3 maps the corresponding RAM bank, $8-$c is for accessing the RTC
                case ROM_BANK_AREA:
                    if (data >= 0x08 && data <= 0x0c) {
                        // set active RTC register
                        this.rtcIndex = data - 0x08;

                        // map this register to $a000-$bfff
                        final byte[] memory_ = gameboy.getCPU().memory;

                        memory_[RAM_BANK_AREA] = (byte) this.rtc[this.rtcIndex];
                        for (int len = 1; len < RAM_BANK_SIZE; len <<= 1) {
                            System.arraycopy(memory_, RAM_BANK_AREA, memory_, RAM_BANK_AREA + len, len);
                        }

                        // we currently don't have a RAM bank active
                        this.currentRAMBank = -1;
                    } else {
                        // we currently don't have the RTC active
                        this.rtcIndex = -1;
                        // we active the requested RAM bank
                        super.writeByte(adr, (byte) (data & 0x03));
                    }
                    break;
                // used for reading the RTC registers
                case 0x6000:
                    if (this.latchRTC && data == 0x01) {
                        latchClock();
                        this.latchRTC = false;
                    } else if (data == 0x00) {
                        this.latchRTC = true;
                    } else {
                        this.latchRTC = false;
                    }
                    break;
                case RAM_BANK_AREA:
                    // write to the RTC or to RAM, depending on data
                    switch (this.rtcIndex) {
                        case SECONDS:
                            updateClock();
                            this.clock.setTime(this.clock.getTime() + ((data & 0xff) - getRTCSeconds()) * 1000);
                            break;
                        case MINUTES:
                            updateClock();
                            this.clock.setTime(this.clock.getTime() + ((data & 0xff) - getRTCMinutes()) * 1000 * 60);
                            break;
                        case HOURS:
                            updateClock();
                            this.clock.setTime(this.clock.getTime() + ((data & 0xff) - getRTCHours()) * 1000 * 60 * 60);
                            break;
                        case DAYS_LOW:
                            updateClock();
                            this.clock.setTime(this.clock.getTime() + ((data & 0xff) - (getRTCDays() % 0x100)) * 1000l * 60 * 60 * 24);
                            latchClock();
                            break;
                        case DAYS_HIGH: {
                            updateClock();

                            final int days = (getRTCDays() % 0x100) + ((data & 0x01) != 0 ? 0x100 : 0) + ((data & 0x80) != 0 ? 0x200 : 0);

                            this.clock.setTime(this.clock.getTime() + (days - getRTCDays()) * 1000l * 60 * 60 * 24);
                            this.isClockActive = (data & 0x40) == 0;
                            break;
                        }
                        default:
                            super.writeByte(adr, data);
                    }
                    break;
                default:
                    super.writeByte(adr, data);
            }
        }

        /**
         * Is the RTC clock currently running
         * 
         * @return  true if it is running, false if it is stopped
         */
        private boolean isClockActive() {
            return this.isClockActive;
        }

        /**
         * Get the seconds from the RTC
         * 
         * @return  0-59
         */
        private int getRTCSeconds() {
            return (int) ((this.clock.getTime() / 1000) % 60);
        }

        /**
         * Get the minutes from the RTC
         * 
         * @return  0-59
         */
        private int getRTCMinutes() {
            return (int) ((this.clock.getTime() / 1000 / 60) % 60);
        }

        /**
         * Get the hours from the RTC
         * 
         * @return  0-23
         */
        private int getRTCHours() {
            return (int) ((this.clock.getTime() / 1000 / 60 / 60) % 24);
        }

        /**
         * Get the days from the RTC
         * 
         * @return  value >= 0
         */
        private int getRTCDays() {
            return (int) (this.clock.getTime() / 1000 / 60 / 60 / 24);
        }

        /**
         * Update the RTC registers with the current time
         */
        private void latchClock() {
            updateClock();

            this.rtc[SECONDS] = getRTCSeconds();
            this.rtc[MINUTES] = getRTCMinutes();
            this.rtc[HOURS] = getRTCHours();
            this.rtc[DAYS_LOW] = getRTCDays() % 0x100;
            this.rtc[DAYS_HIGH] = ((getRTCDays() % 0x200) >> 8) + (isClockActive() ? 0 : 0x40) + (getRTCDays() >= 0x200 ? 0x80 : 0);
        }

        /**
         * Update the clock to the current time
         */
        private void updateClock() {
            if (isClockActive()) {
                if (SYNCHRONIZE_RTC_WITH_CPU) {
                    // we calculate the emulator time that has passed...
                    final CPU cpu = gameboy.getCPU();
                    final long passedMillis = (cpu.getCycles() - this.lastRTCUpdate) * 1000 / this.cpuSpeed;

                    // ...and add this time to the clock
                    this.clock.setTime(this.clock.getTime() + passedMillis);
                    this.lastRTCUpdate = cpu.getCycles();
                } else {
                    // we determine the time that has passed...
                    final long now = new Date().getTime();
                    final long passedMillis = now - this.lastRTCUpdate;

                    // ...and add this time to the clock
                    this.clock.setTime(this.clock.getTime() + passedMillis);
                    this.lastRTCUpdate = now;
                }
            }
        }

        public void serialize(final DataOutputStream out) throws IOException {
            // update the clock before saving, so that we don't have to save the time of the last update because it happened "now"
            updateClock();

            // do normal serialization
            super.serialize(out);
            out.writeLong(this.clock.getTime());
            out.writeBoolean(this.latchRTC);
            out.writeBoolean(this.isClockActive);
            SerializationUtils.serialize(out, this.rtc);
            out.writeLong(System.currentTimeMillis());
        }

        public void deserialize(final DataInputStream in) throws IOException {
            // the last RTC update had happened just before hibernating i.e. "now"
            if (SYNCHRONIZE_RTC_WITH_CPU) {
                this.lastRTCUpdate = gameboy.getCPU().getCycles();
            } else {
                this.lastRTCUpdate = new Date().getTime();
            }

            // do normal deserialization
            super.deserialize(in);
            this.clock.setTime(in.readLong());
            this.latchRTC = in.readBoolean();
            this.isClockActive = in.readBoolean();
            SerializationUtils.deserialize(in, this.rtc);

            // adjust the RTC time according to the time that has passed
            final long oldTime = in.readLong();

            this.clock.setTime(this.clock.getTime() + System.currentTimeMillis() - oldTime);
        }

        // implementation of the Observer interface
        public void update(final Object observed, final Object arg) {
            // we get informed about a new CPU speed?
            if (observed == gameboy.getCPU() && arg instanceof Long) {
                // then update the clock using the old speed and then set the new speed for later calculations
                updateClock();
                this.cpuSpeed = ((Long) arg).longValue();
            }
        }
    }

    /**
     * Implements cartridge writes to MBC5 type cartridges
     */
    class MBC5CartridgeImpl extends MBC1CartridgeImpl {

        public void writeByte(final int adr, final byte data) {
            switch (adr & 0xe000) {
                // writing to $2000-$3fff specifies the ROM bank number
                case 0x2000: {
                    int romBank = this.currentROMBank;

                    if ((adr & 0x1000) != 0) {
                        romBank = (romBank & 0xff) | ((data & 1) != 0 ? 0x100 : 0);
                    } else {
                        romBank = (romBank & 0x100) | (data & 0xff);
                    }
                    setROMBank(romBank);
                    break;
                }
                case ROM_BANK_AREA:
                    if (ramSize > 0) {
                        setRAMBank(data & 0x0f);
                    }
                    break;
                default:
                    super.writeByte(adr, data);
            }
        }
    }

    // implementation of the Observer interface
    public void update(final Object observed, final Object arg) {
        // then notify the implementation class about the new speed
        this.cartridgeImpl.update(observed, arg);
    }
}
