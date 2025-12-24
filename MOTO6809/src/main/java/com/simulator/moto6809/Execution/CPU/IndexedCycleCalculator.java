package com.simulator.moto6809.Execution.CPU;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.DecodedInstruction;

public final class IndexedCycleCalculator {

    private IndexedCycleCalculator() {}

    /**
     * Returns extra cycles for indexed addressing.
     * Convention used here:
     * - 5-bit offset (bit7=0): 0 penalty
     * - for bit7=1: penalty depends on low nibble
     * - indirect adds +3 EXCEPT mode 0xF which is already an indirect form.
     */
    public static int computePenalty(DecodedInstruction instr) {

        if (instr.addressingMode() != AddressingMode.INDEXED) return 0;

        byte[] bytes = instr.bytes();
        if (bytes == null || bytes.length < 2) return 0;

        int opcodeBytes = instr.opcodeByteCount();
        int post = bytes[opcodeBytes] & 0xFF;

        // 5-bit offset => no penalty
        if ((post & 0x80) == 0) return 0;

        int mode = post & 0x0F;
        boolean indirectFlag = (post & 0x10) != 0;

        int penalty = switch (mode) {
            case 0x0, 0x2 -> 1; // ,R+  and ,-R
            case 0x1, 0x3 -> 2; // ,R++ and ,--R
            case 0x8       -> 1; // 8-bit offset
            case 0x9, 0xB  -> 4; // 16-bit offset, D,R
            case 0xC       -> 1; // 8-bit PC-relative
            case 0xD       -> 5; // 16-bit PC-relative
            case 0xF       -> 6; // [nn] extended indirect
            default        -> 0;
        };

        // mode 0xF is already an indirect addressing form (do not add +3 again)
        if (indirectFlag && mode != 0xF) penalty += 3;

        return penalty;
    }
}