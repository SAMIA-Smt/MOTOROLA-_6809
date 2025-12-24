package com.simulator.moto6809.Execution.Instructions;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;
/*
INC, INCA, INCB,DEC, DECA, DECB,NEG, NEGA, NEGB,
CLR, CLRA, CLRB,COM, COMA, COMB,TST, TSTA, TSTB

 */
/*
inc(Register target);
dec(Register target);
neg(Register target);
clr(Register target);
com(Register target);
tst(Register target);
 */

public final class UnaryInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            INC, INCA, INCB,
            DEC, DECA, DECB,
            NEG, NEGA, NEGB,
            COM, COMA, COMB,
            CLR, CLRA, CLRB,
            TST, TSTA, TSTB
    );

    private UnaryInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        boolean isRegister = instr.addressingMode() == AddressingMode.INHERENT;

        int value;
        Integer ea = null;
        Register reg = null;

        if (isRegister) {
            reg = Mnemonics.getMnemonicRegister(instr.mnemonic());
            value = regs.getRegister(reg) & 0xFF;
        } else {
            ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);
            value = mem.read(ea) & 0xFF;
        }

        int result = value;
        boolean writeBack = true;

        switch (instr.mnemonic()) {


            // INC

            case INC, INCA, INCB -> {
                result = (value + 1) & 0xFF;
                regs.updateNZ(result);
                regs.setFlag(Flag.V, result == 0x80);
                return write(instr, regs, mem, isRegister, reg, ea, result);
            }


            // DEC

            case DEC, DECA, DECB -> {
                result = (value - 1) & 0xFF;
                regs.updateNZ(result);
                regs.setFlag(Flag.V, result == 0x7F);
                return write(instr, regs, mem, isRegister, reg, ea, result);
            }


            // NEG

            case NEG, NEGA, NEGB -> {
                result = (-value) & 0xFF;
                regs.updateNZ(result);
                regs.setFlag(Flag.C, value != 0);
                regs.setFlag(Flag.V, result == 0x80);
                return write(instr, regs, mem, isRegister, reg, ea, result);
            }


            // COM

            case COM, COMA, COMB -> {
                result = (~value) & 0xFF;
                regs.updateNZ(result);
                regs.setFlag(Flag.C, true);
                regs.setFlag(Flag.V, false);
                return write(instr, regs, mem, isRegister, reg, ea, result);
            }


            // CLR

            case CLR, CLRA, CLRB -> {
                result = 0;
                regs.setFlag(Flag.N, false);
                regs.setFlag(Flag.Z, true);
                regs.setFlag(Flag.V, false);
                regs.setFlag(Flag.C, false);
                return write(instr, regs, mem, isRegister, reg, ea, result);
            }


            // TST

            case TST, TSTA, TSTB -> {
                regs.updateNZ(value);
                regs.setFlag(Flag.V, false);
                writeBack = false;
                break;
            }

            default -> throw new IllegalStateException(
                    "Unsupported unary instruction: " + instr.mnemonic()
            );
        }

        return instr.cycles();
    }


    // Write-back helper

    private static int write(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem,
            boolean isRegister,
            Register reg,
            Integer ea,
            int result
    ) {
        if (isRegister)
            regs.setRegister(reg, result);
        else
            mem.write(ea, result);

        return instr.cycles();
    }
}

