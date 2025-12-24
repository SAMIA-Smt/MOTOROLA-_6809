package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
ANDA, ANDB ,ORA, ORB,EORA, EORB,BITA, BITB,ANDCC,ORCC,
 */

/*
and(Register target, int operand);
or(Register target, int operand);
xor(Register target, int operand);
bitTest(Register target, int operand);
andCC(int mask);
orCC(int mask);
 */

public final class LogicalInstructions {
    private static final Set<String> SUPPORTED = Set.of(
            ANDA, ANDB,
            ORA, ORB,
            EORA, EORB,
            BITA, BITB,
            ANDCC, ORCC
    );

    private LogicalInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }


    // Dispatcher


    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        String m = instr.mnemonic();

        if (m.equals(ANDCC)) {
            execAndCC(instr, regs);
        }
        else if (m.equals(ORCC)) {
            execOrCC(instr, regs);
        }
        else {
            execAccumulatorLogic(instr, regs, mem);
        }

        return instr.cycles();
    }


    // ANDCC / ORCC  (RAW CC BIT OPERATIONS)


    private static void execAndCC(DecodedInstruction instr, RegisterFunctions regs) {
        int mask = instr.operand() & 0xFF;
        int cc   = regs.getRegister(Register.CC, false);
        regs.setRegister(Register.CC, cc & mask);
    }

    private static void execOrCC(DecodedInstruction instr, RegisterFunctions regs) {
        int mask = instr.operand() & 0xFF;
        int cc   = regs.getRegister(Register.CC, false);
        regs.setRegister(Register.CC, cc | mask);
    }


    // Accumulator logical instructions


    private static void execAccumulatorLogic(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        Register acc = Mnemonics.getMnemonicRegister(instr.mnemonic());
        if (acc == null) {
            throw new IllegalStateException(
                    "Logical instruction without accumulator: " + instr.mnemonic()
            );
        }

        int accVal = regs.getRegister(acc) & 0xFF;

        int operand = switch (instr.addressingMode()) {
            case IMMEDIATE -> instr.operand() & 0xFF;

            case DIRECT, EXTENDED, INDEXED -> {
                int ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);
                yield mem.read(ea) & 0xFF;
            }

            default -> throw new IllegalStateException(
                    "Invalid addressing mode for logical instruction: " +
                            instr.addressingMode()
            );
        };

        int result;

        if (instr.mnemonic().startsWith("AND")) {
            result = accVal & operand;
        }
        else if (instr.mnemonic().startsWith("OR")) {
            result = accVal | operand;
        }
        else if (instr.mnemonic().startsWith("EOR")) {
            result = accVal ^ operand;
        }
        else if (instr.mnemonic().startsWith("BIT")) {
            // BIT: flags only, accumulator unchanged
            result = accVal & operand;
            regs.updateNZ(result, false);
            regs.setFlag(Flag.V, false);
            return;
        }
        else {
            throw new IllegalStateException(
                    "Unsupported logical instruction: " + instr.mnemonic()
            );
        }

        regs.setRegister(acc, result);
        regs.updateNZ(result, false);
        regs.setFlag(Flag.V, false);
    }
}
