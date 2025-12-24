package com.simulator.moto6809.Execution.CPU;

/**
 * Simple cycle counter (can be extended later for accurate penalties/interrupt timing).
 */
public class CycleCounter {

    private long totalCycles = 0;

    public void reset() {
        totalCycles = 0;
    }

    public void add(int cycles) {
        if (cycles < 0) cycles = 0;
        totalCycles += cycles;
    }

    public long getTotalCycles() {
        return totalCycles;
    }
}

