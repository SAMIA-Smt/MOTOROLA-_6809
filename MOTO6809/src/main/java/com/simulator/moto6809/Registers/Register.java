package com.simulator.moto6809.Registers;

public enum Register {
    A,        // Accumulator A (8-bit)
    B,        // Accumulator B (8-bit)
    D,        // Virtual register (A:B combined)
    X,        // Index register X (16-bit)
    Y,        // Index register Y (16-bit)
    S,        // System stack pointer (16-bit)
    U,        // User stack pointer (16-bit)
    PC,       // Program counter (16-bit)
    DP,       // Direct page register (8-bit)
    CC        // Condition code register (8-bit)
}

