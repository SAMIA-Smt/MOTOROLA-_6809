package com.simulator.moto6809.Hardware;
import java.util.ArrayList;
import java.util.List;

/**
 * Motorola MC6821 Peripheral Interface Adapter (PIA)
 *
 * Memory-mapped device (4 bytes):
 * base + 0 : Port A / DDRA
 * base + 1 : Control Register A (CRA)
 * base + 2 : Port B / DDRB
 * base + 3 : Control Register B (CRB)
 *
 * NOTE:
 * - This is a functional skeleton
 * - Full handshaking / IRQ logic can be added later
 */
public class MC6821PIA implements Device {


    // Memory mapping


    private final int baseAddress;


    // Internal registers (8-bit)


    private int portA;   // Peripheral register A
    private int portB;   // Peripheral register B
    private int ddra;    // Data Direction Register A
    private int ddrb;    // Data Direction Register B
    private int cra;     // Control Register A
    private int crb;     // Control Register B


    // Listeners (UI / Debugger ready)


    private final List<DeviceListener> listeners = new ArrayList<>();


    // Constructor


    public MC6821PIA(int baseAddress) {
        this.baseAddress = baseAddress & 0xFFFF;
        reset();
    }


    // Device interface


    @Override
    public boolean handles(int address) {
        address &= 0xFFFF;
        return address >= baseAddress && address < baseAddress + 4;
    }

    @Override
    public int read(int address) {
        int offset = (address & 0xFFFF) - baseAddress;

        return switch (offset) {
            case 0 -> readPortA();
            case 1 -> cra;
            case 2 -> readPortB();
            case 3 -> crb;
            default -> 0x00;
        };
    }

    @Override
    public void write(int address, int value) {
        int offset = (address & 0xFFFF) - baseAddress;
        value &= 0xFF;

        switch (offset) {
            case 0 -> writePortA(value);
            case 1 -> cra = value;
            case 2 -> writePortB(value);
            case 3 -> crb = value;
        }

        notifyListeners();
    }


    // Port handling (simplified, correct for MVP)


    private int readPortA() {
        // For now: return port value masked by DDRA
        return portA & ddra;
    }

    private int readPortB() {
        return portB & ddrb;
    }

    private void writePortA(int value) {
        // Output bits only where DDRA = 1
        portA = (portA & ~ddra) | (value & ddra);
    }

    private void writePortB(int value) {
        portB = (portB & ~ddrb) | (value & ddrb);
    }


    // Reset


    public void reset() {
        portA = 0x00;
        portB = 0x00;
        ddra  = 0x00;
        ddrb  = 0x00;
        cra   = 0x00;
        crb   = 0x00;
        notifyListeners();
    }


    // Listener support (UI-ready)


    public void addListener(DeviceListener listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (DeviceListener l : listeners) {
            l.onDeviceStateChanged(this);
        }
    }


    // Getters (for UI / Debugger later)


    public int getBaseAddress() { return baseAddress; }

    public int getPortA() { return portA; }
    public int getPortB() { return portB; }

    public int getDDRA() { return ddra; }
    public int getDDRB() { return ddrb; }

    public int getCRA() { return cra; }
    public int getCRB() { return crb; }


    // Manual setters (UI / Debug use)


    public void setPortA(int value) {
        portA = value & 0xFF;
        notifyListeners();
    }

    public void setPortB(int value) {
        portB = value & 0xFF;
        notifyListeners();
    }

    public void setDDRA(int value) {
        ddra = value & 0xFF;
        notifyListeners();
    }

    public void setDDRB(int value) {
        ddrb = value & 0xFF;
        notifyListeners();
    }

    public void setCRA(int value) {
        cra = value & 0xFF;
        notifyListeners();
    }

    public void setCRB(int value) {
        crb = value & 0xFF;
        notifyListeners();
    }
}

/*
==>What is intentionally NOT implemented (yet)

*IRQ A / IRQ B signaling

*CA1 / CA2 / CB1 / CB2 handshaking

*Cycle-accurate timing

*External pin simulation

==>These are advanced and can be layered later without changing this API.

*/


