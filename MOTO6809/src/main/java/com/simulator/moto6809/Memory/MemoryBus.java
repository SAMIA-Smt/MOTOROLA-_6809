package com.simulator.moto6809.Memory;

public final class MemoryBus {
    public static final int ADDRESS_SPACE = 0x10000; // 64 KB

    private final byte[] memory;
    private int romStart; // inclusive
    private int romEnd;   // inclusive

    public MemoryBus(byte[] initialMemory) {
        if (initialMemory == null || initialMemory.length != ADDRESS_SPACE) {
            throw new IllegalArgumentException("Memory must be exactly 64KB");
        }
        this.memory = initialMemory;
        // By default: no ROM protection
        this.romStart = 0x10000;
        this.romEnd = 0x0000;
    }

    public MemoryBus(Memory mem) {
        if (mem == null) throw new IllegalArgumentException("mem is null");
        this.memory = mem.getMemory();
        syncRomRangeFrom(mem);
    }

    /** Keep ROM protection in sync with Memory layout (important if layout changes). */
    public void syncRomRangeFrom(Memory mem) {
        if (mem == null) return;
        this.romStart = mem.getROMstart() & 0xFFFF;
        this.romEnd   = mem.getROMend() & 0xFFFF;
    }

    public int romStart() { return romStart & 0xFFFF; }
    public int romEnd()   { return romEnd & 0xFFFF; }


    // BYTE access

    public int read(int address) {
        int a = address & 0xFFFF;
        return memory[a] & 0xFF;
    }

    public void write(int address, int value) {
        int a = address & 0xFFFF;
        if (a >= romStart && a <= romEnd) return; // ignore ROM writes
        memory[a] = (byte) (value & 0xFF);
    }


    // WORD access or BIG-ENDIAN

    public int readWord(int address) {
        int a = address & 0xFFFF;
        int hi = memory[a] & 0xFF;
        int lo = memory[(a + 1) & 0xFFFF] & 0xFF;
        return ((hi << 8) | lo) & 0xFFFF;
    }

    public void writeWord(int address, int value) {
        int a = address & 0xFFFF;
        int a2 = (a + 1) & 0xFFFF;
        int v = value & 0xFFFF;

        if ((a >= romStart && a <= romEnd) || (a2 >= romStart && a2 <= romEnd)) return;

        memory[a]  = (byte) ((v >> 8) & 0xFF);
        memory[a2] = (byte) (v & 0xFF);
    }

    // CLEAR RAM ONLY

    public void clearRamOnly() {
        for (int i = 0; i < ADDRESS_SPACE; i++) {
            if (i >= romStart && i <= romEnd) continue;
            memory[i] = 0;
        }
    }

    public byte[] getRawMemory() {
        return memory;
    }
}
