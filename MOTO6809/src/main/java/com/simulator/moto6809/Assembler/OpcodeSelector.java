package com.simulator.moto6809.Assembler;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Resource.InstructionCsvRow;

import java.util.HashMap;
import java.util.Map;

/**
 * Chooses the correct opcode for a (mnemonic, addressing mode) pair using the opcode CSV table.
 * Returns the full opcode as an int:
 *  - 0x00..0xFF for normal opcodes
 *  - 0x10xx / 0x11xx for prefixed opcodes (page 2 / page 3)
 */
public class OpcodeSelector {

    private final Map<String, InstructionCsvRow> opcodeTable;

    public OpcodeSelector(Map<String, InstructionCsvRow> opcodeTable) {
        if (opcodeTable == null) throw new IllegalArgumentException("opcodeTable is null");

        // Normalize keys to uppercase once (avoid case issues)
        Map<String, InstructionCsvRow> normalized = new HashMap<>();
        for (Map.Entry<String, InstructionCsvRow> e : opcodeTable.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                normalized.put(e.getKey().trim().toUpperCase(), e.getValue());
            }
        }
        this.opcodeTable = normalized;
    }

    public int select(String mnemonic, AddressingMode mode) {
        if (mnemonic == null || mnemonic.isBlank()) {
            throw new IllegalArgumentException("mnemonic is null/blank");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is null");
        }

        String key = mnemonic.trim().toUpperCase();
        InstructionCsvRow row = opcodeTable.get(key);
        if (row == null) {
            throw new IllegalStateException("Unknown mnemonic: " + key);
        }

        String opcodeStr = switch (mode) {
            case IMMEDIATE -> row.imm();
            case DIRECT    -> row.drt();
            case INDEXED   -> row.idx();
            case EXTENDED  -> row.etd();
            case INHERENT  -> row.inh();
            case RELATIVE  -> row.rlv();
        };

        Integer opcodeObj = parseOpcodeHex(opcodeStr);
        if (opcodeObj == null) {
            throw new IllegalStateException("Addressing mode " + mode + " not supported by " + key);
        }

        return opcodeObj & 0xFFFF;
    }

    /**
     * Accepts "86", "8E", "108E", "10 8E", "0x108E".
     * Rejects "(HD6309 only)" and other non-hex junk.
     */
    private Integer parseOpcodeHex(String opcodeStr) {
        if (opcodeStr == null) return null;

        String s = opcodeStr.trim();
        if (s.isEmpty()) return null;

        s = s.replace("0x", "").replace("0X", "");
        s = s.replaceAll("\\s+", "");

        // Only accept pure hex opcodes: 2..4 hex digits
        if (!s.matches("^[0-9A-Fa-f]{2,4}$")) {
            return null;
        }

        return Integer.parseInt(s, 16);
    }
}