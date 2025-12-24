package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
BRA, BRN,BHI, BLS,BCC, BCS,BNE, BEQ,BVC, BVS,BPL, BMI,BGE, BLT,BGT, BLE,
LBRA, LBRN,LBHI, LBLS,LBCC, LBCS,LBNE, LBEQ,LBVC, LBVS,LBPL, LBMI,LBGE, LBLT,LBGT, LBLE,
 */
/*
branch(boolean condition, int offset);
 */
public final class BranchInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            BRA, BRN, BHI, BLS, BCC, BCS, BNE, BEQ,
            BVC, BVS, BPL, BMI, BGE, BLT, BGT, BLE,
            LBRA, LBRN, LBHI, LBLS, LBCC, LBCS, LBNE, LBEQ,
            LBVC, LBVS, LBPL, LBMI, LBGE, LBLT, LBGT, LBLE
    );

    private BranchInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs
    ) {
        boolean takeBranch = switch (instr.mnemonic()) {

            case BRA, LBRA -> true;
            case BRN, LBRN -> false;

            case BHI, LBHI -> !regs.getFlag(Flag.C) && !regs.getFlag(Flag.Z);
            case BLS, LBLS -> regs.getFlag(Flag.C) || regs.getFlag(Flag.Z);

            case BCC, LBCC -> !regs.getFlag(Flag.C);
            case BCS, LBCS -> regs.getFlag(Flag.C);

            case BNE, LBNE -> !regs.getFlag(Flag.Z);
            case BEQ, LBEQ -> regs.getFlag(Flag.Z);

            case BVC, LBVC -> !regs.getFlag(Flag.V);
            case BVS, LBVS -> regs.getFlag(Flag.V);

            case BPL, LBPL -> !regs.getFlag(Flag.N);
            case BMI, LBMI -> regs.getFlag(Flag.N);

            case BGE, LBGE -> regs.getFlag(Flag.N) == regs.getFlag(Flag.V);
            case BLT, LBLT -> regs.getFlag(Flag.N) != regs.getFlag(Flag.V);

            case BGT, LBGT ->
                    !regs.getFlag(Flag.Z) &&
                            (regs.getFlag(Flag.N) == regs.getFlag(Flag.V));

            case BLE, LBLE ->
                    regs.getFlag(Flag.Z) ||
                            (regs.getFlag(Flag.N) != regs.getFlag(Flag.V));

            default -> throw new IllegalStateException(
                    "Unsupported branch instruction: " + instr.mnemonic()
            );
        };

        if (takeBranch) {
            regs.setRegister(Register.PC, instr.relativeTargetAddress());
        } else {
            regs.setRegister(Register.PC, instr.nextPc());
        }

        return instr.cycles();
    }
}

