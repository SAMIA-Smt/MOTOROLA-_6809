package com.simulator.moto6809.Execution.Instructions;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import com.simulator.moto6809.Memory.MemoryBus;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

public final class RegisterTransferInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            TFR, EXG, ABX
    );

    private RegisterTransferInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        String m = instr.mnemonic();

        return switch (m) {
            case TFR -> execTfr(instr, regs);
            case EXG -> execExg(instr, regs);
            case ABX -> execAbx(regs);
            default -> throw new UnsupportedOperationException(
                    "RegisterTransfer not implemented: " + m
            );
        };
    }


    // ABX : X = X + B

    private static int execAbx(RegisterFunctions r) {
        int x = r.getRegister(Register.X);
        int b = r.getRegister(Register.B);

        r.setRegister(Register.X, (x + b) & 0xFFFF);

        // ABX does NOT affect flags
        return 3;
    }


    // TFR : Transfer register

    private static int execTfr(DecodedInstruction instr, RegisterFunctions r) {
        int post = instr.operand() & 0xFF;

        Register src = decodeRegister((post >> 4) & 0x0F);
        Register dst = decodeRegister(post & 0x0F);

        ensureSameSize(src, dst);

        int value = r.getRegister(src);
        r.setRegister(dst, value);

        return 6;
    }


    // EXG : Exchange registers

    private static int execExg(DecodedInstruction instr, RegisterFunctions r) {
        int post = instr.operand() & 0xFF;

        Register r1 = decodeRegister((post >> 4) & 0x0F);
        Register r2 = decodeRegister(post & 0x0F);

        ensureSameSize(r1, r2);

        int v1 = r.getRegister(r1);
        int v2 = r.getRegister(r2);

        r.setRegister(r1, v2);
        r.setRegister(r2, v1);

        return 8;
    }


    // Helpers


    private static Register decodeRegister(int code) {
        return switch (code) {
            case 0x0 -> Register.D;
            case 0x1 -> Register.X;
            case 0x2 -> Register.Y;
            case 0x3 -> Register.U;
            case 0x4 -> Register.S;
            case 0x5 -> Register.PC;
            case 0x8 -> Register.A;
            case 0x9 -> Register.B;
            case 0xA -> Register.CC;
            case 0xB -> Register.DP;
            default -> throw new IllegalStateException(
                    String.format("Illegal register code in TFR/EXG: %X", code)
            );
        };
    }

    private static void ensureSameSize(Register r1, Register r2) {
        boolean r1_16 = is16(r1);
        boolean r2_16 = is16(r2);

        if (r1_16 != r2_16) {
            throw new IllegalStateException(
                    "Illegal TFR/EXG between registers of different sizes: " +
                            r1 + " <-> " + r2
            );
        }
    }

    private static boolean is16(Register r) {
        return switch (r) {
            case D, X, Y, U, S, PC -> true;
            default -> false;
        };
    }
}

