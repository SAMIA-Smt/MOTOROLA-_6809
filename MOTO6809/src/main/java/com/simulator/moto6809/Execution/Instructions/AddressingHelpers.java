package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

public final class AddressingHelpers
{

    private AddressingHelpers() {}

    /**
     * Compute the effective address (EA) for addressing modes that actually use an EA.
     *
     * IMPORTANT:
     *   IMMEDIATE and INHERENT do NOT have an EA.
     *   If code calls this for those modes, it's a bug, so we throw.
     *
     * @return 16-bit EA (0..65535)
     */
    public static int computeEffectiveAddress(
            DecodedInstruction instr,
            RegisterFunctions regs,
            MemoryBus bus
    ) {
        AddressingMode mode = instr.addressingMode();

        return switch (mode) {
            case DIRECT   -> direct(instr, regs);
            case EXTENDED -> extended(instr);
            case RELATIVE -> instr.relativeTargetAddress() & 0xFFFF;
            case INDEXED  -> indexed(instr, regs, bus);

            case IMMEDIATE, INHERENT ->
                    throw new IllegalStateException("No effective address for mode: " + mode
                            + " (mnemonic=" + instr.mnemonic() + ")");
        };
    }

    private static int direct(DecodedInstruction instr, RegisterFunctions regs) {
        int dp = regs.getRegister(Register.DP) & 0xFF;
        return ((dp << 8) | (instr.operand() & 0xFF)) & 0xFFFF;
    }

    private static int extended(DecodedInstruction instr) {
        return instr.operand() & 0xFFFF;
    }


    // INDEXED (FULL)

    private static int indexed(DecodedInstruction instr, RegisterFunctions regs, MemoryBus bus) {

        // Must contain opcode bytes + postbyte + any additional offset bytes
        byte[] bytes = instr.bytes();
        if (bytes == null || bytes.length < 2) {
            throw new IllegalStateException("Indexed decode requires bytes[] including postbyte");
        }

        int opcodeBytes = instr.opcodeByteCount();
        int postIndex = opcodeBytes;
        int post = bytes[postIndex] & 0xFF;

        // X/Y/U/S from bits 6-5
        Register indexReg = switch ((post >> 5) & 0b11) {
            case 0b00 -> Register.X;
            case 0b01 -> Register.Y;
            case 0b10 -> Register.U;
            case 0b11 -> Register.S;
            default -> throw new IllegalStateException();
        };

        int base = regs.getRegister(indexReg) & 0xFFFF;

        // Case 1: 5-bit signed offset (bit7=0)

        if ((post & 0x80) == 0) {
            int off5 = post & 0x1F;
            if ((off5 & 0x10) != 0) off5 |= 0xFFFFFFE0; // sign extend 5-bit
            return (base + off5) & 0xFFFF;
        }


        // Case 2: "complex" indexed (bit7=1)

        boolean indirectFlag = (post & 0x10) != 0;
        int mode = post & 0x0F;

        int ea;

        switch (mode) {

            // ,R+
            case 0x0 -> {
                ea = base;
                regs.setRegister(indexReg, (base + 1) & 0xFFFF);
            }

            // ,R++
            case 0x1 -> {
                ea = base;
                regs.setRegister(indexReg, (base + 2) & 0xFFFF);
            }

            // ,-R
            case 0x2 -> {
                int nb = (base - 1) & 0xFFFF;
                regs.setRegister(indexReg, nb);
                ea = nb;
            }

            // ,--R
            case 0x3 -> {
                int nb = (base - 2) & 0xFFFF;
                regs.setRegister(indexReg, nb);
                ea = nb;
            }

            // ,R (0 offset)
            case 0x4 -> ea = base;

            // B,R (signed 8-bit from B)
            case 0x5 -> {
                int off = (byte) (regs.getRegister(Register.B) & 0xFF);
                ea = (base + off) & 0xFFFF;
            }

            // A,R (signed 8-bit from A)
            case 0x6 -> {
                int off = (byte) (regs.getRegister(Register.A) & 0xFF);
                ea = (base + off) & 0xFFFF;
            }

            // (illegal/undefined on many tables; keep strict)
            case 0x7 -> throw new IllegalStateException("Illegal indexed postbyte mode 0x7");

            // 8-bit offset
            case 0x8 -> {
                int off8 = (byte) (bytes[postIndex + 1] & 0xFF);
                ea = (base + off8) & 0xFFFF;
            }

            // 16-bit offset
            case 0x9 -> {
                int hi = bytes[postIndex + 1] & 0xFF;
                int lo = bytes[postIndex + 2] & 0xFF;
                int off16 = (short) ((hi << 8) | lo);
                ea = (base + off16) & 0xFFFF;
            }

            // ,R (same as 0x4 on some decoders; keep explicit)
            case 0xA -> ea = base;

            // D,R (signed 16-bit from D)
            case 0xB -> {
                int d = regs.getRegister(Register.D) & 0xFFFF;
                int off = (short) d;
                ea = (base + off) & 0xFFFF;
            }

            // 8-bit PC-relative
            case 0xC -> {
                int off8 = (byte) (bytes[postIndex + 1] & 0xFF);
                ea = (instr.nextPc() + off8) & 0xFFFF;
            }

            // 16-bit PC-relative
            case 0xD -> {
                int hi = bytes[postIndex + 1] & 0xFF;
                int lo = bytes[postIndex + 2] & 0xFF;
                int off16 = (short) ((hi << 8) | lo);
                ea = (instr.nextPc() + off16) & 0xFFFF;
            }

            // [addr] from register? (often “illegal”; keep strict)
            case 0xE -> throw new IllegalStateException("Illegal indexed postbyte mode 0xE");

            // [nn] extended indirect (ea = mem[mem[PC+..]])
            case 0xF -> {
                // for 0xF, the addressing is already indirect by definition in  penalty table
                int hi = bytes[postIndex + 1] & 0xFF;
                int lo = bytes[postIndex + 2] & 0xFF;
                int addr = ((hi << 8) | lo) & 0xFFFF;

                int ptrHi = bus.read(addr) & 0xFF;
                int ptrLo = bus.read((addr + 1) & 0xFFFF) & 0xFF;
                ea = ((ptrHi << 8) | ptrLo) & 0xFFFF;

                // IMPORTANT: do not apply indirectFlag again for 0xF.
                indirectFlag = false;
            }

            default -> throw new IllegalStateException("Unknown indexed postbyte mode: " + mode);
        }

        if (indirectFlag) {
            // Indirect: final EA is fetched from memory at computed EA
            int ptrHi = bus.read(ea) & 0xFF;
            int ptrLo = bus.read((ea + 1) & 0xFFFF) & 0xFF;
            ea = ((ptrHi << 8) | ptrLo) & 0xFFFF;
        }

        return ea & 0xFFFF;
    }
}


