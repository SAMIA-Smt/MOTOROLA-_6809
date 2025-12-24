package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

import java.util.Set;

import static com.simulator.moto6809.Execution.Instructions.AddressingHelpers.computeEffectiveAddress;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
JMP,JSR,BSR,LBSR,RTS,RTI,
 */
/*
jmp(int address);
jsr(int address);
rts();
rti();
 */
public final class JumpInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            JMP, JSR, BSR, LBSR
    );

    private JumpInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        switch (instr.mnemonic()) {

            case JMP -> {
                int ea = computeEffectiveAddress(instr, regs, mem);//EA = PC (or PC + offset depending on fetch stage)..EA = -1 (or a defined constant like NO_EA)
                regs.setRegister(Register.PC, ea);
            }

            case JSR -> {
                int ea = computeEffectiveAddress(instr, regs, mem);
                StackHelpers.pushWordS(regs, mem, instr.nextPc());
                regs.setRegister(Register.PC, ea);
            }

            case BSR, LBSR -> {
                StackHelpers.pushWordS(regs, mem, instr.nextPc());
                regs.setRegister(Register.PC, instr.relativeTargetAddress());
            }

            default -> throw new IllegalStateException();
        }

        return instr.cycles();
    }
}

