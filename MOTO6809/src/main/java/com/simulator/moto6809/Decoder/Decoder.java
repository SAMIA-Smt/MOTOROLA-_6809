package com.simulator.moto6809.Decoder;

import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Memory.MemoryBus;

public class Decoder {

    private final InstructionSet instructionSet;
    private final ILogger logger;

    public Decoder(InstructionSet instructionSet, ILogger logger) {
        this.instructionSet = instructionSet;
        this.logger = logger;
    }

    public DecodedInstruction decodeAt(MemoryBus bus, int pc) {
        pc &= 0xFFFF;

        // 1) Fetch first opcode byte
        int b0 = bus.read(pc) & 0xFF;

        // 2) Handle page 2 / page 3 prefixes
        final int opcode;
        final int opcodeBytes;

        if (b0 == 0x10 || b0 == 0x11) {
            int b1 = bus.read((pc + 1) & 0xFFFF) & 0xFF;
            opcode = (b0 << 8) | b1;   // 0x10xx or 0x11xx
            opcodeBytes = 2;
        } else {
            opcode = b0;
            opcodeBytes = 1;
        }

        // 3) Lookup instruction definition
        InstructionDefinition def = instructionSet.getByOpcode(opcode);
        if (def == null) {
            String msg = String.format("Unknown/illegal opcode $%04X at PC=$%04X", opcode, pc);
            logger.log(msg, LogLevel.ERROR);
            throw new IllegalStateException(msg);
        }

        // 4) Resolve addressing mode
        AddressingMode mode = resolveModeFromDefinition(def, opcode);
        if (mode == null) {
            String msg = String.format(
                    "Opcode $%04X found but addressing mode not resolved (PC=$%04X)",
                    opcode, pc
            );
            logger.log(msg, LogLevel.ERROR);
            throw new IllegalStateException(msg);
        }

        // 4b) STRICT consistency/legality guard
        if (!def.supports(mode) || def.getOpcode(mode) != (opcode & 0xFFFF)) {
            String msg = String.format(
                    "Inconsistent decode: opcode $%04X resolved to %s but definition mismatch (PC=$%04X, mnemonic=%s)",
                    opcode, mode, pc, def.getMnemonic()
            );
            logger.log(msg, LogLevel.ERROR);
            throw new IllegalStateException(msg);
        }

        // 5) Base size & cycles
        int baseSize = def.getSize(mode);
        int cycles = def.getCycles(mode);

        if (baseSize < opcodeBytes) {
            throw new IllegalStateException(
                    "Invalid instruction size from table for opcode $" + Integer.toHexString(opcode)
            );
        }

        int size = baseSize;

        // 6) INDEXED special handling (variable length)
        if (mode == AddressingMode.INDEXED) {
            // Postbyte is always right after opcode
            int postAddr = (pc + opcodeBytes) & 0xFFFF;
            int postbyte = bus.read(postAddr) & 0xFF;

            int extra = indexedExtraBytes(postbyte);
            size += extra;

            // Guard: indexed must at least include opcode + postbyte
            if (size < opcodeBytes + 1) {
                throw new IllegalStateException("Indexed instruction size too small for opcode $" +
                        Integer.toHexString(opcode));
            }
        }

        // 7) Fetch all instruction bytes
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) (bus.read((pc + i) & 0xFFFF) & 0xFF);
        }

        // 8) Parse operand correctly (DO NOT include indexed postbyte in operand)
        int operand = parseOperand(mode, bytes, opcodeBytes);

        // 9) Build DecodedInstruction
        return DecodedInstruction.builder()
                .pc(pc)
                .opcode(opcode)
                .mnemonic(def.getMnemonic())
                .addressingMode(mode)
                .cycles(cycles)
                .size(size)
                .operand(operand)
                .bytes(bytes)
                .build();
    }


    // Mode resolution: find which mode inside the definition matches opcode

    private AddressingMode resolveModeFromDefinition(InstructionDefinition def, int opcode) {
        int op = opcode & 0xFFFF;

        for (AddressingMode mode : AddressingMode.values()) {
            if (def.supports(mode) && (def.getOpcode(mode) & 0xFFFF) == op) {
                return mode;
            }
        }
        return null;
    }


    // Operand parsing (per mode)

    private int parseOperand(AddressingMode mode, byte[] bytes, int opcodeBytes) {

        // INHERENT: no operand
        if (mode == AddressingMode.INHERENT) return 0;

        int start = opcodeBytes;

        // INDEXED: bytes[opcodeBytes] is postbyte, not operand
        if (mode == AddressingMode.INDEXED) start = opcodeBytes + 1;

        if (start >= bytes.length) return 0;

        int operand = 0;
        for (int i = start; i < bytes.length; i++) {
            operand = ((operand << 8) | (bytes[i] & 0xFF)) & 0xFFFF;
        }
        return operand;
    }


    // Indexed extra bytes after postbyte (6809 rules)

    private int indexedExtraBytes(int postbyte) {
        // 5-bit constant offset form => no extra bytes after postbyte
        if ((postbyte & 0x80) == 0) return 0;

        int mode = postbyte & 0x0F;

        return switch (mode) {
            case 0x8, 0xC -> 1;       // 8-bit offset, 8-bit PC-relative
            case 0x9, 0xD, 0xF -> 2;  // 16-bit offset, 16-bit PC-relative, [nn] extended indirect
            default -> 0;             // auto inc/dec, ,R, A/B/D,R, etc.
        };
    }
}