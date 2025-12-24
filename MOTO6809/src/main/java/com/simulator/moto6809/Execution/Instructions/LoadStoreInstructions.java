package com.simulator.moto6809.Execution.Instructions;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;
import java.util.Set;
import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

/*
LDA, LDB, LDD,LDX, LDY, LDU, LDS,
STA, STB, STD,STX, STY, STU, STS,

 */
/*
load(Register target, int value);
store(Register source, int address);

 */

public final class LoadStoreInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            LDA, LDB, LDD,LDX, LDY, LDU, LDS,
            STA, STB, STD,STX, STY, STU, STS
    );

    private LoadStoreInstructions() {}


    // Dispatcher


    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    ) {
        Register target = Mnemonics.getMnemonicRegister(instr.mnemonic());

        if (target == null) {
            throw new IllegalStateException(
                    "Load/Store without target register: " + instr.mnemonic()
            );
        }

        boolean isStore = instr.mnemonic().startsWith("ST");

        if (isStore) {
            store(instr, regs, mem, target);
        } else {
            load(instr, regs, mem, target);
        }

        return instr.cycles();
    }


    // Core operations


    private static void load(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem,
            Register reg
    ) {
        boolean is16 = regs.is16BitRegister(reg);

        int value = switch (instr.addressingMode()) {

            case IMMEDIATE ->
                    instr.operand();

            case DIRECT, EXTENDED, INDEXED -> {
                int ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);
                yield is16 ? mem.readWord(ea) : mem.read(ea);
            }

            default -> throw new IllegalStateException(
                    "Invalid addressing mode for LOAD: " + instr.addressingMode()
            );
        };

        regs.setRegister(reg, is16 ? value & 0xFFFF : value & 0xFF);
        regs.updateNZ(value, is16);
    }

    private static void store(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem,
            Register reg
    ) {
        boolean is16 = regs.is16BitRegister(reg);
        int value = regs.getRegister(reg);

        int ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);

        if (is16)
            mem.writeWord(ea, value);
        else
            mem.write(ea, value & 0xFF);
    }
}

