package com.simulator.moto6809.Debugger;

public class Breakpoint {

    private final int address;
    private boolean enabled = true;

    public Breakpoint(int address) {
        this.address = address & 0xFFFF;
    }

    public int getAddress() {
        return address;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

