package com.simulator.moto6809.Decoder;

import java.util.EnumMap;
import java.util.Map;

public class InstructionDefinition {

    private final String mnemonic;

    private final Map<AddressingMode, Integer> opcodeMap = new EnumMap<>(AddressingMode.class);
    private final Map<AddressingMode, Integer> cycleMap  = new EnumMap<>(AddressingMode.class);
    private final Map<AddressingMode, Integer> sizeMap   = new EnumMap<>(AddressingMode.class);

    public InstructionDefinition(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    public String getMnemonic() {
        return mnemonic;
    }

    // Registration
    public void addOpcode(AddressingMode mode, int opcode) {
        opcodeMap.put(mode, opcode & 0xFFFF);
    }

    public void addCycles(AddressingMode mode, int cycles) {
        cycleMap.put(mode, cycles);
    }

    public void addSize(AddressingMode mode, int size) {
        sizeMap.put(mode, size);
    }

    // Queries
    public boolean supports(AddressingMode mode) {
        return opcodeMap.containsKey(mode);
    }

    public int getOpcode(AddressingMode mode) {
        Integer v = opcodeMap.get(mode);
        if (v == null) {
            throw new IllegalStateException("No opcode registered for " + mnemonic + " mode=" + mode);
        }
        return v & 0xFFFF;
    }

    public int getCycles(AddressingMode mode) {
        Integer v = cycleMap.get(mode);
        if (v == null) {
            throw new IllegalStateException("No cycles registered for " + mnemonic + " mode=" + mode);
        }
        return v;
    }

    public int getSize(AddressingMode mode) {
        Integer v = sizeMap.get(mode);
        if (v == null) {
            throw new IllegalStateException("No size registered for " + mnemonic + " mode=" + mode);
        }
        return v;
    }
}