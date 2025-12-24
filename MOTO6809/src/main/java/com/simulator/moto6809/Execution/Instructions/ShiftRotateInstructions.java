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
ASL, ASLA, ASLB,LSL, LSLA, LSLB,ASR, ASRA, ASRB,
LSR, LSRA, LSRB,ROL, ROLA, ROLB,ROR, RORA, RORB

 */

/*
asl(Register target);
lsr(Register target);
asr(Register target);
rol(Register target);
ror(Register target);
 */
public final class ShiftRotateInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            ASL, ASLA, ASLB, LSL, LSLA, LSLB,
            ASR, ASRA, ASRB,
            LSR, LSRA, LSRB,
            ROL, ROLA, ROLB,
            ROR, RORA, RORB
    );

    private ShiftRotateInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        boolean isRegister =
                instr.addressingMode() == AddressingMode.INHERENT;

        int value;
        Integer ea = null;
        Register reg = null;

        if (isRegister) {
            reg = Mnemonics.getMnemonicRegister(instr.mnemonic());
            if (reg == null)
                throw new IllegalStateException("Shift on unknown register");
            value = regs.getRegister(reg) & 0xFF;
        } else {
            ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);
            value = mem.read(ea) & 0xFF;
        }

        boolean carryIn = regs.getFlag(Flag.C);
        boolean carryOut;
        int result;

        switch (instr.mnemonic()) {


            // ASL / LSL

            case ASL, ASLA, ASLB, LSL, LSLA, LSLB -> {
                carryOut = (value & 0x80) != 0;
                result = (value << 1) & 0xFF;
            }


            // LSR

            case LSR, LSRA, LSRB -> {
                carryOut = (value & 0x01) != 0;
                result = (value >>> 1) & 0x7F;//result = (value >> 1) & 0x7F;
            }


            // ASR

            case ASR, ASRA, ASRB -> {
                carryOut = (value & 0x01) != 0;
                result = ((value & 0x80) | (value >> 1)) & 0xFF;
            }


            // ROL

            case ROL, ROLA, ROLB -> {
                carryOut = (value & 0x80) != 0;
                result = ((value << 1) | (carryIn ? 1 : 0)) & 0xFF;
            }


            // ROR

            case ROR, RORA, RORB -> {
                carryOut = (value & 0x01) != 0;
                result = ((carryIn ? 0x80 : 0) | (value >> 1)) & 0xFF;
            }

            default -> throw new IllegalStateException(
                    "Unsupported shift/rotate: " + instr.mnemonic()
            );
        }


        // Write back

        if (isRegister)
            regs.setRegister(reg, result);
        else
            mem.write(ea, result);


        // Flags

        regs.setFlag(Flag.C, carryOut);
        regs.updateNZ(result);
        regs.setFlag(Flag.V, regs.getFlag(Flag.N) ^ regs.getFlag(Flag.C));


        return instr.cycles();
    }
}


