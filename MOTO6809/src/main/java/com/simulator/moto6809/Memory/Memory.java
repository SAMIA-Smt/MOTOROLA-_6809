package com.simulator.moto6809.Memory;

import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Memory {

    private final byte[] memory = new byte[0x10000]; // 64 KB

    private int RAMstart = 0x0000, RAMend = 0xDFFF;
    private int ROMstart = 0xE000, ROMend = 0xFFFF;

    private final ILogger logger;


    // Constructors

    public Memory(int ramStart, int ramEnd, int romStart, ILogger logger) {
        this.logger = logger;
        setMemoryBoundaries(ramStart, ramEnd);
        setROMStartAddress(romStart);
    }

    public Memory(ILogger logger) {
        this.logger = logger;
        initializeResetMemory();
    }

    // Default configuration (RAM 0–0xDFFF, ROM 0xE000–0xFFFF)
    public final void initializeResetMemory() {
        setMemoryBoundaries(0x0000, 0xDFFF);
        setROMStartAddress(0xE000);
    }

    // Boundary Configuration

    public void setMemoryBoundaries(int ramStartAddress, int ramEndAddress) {
        RAMstart = ramStartAddress & 0xFFFF;
        RAMend   = ramEndAddress   & 0xFFFF;

        if (logger != null) {
            logger.log(String.format("RAM = $%04X - $%04X", RAMstart, RAMend), LogLevel.INFO);
        }
    }

    public void setROMStartAddress(int romStartAddress) {
        ROMstart = romStartAddress & 0xFFFF;
        ROMend   = 0xFFFF;

        if (logger != null) {
            logger.log(String.format("ROM = $%04X - $%04X", ROMstart, ROMend), LogLevel.INFO);
        }
    }


    // Helper: read-only memory check

    public boolean isReadonly(int address) {
        int a = address & 0xFFFF;
        return a >= ROMstart && a <= ROMend;
    }


    // Memory read operations

    public int readMem(int address) {
        int a = address & 0xFFFF;
        return memory[a] & 0xFF;
    }

    public int readMemWord(int address) {
        int a = address & 0xFFFF;
        int high = readMem(a);
        int low  = readMem((a + 1) & 0xFFFF);
        return ((high << 8) | low) & 0xFFFF;
    }


    // Memory write operations

    public void writeToMem(int address, byte newValue) {
        int a = address & 0xFFFF;

        if (isReadonly(a)) {
            if (logger != null) {
                logger.log(String.format("Attempted ROM write at $%04X – ignored", a), LogLevel.WARNING);
            }
            return;
        }

        memory[a] = newValue;
        notifyWrite(a, newValue & 0xFF);
    }

    public void writeWordToMem(int address, int newValue) {
        int a = address & 0xFFFF;
        int a2 = (a + 1) & 0xFFFF;
        int v = newValue & 0xFFFF;

        if (isReadonly(a) || isReadonly(a2)) {
            if (logger != null) {
                logger.log(String.format("Attempted word write to ROM at $%04X – ignored", a), LogLevel.WARNING);
            }
            return;
        }

        writeToMem(a, (byte) ((v >> 8) & 0xFF));
        writeToMem(a2, (byte) (v & 0xFF));
    }


    // Memory management


    /** Efface seulement la RAM (préserve la ROM). */
    public void flushRamOnly() {
        for (int i = 0; i < memory.length; i++) {
            if (i >= ROMstart && i <= ROMend) continue;
            memory[i] = 0;
        }
        notifyReset();
        if (logger != null) logger.log("Memory flushed (RAM only)", LogLevel.DEBUG);
    }

    /** Efface RAM + ROM (à utiliser rarement). */
    public void flushAll() {
        Arrays.fill(memory, (byte) 0);
        notifyReset();
        if (logger != null) logger.log("Memory flushed (RAM + ROM)", LogLevel.DEBUG);
    }

    public void resetMemory() {
        flushAll();
        initializeResetMemory();
        notifyReset();
        if (logger != null) logger.log("Memory reset to default layout", LogLevel.INFO);
    }


    // Loading byte arrays (ROM, programs, etc.)

    public void loadBytes(int startAddress, byte[] data, boolean allowROMWrite) {
        int addr = startAddress & 0xFFFF;

        for (byte b : data) {
            if (!allowROMWrite && isReadonly(addr)) {
                if (logger != null) {
                    logger.log(String.format("Skipping ROM write @ $%04X during load", addr), LogLevel.WARNING);
                }
            } else {
                memory[addr] = b;
                notifyWrite(addr, b & 0xFF);
            }
            addr = (addr + 1) & 0xFFFF;
        }
    }

    public byte[] getMemory() {
        return memory; // IMPORTANT: returns same reference (no clone)
    }

    public int getROMstart() { return ROMstart & 0xFFFF; }
    public int getROMend()   { return ROMend & 0xFFFF; }
    public int getRAMstart() { return RAMstart & 0xFFFF; }
    public int getRAMend()   { return RAMend & 0xFFFF; }


    // Listeners

    private final List<MemoryListener> listeners = new ArrayList<>();

    public void addListener(MemoryListener listener) {
        if (listener != null) listeners.add(listener);
    }

    private void notifyWrite(int address, int value) {
        for (MemoryListener l : listeners) {
            l.onMemoryWrite(address & 0xFFFF, value & 0xFF);
        }
    }

    private void notifyReset() {
        for (MemoryListener l : listeners) {
            l.onMemoryReset();
        }
    }

    public void clearRom(boolean keepVectors) {
        int start = ROMstart & 0xFFFF;
        int end = 0xFFFF;

        if (!keepVectors) {
            java.util.Arrays.fill(memory, start, end + 1, (byte) 0);
            notifyReset();
            return;
        }

        byte[] saved = new byte[0x10000 - 0xFFF2];
        System.arraycopy(memory, 0xFFF2, saved, 0, saved.length);

        java.util.Arrays.fill(memory, start, end + 1, (byte) 0);
        System.arraycopy(saved, 0xFFF2, memory, 0xFFF2, saved.length);

        notifyReset();
    }

}