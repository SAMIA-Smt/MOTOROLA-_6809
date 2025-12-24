package com.simulator.moto6809.Assembler;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.InstructionDefinition;
import com.simulator.moto6809.Decoder.InstructionSet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Assembler {

    private final OpcodeSelector opcodeSelector;
    private final InstructionSet instructionSet;

    public Assembler(OpcodeSelector opcodeSelector, InstructionSet instructionSet) {
        this.opcodeSelector = opcodeSelector;
        this.instructionSet = instructionSet;
    }

    // LABEL,PC or [LABEL,PC]
    private static final Pattern LABEL_PC =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*,\\s*PC$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern EXT_INDIRECT = Pattern.compile("^\\[(\\s*[^\\]]+)\\]$"); // [ ... ]
    private static final Pattern ONLY_BRACKET_ADDR =
            Pattern.compile("^\\s*(\\$[0-9A-Fa-f]{1,4}|%[01]+|@[0-7]+|[-+]?\\d+)\\s*$");

    // NOTE: PC is NOT valid for ",R" auto forms. Keep only X/Y/U/S here.
    private static final Pattern ZERO_OR_AUTO =
            Pattern.compile("^,\\s*(?:(\\+\\+|\\+|--|-)?\\s*(X|Y|U|S)|(X|Y|U|S)\\s*(\\+\\+|\\+|--|-))\\s*$",
                    Pattern.CASE_INSENSITIVE);

    // A,R  B,R  D,R (PC not allowed)
    private static final Pattern ACC_OFFSET =
            Pattern.compile("^(A|B|D)\\s*,\\s*(X|Y|U|S)\\s*$", Pattern.CASE_INSENSITIVE);

    // n,R or #n,R (PC allowed here: n,PC)
    private static final Pattern CONST_OFFSET =
            Pattern.compile("^(#?\\s*[-+]?\\s*(?:\\$[0-9A-Fa-f]+|%[01]+|@[0-7]+|\\d+))\\s*,\\s*(X|Y|U|S|PC)\\s*$",
                    Pattern.CASE_INSENSITIVE);

    public List<Integer> assembleLine(String line) {

        String trimmed = line.trim();
        if (trimmed.isEmpty()) return List.of();

        String mnemonic;
        String operandText = "";
        String[] parts = WS.split(trimmed, 2);
        mnemonic = parts[0].toUpperCase();
        if (parts.length > 1) operandText = parts[1].trim();


        // BRANCH (RELATIVE)

        if (isShortBranch(mnemonic) || isLongBranch(mnemonic)) {

            int opcode = opcodeSelector.select(mnemonic, AddressingMode.RELATIVE);
            int operandBytes = requiredOperandBytes(mnemonic, AddressingMode.RELATIVE, opcode);

            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);

            for (int i = 0; i < operandBytes; i++) bytes.add(0x00); // placeholder
            return bytes;
        }


        // INHERENT

        if (operandText.isEmpty()) {
            int opcode = opcodeSelector.select(mnemonic, AddressingMode.INHERENT);
            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);
            return bytes;
        }


        // IMMEDIATE

        if (operandText.startsWith("#") && !operandText.contains(",")) {

            int opcode = opcodeSelector.select(mnemonic, AddressingMode.IMMEDIATE);
            int operandBytes = requiredOperandBytes(mnemonic, AddressingMode.IMMEDIATE, opcode);

            String imm = operandText.substring(1).trim();

            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);

            if (looksLikeNumber(imm)) {
                int value = parseNumber(imm);
                emitBigEndian(bytes, value, operandBytes);
            } else {
                // label placeholder
                for (int i = 0; i < operandBytes; i++) bytes.add(0x00);
            }
            return bytes;
        }


        // INDEXED (direct/indirect)

        boolean indirect = false;
        String op = operandText;

        Matcher mIndirect = EXT_INDIRECT.matcher(op);
        if (mIndirect.matches()) {
            indirect = true;
            op = mIndirect.group(1).trim(); // strip [ ]
        }

        // Special: [$nnnn] => postbyte 0x9F + 16-bit
        if (indirect && !op.contains(",")) {
            if (!ONLY_BRACKET_ADDR.matcher(op).matches()) {
                throw new IllegalStateException("Invalid indexed indirect syntax: " + line);
            }
            int addr = parseNumber(op);
            IndexedOperand io = IndexedEncoder.encodeExtendedIndirect(addr);

            int opcode = opcodeSelector.select(mnemonic, AddressingMode.INDEXED);

            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);
            bytes.add(io.postbyte);
            bytes.add((io.extra >> 8) & 0xFF);
            bytes.add(io.extra & 0xFF);
            return bytes;
        }

        if (op.contains(",")) {

            IndexedOperand io;

            Matcher m0 = ZERO_OR_AUTO.matcher(op);
            if (m0.matches()) {

                String reg = null;
                String suf = null;

                if (m0.group(2) != null) {
                    suf = m0.group(1);
                    reg = m0.group(2);
                } else if (m0.group(3) != null) {
                    reg = m0.group(3);
                    suf = m0.group(4);
                }

                if (reg == null) throw new IllegalStateException("Invalid auto index syntax: " + line);
                reg = reg.toUpperCase();

                if (suf == null) {
                    io = IndexedEncoder.encodeZeroOffset(reg, indirect);
                } else {
                    int delta = switch (suf) {
                        case "+"  -> +1;
                        case "++" -> +2;
                        case "-"  -> -1;
                        case "--" -> -2;
                        default   -> throw new IllegalStateException("Invalid auto suffix: " + suf);
                    };
                    io = IndexedEncoder.encodeAuto(reg, delta, indirect);
                }
            } else {
                Matcher mAcc = ACC_OFFSET.matcher(op);
                if (mAcc.matches()) {
                    io = IndexedEncoder.encodeRegisterOffset(
                            mAcc.group(1).toUpperCase(),
                            mAcc.group(2).toUpperCase(),
                            indirect
                    );
                } else {
                    Matcher mConst = CONST_OFFSET.matcher(op);
                    Matcher mLabelPc = LABEL_PC.matcher(op);

                    if (mConst.matches()) {
                        String nStr = mConst.group(1).replace(" ", "");
                        String idxReg = mConst.group(2).toUpperCase();
                        if (nStr.startsWith("#")) nStr = nStr.substring(1);
                        int n = parseNumber(nStr);

                        if (idxReg.equals("PC")) {
                            io = IndexedEncoder.encodePcRelative(n, indirect);
                        } else {
                            io = IndexedEncoder.encodeConstantOffset(n, idxReg, indirect);
                        }
                    } else if (mLabelPc.matches()) {
                        // IMPORTANT: for forward labels, always use 16-bit placeholder to avoid "too far" later
                        io = IndexedEncoder.encodePcRelative16(indirect);
                    } else {
                        throw new IllegalStateException("Invalid indexed syntax: " + line);
                    }
                }
            }

            int opcode = opcodeSelector.select(mnemonic, AddressingMode.INDEXED);

            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);
            bytes.add(io.postbyte);

            if (io.extra != null) {
                // if extra is 0..255 => 1 byte, else 2 bytes
                if (io.extra <= 0xFF) bytes.add(io.extra & 0xFF);
                else {
                    bytes.add((io.extra >> 8) & 0xFF);
                    bytes.add(io.extra & 0xFF);
                }
            }

            return bytes;
        }


        // DIRECT / EXTENDED
        // - if operand is a label, encode as EXTENDED placeholder (stable size)

        if (!looksLikeNumber(op)) {
            // choose EXTENDED if possible, else DIRECT
            AddressingMode mode = AddressingMode.EXTENDED;
            int opcode;
            try {
                opcode = opcodeSelector.select(mnemonic, mode);
            } catch (Exception ex) {
                mode = AddressingMode.DIRECT;
                opcode = opcodeSelector.select(mnemonic, mode);
            }

            int operandBytes = requiredOperandBytes(mnemonic, mode, opcode);

            List<Integer> bytes = new ArrayList<>();
            emitOpcode(bytes, opcode);
            for (int i = 0; i < operandBytes; i++) bytes.add(0x00);
            return bytes;
        }

        int operand = parseNumber(op);
        AddressingMode mode = (operand <= 0xFF) ? AddressingMode.DIRECT : AddressingMode.EXTENDED;
        int opcode = opcodeSelector.select(mnemonic, mode);

        int operandBytes = requiredOperandBytes(mnemonic, mode, opcode);

        List<Integer> bytes = new ArrayList<>();
        emitOpcode(bytes, opcode);
        emitBigEndian(bytes, operand, operandBytes);
        return bytes;
    }


    // Helpers


    private int requiredOperandBytes(String mnemonic, AddressingMode mode, int opcode) {
        InstructionDefinition def = instructionSet.getByMnemonic(mnemonic);
        if (def == null) throw new IllegalStateException("Unknown mnemonic: " + mnemonic);
        if (!def.supports(mode)) throw new IllegalStateException("Mode " + mode + " not supported by " + mnemonic);

        int totalSize = def.getSize(mode);
        int opBytes = opcodeByteCount(opcode);

        if (totalSize < opBytes) {
            throw new IllegalStateException("Bad size table for " + mnemonic + " " + mode +
                    ": totalSize=" + totalSize + " opcodeBytes=" + opBytes);
        }
        return totalSize - opBytes;
    }

    private static int opcodeByteCount(int opcode) {
        return ((opcode & 0xFF00) == 0x1000 || (opcode & 0xFF00) == 0x1100) ? 2 : 1;
    }

    private static void emitOpcode(List<Integer> out, int opcode) {
        if (((opcode & 0xFF00) == 0x1000) || ((opcode & 0xFF00) == 0x1100)) {
            out.add((opcode >> 8) & 0xFF); // 0x10 or 0x11
        }
        out.add(opcode & 0xFF);
    }

    private static void emitBigEndian(List<Integer> out, int value, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            out.add((value >> (8 * i)) & 0xFF);
        }
    }

    private int parseNumber(String s) {
        s = s.trim().toUpperCase();
        if (s.isEmpty()) throw new IllegalStateException("Empty number");

        int sign = 1;
        if (s.startsWith("+")) {
            s = s.substring(1).trim();
        } else if (s.startsWith("-")) {
            sign = -1;
            s = s.substring(1).trim();
        }

        int v;
        if (s.startsWith("$")) v = Integer.parseInt(s.substring(1), 16);
        else if (s.startsWith("%")) v = Integer.parseInt(s.substring(1), 2);
        else if (s.startsWith("@")) v = Integer.parseInt(s.substring(1), 8);
        else v = Integer.parseInt(s);

        return sign * v;
    }


    private boolean isShortBranch(String m) {
        return List.of("BRA","BEQ","BNE","BMI","BPL","BCC","BCS","BVC","BVS","BGE","BLT","BGT","BLE").contains(m);
    }

    private boolean isLongBranch(String m) {
        return m.startsWith("LB");
    }

    private boolean looksLikeNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = s.charAt(0);
        return c == '$' || c == '%' || c == '@' ||
                Character.isDigit(c) || c == '+' || c == '-';
    }
}