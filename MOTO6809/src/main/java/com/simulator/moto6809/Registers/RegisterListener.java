package com.simulator.moto6809.Registers;

public interface RegisterListener {

    /**
     * Called whenever a register or flag changes
     */
    void onRegisterChanged(Register register, int newValue);
}

