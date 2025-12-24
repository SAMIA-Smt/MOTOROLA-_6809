package com.simulator.moto6809.Decoder;

import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Resource.InstructionCsvLoader;
import com.simulator.moto6809.Resource.InstructionCsvRow;

import java.util.HashMap;
import java.util.Map;

/**
 * Central database of all Motorola 6809 instructions.
 * Built from CSV tables at startup.
 */
public class InstructionSet {

    /** Lookup by mnemonic (e.g. "LDA") */
    private final Map<String, InstructionDefinition> byMnemonic = new HashMap<>();

    /** Lookup by full opcode (0x00–0xFF, 0x10xx, 0x11xx) */
    private final Map<Integer, InstructionDefinition> byOpcode = new HashMap<>();

    private final ILogger logger;

    public InstructionSet(ILogger logger) {
        this.logger = logger;
        load();
    }


    // Public lookup API


    public InstructionDefinition getByMnemonic(String mnemonic) {
        if (mnemonic == null) return null;
        return byMnemonic.get(mnemonic.toUpperCase());
    }

    public InstructionDefinition getByOpcode(int opcode) {
        return byOpcode.get(opcode & 0xFFFF);
    }

    public boolean containsMnemonic(String mnemonic) {
        if (mnemonic == null) return false;
        return byMnemonic.containsKey(mnemonic.toUpperCase());
    }


    // Load & build instruction database


    private void load() {

        InstructionCsvLoader loader = new InstructionCsvLoader(logger);

        Map<String, InstructionCsvRow> opcodeTable = loader.loadOpcodeTable();
        Map<String, InstructionCsvRow> cycleTable  = loader.loadCycleTable();
        Map<String, InstructionCsvRow> sizeTable   = loader.loadSizeTable();

        for (String mnemonic : opcodeTable.keySet()) {

            InstructionCsvRow opRow = opcodeTable.get(mnemonic);
            InstructionCsvRow cyRow = cycleTable.get(mnemonic);
            InstructionCsvRow szRow = sizeTable.get(mnemonic);

            // If cycle/size rows are missing, skip (or log warning)
            if (opRow == null || cyRow == null || szRow == null) {
                if (logger != null) {
                    logger.log("InstructionSet: missing CSV row for mnemonic: " + mnemonic, LogLevel.WARNING);
                }
                continue;
            }

            InstructionDefinition def = new InstructionDefinition(mnemonic);

            register(def, AddressingMode.IMMEDIATE, opRow.imm(), cyRow.imm(), szRow.imm());
            register(def, AddressingMode.DIRECT,    opRow.drt(), cyRow.drt(), szRow.drt());
            register(def, AddressingMode.INDEXED,   opRow.idx(), cyRow.idx(), szRow.idx());
            register(def, AddressingMode.EXTENDED,  opRow.etd(), cyRow.etd(), szRow.etd());
            register(def, AddressingMode.INHERENT,  opRow.inh(), cyRow.inh(), szRow.inh());
            register(def, AddressingMode.RELATIVE,  opRow.rlv(), cyRow.rlv(), szRow.rlv());

            boolean any =
                    def.supports(AddressingMode.INHERENT) ||
                            def.supports(AddressingMode.IMMEDIATE) ||
                            def.supports(AddressingMode.DIRECT) ||
                            def.supports(AddressingMode.EXTENDED) ||
                            def.supports(AddressingMode.INDEXED) ||
                            def.supports(AddressingMode.RELATIVE);

            if (!any) {
                continue; // skip HD6309-only or invalid rows
            }

            byMnemonic.put(mnemonic.toUpperCase(), def);
        }

        if (logger != null) {
            logger.log(
                    "InstructionSet initialized: " +
                            byMnemonic.size() + " mnemonics, " +
                            byOpcode.size() + " opcodes",
                    LogLevel.INFO
            );
        }
    }


    // Internal helpers


    private void register(InstructionDefinition def,
                          AddressingMode mode,
                          String opcodeStr,
                          String cyclesStr,
                          String sizeStr) {

        Integer opcodeObj = parseOpcodeHex(opcodeStr);
        if (opcodeObj == null) {
            // skip cells like "(HD6309 only)" or empty
            return;
        }

        int opcode = opcodeObj & 0xFFFF;

        int cycles = parseCycles(cyclesStr);
        int size   = parseInt(sizeStr); // tolerant (handles "2+")

        // IMPORTANT:
        // size CSV counts opcode as 1 byte even for prefixed opcodes (10xx/11xx)
        // Example: LDY imm has "3" in CSV, but opcode is 2 bytes => correct total is 4.
        size = normalizeSizeForPrefix(size, mode, opcode);

        def.addOpcode(mode, opcode);
        def.addCycles(mode, cycles);
        def.addSize(mode, size);

        byOpcode.put(opcode, def);
    }

    /**
     * Accepts "86", "8E", "108E", "10 8E", "0x108E".
     */
    private Integer parseOpcodeHex(String opcodeStr) {
        if (opcodeStr == null) return null;

        String s = opcodeStr.trim();
        if (s.isEmpty()) return null;

        s = s.replace("0x", "").replace("0X", "");
        s = s.replaceAll("\\s+", "");

        if (!s.matches("^[0-9A-Fa-f]{2,4}$")) {
            return null;
        }

        return Integer.parseInt(s, 16);
    }

    /**
     * If opcode is prefixed (0x10xx or 0x11xx), and the CSV size looks like it did NOT
     * include the prefix byte, adjust it.
     */
    private int normalizeSizeForPrefix(int baseSize, AddressingMode mode, int opcode) {
        if (baseSize <= 0) return baseSize;

        boolean prefixed = ((opcode & 0xFF00) == 0x1000) || ((opcode & 0xFF00) == 0x1100);
        if (!prefixed) return baseSize;

        // Minimum plausible total size (including prefix) for each mode.
        int minTotal = switch (mode) {
            case INHERENT  -> 2; // 10 xx
            case DIRECT    -> 3; // 10 xx + 1
            case EXTENDED  -> 4; // 10 xx + 2
            case INDEXED   -> 3; // 10 xx + postbyte (minimum)
            case RELATIVE  -> 4; // LBxx already correct in your CSV (usually 4)
            case IMMEDIATE -> 3; // 10 xx + at least 1
        };

        // Special case: CSV uses "3" for 16-bit immediates (opcode1 + imm2).
        // For prefixed opcode, that must become 4 (prefix+opcode + imm2).
        if (mode == AddressingMode.IMMEDIATE && baseSize == 3) {
            return 4;
        }

        // If baseSize is smaller than minimum plausible for prefixed, it missed the prefix.
        if (baseSize < minTotal) {
            return baseSize + 1;
        }

        return baseSize;
    }

    /**
     * Extracts base cycle count from values like "5+", "6(7)", "3"
     */
    private int parseCycles(String value) {
        if (value == null || value.isEmpty()) return 0;

        StringBuilder digits = new StringBuilder();
        for (char c : value.trim().toCharArray()) {
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }

        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    /**
     * Tolerant int parser:
     * "2+" -> 2, "6(7)" -> 6, "" -> 0
     */
    private int parseInt(String value) {
        if (value == null) return 0;

        String s = value.trim();
        if (s.isEmpty()) return 0;

        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;

        if (i == 0) return 0;
        return Integer.parseInt(s.substring(0, i));
    }
}