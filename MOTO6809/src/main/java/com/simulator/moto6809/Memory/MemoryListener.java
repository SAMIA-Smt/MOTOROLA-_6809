package com.simulator.moto6809.Memory;

public interface MemoryListener {

    /**
     * Called when a memory byte is written
     */
    void onMemoryWrite(int address, int value);

    /**
     * Called when memory is reset or flushed
     */
    void onMemoryReset();
}