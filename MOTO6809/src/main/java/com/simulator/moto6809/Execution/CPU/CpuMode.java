package com.simulator.moto6809.Execution.CPU;

public enum CpuMode {
    RUNNING,
    HALTED,
    WAIT_SYNC,  // SYNC wait until interrupt
    WAIT_CWAI   // CWAI wait until interrupt (special)
}

