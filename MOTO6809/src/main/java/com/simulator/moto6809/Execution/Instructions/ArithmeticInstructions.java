/*
ADDA, ADDB, ADDD,
ADCA, ADCB,
SUBA, SUBB, SUBD,
SBCA, SBCB,
MUL,
DAA,
*/

/*
add(Register target, int operand, boolean withCarry);
sub(Register target, int operand, boolean withBorrow);
mul();                 // A * B → D
daa();                 // Decimal Adjust A

 */
package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

import java.util.Set;

import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

public final class ArithmeticInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            ADDA, ADDB, ADDD,
            ADCA, ADCB,
            SUBA, SUBB, SUBD,
            SBCA, SBCB,
            MUL,
            DAA
    );

    private ArithmeticInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    //execute return le nombre de cycle
    public static int execute(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem
    )
    {
        return switch (instr.mnemonic()) {

            case ADDA -> add(regs, Register.A, instr, mem, false);
            case ADDB -> add(regs, Register.B, instr, mem, false);
            case ADDD -> add(regs, Register.D, instr, mem, false);

            case ADCA -> add(regs, Register.A, instr, mem, true);
            case ADCB -> add(regs, Register.B, instr, mem, true);

            case SUBA -> sub(regs, Register.A, instr, mem, false);
            case SUBB -> sub(regs, Register.B, instr, mem, false);
            case SUBD -> sub(regs, Register.D, instr, mem, false);

            case SBCA -> sub(regs, Register.A, instr, mem, true);
            case SBCB -> sub(regs, Register.B, instr, mem, true);

            case MUL  -> mul(regs);
            case DAA  -> daa(regs);

            default -> throw new IllegalStateException(
                    "Arithmetic instruction not supported: " + instr.mnemonic()
            );
        };
    }


    // ADD / ADC


    private static int add(RegisterFunctions regs, Register reg, DecodedInstruction instr, MemoryBus mem, boolean withCarry)
    {
        boolean is16 = (reg == Register.D);

        int a = regs.getRegister(reg);
        int b = fetchOperand(instr, regs, mem, is16);
        int carry = (withCarry && regs.getFlag(Flag.C)) ? 1 : 0;
        int mask = is16 ? 0xFFFF : 0xFF;
        int result = a + b + carry;

        regs.setRegister(reg, result & mask);

        updateFlagsAdd(regs, a, b, result, carry, is16);

        return instr.cycles();
    }


    // SUB / SBC


    private static int sub(
            RegisterFunctions regs,
            Register reg,
            DecodedInstruction instr,
            MemoryBus mem,
            boolean withCarry
    ) {
        boolean is16 = (reg == Register.D);

        int a = regs.getRegister(reg);
        int b = fetchOperand(instr, regs, mem, is16);
        int carry = (withCarry && regs.getFlag(Flag.C)) ? 1 : 0;

        int mask = is16 ? 0xFFFF : 0xFF;
        int result = a - b - carry;

        regs.setRegister(reg, result & mask);

        updateFlagsSub(regs, a, b, result, carry, is16);

        return instr.cycles();
    }


    // MUL


    private static int mul(RegisterFunctions regs) {
        int a = regs.getRegister(Register.A);
        int b = regs.getRegister(Register.B);

        int result = a * b;
        regs.setRegister(Register.D, result & 0xFFFF);

        regs.setFlag(Flag.Z, (result & 0xFFFF) == 0);
        regs.setFlag(Flag.C, (result & 0x80) != 0);

        return 11;
    }


    // DAA

    private static int daa(RegisterFunctions regs) {
        int a = regs.getRegister(Register.A);
        int adj = 0;

        if ((a & 0x0F) > 9 || regs.getFlag(Flag.H)) adj |= 0x06;
        if (a > 0x99 || regs.getFlag(Flag.C))       adj |= 0x60;

        int result = (a + adj) & 0xFF;
        regs.setRegister(Register.A, result);

        regs.setFlag(Flag.C, adj >= 0x60);
        regs.updateNZ(result);

        return 2;
    }


    // OPERAND FETCH


    private static int fetchOperand(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus mem,
            boolean is16
    ) {
        return switch (instr.addressingMode()) {
            case IMMEDIATE -> instr.operand() & (is16 ? 0xFFFF : 0xFF);

            case DIRECT, EXTENDED, INDEXED -> {
                int ea = AddressingHelpers.computeEffectiveAddress(instr, regs, mem);
                yield is16 ? mem.readWord(ea) & 0xFFFF
                        : mem.read(ea) & 0xFF;
            }

            default -> throw new IllegalStateException(
                    "Invalid addressing mode for arithmetic: " + instr.addressingMode()
            );
        };
    }


    // FLAGS


    private static void updateFlagsAdd(
            RegisterFunctions regs,
            int a, int b, int r, int carry, boolean is16
    ) {
        int mask = is16 ? 0xFFFF : 0xFF;
        int sign = is16 ? 0x8000 : 0x80;

        regs.setFlag(Flag.C, (r & ~mask) != 0);
        regs.setFlag(Flag.V, (~(a ^ b) & (a ^ r) & sign) != 0);
        regs.updateNZ(r & mask, is16);

        if (!is16) {
            regs.setFlag(Flag.H, ((a & 0x0F) + (b & 0x0F) + carry) > 0x0F);
        }
    }

    private static void updateFlagsSub(
            RegisterFunctions regs,
            int a, int b, int r, int carry, boolean is16
    ) {
        int mask = is16 ? 0xFFFF : 0xFF;
        int sign = is16 ? 0x8000 : 0x80;

        regs.setFlag(Flag.C, (a - b - carry) < 0);
        regs.setFlag(Flag.V, ((a ^ b) & (a ^ r) & sign) != 0);
        regs.updateNZ(r & mask, is16);

        if (!is16) {
            regs.setFlag(Flag.H, ((a & 0x0F) - (b & 0x0F) - carry) < 0);
        }
    }
}
