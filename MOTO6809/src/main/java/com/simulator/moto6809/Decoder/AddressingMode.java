package com.simulator.moto6809.Decoder;

/**
 * Motorola 6809 addressing modes.
 *
 * This enum represents how an instruction accesses its operand.
 * It is used by:
 *  - InstructionSet
 *  - Decoder
 *  - DecodedInstruction
 *  - Execution helpers
 */
public enum AddressingMode {


    IMMEDIATE,

    DIRECT,

    INDEXED,

    EXTENDED,

    INHERENT,

    RELATIVE;


    // Helper methods


    /**
     * Returns true if this mode uses an operand byte(s)
     */
    public boolean hasOperand() {
        return this != INHERENT;
    }

    /**
     * Returns true if this addressing mode performs a branch
     */
    public boolean isRelative() {
        return this == RELATIVE;
    }

    /**
     * Returns true if this mode refers to memory
     */
    public boolean accessesMemory() {
        return this != IMMEDIATE && this != INHERENT;
    }
}
