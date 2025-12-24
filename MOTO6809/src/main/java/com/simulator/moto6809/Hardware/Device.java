package com.simulator.moto6809.Hardware;

public interface Device {
    boolean handles(int address);
    int read(int address);
    void write(int address, int value);
}

