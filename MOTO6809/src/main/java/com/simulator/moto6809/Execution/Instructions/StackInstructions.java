package com.simulator.moto6809.Execution.Instructions;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
PSHS, PULS,PSHU, PULU
 */
/*
push(StackType stack, int registerMask);
pull(StackType stack, int registerMask);

 */

public final class StackInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            PSHS, PULS,
            PSHU, PULU
    );

    private StackInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        String m = instr.mnemonic().toUpperCase();
        int mask = instr.operand() & 0xFF;

        switch (m) {


            // PSHS — Push on System Stack (S)

            case PSHS -> {
                if ((mask & 0x80) != 0) StackHelpers.pushWordS(regs, mem, regs.getRegister(Register.PC, false));
                if ((mask & 0x40) != 0) StackHelpers.pushWordS(regs, mem, regs.getRegister(Register.U,  false));
                if ((mask & 0x20) != 0) StackHelpers.pushWordS(regs, mem, regs.getRegister(Register.Y,  false));
                if ((mask & 0x10) != 0) StackHelpers.pushWordS(regs, mem, regs.getRegister(Register.X,  false));
                if ((mask & 0x08) != 0) StackHelpers.pushByteS(regs, mem, regs.getRegister(Register.DP, false));
                if ((mask & 0x04) != 0) StackHelpers.pushByteS(regs, mem, regs.getRegister(Register.B,  false));
                if ((mask & 0x02) != 0) StackHelpers.pushByteS(regs, mem, regs.getRegister(Register.A,  false));
                if ((mask & 0x01) != 0) StackHelpers.pushByteS(regs, mem, regs.getRegister(Register.CC, false));
            }


            // PULS — Pull from System Stack (S)

            case PULS -> {
                if ((mask & 0x01) != 0) regs.setRegister(Register.CC, StackHelpers.pullByteS(regs, mem), false);
                if ((mask & 0x02) != 0) regs.setRegister(Register.A,  StackHelpers.pullByteS(regs, mem), false);
                if ((mask & 0x04) != 0) regs.setRegister(Register.B,  StackHelpers.pullByteS(regs, mem), false);
                if ((mask & 0x08) != 0) regs.setRegister(Register.DP, StackHelpers.pullByteS(regs, mem), false);
                if ((mask & 0x10) != 0) regs.setRegister(Register.X,  StackHelpers.pullWordS(regs, mem), false);
                if ((mask & 0x20) != 0) regs.setRegister(Register.Y,  StackHelpers.pullWordS(regs, mem), false);
                if ((mask & 0x40) != 0) regs.setRegister(Register.U,  StackHelpers.pullWordS(regs, mem), false);
                if ((mask & 0x80) != 0) regs.setRegister(Register.PC, StackHelpers.pullWordS(regs, mem), false);
            }


            // PSHU — Push on User Stack (U)

            case PSHU -> {
                if ((mask & 0x80) != 0) StackHelpers.pushWordU(regs, mem, regs.getRegister(Register.PC, false));
                if ((mask & 0x40) != 0) StackHelpers.pushWordU(regs, mem, regs.getRegister(Register.S,  false));
                if ((mask & 0x20) != 0) StackHelpers.pushWordU(regs, mem, regs.getRegister(Register.Y,  false));
                if ((mask & 0x10) != 0) StackHelpers.pushWordU(regs, mem, regs.getRegister(Register.X,  false));
                if ((mask & 0x08) != 0) StackHelpers.pushByteU(regs, mem, regs.getRegister(Register.DP, false));
                if ((mask & 0x04) != 0) StackHelpers.pushByteU(regs, mem, regs.getRegister(Register.B,  false));
                if ((mask & 0x02) != 0) StackHelpers.pushByteU(regs, mem, regs.getRegister(Register.A,  false));
                if ((mask & 0x01) != 0) StackHelpers.pushByteU(regs, mem, regs.getRegister(Register.CC, false));
            }


            // PULU — Pull from User Stack (U)

            case PULU -> {
                if ((mask & 0x01) != 0) regs.setRegister(Register.CC, StackHelpers.pullByteU(regs, mem), false);
                if ((mask & 0x02) != 0) regs.setRegister(Register.A,  StackHelpers.pullByteU(regs, mem), false);
                if ((mask & 0x04) != 0) regs.setRegister(Register.B,  StackHelpers.pullByteU(regs, mem), false);
                if ((mask & 0x08) != 0) regs.setRegister(Register.DP, StackHelpers.pullByteU(regs, mem), false);
                if ((mask & 0x10) != 0) regs.setRegister(Register.X,  StackHelpers.pullWordU(regs, mem), false);
                if ((mask & 0x20) != 0) regs.setRegister(Register.Y,  StackHelpers.pullWordU(regs, mem), false);
                if ((mask & 0x40) != 0) regs.setRegister(Register.S,  StackHelpers.pullWordU(regs, mem), false);
                if ((mask & 0x80) != 0) regs.setRegister(Register.PC, StackHelpers.pullWordU(regs, mem), false);
            }

            default -> throw new UnsupportedOperationException(
                    "Stack instruction not supported: " + m
            );
        }

        return instr.cycles();
    }
}




