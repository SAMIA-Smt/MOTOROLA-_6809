package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.AddressingHelpers.computeEffectiveAddress;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
CMPA, CMPB, CMPD,CMPX, CMPY,CMPU, CMPS,
 */
/*
compare(Register target, int operand);
 */
public class CompareInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            CMPA, CMPB, CMPD,CMPX, CMPY,CMPU, CMPS
    );

    private CompareInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        Register reg = Mnemonics.getMnemonicRegister(instr.mnemonic());
        if (reg == null)
            throw new IllegalStateException("CMP without register: " + instr.mnemonic());

        boolean is16 = regs.is16BitRegister(reg);

        int regVal = regs.getRegister(reg);
        int operand = fetchOperand(instr, regs, mem, is16);

        int mask = is16 ? 0xFFFF : 0xFF;
        int result = (regVal - operand) & mask;


        // Flags

        regs.updateNZ(result, is16);

        // Carry = borrow
        regs.setFlag(Flag.C, regVal < operand);

        // Overflow
        int signBit = is16 ? 0x8000 : 0x80;
        boolean overflow =
                ((regVal ^ operand) & (regVal ^ result) & signBit) != 0;
        regs.setFlag(Flag.V, overflow);

        return instr.cycles();
    }


    // Operand fetch helper


    private static int fetchOperand(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem,
            boolean is16
    ) {
        return switch (instr.addressingMode()) {
            case IMMEDIATE -> instr.operand() & (is16 ? 0xFFFF : 0xFF);

            case DIRECT, EXTENDED, INDEXED -> {
                int ea = computeEffectiveAddress(instr, regs, mem);
                if (is16)
                    yield mem.readWord(ea) & 0xFFFF;
                else
                    yield mem.read(ea) & 0xFF;
            }

            default -> throw new IllegalStateException(
                    "Invalid addressing mode for CMP: " + instr.addressingMode()
            );
        };
    }

}

