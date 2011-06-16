/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation. For the full
 * license text, see http://www.gnu.org/licenses/gpl.html.
 */
package de.joergjahnke.gameboy.core;

import de.joergjahnke.common.emulation.RunnableDevice;
import de.joergjahnke.common.emulation.ThrottleableCPU;
import de.joergjahnke.common.io.Serializable;
import de.joergjahnke.common.io.SerializationUtils;
import de.joergjahnke.common.util.Logger;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Implementation of the core functionality of the Gameboy CPU.<br>
 * <br>
 * A good documentation on the Gameboy CPU can be found at <a href='http://marc.rawer.de/Gameboy/Docs/GBCPUman.pdf'>http://marc.rawer.de/Gameboy/Docs/GBCPUman.pdf</a>.<br>
 * Some more information on the CPU opcodes can be found at <a href='http://www.devrs.com/gb/files/opcodes.html'>http://www.devrs.com/gb/files/opcodes.html</a>.<br>
 * Good documentation on the different Memory Bank Controllers (MBCs) can also be found at <a href='http://verhoeven272.nl/fruttenboel/Gameboy/pandocs.html.gz'>http://verhoeven272.nl/fruttenboel/Gameboy/pandocs.html.gz</a>.<br>
 * More helpful information can be found in the <a href='http://www.devrs.com/gb/files/faqs.html'>Gameboy FAQ</a><br>.
 *
 * @author Joerg Jahnke (joergjahnke@users.sourceforge.net)
 */
public class CPU extends RunnableDevice implements ThrottleableCPU, Serializable {

    /**
     * Do we need debug information about the executed code?
     */
    private final static boolean DEBUG_CODE = false;
    /**
     * Do we need debug information about interrupts?
     */
    private final static boolean DEBUG_INTERRUPTS = false;
    /**
     * Do we need debug information about interrupts?
     */
    private final static boolean DEBUG_DMA = false;
    /**
     * execute all HDMA transfers as if they were General Purpose HDMAs i.e. in one step
     */
    private final static boolean IMMEDIATE_HDMA = false;
    /**
     * Size of a GBC WRAM bank is 4k
     */
    private final static int WRAM_BANK_SIZE = 0x1000;
    /**
     * Total size of the WRAM is 8k
     */
    private static final int WRAM_SIZE = 0x2000;
    /**
     * memory location of the video RAM
     */
    private static final int VRAM_AREA = 0x8000;
    /**
     * memory location of the switchable RAM bank
     */
    private static final int RAM_BANK_AREA = 0xa000;
    /**
     * memory location of the work RAM
     */
    private static final int WRAM_AREA = 0xc000;
    /**
     * memory location of the switchable WRAM area of the GBC
     */
    private static final int SWITCHABLE_WRAM_AREA = 0xd000;
    /**
     * memory location of the Echo RAM
     */
    private static final int ECHO_RAM_AREA = 0xe000;
    /**
     * memory location of the High RAM
     */
    private static final int HIGH_RAM_AREA = 0xfe00;
    /**
     * memory location of the IO area
     */
    private static final int IO_AREA = 0xff00;
    // flags
    private final static int ZERO = 0x80;
    private final static int NEGATIVE = 0x40;
    private final static int HALFCARRY = 0x20;
    private final static int CARRY = 0x10;
    // some registers
    private static final int JOYPAD_PORT = 0xff00;
    private static final int INTERRUPT_FLAG = 0xff0f;
    private static final int HDMA_SOURCE_HIGH = 0xff51;
    private static final int HDMA_SOURCE_LOW = 0xff52;
    private static final int HDMA_DEST_HIGH = 0xff53;
    private static final int HDMA_DEST_LOW = 0xff54;
    private static final int HDMA_CONTROL = 0xff55;
    private static final int LCD_CONTROL = 0xff40;
    private static final int LCD_STATUS = 0xff41;
    private static final int LCD_LINE = 0xff44;
    private static final int SPEED_SWITCH = 0xff4d;
    private static final int INTERRUPT_ENABLE = 0xffff;
    // IRQs
    /**
     * V-Blank interrupt, occurs once the video chip reaches the V-Blank area, i.e. lines 144-153
     */
    public final static int IRQ_VBLANK = 1 << 0;
    /**
     * LCD status interrupt, occurs on special video chip events
     */
    public final static int IRQ_LCDSTAT = 1 << 1;
    /**
     * timer interrupt
     */
    public final static int IRQ_TIMER = 1 << 2;
    /**
     * serial interrupt, not implemented
     */
    public final static int IRQ_SERIAL = 1 << 3;
    /**
     * joypad interrupt, requested when buttons or the joystick get pressed
     */
    public final static int IRQ_JOYPAD = 1 << 4;
    /**
     * H-Blank IRQ vector
     */
    private static final int IRQ_VECTOR_VBLANK = 0x40;
    /**
     * Joypad IRQ vector
     */
    private static final int IRQ_VECTOR_JOYPAD = 0x60;
    /**
     * here we can read the address where to start the emulation
     */
    private final static int RESET_VECTOR = 0x0100;
    /**
     * device the CPU works for
     */
    protected final Gameboy gameboy;
    /**
     * the Gameboy's video chip, we need this quite often, so we keep it locally here
     */
    private VideoChip video;
    /**
     * the Gameboy's sound chip, we need this quite often, so we keep it locally here
     */
    private SoundChip sound;
    /**
     * the cartridge attached to the device
     */
    protected final Cartridge cartridge;
    /**
     * RAM and ROM memory
     */
    protected final byte memory[];
    /**
     * used for warnings, errors and information
     */
    protected Logger logger;
    /**
     * the number of cycles we emulated
     */
    private long cycles = 0;
    /**
     * number of milliseconds we were throttled since last reset of this counter
     */
    private long throttledMillis = 0;
    /**
     * the 8-bit registers A-F
     */
    private int a,  b = 0,  c = 0x13,  d = 0,  e = 0xd8,  f = 0xb0;
    /**
     * the 16-bit registers HL and SP
     */
    private int hl = 0x014d,  sp = 0xfffe;
    /**
     * the program counter
     */
    private int pc = RESET_VECTOR;
    /**
     * are interrupts enabled?
     */
    private boolean isInterruptEnabled = false;
    /**
     * will interrupts enable state switch after the next operation?
     */
    private boolean doesInterruptEnableSwitch = false;
    /**
     * denotes requested interrupts
     */
    private int irqsRequested = 0;
    /**
     * denotes enabled interrupts
     */
    private int irqsEnabled = 0;
    /**
     * Currently active 4k GBC WRAM bank in memory $d000-$dfff
     */
    private int currentGBCRAMBank = 1;
    /**
     * switchable GBC WRAM banks 1-7
     */
    private final byte[][] gbcRAMBanks = new byte[7][0x1000];
    /**
     * internal timer
     */
    private final Timer timer = new Timer();
    /**
     * CPU cycle of next IRQ request or other update event
     */
    private long nextEvent = 0;
    /**
     * CPU speed
     */
    private int cpuSpeed;
    /**
     * CPU instructions per Timer DIV increase
     */
    private final int instructionsPerDiv;
    /**
     * is the sound activated
     */
    private boolean isSoundOn;
    /**
     * do we have a listener for the sound?
     */
    private boolean hasSoundListener;
    /**
     * is a HDMA transfer in progress?
     */
    private boolean isHDMARunning = false;

    /**
     * Create a new Gameboy CPU
     * 
     * @param	gameboy	Gameboy instance we belong to
     * @param	cartridge	cartridge with data to load into memory
     */
    public CPU(final Gameboy gameboy, final Cartridge cartridge) {
        this.gameboy = gameboy;
        this.cartridge = cartridge;

        // reserve 64k of memory
        this.memory = new byte[0x10000];

        // copy ROM banks 0-1 into memory at $0000-$7fff
        System.arraycopy(cartridge.getROMBanks()[ 0], 0, this.memory, 0x0000, Cartridge.ROM_BANK_SIZE);
        System.arraycopy(cartridge.getROMBanks()[ 1], 0, this.memory, Cartridge.ROM_BANK_SIZE, Cartridge.ROM_BANK_SIZE);
        // copy RAM bank 0 into memory at $a000-$bfff
        System.arraycopy(cartridge.getRAMBanks()[ 0], 0, this.memory, RAM_BANK_AREA, Cartridge.RAM_BANK_SIZE);

        // do some initializations depending on the Gameboy mode we are running
        if (cartridge.isGBC()) {
            this.a = 0x11;
        } else {
            this.a = 0x01;
        }

        // set speed
        this.cpuSpeed = Gameboy.ORIGINAL_SPEED_CLASSIC;
        this.instructionsPerDiv = this.cpuSpeed / 16384;
    }

    /**
     * Get the number of CPU cycles that were processed
     * 
     * @return	number of cycles
     */
    public final long getCycles() {
        return this.cycles;
    }

    /**
     * Request an interrupt.
     * The interrupt is only executed if it is also enabled.
     * 
     * @param   irq the IRQ to request
     */
    public final void requestIRQ(final int irq) {
        if (DEBUG_INTERRUPTS) {
            System.out.println("Requested IRQ $" + Integer.toHexString(irq) + " at cycle " + this.cycles);
        }
        this.irqsRequested |= irq;
    }

    /**
     * Check for timer, video chip and sound chip events
     */
    private void checkEvents() {
        final long cycles_ = this.cycles;
        final VideoChip video_ = this.video;

        // a timer request?
        if (cycles_ >= this.timer.getNextIRQRequest()) {
            // then trigger IRQ and restart timer
            requestIRQ(IRQ_TIMER);
            this.timer.restart();
        }

        // the video chip needs to be updated?
        if (cycles_ >= video_.getNextUpdate()) {
            // then update the video chip
            video_.update(cycles_);
            // continue HDMA if necessary
            if (isHDMARunning() && video_.getLCDLine() < VideoChip.SCREEN_HEIGHT && video_.getVideoMode() == VideoChip.MODE_HBLANK) {
                performHDMA(1);
            }
        }

        this.nextEvent = Math.min(this.timer.getNextIRQRequest(), video_.getNextUpdate());

        // sound is also active?
        if (this.isSoundOn && this.hasSoundListener) {
            // another sound sample series needs to be created?
            if (cycles_ >= this.sound.getNextUpdate()) {
                this.sound.update(cycles_);
            }

            // the next event might also be triggered by the sound chip
            this.nextEvent = Math.min(this.nextEvent, this.sound.getNextUpdate());
        }
    }

    /**
     * Is a HDMA transfer still running
     * 
     * @return  true if we have a running HDMA transfer
     */
    private boolean isHDMARunning() {
        return this.isHDMARunning;
    }

    /**
     * Perform HDMA transfer
     * 
     * @param   len number of bytes to transfer
     */
    private void performHDMA(final int blocks) {
        // copy given number of 16 byte blocks to VRAM
        final int source = ((this.memory[HDMA_SOURCE_HIGH] & 0xff) << 8) + (this.memory[HDMA_SOURCE_LOW] & 0xf0);
        final int dest = ((this.memory[HDMA_DEST_HIGH] & 0x1f) << 8) + (this.memory[HDMA_DEST_LOW] & 0xf0);

        if (DEBUG_DMA) {
            System.out.println("HDMA copy of " + blocks + " block(s) from $" + Integer.toHexString(source) + " to VRAM $" + Integer.toHexString(dest));
        }

        final VideoChip vc = this.video;

        for (int i = 0, len = blocks << 4; i < len; ++i) {
            vc.writeByte((dest + i) & 0x1fff, (byte) readByte(source + i));
        }

        // reduce number of remaining blocks, a value of $ff indicates that we are finished
        this.memory[HDMA_CONTROL] -= blocks;
        this.isHDMARunning = (this.memory[HDMA_CONTROL] & 0xff) != 0xff;

        // adjust source and destination address if we have to continue the transfer
        if (isHDMARunning()) {
            this.memory[HDMA_SOURCE_HIGH] = (byte) ((source + (blocks << 4)) >> 8);
            this.memory[HDMA_SOURCE_LOW] = (byte) ((source + (blocks << 4)) & 0xff);
            this.memory[HDMA_DEST_HIGH] = (byte) ((dest + (blocks << 4)) >> 8);
            this.memory[HDMA_DEST_LOW] = (byte) ((dest + (blocks << 4)) & 0xff);
        }
    }

    /**
     * Initialize local variables for video chip and sound chip
     */
    private void initialize() {
        // we need to retrieve the video and sound chip now, we use it as class variable to avoid access via e.g. gameboy.getVideoChip everytime
        this.video = this.gameboy.getVideoChip();
        this.sound = this.gameboy.getSoundChip();
        this.hasSoundListener = this.sound.countObservers() > 0;
    }

    /**
     * Power up the game boy, setting some values in the IO area
     */
    public void powerUp() {
        initialize();

        // set some IO area default values
        writeByte(INTERRUPT_FLAG, (byte) 0x01);
        /*writeByte(0xff10, (byte) 0x80);
        writeByte(0xff11, (byte) 0xbf);
        writeByte(0xff12, (byte) 0xf3);
        writeByte(0xff14, (byte) 0xbf);
        writeByte(0xff16, (byte) 0x3f);
        writeByte(0xff19, (byte) 0xbf);
        writeByte(0xff1a, (byte) 0x7f);
        writeByte(0xff1b, (byte) 0xff);
        writeByte(0xff1c, (byte) 0x9f);
        writeByte(0xff1e, (byte) 0xbf);
        writeByte(0xff20, (byte) 0xff);*/
        writeByte(0xff23, (byte) 0xbf);
        writeByte(0xff24, (byte) 0x77);
        writeByte(0xff25, (byte) 0xf3);
        writeByte(0xff26, (byte) 0xf1);
        writeByte(LCD_CONTROL, (byte) 0x91);
        writeByte(0xff47, (byte) 0xfc);
        writeByte(0xff48, (byte) 0xff);
        writeByte(0xff49, (byte) 0xff);
        this.memory[HDMA_CONTROL] = (byte) 0xff;
    }

    /**
     * Enable/disable interrupts
     * 
     * @param   isInterruptEnabled  true to enable all interrupts, false to disable
     */
    private void setInterruptEnabled(final boolean isInterruptEnabled) {
        if (DEBUG_INTERRUPTS && isInterruptEnabled != this.isInterruptEnabled) {
            System.out.println((isInterruptEnabled ? "Enable" : "Disable") + " interrupts at cycle " + this.cycles);
        }
        this.isInterruptEnabled = isInterruptEnabled;
    }

    /**
     * Set high byte of register HL
     * 
     * @param	data	byte to set
     */
    private void setH(final int data) {
        this.hl &= 0x00ff;
        this.hl |= (data << 8);
    }

    /**
     * Set low byte of register HL
     * 
     * @param	data	byte to set
     */
    private void setL(final int data) {
        this.hl &= 0xff00;
        this.hl |= data;
    }

    /**
     * Push a byte onto the stack and decrease the stack pointer
     * 
     * @param   data    byte to save
     */
    private void pushByte(final int data) {
        this.sp = (this.sp - 1) & 0xffff;
        writeByte(this.sp, (byte) data);
    }

    /**
     * Push a word onto the stack and decrease the stack pointer
     * 
     * @param   data    word to save
     */
    private void pushWord(final int data) {
        this.sp = (this.sp - 2) & 0xffff;
        writeByte(this.sp, (byte) (data & 0xff));
        writeByte(this.sp + 1, (byte) (data >> 8));
    }

    /**
     * Retrieve a byte from the stack and increase the stack pointer
     * 
     * @return  byte from the stack
     */
    private int popByte() {
        final int result = readByte(this.sp);

        this.sp = (this.sp + 1) & 0xffff;

        return result;
    }

    /**
     * Retrieve a word from the stack and increase the stack pointer
     * 
     * @return  byte from the stack
     */
    private int popWord() {
        final int result = readByte(this.sp) + (readByte(this.sp + 1) << 8);

        this.sp = (this.sp + 2) & 0xffff;

        return result;
    }

    /**
     * Add without carry
     * 
     * @param	data	data to add to register A
     */
    private void operationADD(final int data) {
        final int value = (this.a + data) & 0xff;

        this.f = ((value == 0 ? ZERO : 0) + ((this.a & 0x0f) + (data & 0x0f) >= 0x10 ? HALFCARRY : 0) + (value < this.a ? CARRY : 0));
        this.a = value;
    }

    /**
     * 16bit add
     * 
     * @param	data	data to add to register HL
     */
    private void operationADD16(final int data) {
        final int value = this.hl + data;

        this.f &= ZERO;
        this.f |= (value >= 0x10000 ? CARRY : 0);
        this.f |= ((this.hl & 0x0fff) + (data & 0x0fff) >= 0x1000 ? HALFCARRY : 0);
        this.hl = value & 0xffff;
    }

    /**
     * Add with carry
     * 
     * @param	data	data to add to register A
     */
    private void operationADC(final int data) {
        final int value = this.a + data + ((this.f & CARRY) != 0 ? 1 : 0);

        this.f = (((value & 0xff) == 0 ? ZERO : 0) + ((this.a & 0x0f) + (data & 0x0f) >= 0x10 ? HALFCARRY : 0) + (value >= 0x100 ? CARRY : 0));
        this.a = (value & 0xff);
    }

    /**
     * Subtract without carry
     * 
     * @param	data	data to subtract from register A
     */
    private void operationSUB(final int data) {
        final int value = (this.a - data) & 0xff;

        this.f = ((value == 0 ? ZERO : 0) + NEGATIVE + ((this.a & 0x0f) < (data & 0x0f) ? HALFCARRY : 0) + (value > this.a ? CARRY : 0));
        this.a = value;
    }

    /**
     * Subtract with carry
     * 
     * @param	data	data to subtract from register A
     */
    private void operationSBC(final int data) {
        final int value = this.a - data - ((this.f & CARRY) != 0 ? 1 : 0);

        this.f = (((value & 0xff) == 0 ? ZERO : 0) + NEGATIVE + ((this.a & 0x0f) < (data & 0x0f) ? HALFCARRY : 0) + (value < 0 ? CARRY : 0));
        this.a = (value & 0xff);
    }

    /**
     * Logical AND
     * 
     * @param	data	data to combine with register A
     */
    private void operationAND(final int data) {
        this.a &= data;
        this.f = HALFCARRY + (this.a == 0 ? ZERO : 0);
    }

    /**
     * Logical OR
     * 
     * @param	data	data to combine with register A
     */
    private void operationOR(final int data) {
        this.a |= data;
        this.f = (this.a == 0 ? ZERO : 0);
    }

    /**
     * Logical XOR
     * 
     * @param	data	data to combine with register A
     */
    private void operationXOR(final int data) {
        this.a ^= data;
        this.f = (this.a == 0 ? ZERO : 0);
    }

    /**
     * Compare with register A
     * 
     * @param	data	data to compare with register A
     */
    private void operationCP(final int data) {
        this.f = ((this.a == data ? ZERO : 0) + NEGATIVE + ((this.a & 0x0f) < (data & 0x0f) ? HALFCARRY : 0) + (this.a < data ? CARRY : 0));
    }

    /**
     * Increase register / byte in memory
     * 
     * @param	data	data to increase
     * @return	increased value
     */
    private int operationINC(final int data) {
        final int value = (data + 1) & 0xff;

        this.f &= CARRY;
        this.f |= (value == 0 ? ZERO : 0);
        this.f |= ((value & 0x0f) == 0 ? HALFCARRY : 0);

        return value;
    }

    /**
     * Decrease register / byte in memory
     * 
     * @param	data	data to decrease
     * @return	decreased value
     */
    private int operationDEC(final int data) {
        final int value = (data - 1) & 0xff;

        this.f &= CARRY;
        this.f |= NEGATIVE;
        this.f |= (value == 0 ? ZERO : 0);
        this.f |= ((value & 0x0f) == 0x0f ? HALFCARRY : 0);

        return value;
    }

    /**
     * Swap nibbles of operand
     * 
     * @param	data	byte where to swap nibbles
     * @return	swapped byte
     */
    private int operationSWAP(final int data) {
        final int value = (data >> 4) | ((data & 0x0f) << 4);

        this.f = (value == 0 ? ZERO : 0);

        return value;
    }

    /**
     * Shift left into carry, LSB is set to 0
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationSLA(final int data) {
        final int value = (data << 1) & 0xff;

        this.f = ((value == 0 ? ZERO : 0) + (data >= 0x80 ? CARRY : 0));

        return value;
    }

    /**
     * Rotate left with highest bit re-appearing as lowest bit
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationRLC(final int data) {
        final int value = ((data << 1) + (data >> 7)) & 0xff;

        this.f = ((value == 0 ? ZERO : 0) + (data >= 0x80 ? CARRY : 0));

        return value;
    }

    /**
     * Rotate left with carry
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationRL(final int data) {
        final int value = ((data << 1) + ((this.f & CARRY) != 0 ? 1 : 0)) & 0xff;

        this.f = ((value == 0 ? ZERO : 0) + (data >= 0x80 ? CARRY : 0));

        return value;
    }

    /**
     * Shift right into carry, MSB is set to 0
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationSRL(final int data) {
        final int value = data >> 1;

        this.f = ((value == 0 ? ZERO : 0) + ((data & 0x01) != 0 ? CARRY : 0));

        return value;
    }

    /**
     * Shift right without carry, MSB doesn't change
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationSRA(final int data) {
        final int value = (data >> 1) | (data & 0x80);

        this.f = ((value == 0 ? ZERO : 0) + ((data & 0x01) != 0 ? CARRY : 0));

        return value;
    }

    /**
     * Rotate right with lowest bit re-appearing as highest bit
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationRRC(final int data) {
        final int value = (data >> 1) + ((data & 1) != 0 ? 0x80 : 0);

        this.f = ((value == 0 ? ZERO : 0) + ((data & 0x01) != 0 ? CARRY : 0));

        return value;
    }

    /**
     * Rotate right with carry
     * 
     * @param	data	data to shift
     * @return	shifted data
     */
    private int operationRR(final int data) {
        final int value = (data >> 1) + ((this.f & CARRY) != 0 ? 0x80 : 0);

        this.f = ((value == 0 ? ZERO : 0) + ((data & 0x01) != 0 ? CARRY : 0));

        return value;
    }

    /**
     * Test bit and set flags accordingly
     * 
     * @param	data	data to shift
     * @param	bit	bit to test
     */
    private void operationBIT(final int data, final int bit) {
        this.f &= CARRY;
        this.f |= (((data & (1 << bit)) == 0 ? ZERO : 0) + HALFCARRY);
    }

    /**
     * Call of a subroutine.
     * The address to jump to is read from the program, the old address is put to the stack
     * and the new address is loaded into the program counter.
     */
    private void operationCALL() {
        final int newPC = (this.memory[this.pc] & 0xff) + ((this.memory[this.pc + 1] & 0xff) << 8);

        this.pc += 2;
        operationCALL(newPC);
    }

    /**
     * Call of a subroutine.
     * Pushes the current program counter onto the stack and jumps to the new address.
     * 
     * @param address   address to jump to
     */
    private void operationCALL(final int address) {
        pushWord(this.pc);
        this.pc = address;
    }

    /**
     * Enable interrupts and then wait until the next interrupt occurs
     */
    private void operationHALT() {
        setInterruptEnabled(true);
        // wait until the next interrupt occurs
        while ((this.irqsRequested & this.irqsEnabled) == 0) {
            this.cycles = this.nextEvent;
            checkEvents();
        }
        // wake up needs 16 CPU cycles
        this.cycles += 16;
    }

    /**
     * Run the CPU until stopped
     */
    public final void run() {
        super.run();

        initialize();

        // cache memory variable for faster access
        final byte[] memory_ = this.memory;
        int opCode;

        // run until the CPU stops
        while (this.isRunning) {
            while (!this.isPaused) {
                // fetch next instruction
                opCode = memory_[this.pc++] & 0xff;

                if (DEBUG_CODE) {
                    System.out.print("pc=" + Integer.toHexString(pc - 1) + ", ins=" + Integer.toHexString(opCode) + ", next:" + Integer.toHexString(readByte(pc)) + "," + Integer.toHexString(readByte(pc + 1)) + ", flags=" + Integer.toBinaryString(f));
                    System.out.print("; a=" + Integer.toHexString(a) + ", b=" + Integer.toHexString(b) + ", c=" + Integer.toHexString(c) + ", d=" + Integer.toHexString(d) + ", e=" + Integer.toHexString(e) + ", hl=" + Integer.toHexString(hl) + ", sp=" + Integer.toHexString(sp));
                    System.out.println();
                }

                switch (opCode) {
                    // 8bit load operations
                    case 0x06:
                        this.b = memory_[this.pc++] & 0xff;
                        this.cycles += 8;
                        break;
                    case 0x0e:
                        this.c = memory_[this.pc++] & 0xff;
                        this.cycles += 8;
                        break;
                    case 0x16:
                        this.d = memory_[this.pc++] & 0xff;
                        this.cycles += 8;
                        break;
                    case 0x1e:
                        this.e = memory_[this.pc++] & 0xff;
                        this.cycles += 8;
                        break;
                    case 0x26:
                        setH(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;
                    case 0x2e:
                        setL(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;
                    case 0x36:
                        writeByte(this.hl, memory_[this.pc++]);
                        this.cycles += 12;
                        break;
                    case 0x3e:
                        this.a = memory_[this.pc++] & 0xff;
                        this.cycles += 8;
                        break;

                    case 0x40:
                        this.cycles += 4;
                        break;
                    case 0x41:
                        this.b = this.c;
                        this.cycles += 4;
                        break;
                    case 0x42:
                        this.b = this.d;
                        this.cycles += 4;
                        break;
                    case 0x43:
                        this.b = this.e;
                        this.cycles += 4;
                        break;
                    case 0x44:
                        this.b = (this.hl >> 8);
                        this.cycles += 4;
                        break;
                    case 0x45:
                        this.b = (this.hl & 0xff);
                        this.cycles += 4;
                        break;
                    case 0x46:
                        this.b = readByte(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x47:
                        this.b = this.a;
                        this.cycles += 4;
                        break;

                    case 0x48:
                        this.c = this.b;
                        this.cycles += 4;
                        break;
                    case 0x49:
                        this.cycles += 4;
                        break;
                    case 0x4a:
                        this.c = this.d;
                        this.cycles += 4;
                        break;
                    case 0x4b:
                        this.c = this.e;
                        this.cycles += 4;
                        break;
                    case 0x4c:
                        this.c = (this.hl >> 8);
                        this.cycles += 4;
                        break;
                    case 0x4d:
                        this.c = (this.hl & 0xff);
                        this.cycles += 4;
                        break;
                    case 0x4e:
                        this.c = readByte(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x4f:
                        this.c = this.a;
                        this.cycles += 4;
                        break;

                    case 0x50:
                        this.d = this.b;
                        this.cycles += 4;
                        break;
                    case 0x51:
                        this.d = this.c;
                        this.cycles += 4;
                        break;
                    case 0x52:
                        this.cycles += 4;
                        break;
                    case 0x53:
                        this.d = this.e;
                        this.cycles += 4;
                        break;
                    case 0x54:
                        this.d = (this.hl >> 8);
                        this.cycles += 4;
                        break;
                    case 0x55:
                        this.d = (this.hl & 0xff);
                        this.cycles += 4;
                        break;
                    case 0x56:
                        this.d = readByte(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x57:
                        this.d = this.a;
                        this.cycles += 4;
                        break;

                    case 0x58:
                        this.e = this.b;
                        this.cycles += 4;
                        break;
                    case 0x59:
                        this.e = this.c;
                        this.cycles += 4;
                        break;
                    case 0x5a:
                        this.e = this.d;
                        this.cycles += 4;
                        break;
                    case 0x5b:
                        this.cycles += 4;
                        break;
                    case 0x5c:
                        this.e = (this.hl >> 8);
                        this.cycles += 4;
                        break;
                    case 0x5d:
                        this.e = (this.hl & 0xff);
                        this.cycles += 4;
                        break;
                    case 0x5e:
                        this.e = readByte(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x5f:
                        this.e = this.a;
                        this.cycles += 4;
                        break;

                    case 0x60:
                        setH(this.b);
                        this.cycles += 4;
                        break;
                    case 0x61:
                        setH(this.c);
                        this.cycles += 4;
                        break;
                    case 0x62:
                        setH(this.d);
                        this.cycles += 4;
                        break;
                    case 0x63:
                        setH(this.e);
                        this.cycles += 4;
                        break;
                    case 0x64:
                        this.cycles += 4;
                        break;
                    case 0x65:
                        setH((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0x66:
                        setH(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x67:
                        setH(this.a);
                        this.cycles += 4;
                        break;

                    case 0x68:
                        setL(this.b);
                        this.cycles += 4;
                        break;
                    case 0x69:
                        setL(this.c);
                        this.cycles += 4;
                        break;
                    case 0x6a:
                        setL(this.d);
                        this.cycles += 4;
                        break;
                    case 0x6b:
                        setL(this.e);
                        this.cycles += 4;
                        break;
                    case 0x6c:
                        setL((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0x6d:
                        this.cycles += 4;
                        break;
                    case 0x6e:
                        setL(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x6f:
                        setL(this.a);
                        this.cycles += 4;
                        break;

                    case 0x70:
                        writeByte(this.hl, (byte) this.b);
                        this.cycles += 8;
                        break;
                    case 0x71:
                        writeByte(this.hl, (byte) this.c);
                        this.cycles += 8;
                        break;
                    case 0x72:
                        writeByte(this.hl, (byte) this.d);
                        this.cycles += 8;
                        break;
                    case 0x73:
                        writeByte(this.hl, (byte) this.e);
                        this.cycles += 8;
                        break;
                    case 0x74:
                        writeByte(this.hl, (byte) (this.hl >> 8));
                        this.cycles += 8;
                        break;
                    case 0x75:
                        writeByte(this.hl, (byte) (this.hl & 0xff));
                        this.cycles += 8;
                        break;
                    case 0x77:
                        writeByte(this.hl, (byte) this.a);
                        this.cycles += 8;
                        break;

                    case 0x78:
                        this.a = this.b;
                        this.cycles += 4;
                        break;
                    case 0x79:
                        this.a = this.c;
                        this.cycles += 4;
                        break;
                    case 0x7a:
                        this.a = this.d;
                        this.cycles += 4;
                        break;
                    case 0x7b:
                        this.a = this.e;
                        this.cycles += 4;
                        break;
                    case 0x7c:
                        this.a = (this.hl >> 8);
                        this.cycles += 4;
                        break;
                    case 0x7d:
                        this.a = (this.hl & 0xff);
                        this.cycles += 4;
                        break;
                    case 0x7e:
                        this.a = readByte(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x7f:
                        this.cycles += 4;
                        break;

                    case 0x0a:
                        this.a = readByte((this.b << 8) + this.c);
                        this.cycles += 8;
                        break;
                    case 0x1a:
                        this.a = readByte((this.d << 8) + this.e);
                        this.cycles += 8;
                        break;
                    case 0xfa:
                        this.a = readByte((memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8));
                        this.pc += 2;
                        this.cycles += 16;
                        break;

                    case 0x02:
                        writeByte((this.b << 8) + this.c, (byte) this.a);
                        this.cycles += 8;
                        break;
                    case 0x12:
                        writeByte((this.d << 8) + this.e, (byte) this.a);
                        this.cycles += 8;
                        break;
                    case 0xea:
                        writeByte((memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8), (byte) this.a);
                        this.pc += 2;
                        this.cycles += 16;
                        break;

                    case 0xf2:
                        this.a = readIO(IO_AREA + this.c);
                        this.cycles += 8;
                        break;
                    case 0xe2:
                        writeIO(IO_AREA + this.c, (byte) this.a);
                        this.cycles += 8;
                        break;

                    case 0xe0:
                        writeIO(IO_AREA + (memory_[this.pc++] & 0xff), (byte) this.a);
                        this.cycles += 12;
                        break;
                    case 0xf0:
                        this.a = readIO(IO_AREA + (memory_[this.pc++] & 0xff));
                        this.cycles += 12;
                        break;

                    // load and decrement operations
                    case 0x3a:
                        this.a = readByte(this.hl--);
                        this.hl &= 0xffff;
                        this.cycles += 8;
                        break;
                    case 0x32:
                        writeByte(this.hl--, (byte) this.a);
                        this.hl &= 0xffff;
                        this.cycles += 8;
                        break;

                    // load and increment operations
                    case 0x2a:
                        this.a = readByte(this.hl++);
                        this.hl &= 0xffff;
                        this.cycles += 8;
                        break;
                    case 0x22:
                        writeByte(this.hl++, (byte) this.a);
                        this.hl &= 0xffff;
                        this.cycles += 8;
                        break;

                    // 16bit load operations
                    case 0x01:
                        this.c = memory_[this.pc++] & 0xff;
                        this.b = memory_[this.pc++] & 0xff;
                        this.cycles += 12;
                        break;
                    case 0x11:
                        this.e = memory_[this.pc++] & 0xff;
                        this.d = memory_[this.pc++] & 0xff;
                        this.cycles += 12;
                        break;
                    case 0x21:
                        this.hl = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                        this.pc += 2;
                        this.cycles += 12;
                        break;
                    case 0x31:
                        this.sp = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                        this.pc += 2;
                        this.cycles += 12;
                        break;

                    case 0xf9:
                        this.sp = this.hl;
                        this.cycles += 8;
                        break;

                    case 0xf8: {
                        final int value = this.sp + memory_[this.pc++];

                        this.hl = value & 0xffff;
                        this.f = (value >= 0x10000 ? CARRY : 0);
                        this.cycles += 12;
                        break;
                    }

                    case 0x08:
                        writeWord((memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8), this.sp);
                        this.pc += 2;
                        this.cycles += 20;
                        break;

                    // push register pair onto the stack
                    case 0xc5:
                        pushByte(this.b);
                        pushByte(this.c);
                        this.cycles += 16;
                        break;
                    case 0xd5:
                        pushByte(this.d);
                        pushByte(this.e);
                        this.cycles += 16;
                        break;
                    case 0xe5:
                        pushWord(this.hl);
                        this.cycles += 16;
                        break;
                    case 0xf5:
                        pushByte(this.a);
                        pushByte(this.f);
                        this.cycles += 16;
                        break;

                    // pop register pair from the stack
                    case 0xc1:
                        this.c = popByte();
                        this.b = popByte();
                        this.cycles += 12;
                        break;
                    case 0xd1:
                        this.e = popByte();
                        this.d = popByte();
                        this.cycles += 12;
                        break;
                    case 0xe1:
                        this.hl = popWord();
                        this.cycles += 12;
                        break;
                    case 0xf1:
                        this.f = popByte() & 0xf0;
                        this.a = popByte();
                        this.cycles += 12;
                        break;

                    // add without carry
                    case 0x80:
                        operationADD(this.b);
                        this.cycles += 4;
                        break;
                    case 0x81:
                        operationADD(this.c);
                        this.cycles += 4;
                        break;
                    case 0x82:
                        operationADD(this.d);
                        this.cycles += 4;
                        break;
                    case 0x83:
                        operationADD(this.e);
                        this.cycles += 4;
                        break;
                    case 0x84:
                        operationADD((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0x85:
                        operationADD((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0x86:
                        operationADD(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x87:
                        operationADD(this.a);
                        this.cycles += 4;
                        break;
                    case 0xc6:
                        operationADD(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // add with carry
                    case 0x88:
                        operationADC(this.b);
                        this.cycles += 4;
                        break;
                    case 0x89:
                        operationADC(this.c);
                        this.cycles += 4;
                        break;
                    case 0x8a:
                        operationADC(this.d);
                        this.cycles += 4;
                        break;
                    case 0x8b:
                        operationADC(this.e);
                        this.cycles += 4;
                        break;
                    case 0x8c:
                        operationADC((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0x8d:
                        operationADC((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0x8e:
                        operationADC(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x8f:
                        operationADC(this.a);
                        this.cycles += 4;
                        break;
                    case 0xce:
                        operationADC(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // subtract without carry
                    case 0x90:
                        operationSUB(this.b);
                        this.cycles += 4;
                        break;
                    case 0x91:
                        operationSUB(this.c);
                        this.cycles += 4;
                        break;
                    case 0x92:
                        operationSUB(this.d);
                        this.cycles += 4;
                        break;
                    case 0x93:
                        operationSUB(this.e);
                        this.cycles += 4;
                        break;
                    case 0x94:
                        operationSUB((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0x95:
                        operationSUB((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0x96:
                        operationSUB(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x97:
                        operationSUB(this.a);
                        this.cycles += 4;
                        break;
                    case 0xd6:
                        operationSUB(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // subtract with carry
                    case 0x98:
                        operationSBC(this.b);
                        this.cycles += 4;
                        break;
                    case 0x99:
                        operationSBC(this.c);
                        this.cycles += 4;
                        break;
                    case 0x9a:
                        operationSBC(this.d);
                        this.cycles += 4;
                        break;
                    case 0x9b:
                        operationSBC(this.e);
                        this.cycles += 4;
                        break;
                    case 0x9c:
                        operationSBC((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0x9d:
                        operationSBC((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0x9e:
                        operationSBC(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0x9f:
                        operationSBC(this.a);
                        this.cycles += 4;
                        break;
                    case 0xde:
                        operationSBC(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // logical AND
                    case 0xa0:
                        operationAND(this.b);
                        this.cycles += 4;
                        break;
                    case 0xa1:
                        operationAND(this.c);
                        this.cycles += 4;
                        break;
                    case 0xa2:
                        operationAND(this.d);
                        this.cycles += 4;
                        break;
                    case 0xa3:
                        operationAND(this.e);
                        this.cycles += 4;
                        break;
                    case 0xa4:
                        operationAND((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0xa5:
                        operationAND((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0xa6:
                        operationAND(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0xa7:
                        operationAND(this.a);
                        this.cycles += 4;
                        break;
                    case 0xe6:
                        operationAND(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // logical XOR
                    case 0xa8:
                        operationXOR(this.b);
                        this.cycles += 4;
                        break;
                    case 0xa9:
                        operationXOR(this.c);
                        this.cycles += 4;
                        break;
                    case 0xaa:
                        operationXOR(this.d);
                        this.cycles += 4;
                        break;
                    case 0xab:
                        operationXOR(this.e);
                        this.cycles += 4;
                        break;
                    case 0xac:
                        operationXOR((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0xad:
                        operationXOR((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0xae:
                        operationXOR(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0xaf:
                        operationXOR(this.a);
                        this.cycles += 4;
                        break;
                    case 0xee:
                        operationXOR(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // logical OR
                    case 0xb0:
                        operationOR(this.b);
                        this.cycles += 4;
                        break;
                    case 0xb1:
                        operationOR(this.c);
                        this.cycles += 4;
                        break;
                    case 0xb2:
                        operationOR(this.d);
                        this.cycles += 4;
                        break;
                    case 0xb3:
                        operationOR(this.e);
                        this.cycles += 4;
                        break;
                    case 0xb4:
                        operationOR((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0xb5:
                        operationOR((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0xb6:
                        operationOR(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0xb7:
                        operationOR(this.a);
                        this.cycles += 4;
                        break;
                    case 0xf6:
                        operationOR(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // compare with register A
                    case 0xb8:
                        operationCP(this.b);
                        this.cycles += 4;
                        break;
                    case 0xb9:
                        operationCP(this.c);
                        this.cycles += 4;
                        break;
                    case 0xba:
                        operationCP(this.d);
                        this.cycles += 4;
                        break;
                    case 0xbb:
                        operationCP(this.e);
                        this.cycles += 4;
                        break;
                    case 0xbc:
                        operationCP((this.hl >> 8));
                        this.cycles += 4;
                        break;
                    case 0xbd:
                        operationCP((this.hl & 0xff));
                        this.cycles += 4;
                        break;
                    case 0xbe:
                        operationCP(readByte(this.hl));
                        this.cycles += 8;
                        break;
                    case 0xbf:
                        operationCP(this.a);
                        this.cycles += 4;
                        break;
                    case 0xfe:
                        operationCP(memory_[this.pc++] & 0xff);
                        this.cycles += 8;
                        break;

                    // increase value
                    case 0x04:
                        this.b = operationINC(this.b);
                        this.cycles += 4;
                        break;
                    case 0x0c:
                        this.c = operationINC(this.c);
                        this.cycles += 4;
                        break;
                    case 0x14:
                        this.d = operationINC(this.d);
                        this.cycles += 4;
                        break;
                    case 0x1c:
                        this.e = operationINC(this.e);
                        this.cycles += 4;
                        break;
                    case 0x24:
                        setH(operationINC((this.hl >> 8)));
                        this.cycles += 4;
                        break;
                    case 0x2c:
                        setL(operationINC((this.hl & 0xff)));
                        this.cycles += 4;
                        break;
                    case 0x34:
                        writeByte(this.hl, (byte) operationINC(readByte(this.hl)));
                        this.cycles += 12;
                        break;
                    case 0x3c:
                        this.a = operationINC(this.a);
                        this.cycles += 4;
                        break;

                    // decrease value
                    case 0x05:
                        this.b = operationDEC(this.b);
                        this.cycles += 4;
                        break;
                    case 0x0d:
                        this.c = operationDEC(this.c);
                        this.cycles += 4;
                        break;
                    case 0x15:
                        this.d = operationDEC(this.d);
                        this.cycles += 4;
                        break;
                    case 0x1d:
                        this.e = operationDEC(this.e);
                        this.cycles += 4;
                        break;
                    case 0x25:
                        setH(operationDEC((this.hl >> 8)));
                        this.cycles += 4;
                        break;
                    case 0x2d:
                        setL(operationDEC((this.hl & 0xff)));
                        this.cycles += 4;
                        break;
                    case 0x35:
                        writeByte(this.hl, (byte) operationDEC(readByte(this.hl)));
                        this.cycles += 12;
                        break;
                    case 0x3d:
                        this.a = operationDEC(this.a);
                        this.cycles += 4;
                        break;

                    // 16bit add to HL
                    case 0x09:
                        operationADD16((this.b << 8) + this.c);
                        this.cycles += 8;
                        break;
                    case 0x19:
                        operationADD16((this.d << 8) + this.e);
                        this.cycles += 8;
                        break;
                    case 0x29:
                        operationADD16(this.hl);
                        this.cycles += 8;
                        break;
                    case 0x39:
                        operationADD16((this.a << 8) + this.f);
                        this.cycles += 8;
                        break;

                    // 16bit add to SP
                    case 0xe8: {
                        final int value = this.sp + memory_[this.pc++];

                        this.f = (value >= 0x10000 ? CARRY : 0);
                        this.sp = value & 0xffff;
                        this.cycles += 16;
                        break;
                    }

                    // 16bit increments
                    case 0x03: {
                        final int value = ((this.b << 8) + this.c + 1) & 0xffff;

                        this.b = (value >> 8);
                        this.c = (value & 0xff);
                        this.cycles += 8;
                        break;
                    }
                    case 0x13: {
                        final int value = ((this.d << 8) + this.e + 1) & 0xffff;

                        this.d = (value >> 8);
                        this.e = (value & 0xff);
                        this.cycles += 8;
                        break;
                    }
                    case 0x23:
                        this.hl = (this.hl + 1) & 0xffff;
                        this.cycles += 8;
                        break;
                    case 0x33:
                        this.sp = (this.sp + 1) & 0xffff;
                        this.cycles += 8;
                        break;

                    // 16bit decrements
                    case 0x0b: {
                        final int value = ((this.b << 8) + this.c - 1) & 0xffff;

                        this.b = (value >> 8);
                        this.c = (value & 0xff);
                        this.cycles += 8;
                        break;
                    }
                    case 0x1b: {
                        final int value = ((this.d << 8) + this.e - 1) & 0xffff;

                        this.d = (value >> 8);
                        this.e = (value & 0xff);
                        this.cycles += 8;
                        break;
                    }
                    case 0x2b:
                        this.hl = (this.hl - 1) & 0xffff;
                        this.cycles += 8;
                        break;
                    case 0x3b:
                        this.sp = (this.sp - 1) & 0xffff;
                        this.cycles += 8;
                        break;

                    // $cb indicates an operation identified by two bytes
                    case 0xcb:
                        executeTwoByteOperation(memory_[this.pc++] & 0xff);
                        break;

                    // decimal adjustment of register A
                    case 0x27: {
                        final int value = (this.a % 10) + (((this.a / 10) % 10) << 4);

                        this.f &= NEGATIVE;
                        this.f |= ((value == 0 ? ZERO : 0) + (this.a > 100 ? CARRY : 0));
                        this.a = value;
                        this.cycles += 4;
                        break;
                    }

                    // complement register A
                    case 0x2f:
                        this.a = ((~this.a) & 0xff);
                        this.f |= NEGATIVE | HALFCARRY;
                        this.cycles += 4;
                        break;

                    // complement carry flag
                    case 0x3f:
                        this.f ^= CARRY;
                        this.f &= (CARRY | ZERO);
                        this.cycles += 4;
                        break;

                    // set carry flag
                    case 0x37:
                        this.f |= CARRY;
                        this.f &= (CARRY | ZERO);
                        this.cycles += 4;
                        break;

                    // no operation
                    case 0x00:
                        this.cycles += 4;
                        break;

                    // HALT
                    case 0x76:
                        operationHALT();
                        this.cycles += 4;
                        break;

                    // STOP
                    case 0x10:
                        // halt CPU and display until a button gets pressed, or change speed
                        // a speed change?
                        if ((readByte(SPEED_SWITCH) & 0x01) != 0) {
                            // set "other" speed
                            this.cpuSpeed = this.cpuSpeed == Gameboy.ORIGINAL_SPEED_CLASSIC ? Gameboy.ORIGINAL_SPEED_COLOR : Gameboy.ORIGINAL_SPEED_CLASSIC;
                            // clear the "prepare speed switch" bit
                            writeByte(SPEED_SWITCH, (byte) (readByte(SPEED_SWITCH) & 0xfe));
                            // notify observers about the new speed
                            setChanged(true);
                            notifyObservers(new Long(this.cpuSpeed));
                            // inform the performance meter about the change
                            this.gameboy.getPerformanceMeter().setTargetSpeed(this.cpuSpeed);
                            this.gameboy.getPerformanceMeter().setupNextMeasurement(this.cycles);
                            // show a message with the new speed
                            this.gameboy.getLogger().info("Set Gameboy CPU speed to " + this.cpuSpeed + " Hz");
                        } else {
                            operationHALT();
                        }
                        ++this.pc;
                        this.cycles += 4;
                        break;

                    // disable interrupts
                    case 0xf3:
                        setInterruptEnabled(false);
                        this.cycles += 4;
                        break;
                    // enable interrupts
                    case 0xfb:
                        if (!this.isInterruptEnabled) {
                            this.doesInterruptEnableSwitch = true;
                        }
                        this.cycles += 4;
                        break;

                    // rotate left with lowest bit re-appearing as highest bit
                    case 0x07: {
                        this.a = operationRLC(this.a);
                        this.cycles += 4;
                        break;
                    }
                    // rotate left with carry
                    case 0x17: {
                        this.a = operationRL(this.a);
                        this.cycles += 4;
                        break;
                    }

                    // rotate right with highest bit re-appearing as lowest bit
                    case 0x0f: {
                        this.a = operationRRC(this.a);
                        this.cycles += 4;
                        break;
                    }
                    // rotate right with carry
                    case 0x1f: {
                        this.a = operationRR(this.a);
                        this.cycles += 4;
                        break;
                    }

                    // jump
                    case 0xc3:
                        this.pc = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                        this.cycles += 16;
                        break;
                    case 0xe9:
                        this.pc = this.hl;
                        this.cycles += 4;
                        break;

                    // relative jump
                    case 0x18:
                        this.pc += memory_[this.pc++] + 1;
                        this.cycles += 12;
                        break;

                    // conditional jumps
                    case 0xc2:
                        if ((this.f & ZERO) == 0) {
                            this.pc = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                            this.cycles += 16;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xca:
                        if ((this.f & ZERO) != 0) {
                            this.pc = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                            this.cycles += 16;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xd2:
                        if ((this.f & CARRY) == 0) {
                            this.pc = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                            this.cycles += 16;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xda:
                        if ((this.f & CARRY) != 0) {
                            this.pc = (memory_[this.pc] & 0xff) + ((memory_[this.pc + 1] & 0xff) << 8);
                            this.cycles += 16;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;

                    // relative conditional jumps
                    case 0x20:
                        if ((this.f & ZERO) == 0) {
                            this.pc += (byte) (memory_[this.pc] & 0xff) + 1;
                            this.cycles += 12;
                        } else {
                            ++this.pc;
                            this.cycles += 8;
                        }
                        break;
                    case 0x28:
                        if ((this.f & ZERO) != 0) {
                            this.pc += (byte) (memory_[this.pc] & 0xff) + 1;
                            this.cycles += 12;
                        } else {
                            ++this.pc;
                            this.cycles += 8;
                        }
                        break;
                    case 0x30:
                        if ((this.f & CARRY) == 0) {
                            this.pc += (byte) (memory_[this.pc] & 0xff) + 1;
                            this.cycles += 12;
                        } else {
                            ++this.pc;
                            this.cycles += 8;
                        }
                        break;
                    case 0x38:
                        if ((this.f & CARRY) != 0) {
                            this.pc += (byte) (memory_[this.pc] & 0xff) + 1;
                            this.cycles += 12;
                        } else {
                            ++this.pc;
                            this.cycles += 8;
                        }
                        break;

                    // subroutine calls
                    case 0xcd:
                        operationCALL();
                        this.cycles += 24;
                        break;

                    // conditional subroutine calls
                    case 0xc4:
                        if ((this.f & ZERO) == 0) {
                            operationCALL();
                            this.cycles += 24;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xcc:
                        if ((this.f & ZERO) != 0) {
                            operationCALL();
                            this.cycles += 24;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xd4:
                        if ((this.f & CARRY) == 0) {
                            operationCALL();
                            this.cycles += 24;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;
                    case 0xdc:
                        if ((this.f & CARRY) != 0) {
                            operationCALL();
                            this.cycles += 24;
                        } else {
                            this.pc += 2;
                            this.cycles += 12;
                        }
                        break;

                    // restart
                    case 0xc7:
                    case 0xcf:
                    case 0xd7:
                    case 0xdf:
                    case 0xe7:
                    case 0xef:
                    case 0xf7:
                    case 0xff:
                        operationCALL(opCode - 0xc7);
                        this.cycles += 16;
                        break;

                    // return from subroutine
                    case 0xc9:
                        this.pc = popWord();
                        this.cycles += 16;
                        break;

                    // return from subroutine and enable interrupts
                    case 0xd9:
                        this.pc = popWord();
                        if (!this.isInterruptEnabled) {
                            this.doesInterruptEnableSwitch = true;
                        }
                        this.cycles += 16;
                        break;

                    // conditional return from subroutine
                    case 0xc0:
                        if ((this.f & ZERO) == 0) {
                            this.pc = popWord();
                            this.cycles += 20;
                        } else {
                            this.cycles += 8;
                        }
                        break;
                    case 0xc8:
                        if ((this.f & ZERO) != 0) {
                            this.pc = popWord();
                            this.cycles += 20;
                        } else {
                            this.cycles += 8;
                        }
                        break;
                    case 0xd0:
                        if ((this.f & CARRY) == 0) {
                            this.pc = popWord();
                            this.cycles += 20;
                        } else {
                            this.cycles += 8;
                        }
                        break;
                    case 0xd8:
                        if ((this.f & CARRY) != 0) {
                            this.pc = popWord();
                            this.cycles += 20;
                        } else {
                            this.cycles += 8;
                        }
                        break;
                }

                // check if some scheduled events like the next timer call or H-Blank request are pending
                if (this.cycles >= this.nextEvent) {
                    checkEvents();
                }

                // check for interrupts
                if (this.isInterruptEnabled && this.irqsRequested != 0 && (this.irqsRequested & this.irqsEnabled) != 0) {
                    // find the interrupt with the highest priority (lowest set bit)
                    for (int irqVector = IRQ_VECTOR_VBLANK, mask = 1; irqVector <= IRQ_VECTOR_JOYPAD; irqVector += 0x08, mask <<= 1) {
                        // we have found the interrupt?
                        if ((this.irqsRequested & this.irqsEnabled & mask) != 0) {
                            if (DEBUG_INTERRUPTS) {
                                System.out.println("Triggered IRQ $" + Integer.toHexString(mask) + " at cycle " + this.cycles);
                            }
                            // then clear it in the interrupt request register
                            this.irqsRequested ^= mask;
                            // disable interrupts
                            setInterruptEnabled(false);
                            this.doesInterruptEnableSwitch = false;
                            // save current program position and jump to IRQ vector
                            operationCALL(irqVector);
                            break;
                        }
                    }
                }

                // switch interrupt if necessary
                if (this.doesInterruptEnableSwitch) {
                    setInterruptEnabled(!this.isInterruptEnabled);
                    this.doesInterruptEnableSwitch = false;
                }
            }
            if (this.isPaused) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    /**
     * Executes an operation identified by $cb plus an additional byte
     * 
     * @param opCode	byte following $cb and identifying the operation
     */
    private void executeTwoByteOperation(final int opCode) {
        switch (opCode & 0xc0) {
            case 0x00:
                // we have different operations, such as SWAP or rotate
                switch (opCode) {
                    // rotate left with lowest bit re-appearing as highest bit
                    case 0x00:
                        this.b = operationRLC(this.b);
                        this.cycles += 8;
                        break;
                    case 0x01:
                        this.c = operationRLC(this.c);
                        this.cycles += 8;
                        break;
                    case 0x02:
                        this.d = operationRLC(this.d);
                        this.cycles += 8;
                        break;
                    case 0x03:
                        this.e = operationRLC(this.e);
                        this.cycles += 8;
                        break;
                    case 0x04:
                        setH(operationRLC((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x05:
                        setL(operationRLC((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x06:
                        writeByte(this.hl, (byte) operationRLC(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x07:
                        this.a = operationRLC(this.a);
                        this.cycles += 8;
                        break;

                    // rotate left with highest bit re-appearing as lowest bit
                    case 0x08:
                        this.b = operationRRC(this.b);
                        this.cycles += 8;
                        break;
                    case 0x09:
                        this.c = operationRRC(this.c);
                        this.cycles += 8;
                        break;
                    case 0x0a:
                        this.d = operationRRC(this.d);
                        this.cycles += 8;
                        break;
                    case 0x0b:
                        this.e = operationRRC(this.e);
                        this.cycles += 8;
                        break;
                    case 0x0c:
                        setH(operationRRC((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x0d:
                        setL(operationRRC((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x0e:
                        writeByte(this.hl, (byte) operationRRC(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x0f:
                        this.a = operationRRC(this.a);
                        this.cycles += 8;
                        break;

                    // rotate left with carry
                    case 0x10:
                        this.b = operationRL(this.b);
                        this.cycles += 8;
                        break;
                    case 0x11:
                        this.c = operationRL(this.c);
                        this.cycles += 8;
                        break;
                    case 0x12:
                        this.d = operationRL(this.d);
                        this.cycles += 8;
                        break;
                    case 0x13:
                        this.e = operationRL(this.e);
                        this.cycles += 8;
                        break;
                    case 0x14:
                        setH(operationRL((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x15:
                        setL(operationRL((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x16:
                        writeByte(this.hl, (byte) operationRL(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x17:
                        this.a = operationRL(this.a);
                        this.cycles += 8;
                        break;

                    // rotate right with carry
                    case 0x18:
                        this.b = operationRR(this.b);
                        this.cycles += 8;
                        break;
                    case 0x19:
                        this.c = operationRR(this.c);
                        this.cycles += 8;
                        break;
                    case 0x1a:
                        this.d = operationRR(this.d);
                        this.cycles += 8;
                        break;
                    case 0x1b:
                        this.e = operationRR(this.e);
                        this.cycles += 8;
                        break;
                    case 0x1c:
                        setH(operationRR((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x1d:
                        setL(operationRR((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x1e:
                        writeByte(this.hl, (byte) operationRR(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x1f:
                        this.a = operationRR(this.a);
                        this.cycles += 8;
                        break;

                    // shift left into carry, LSB is set to 0
                    case 0x20:
                        this.b = operationSLA(this.b);
                        this.cycles += 8;
                        break;
                    case 0x21:
                        this.c = operationSLA(this.c);
                        this.cycles += 8;
                        break;
                    case 0x22:
                        this.d = operationSLA(this.d);
                        this.cycles += 8;
                        break;
                    case 0x23:
                        this.e = operationSLA(this.e);
                        this.cycles += 8;
                        break;
                    case 0x24:
                        setH(operationSLA((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x25:
                        setL(operationSLA((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x26:
                        writeByte(this.hl, (byte) operationSLA(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x27:
                        this.a = operationSLA(this.a);
                        this.cycles += 8;
                        break;

                    // shift right without carry, MSB doesn't change
                    case 0x28:
                        this.b = operationSRA(this.b);
                        this.cycles += 8;
                        break;
                    case 0x29:
                        this.c = operationSRA(this.c);
                        this.cycles += 8;
                        break;
                    case 0x2a:
                        this.d = operationSRA(this.d);
                        this.cycles += 8;
                        break;
                    case 0x2b:
                        this.e = operationSRA(this.e);
                        this.cycles += 8;
                        break;
                    case 0x2c:
                        setH(operationSRA((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x2d:
                        setL(operationSRA((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x2e:
                        writeByte(this.hl, (byte) operationSRA(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x2f:
                        this.a = operationSRA(this.a);
                        this.cycles += 8;
                        break;

                    // swap nibbles
                    case 0x30:
                        this.b = operationSWAP(this.b);
                        this.cycles += 8;
                        break;
                    case 0x31:
                        this.c = operationSWAP(this.c);
                        this.cycles += 8;
                        break;
                    case 0x32:
                        this.d = operationSWAP(this.d);
                        this.cycles += 8;
                        break;
                    case 0x33:
                        this.e = operationSWAP(this.e);
                        this.cycles += 8;
                        break;
                    case 0x34:
                        setH(operationSWAP((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x35:
                        setL(operationSWAP((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x36:
                        writeByte(this.hl, (byte) operationSWAP(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x37:
                        this.a = operationSWAP(this.a);
                        this.cycles += 8;
                        break;

                    // shift right into carry, MSB set to 0
                    case 0x38:
                        this.b = operationSRL(this.b);
                        this.cycles += 8;
                        break;
                    case 0x39:
                        this.c = operationSRL(this.c);
                        this.cycles += 8;
                        break;
                    case 0x3a:
                        this.d = operationSRL(this.d);
                        this.cycles += 8;
                        break;
                    case 0x3b:
                        this.e = operationSRL(this.e);
                        this.cycles += 8;
                        break;
                    case 0x3c:
                        setH(operationSRL((this.hl >> 8)));
                        this.cycles += 8;
                        break;
                    case 0x3d:
                        setL(operationSRL((this.hl & 0xff)));
                        this.cycles += 8;
                        break;
                    case 0x3e:
                        writeByte(this.hl, (byte) operationSRL(readByte(this.hl)));
                        this.cycles += 16;
                        break;
                    case 0x3f:
                        this.a = operationSRL(this.a);
                        this.cycles += 8;
                        break;
                    default:
                        ;
                }
                break;

            case 0x40:
            case 0x80:
            case 0xc0: {
                final int bit = (opCode & 0x38) >> 3;

                switch (opCode & 0xc7) {
                    case 0x40:
                        operationBIT(this.b, bit);
                        this.cycles += 8;
                        break;
                    case 0x41:
                        operationBIT(this.c, bit);
                        this.cycles += 8;
                        break;
                    case 0x42:
                        operationBIT(this.d, bit);
                        this.cycles += 8;
                        break;
                    case 0x43:
                        operationBIT(this.e, bit);
                        this.cycles += 8;
                        break;
                    case 0x44:
                        operationBIT((this.hl >> 8), bit);
                        this.cycles += 8;
                        break;
                    case 0x45:
                        operationBIT((this.hl & 0xff), bit);
                        this.cycles += 8;
                        break;
                    case 0x46:
                        operationBIT(readByte(this.hl), bit);
                        this.cycles += 16;
                        break;
                    case 0x47:
                        operationBIT(this.a, bit);
                        this.cycles += 8;
                        break;

                    case 0x80:
                        this.b |= (1 << bit);
                        this.b ^= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0x81:
                        this.c |= (1 << bit);
                        this.c ^= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0x82:
                        this.d |= (1 << bit);
                        this.d ^= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0x83:
                        this.e |= (1 << bit);
                        this.e ^= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0x84: {
                        final int bits = 1 << bit;
                        final int value = ((this.hl >> 8) | bits);

                        setH((value ^ bits));
                        this.cycles += 8;
                        break;
                    }
                    case 0x85: {
                        final int bits = 1 << bit;
                        final int value = ((this.hl & 0xff) | bits);

                        setL((value ^ bits));
                        this.cycles += 8;
                        break;
                    }
                    case 0x86: {
                        final int bits = 1 << bit;
                        final int value = (readByte(this.hl) | bits);

                        writeByte(this.hl, (byte) (value ^ bits));
                        this.cycles += 16;
                        break;
                    }
                    case 0x87:
                        this.a |= (1 << bit);
                        this.a ^= (1 << bit);
                        this.cycles += 8;
                        break;

                    case 0xc0:
                        this.b |= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0xc1:
                        this.c |= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0xc2:
                        this.d |= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0xc3:
                        this.e |= (1 << bit);
                        this.cycles += 8;
                        break;
                    case 0xc4:
                        setH(((this.hl >> 8) | (1 << bit)));
                        this.cycles += 8;
                        break;
                    case 0xc5:
                        setL(((this.hl & 0xff) | (1 << bit)));
                        this.cycles += 8;
                        break;
                    case 0xc6:
                        writeByte(this.hl, (byte) (readByte(this.hl) | (1 << bit)));
                        this.cycles += 16;
                        break;
                    case 0xc7:
                        this.a |= (1 << bit);
                        this.cycles += 8;
                        break;
                }
            }
        }
    }

    /**
     * Reads a byte from memory
     *
     * @param   adr address to read from
     */
    private int readByte(final int adr) {
        switch (adr & 0xe000) {
            // read from VRAM?
            case VRAM_AREA:
                return this.video.readByte(adr & 0x1fff);
            // read from echo-RAM, HRAM, IO reads, or reads from the sprite attribute table
            case ECHO_RAM_AREA:
                switch (adr & 0xff00) {
                    // HRAM reads and reads from the sprite attribute table are read from memory normally
                    case HIGH_RAM_AREA:
                        return this.memory[adr] & 0xff;
                    // IO reads or High-RAM reads?
                    case IO_AREA:
                        return readIO(adr);
                    // read from echo-RAM?
                    default:
                        return this.memory[adr - WRAM_SIZE] & 0xff;
                }
            // normal RAM reads
            default:
                return this.memory[adr] & 0xff;
        }
    }

    /**
     * Read a byte from the IO area
     * 
     * @param	adr	IO address to read
     * @return	read byte
     */
    protected final int readIO(final int adr) {
        switch (adr) {
            // timer-related IO ports
            case 0xff04:
                return this.timer.getDiv();
            case 0xff05:
                return this.timer.getTima();
            case 0xff06:
                return this.timer.getTma();
            case 0xff07:
                return this.timer.getTac();
            // interrupt flag register
            case INTERRUPT_FLAG:
                return this.irqsRequested;
            // sound chip-related registers
            case 0xff26: {
                final SoundChannel[] soundChannels = this.sound.getSoundChannels();

                return (this.memory[adr] & 0xf0) | (soundChannels[0].isActive() ? 0x01 : 0) | (soundChannels[1].isActive() ? 0x02 : 0) | (soundChannels[2].isActive() ? 0x04 : 0) | (soundChannels[3].isActive() ? 0x08 : 0);
            }
            // video-related registers
            case LCD_STATUS: {
                int result = this.memory[adr] & 0xfc;

                // set coincidence flag
                result |= (this.video.getLCDLine() == readIO(0xff45) ? 0x04 : 0);
                // set mode bits
                result |= this.video.getVideoMode();
                return result;
            }
            case LCD_LINE:
                return this.video.getLCDLine();
            case 0xff68: {
                final int index = this.memory[adr + 1] & (VideoChip.NUM_COLORS * 2 - 1);

                return (byte) this.video.getColorByte(VideoChip.PALETTE_BACKGROUND + index);
            }
            case 0xff6a: {
                final int index = this.memory[adr + 1] & (VideoChip.NUM_COLORS * 2 - 1);

                return (byte) this.video.getColorByte(VideoChip.PALETTE_SPRITES + index);
            }
            // CGB registers
            case SPEED_SWITCH:
                return (this.memory[adr] & 0x7f) | (this.cpuSpeed == Gameboy.ORIGINAL_SPEED_COLOR ? 0x80 : 0);
            default:
                return this.memory[adr] & 0xff;
        }
    }

    /**
     * Writes a byte to memory
     *
     * @param   adr address to write to
     * @param   data    data to write
     */
    private void writeByte(final int adr, final byte data) {
        switch (adr & 0xe000) {
            // writes to Video RAM
            case VRAM_AREA:
                this.video.writeByte(adr & 0x1fff, data);
                break;
            // writes to the Work RAM
            case WRAM_AREA:
                this.memory[adr] = data;
                break;
            // writes to sprite attribute table, High-RAM or Echo-RAM
            case ECHO_RAM_AREA:
                switch (adr & 0xff00) {
                    // writes to the sprite attribute table
                    case HIGH_RAM_AREA: {
                        // first write to RAM normally
                        this.memory[adr] = data;

                        if (adr < 0xfea0) {
                            writeOAM(adr, data);
                        }
                        break;
                    }
                    // IO writes or High-RAM writes
                    case IO_AREA:
                        writeIO(adr, data);
                        break;

                    // Echo RAM writes: we write to $c000-dfff
                    default:
                        this.memory[adr - WRAM_SIZE] = data;
                        break;
                }
                break;
            // writes to the cartridge
            default:
                this.cartridge.writeByte(adr, data);
                break;
        }
    }

    /**
     * Write to sprite attribute table (OAM)
     * 
     * @param   adr address to write to
     * @param   data    data to write
     */
    private void writeOAM(final int adr, final byte data) {
        // determine sprite
        final Sprite sprite = this.video.getSprites()[(adr & 0xfc) >> 2];

        // set corresponding attribute
        switch (adr & 0x03) {
            case 0:
                sprite.setY((data & 0xff) - 16);
                break;
            case 1:
                sprite.setX((data & 0xff) - 8);
                break;
            case 2:
                sprite.setTile(data & 0xff);
                break;
            case 3:
                sprite.setAttributes(data & 0xff);
                break;
            default:
                // cannot happen
                ;
        }
    }

    /**
     * Writes a word to memory
     *
     * @param   adr address to write to
     * @param   data    data to write
     */
    private void writeWord(final int adr, final int data) {
        writeByte(adr, (byte) (data & 0xff));
        writeByte(adr + 1, (byte) (data >> 8));
    }

    /**
     * Write a byte to the IO area
     * 
     * @param	adr	IO address to write to 
     * @param	data	byte to write
     */
    protected final void writeIO(final int adr, byte data) {
        switch (adr) {
            // joypad port
            case JOYPAD_PORT: {
                // determine set bits
                int jp = 0xff;

                if ((data & 0x10) == 0) {
                    jp &= (0xff - this.gameboy.getJoypad().getDirections());
                }
                if ((data & 0x20) == 0) {
                    jp &= (0xff - this.gameboy.getJoypad().getButtons());
                }
                data = (byte) jp;

                // raise IRQ if the lower 4 bits are modified
                if (((readIO(adr) & ~data) & 0x0f) != 0) {
                    requestIRQ(IRQ_JOYPAD);
                }
                break;
            }
            // serial port
            case 0xff02:
                // always disable transfer start flag
                data &= 0x7f;
                // set Serial Transfer Data register to $ff to signal that we have no connection
                if ((data & 0x01) == 1) {
                    this.memory[ 0xff01] = (byte) 0xff;
                }
                break;
            // timer-related IO ports
            case 0xff04:
                this.timer.setDiv(data & 0xff);
                break;
            case 0xff05:
                this.timer.setTima(data & 0xff);
                break;
            case 0xff06:
                this.timer.setTma(data & 0xff);
                break;
            case 0xff07:
                this.timer.setTac(data & 0xff);
                break;
            // interrupt flag register
            case INTERRUPT_FLAG:
                if (DEBUG_INTERRUPTS) {
                    System.out.println("Requested IRQs $" + Integer.toHexString(data & 0xff) + " at cycle " + this.cycles);
                }
                this.irqsRequested = data & 0xff;
                break;
            // sound-related registers
            // - channel 1
            case 0xff10:
                ((SquareWaveChannel) this.sound.getSoundChannels()[0]).setSweep((data & 0x70) >> 4, (data & 0x08) != 0, data & 0x07);
                break;
            case 0xff11:
                ((SquareWaveChannel) this.sound.getSoundChannels()[0]).setWavePatternDuty((data & 0xc0) >> 6);
                break;
            case 0xff12:
                ((SquareWaveChannel) this.sound.getSoundChannels()[0]).setVolumeEnvelope((data & 0xf0) >> 4, (data & 0x08) != 0, data & 0x07);
                break;
            case 0xff13:
            case 0xff14: {
                this.memory[adr] = data;

                final int frequencyGB = (this.memory[0xff13] & 0xff) + ((this.memory[0xff14] & 0x07) << 8);

                ((SquareWaveChannel) this.sound.getSoundChannels()[0]).setFrequency(frequencyGB);
                if (adr == 0xff14) {
                    if ((data & 0x80) != 0) {
                        this.sound.getSoundChannels()[0].setRepeat((data & 0x40) == 0);
                        this.sound.getSoundChannels()[0].setLength(0x40 - (this.memory[0xff11] & 0x3f));
                    }
                }
                break;
            }
            // - channel 2
            case 0xff16:
                ((SquareWaveChannel) this.sound.getSoundChannels()[1]).setWavePatternDuty((data & 0xc0) >> 6);
                break;
            case 0xff17:
                ((SquareWaveChannel) this.sound.getSoundChannels()[1]).setVolumeEnvelope((data & 0xf0) >> 4, (data & 0x08) != 0, data & 0x07);
                break;
            case 0xff18:
            case 0xff19: {
                this.memory[adr] = data;

                final int frequencyGB = (this.memory[0xff18] & 0xff) + ((this.memory[0xff19] & 0x07) << 8);

                ((SquareWaveChannel) this.sound.getSoundChannels()[1]).setFrequency(frequencyGB);
                if (adr == 0xff19) {
                    if ((data & 0x80) != 0) {
                        this.sound.getSoundChannels()[1].setRepeat((data & 0x40) == 0);
                        this.sound.getSoundChannels()[1].setLength(0x40 - (this.memory[0xff16] & 0x3f));
                    }
                }
                break;
            }
            // - channel 3
            case 0xff1a:
                ((VoluntaryWaveChannel) this.sound.getSoundChannels()[2]).setActive((data & 0x80) != 0);
                break;
            case 0xff1b:
                break;
            case 0xff1c: {
                final int vol = (data >> 5) & 0x03;

                ((VoluntaryWaveChannel) this.sound.getSoundChannels()[2]).setOutputLevel(vol == 0 ? 0 : 200 >> vol);
                break;
            }
            case 0xff1d:
            case 0xff1e: {
                this.memory[adr] = data;

                final int frequencyGB = (this.memory[0xff1d] & 0xff) + ((this.memory[0xff1e] & 0x07) << 8);

                ((VoluntaryWaveChannel) this.sound.getSoundChannels()[2]).setFrequency(frequencyGB);
                if (adr == 0xff1e) {
                    if ((data & 0x80) != 0) {
                        this.sound.getSoundChannels()[2].setRepeat((data & 0x40) == 0);
                        this.sound.getSoundChannels()[2].setLength(0xff - (this.memory[0xff1b] & 0xff));
                    }
                }
                break;
            }
            // - channel 4
            case 0xff20:
                break;
            case 0xff21:
                ((WhiteNoiseChannel) this.sound.getSoundChannels()[3]).setVolumeEnvelope((data & 0xf0) >> 4, (data & 0x08) != 0, data & 0x07);
                break;
            case 0xff22:
                ((WhiteNoiseChannel) this.sound.getSoundChannels()[3]).setPolynomialCounter((data & 0xf0) >> 4, (data & 0x08) != 0, data & 0x07);
                break;
            case 0xff23:
                if ((data & 0x80) != 0) {
                    this.sound.getSoundChannels()[3].setRepeat((data & 0x40) == 0);
                    this.sound.getSoundChannels()[3].setLength(0x40 - (this.memory[0xff20] & 0x3f));
                }
                break;
            // - sound control registers
            case 0xff25:
                this.sound.getSoundChannels()[0].setTerminalActive(SoundChannel.LEFT, (data & 0x01) != 0);
                this.sound.getSoundChannels()[0].setTerminalActive(SoundChannel.RIGHT, (data & 0x10) != 0);
                this.sound.getSoundChannels()[1].setTerminalActive(SoundChannel.LEFT, (data & 0x02) != 0);
                this.sound.getSoundChannels()[1].setTerminalActive(SoundChannel.RIGHT, (data & 0x20) != 0);
                this.sound.getSoundChannels()[2].setTerminalActive(SoundChannel.LEFT, (data & 0x04) != 0);
                this.sound.getSoundChannels()[2].setTerminalActive(SoundChannel.RIGHT, (data & 0x40) != 0);
                this.sound.getSoundChannels()[3].setTerminalActive(SoundChannel.LEFT, (data & 0x08) != 0);
                this.sound.getSoundChannels()[3].setTerminalActive(SoundChannel.RIGHT, (data & 0x80) != 0);
                break;
            case 0xff26:
                this.isSoundOn = (data & 0x80) != 0;
                break;
            // - channel 2 wave pattern data
            case 0xff30:
            case 0xff31:
            case 0xff32:
            case 0xff33:
            case 0xff34:
            case 0xff35:
            case 0xff36:
            case 0xff37:
            case 0xff38:
            case 0xff39:
            case 0xff3a:
            case 0xff3b:
            case 0xff3c:
            case 0xff3d:
            case 0xff3e:
            case 0xff3f:
                ((VoluntaryWaveChannel) this.sound.getSoundChannels()[2]).setWavePattern(adr & 0x0f, data);
                break;
            // video-related registers
            case LCD_CONTROL: {
                final VideoChip vc = this.video;

                vc.setLCDEnabled((data & 0x80) != 0);
                vc.setWindowTileArea((data & 0x40) != 0 ? 0x9c00 : 0x9800);
                vc.setWindowEnabled((data & 0x20) != 0);
                vc.setTileDataArea((data & 0x10) != 0 ? VRAM_AREA : VRAM_AREA + 0x800);
                vc.setBackgroundTileArea((data & 0x08) != 0 ? 0x9c00 : 0x9800);
                vc.setSpriteHeight((data & 0x04) != 0 ? VideoChip.TILE_HEIGHT * 2 : VideoChip.TILE_HEIGHT);
                vc.setSpritesEnabled((data & 0x02) != 0);
                if (this.cartridge.isGBC()) {
                    vc.setHaveSpritesPriority((data & 0x01) == 0);
                } else {
                    vc.setBackgroundBlank((data & 0x01) == 0);
                }
                break;
            }
            case LCD_STATUS: {
                data = (byte) ((this.memory[adr] & 0x87) | (data & 0x78));

                final VideoChip vc = this.video;

                vc.setCoincidenceIRQEnabled((data & 0x40) != 0);
                vc.setOAMIRQEnabled((data & 0x20) != 0);
                vc.setVBlankIRQEnabled((data & 0x10) != 0);
                vc.setHBlankIRQEnabled((data & 0x08) != 0);

                // Gameboy LCD bug
                if (vc.isLCDEnabled() && (data & 0x03) == 0x01 && (data & 0x44) != 0x44) {
                    requestIRQ(IRQ_LCDSTAT);
                }
                break;
            }
            case 0xff42:
                this.video.setScrollY(data & 0xff);
                break;
            case 0xff43:
                this.video.setScrollX(data & 0xff);
                break;
            case 0xff45:
                if (this.video.isLCDEnabled()) {
                    this.video.checkCoincidenceIRQ();
                }
                break;
            case 0xff46: {
                // DMA transfer
                final int source = (data & 0xff) << 8;

                if (DEBUG_DMA) {
                    System.out.println("DMA copy from " + Integer.toHexString(source) + " to OAM");
                }

                for (int i = 0; i < 0xa0; ++i) {
                    writeOAM(HIGH_RAM_AREA + i, (byte) readByte(source + i));
                }
                break;
            }
            case 0xff47:
            case 0xff48:
            case 0xff49: {
                if (!this.cartridge.isGBC()) {
                    final int palette = adr == 0xff47 ? VideoChip.PALETTE_BACKGROUND : VideoChip.PALETTE_SPRITES + (adr - 0xff48);
                    final int[] colors = {0xffffffff, 0xffaaaaaa, 0xff555555, 0xff000000};

                    for (int i = 0, shift = 0; i < ColorPalette.COLORS_PER_PALETTE; ++i, shift += 2) {
                        final int col = colors[((data & 0xff) >> shift) & 0x03];

                        this.video.getColorPalettes()[palette].setColor(i, col);
                        this.video.invalidateTiles();
                    }
                }
                break;
            }
            case 0xff4a:
                this.video.setWindowY(data & 0xff);
                break;
            case 0xff4b:
                this.video.setWindowX((data & 0xff) - 7);
                break;
            case 0xff4f:
                this.video.setGBCVRAMBank(data & 0x01);
                break;
            case HDMA_CONTROL: {
                if (!isHDMARunning()) {
                    // start HDMA transfer?
                    if ((data & 0x80) == 0 || IMMEDIATE_HDMA) {
                        // do General Purpose DMA, copying all blocks at once
                        final int len = ((data & 0x7f) + 1);

                        performHDMA(len);
                        this.isHDMARunning = false;
                    } else {
                        // do H-Blank DMA, copying 1 block per H-Blank
                        data &= 0x7f;
                        this.isHDMARunning = true;
                    }
                } else {
                    // terminate the transfer?
                    if ((data & 0x80) == 0) {
                        // then set bit 7 to indicate completed transfer
                        data |= 0x80;
                        this.isHDMARunning = false;
                    } else {
                        // continue transfer
                        data &= 0x7f;
                        this.isHDMARunning = true;
                    }
                }
                break;
            }
            case 0xff69:
            case 0xff6b: {
                if (this.cartridge.isGBC()) {
                    // get index in palette memory and palette
                    final int index = this.memory[adr - 1] & 0xff;
                    final int palette = (adr == 0xff69 ? VideoChip.PALETTE_BACKGROUND : VideoChip.PALETTE_SPRITES) * ColorPalette.COLORS_PER_PALETTE << 1;

                    // set new value
                    this.video.setColorByte(palette + (index & (VideoChip.NUM_COLORS * 2 - 1)), data & 0xff);
                    // auto-increment palette index register?
                    if (index >= 0x80) {
                        this.memory[adr - 1] = (byte) (0x80 | ((index + 1) & (VideoChip.NUM_COLORS * 2 - 1)));
                    }
                }
                break;
            }
            // select GBC WRAM bank
            case 0xff70:
                if (this.cartridge.isGBC()) {
                    setGBCRAMBank(Math.max(data & 0x07, 1));
                }
                break;
            // write to IRQ enable register
            case INTERRUPT_ENABLE:
                if (DEBUG_INTERRUPTS) {
                    System.out.println("Enabled IRQs $" + Integer.toHexString(data & 0xff) + " at cycle " + this.cycles);
                }
                this.irqsEnabled = data & 0xff;
                break;
            default:
                ;
        }

        // write data to memory, the data might have been modified above
        this.memory[adr] = data;
    }

    /**
     * Set new GBC RAM bank to be active at $d000-$dfff of the main memory.
     * Also copies the old RAM back to the cartridge and the new RAM from the cartridge to main memory.
     * 
     * @param ramBank	RAM bank number to activate
     */
    private void setGBCRAMBank(final int ramBank) {
        if (this.currentGBCRAMBank != ramBank) {
            // copy content of old RAM bank back to cartridge
            System.arraycopy(this.memory, SWITCHABLE_WRAM_AREA, this.gbcRAMBanks[this.currentGBCRAMBank - 1], 0, WRAM_BANK_SIZE);
            // copy content of RAM on cartridge to memory
            this.currentGBCRAMBank = ramBank;
            System.arraycopy(this.gbcRAMBanks[this.currentGBCRAMBank - 1], 0, this.memory, SWITCHABLE_WRAM_AREA, WRAM_BANK_SIZE);
        }
    }

    // implementation of the Throttleable interface
    public final void throttle(final long ms) {
        this.throttledMillis += ms;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
        }
    }

    public final long getThrottledTime() {
        return this.throttledMillis;
    }

    public final void resetThrottleTime() {
        this.throttledMillis = 0;
    }

    // implementation of the Serializable interface
    public void serialize(final DataOutputStream out) throws IOException {
        SerializationUtils.serialize(out, this.memory);
        out.writeLong(this.cycles);
        out.writeInt(this.a);
        out.writeInt(this.b);
        out.writeInt(this.c);
        out.writeInt(this.d);
        out.writeInt(this.e);
        out.writeInt(this.f);
        out.writeInt(this.hl);
        out.writeInt(this.sp);
        out.writeInt(this.pc);
        out.writeBoolean(this.isInterruptEnabled);
        out.writeBoolean(this.doesInterruptEnableSwitch);
        out.writeInt(this.irqsRequested);
        out.writeInt(this.irqsEnabled);
        out.writeInt(this.currentGBCRAMBank);
        for (int i = 0; i < this.gbcRAMBanks.length; ++i) {
            SerializationUtils.serialize(out, this.gbcRAMBanks[i]);
        }
        timer.serialize(out);
        out.writeLong(this.nextEvent);
        out.writeInt(this.cpuSpeed);
        out.writeBoolean(this.hasSoundListener);
        out.writeBoolean(this.isHDMARunning);
        this.cartridge.serialize(out);
    }

    public void deserialize(final DataInputStream in) throws IOException {
        SerializationUtils.deserialize(in, this.memory);
        this.cycles = in.readLong();
        this.a = in.readInt();
        this.b = in.readInt();
        this.c = in.readInt();
        this.d = in.readInt();
        this.e = in.readInt();
        this.f = in.readInt();
        this.hl = in.readInt();
        this.sp = in.readInt();
        this.pc = in.readInt();
        this.isInterruptEnabled = in.readBoolean();
        this.doesInterruptEnableSwitch = in.readBoolean();
        this.irqsRequested = in.readInt();
        this.irqsEnabled = in.readInt();
        this.currentGBCRAMBank = in.readInt();
        for (int i = 0; i < this.gbcRAMBanks.length; ++i) {
            SerializationUtils.deserialize(in, this.gbcRAMBanks[i]);
        }
        timer.deserialize(in);
        this.nextEvent = in.readLong();
        this.cpuSpeed = in.readInt();
        this.hasSoundListener = in.readBoolean();
        this.isHDMARunning = in.readBoolean();
        this.cartridge.deserialize(in);
    }

    // inner class for Timer handling
    class Timer implements Serializable {

        /**
         * CPU cycle when the Divider Register was reset the last time
         */
        private long divCycle;
        /**
         * Instructions we execute per TIMA increase
         */
        private int instructionsPerTima = Integer.MAX_VALUE;
        /**
         * CPU cycle when the timer was last started
         */
        private long lastStartedCycle;
        /**
         * Timer Modulo
         */
        private int tma;
        /**
         * Timer Control
         */
        private int tac;
        /**
         * Timer Counter
         */
        private int tima;
        /**
         * next CPU cycle when we will trigger an IRQ
         */
        private long nextTimerIRQRequest = Long.MAX_VALUE;

        /**
         * Get the timer divider
         * 
         * @return  divider register value
         */
        public int getDiv() {
            return (int) ((getCycles() - this.divCycle) / instructionsPerDiv) & 0xff;
        }

        /**
         * Reset the timer divider to zero
         * 
         * @param   div does not matter, any value resets the register to zero
         */
        public final void setDiv(final int div) {
            this.divCycle = getCycles();
        }

        /**
         * Get the timer counter
         * 
         * @return  counter register value
         */
        public final int getTima() {
            if (isRunning()) {
                return (this.tima + (int) ((getCycles() - this.lastStartedCycle) / this.instructionsPerTima)) & 0xff;
            } else {
                return this.tima;
            }
        }

        /**
         * Set the timer counter
         * 
         * @param   tima    new timer counter
         */
        public final void setTima(final int tima) {
            this.tima = tima;
            determineNextIRQ();
        }

        /**
         * Get the timer modulo
         * 
         * @return  modulo register value
         */
        public final int getTma() {
            return tma;
        }

        /**
         * Set the timer modulo
         * 
         * @param   tima    new timer modulo
         */
        public final void setTma(final int tma) {
            this.tma = tma;
        }

        /**
         * Get the timer control
         * 
         * @return  control register value
         */
        public final int getTac() {
            return 0xf8 & this.tac;
        }

        /**
         * Set the timer control
         * 
         * @param   tima    new timer control
         */
        public final void setTac(final int tac) {
            this.tac = tac;
            switch (tac & 0x03) {
                case 0:
                    this.instructionsPerTima = cpuSpeed / 4096;
                    break;
                case 1:
                    this.instructionsPerTima = cpuSpeed / 262144;
                    break;
                case 2:
                    this.instructionsPerTima = cpuSpeed / 65536;
                    break;
                case 3:
                    this.instructionsPerTima = cpuSpeed / 16384;
                    break;
            }
            determineNextIRQ();
        }

        /**
         * Check whether the timer is running
         * 
         * @return  true if the timer is currently running
         */
        public final boolean isRunning() {
            return (this.tac & 0x04) != 0;
        }

        /**
         * Determine the CPU cycle when we raise the next timer IRQ.
         * This method also sets the CPU's next IRQ request cycle if necessary.
         */
        private final void determineNextIRQ() {
            this.tima = getTima();
            if (isRunning()) {
                this.lastStartedCycle = getCycles();
                this.nextTimerIRQRequest = this.lastStartedCycle + this.instructionsPerTima * (0x100 - this.tima);
                if (this.nextTimerIRQRequest < nextEvent) {
                    nextEvent = this.nextTimerIRQRequest;
                }
            } else {
                this.nextTimerIRQRequest = Long.MAX_VALUE;
                this.instructionsPerTima = Integer.MAX_VALUE;
            }
        }

        /**
         * Restart the timer after a timer IRQ was raised
         */
        protected final void restart() {
            setTima(getTma());
        }

        /**
         * Get the CPU cycle when the next timer IRQ will be raised
         * 
         * @return  CPU cycle or Long.MAX_VALUE if the timer is inactive
         */
        protected final long getNextIRQRequest() {
            return this.nextTimerIRQRequest;
        }

        // implementation of the Serializable interface
        public void serialize(final DataOutputStream out) throws IOException {
            out.writeLong(this.divCycle);
            out.writeInt(this.instructionsPerTima);
            out.writeLong(this.lastStartedCycle);
            out.writeInt(this.tma);
            out.writeInt(this.tac);
            out.writeLong(this.nextTimerIRQRequest);
        }

        public void deserialize(final DataInputStream in) throws IOException {
            this.divCycle = in.readLong();
            this.instructionsPerTima = in.readInt();
            this.lastStartedCycle = in.readLong();
            this.tma = in.readInt();
            this.tac = in.readInt();
            this.nextTimerIRQRequest = in.readLong();
        }
    }
}
