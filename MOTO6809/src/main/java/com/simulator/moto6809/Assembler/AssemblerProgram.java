package com.simulator.moto6809.Assembler;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.InstructionDefinition;
import com.simulator.moto6809.Decoder.InstructionSet;
import com.simulator.moto6809.Memory.Memory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


/**
 * Two-pass assembler driver:
 *  - PASS 1: compute addresses + define symbols + produce byte_shape placeholders (correct sizes)
 *  - PASS 2: patch placeholders using resolved symbols (branches, immediates, absolute labels, PC-relative...)
 */
public class AssemblerProgram {

    private final Assembler assembler;
    private final SymbolTable symbols = new SymbolTable();
    private final InstructionSet instructionSet; // required for correct sizing + prefixed opcodes

    public AssemblerProgram(Assembler assembler) {
        this(assembler, null);
    }

    public AssemblerProgram(Assembler assembler, InstructionSet instructionSet) {
        if (assembler == null) throw new IllegalArgumentException("assembler is null");
        this.assembler = assembler;
        this.instructionSet = instructionSet;
    }


    // Internal line bookkeeping

    private enum LineKind {
        EMPTY,
        INSTRUCTION,
        ORG,
        RMB,
        DATA_FCB,
        DATA_FDB,
        EQU,
        SET,
        END
    }

    private static final class LineEntry {
        final String rawLine;     // original line from input (trimmed, without comments)
        final LineKind kind;

        final String label;
        final String keyword;     // mnemonic or directive
        final String operandText; // raw operand text (may be "")

        final AddressingMode mode;    // only for INSTRUCTION otherwise null
        final List<Integer> bytes;    // placeholders from pass1 patched in pass2
        final List<String> dataItems; // for FCB/FDB items to resolve in pass2

        final int pcBefore;
        final int pcAfter;

        LineEntry(String rawLine,
                  LineKind kind,
                  String label,
                  String keyword,
                  String operandText,
                  AddressingMode mode,
                  List<Integer> bytes,
                  List<String> dataItems,
                  int pcBefore,
                  int pcAfter) {
            this.rawLine = rawLine;
            this.kind = kind;
            this.label = label;
            this.keyword = keyword;
            this.operandText = operandText;
            this.mode = mode;
            this.bytes = bytes;
            this.dataItems = dataItems;
            this.pcBefore = pcBefore;
            this.pcAfter = pcAfter;
        }
    }


    // Public API


    /** Returns linear bytes (concatenated) in source order (ORG/RMB create gaps that are NOT represented here).*/
    public List<Integer> assemble(List<String> lines, int origin) {
        return assembleInternal(lines, origin).linearBytes;
    }

    /** Returns an address->byte map (ORG/RMB are respected by address). */
    public Map<Integer, Integer> assembleToMemory(List<String> lines, int origin) {
        return assembleInternal(lines, origin).memoryImage;
    }

    public SymbolTable getSymbolTable() {
        return symbols;
    }

    // Listing (for UI "Programme" window)


    public static final class ListingRow {
        /** Index de la ligne dans le fichier source (0-based, correspond à l'ordre des lignes lues) */
        public final int lineIndex;

        /** Type de ligne (INSTRUCTION, ORG, RMB, FCB, FDB, etc.) */
        public final String kind;

        /** Adresse PC avant traitement de la ligne */
        public final int pcBefore;

        /** Adresse PC après traitement de la ligne (utile pour ORG/RMB) */
        public final int pcAfter;

        /** Bytes générés par la ligne (bytes finals après PASS2). Vide si la ligne n'émet rien (ORG/RMB/EQU/SET/EMPTY/END).*/
        public final List<Integer> bytes;

        /** Ligne source (sans commentaires, comme ton stripComment) */
        public final String source;

        public ListingRow(int lineIndex, String kind, int pcBefore, int pcAfter, List<Integer> bytes, String source) {
            this.lineIndex = lineIndex;
            this.kind = kind;
            this.pcBefore = pcBefore & 0xFFFF;
            this.pcAfter  = pcAfter  & 0xFFFF;
            this.bytes = bytes;
            this.source = source;
        }
    }

    /** Assemble et retourne un listing complet (pour la fenêtre Programme). */
    public List<ListingRow> assembleListing(List<String> lines, int origin) {
        return assembleInternal(lines, origin).listing;
    }

    // Internal result container


    private static final class AssemblyResult {
        final List<Integer> linearBytes;
        final Map<Integer, Integer> memoryImage;
        final List<ListingRow> listing;

        AssemblyResult(List<Integer> linearBytes, Map<Integer, Integer> memoryImage, List<ListingRow> listing) {
            this.linearBytes = linearBytes;
            this.memoryImage = memoryImage;
            this.listing = listing;
        }
    }


    // Core assembly


    private AssemblyResult assembleInternal(List<String> lines, int origin)
    {
        symbols.clear();

        List<LineEntry> entries = new ArrayList<>();
        int pc = origin & 0xFFFF;


        // PASS 1 — collect labels + compute PC + produce correct-sized placeholders

        for (String raw : lines) {

            String rawLine = stripComment(raw);
            if (rawLine.isEmpty()) {
                entries.add(new LineEntry(rawLine, LineKind.EMPTY,
                        null, "", "",
                        null,
                        List.of(), List.of(),
                        pc, pc));
                continue;
            }

            int pcBefore = pc;


            // Parse label with ":" form

            String line = rawLine;
            String label = null;

            int colon = line.indexOf(':');
            if (colon >= 0) {
                label = line.substring(0, colon).trim().replace(":", "").trim();
                if (!label.isEmpty()) {
                    symbols.define(label, pc);
                }
                line = line.substring(colon + 1).trim();
            }

            if (line.isEmpty()) {
                entries.add(new LineEntry(rawLine, LineKind.EMPTY,
                        label, "", "",
                        null,
                        List.of(), List.of(),
                        pcBefore, pc));
                continue;
            }


            // Tokenize: keyword + operandText
            // Support:
            //   LABEL EQU 10
            //   LABEL RMB 20
            //   LABEL LDA #1  no colon if InstructionSet is provided

            String keyword;
            String operandText = "";

            String[] parts = line.split("\\s+", 3);

            // Label before directive (no colon)
            if (label == null && parts.length >= 2 && isDirective(parts[1])) {
                label = parts[0].trim();
                keyword = parts[1].trim().toUpperCase();
                operandText = (parts.length == 3) ? parts[2].trim() : "";

                if (!keyword.equals("EQU") && !keyword.equals("SET")) {
                    if (!label.isEmpty()) symbols.define(label, pc);
                }
            }
            // Label before instruction (no colon) – only if InstructionSet present
            else if (label == null && parts.length >= 2 && isProbablyMnemonic(parts[1])) {
                label = parts[0].trim();
                if (!label.isEmpty()) symbols.define(label, pc);

                keyword = parts[1].trim().toUpperCase();
                operandText = (parts.length == 3) ? parts[2].trim() : "";
            }
            // Normal form: KEYWORD [operand...]
            else {
                keyword = parts[0].trim().toUpperCase();
                operandText = (line.length() > parts[0].length())
                        ? line.substring(parts[0].length()).trim()
                        : "";
            }


            // END

            if (keyword.equals("END")) {
                entries.add(new LineEntry(rawLine, LineKind.END,
                        label, keyword, operandText,
                        null,
                        List.of(), List.of(),
                        pcBefore, pc));
                break;
            }


            // ORG

            if (keyword.equals("ORG")) {
                if (operandText.isEmpty()) throw new IllegalStateException("ORG requires an operand: " + rawLine);

                int newPc = resolveValueOrNumber(operandText);
                if (newPc < 0 || newPc > 0xFFFF) throw new IllegalStateException("ORG out of range: " + operandText);

                pc = newPc & 0xFFFF;

                entries.add(new LineEntry(rawLine, LineKind.ORG,
                        label, keyword, operandText,
                        null,
                        List.of(), List.of(),
                        pcBefore, pc));
                continue;
            }


            // EQU / SET

            if (keyword.equals("EQU") || keyword.equals("SET")) {
                if (label == null || label.isEmpty())
                    throw new IllegalStateException(keyword + " requires a label: " + rawLine);
                if (operandText.isEmpty())
                    throw new IllegalStateException(keyword + " requires a value: " + rawLine);

                if (keyword.equals("EQU") && symbols.contains(label)) {
                    throw new IllegalStateException("EQU symbol redefined: " + label);
                }

                int value = resolveValueOrNumber(operandText);
                symbols.define(label, value);

                entries.add(new LineEntry(rawLine, keyword.equals("EQU") ? LineKind.EQU : LineKind.SET,
                        label, keyword, operandText,
                        null,
                        List.of(), List.of(),
                        pcBefore, pc));
                continue;
            }


            // FCB / FDB (data)

            if (keyword.equals("FCB") || keyword.equals("FDB")) {
                if (operandText.isEmpty()) throw new IllegalStateException(keyword + " requires values: " + rawLine);

                List<String> items = splitCommaList(operandText);
                List<Integer> bytes = new ArrayList<>();

                for (String ignored : items) {
                    if (keyword.equals("FCB")) {
                        bytes.add(0x00);
                        pc = (pc + 1) & 0xFFFF;
                    } else {
                        bytes.add(0x00);
                        bytes.add(0x00);
                        pc = (pc + 2) & 0xFFFF;
                    }
                }

                entries.add(new LineEntry(rawLine, keyword.equals("FCB") ? LineKind.DATA_FCB : LineKind.DATA_FDB,
                        label, keyword, operandText,
                        null,
                        bytes, items,
                        pcBefore, pc));
                continue;
            }


            // RMB

            if (keyword.equals("RMB")) {
                if (operandText.isEmpty()) throw new IllegalStateException("RMB requires a size: " + rawLine);

                int size = resolveValueOrNumber(operandText);
                if (size < 0) throw new IllegalStateException("RMB size must be >= 0: " + rawLine);

                pc = (pc + size) & 0xFFFF;

                entries.add(new LineEntry(rawLine, LineKind.RMB,
                        label, keyword, operandText,
                        null,
                        List.of(), List.of(),
                        pcBefore, pc));
                continue;
            }


            // INSTRUCTION

            AddressingMode mode = decideMode(keyword, operandText);
            List<Integer> placeholder = buildPlaceholdersPass1(keyword, operandText, mode);

            pc = (pc + placeholder.size()) & 0xFFFF;

            entries.add(new LineEntry(rawLine, LineKind.INSTRUCTION,
                    label, keyword, operandText,
                    mode,
                    placeholder, List.of(),
                    pcBefore, pc));
        }


        // PASS 2 — patch placeholders + build outputs + build listing

        List<Integer> linear = new ArrayList<>();
        Map<Integer, Integer> mem = new HashMap<>();
        List<ListingRow> listing = new ArrayList<>(entries.size());

        int lineIndex = 0;

        for (LineEntry e : entries) {

            // Bytes finals pour la ligne (souvent vide)
            List<Integer> lineBytes = List.of();

            // ---- FCB/FDB ----
            if (e.kind == LineKind.DATA_FCB || e.kind == LineKind.DATA_FDB) {
                ArrayList<Integer> block = new ArrayList<>();
                int addr = e.pcBefore & 0xFFFF;

                if (e.kind == LineKind.DATA_FCB) {
                    for (String item : e.dataItems) {
                        int v = resolveValueOrNumber(item.trim()) & 0xFF;
                        block.add(v);

                        linear.add(v);
                        mem.put(addr, v);
                        addr = (addr + 1) & 0xFFFF;
                    }
                } else {
                    for (String item : e.dataItems) {
                        int v = resolveValueOrNumber(item.trim()) & 0xFFFF;
                        int hi = (v >> 8) & 0xFF;
                        int lo = v & 0xFF;

                        block.add(hi);
                        block.add(lo);

                        linear.add(hi);
                        linear.add(lo);

                        mem.put(addr, hi);
                        mem.put((addr + 1) & 0xFFFF, lo);

                        addr = (addr + 2) & 0xFFFF;
                    }
                }

                lineBytes = List.copyOf(block);
                listing.add(new ListingRow(lineIndex++, e.kind.name(), e.pcBefore, e.pcAfter, lineBytes, e.rawLine));
                continue;
            }

            // ---- INSTRUCTION ----
            if (e.kind == LineKind.INSTRUCTION) {
                ArrayList<Integer> block = new ArrayList<>(e.bytes);
                patchIfNeeded(e, block);

                // emit to linear + memory map
                int addr = e.pcBefore & 0xFFFF;
                for (int b : block) {
                    int v = b & 0xFF;
                    linear.add(v);
                    mem.put(addr, v);
                    addr = (addr + 1) & 0xFFFF;
                }

                lineBytes = List.copyOf(block);
                listing.add(new ListingRow(lineIndex++, e.kind.name(), e.pcBefore, e.pcAfter, lineBytes, e.rawLine));
                continue;
            }

            // ---- ORG / RMB / EQU / SET / END / EMPTY ----
            // Rien n'est émis, mais on veut quand même les afficher dans le listing UI
            listing.add(new ListingRow(lineIndex++, e.kind.name(), e.pcBefore, e.pcAfter, lineBytes, e.rawLine));
        }

        return new AssemblyResult(linear, mem, listing);

    }


    // PASS 1 helpers


    private AddressingMode decideMode(String mnemonic, String operandText) {
        String m = mnemonic.toUpperCase();
        String op = operandText == null ? "" : operandText.trim();

        if (op.isEmpty()) return AddressingMode.INHERENT;

        if (isBranchMnemonic(m)) return AddressingMode.RELATIVE;

        if (op.startsWith("#") && !op.contains(",")) return AddressingMode.IMMEDIATE;

        // Indexed family:
        // - any comma
        // - any brackets (e.g. [$1234])
        if (op.contains(",") || op.contains("[") || op.contains("]")) return AddressingMode.INDEXED;

        // Absolute (DIRECT/EXTENDED) decided later
        // but this method only returns a "family" – we decide exact in buildPlaceholdersPass1
        // We'll return EXTENDED as safe default for labels; numeric may become DIRECT.
        if (!looksLikeNumber(op)) return AddressingMode.EXTENDED;

        int value = parseNumber(op) & 0xFFFF;
        return (value <= 0xFF) ? AddressingMode.DIRECT : AddressingMode.EXTENDED;
    }

    private List<Integer> buildPlaceholdersPass1(String mnemonic, String operandText, AddressingMode mode) {

        // InstructionSet is required to be correct with:
        // - prefixed opcodes
        // - immediate operand size correctness
        if (instructionSet == null) {
            // numeric-only assembly can still work, but label cases must not silently produce wrong sizes
            if (containsLabelReference(mnemonic, operandText)) {
                throw new IllegalStateException(
                        "AssemblerProgram requires InstructionSet to assemble label-based operands correctly. " +
                                "Rebuild AssemblerProgram with (assembler, instructionSet). Line: " +
                                mnemonic + (operandText == null ? "" : " " + operandText)
                );
            }
        }

        String m = mnemonic.toUpperCase();
        String op = operandText == null ? "" : operandText.trim();


        // INHERENT

        if (mode == AddressingMode.INHERENT) {
            return encodeFixedBytes(m, AddressingMode.INHERENT, 0);
        }

        // RELATIVE (branches)
        // operand is target label/address; we patch offset in pass2

        if (mode == AddressingMode.RELATIVE) {
            int size = requiredSize(m, AddressingMode.RELATIVE);
            int opcode = requiredOpcode(m, AddressingMode.RELATIVE);
            int opBytes = opcodeByteCount(opcode);

            ArrayList<Integer> out = new ArrayList<>(size);
            writeOpcode(out, opcode);
            // placeholders for offset bytes
            for (int i = 0; i < (size - opBytes); i++) out.add(0x00);
            return out;
        }

        // IMMEDIATE

        if (mode == AddressingMode.IMMEDIATE) {
            int size = requiredSize(m, AddressingMode.IMMEDIATE);
            int opcode = requiredOpcode(m, AddressingMode.IMMEDIATE);
            int opBytes = opcodeByteCount(opcode);
            int immBytes = size - opBytes;

            ArrayList<Integer> out = new ArrayList<>(size);
            writeOpcode(out, opcode);

            if (op.startsWith("#")) op = op.substring(1).trim();

            if (looksLikeNumber(op)) {
                int v = parseNumber(op) & 0xFFFF;
                if (immBytes == 1) {
                    out.add(v & 0xFF);
                } else if (immBytes == 2) {
                    out.add((v >> 8) & 0xFF);
                    out.add(v & 0xFF);
                } else {
                    // unexpected, but keep placeholders
                    for (int i = 0; i < immBytes; i++) out.add(0x00);
                }
            } else {
                // label -> placeholders
                for (int i = 0; i < immBytes; i++) out.add(0x00);
            }
            return out;
        }


        // INDEXED
        if (mode == AddressingMode.INDEXED) {

            // Special: [LABEL] (extended indirect with label)
            if (isBracketOnly(op)) {
                String inner = stripOuterBrackets(op).trim();
                if (!inner.contains(",") && !looksLikeNumber(inner)) {
                    // opcode + postbyte(0x9F) + 16-bit address
                    int opcode = requiredOpcode(m, AddressingMode.INDEXED);
                    ArrayList<Integer> out = new ArrayList<>();
                    writeOpcode(out, opcode);
                    out.add(0x9F);
                    out.add(0x00);
                    out.add(0x00);
                    return out;
                }
            }

            // Force 16-bit PC-relative for label,PC
            String opForEncoding = op;
            if (op.contains(",PC")) {
                String expr = op.replace("[", "").replace("]", "").replace(",PC", "").trim();
                if (!looksLikeNumber(expr)) {
                    // force 16-bit form by using a numeric offset outside 8-bit range
                    // keep brackets if present
                    String forced = "$0200,PC";
                    if (op.trim().startsWith("[") && op.trim().endsWith("]")) {
                        opForEncoding = "[" + forced + "]";
                    } else {
                        opForEncoding = forced;
                    }
                }
            }

            // Ask existing assembler to encode indexed operand bytes
            List<Integer> tmp = assembler.assembleLine(m + " " + opForEncoding);
            if (tmp.isEmpty()) throw new IllegalStateException("Failed to encode indexed instruction: " + m + " " + op);

            // Replace opcode (tmp[0]) with correct opcode bytes (including prefix)
            int opcode = requiredOpcode(m, AddressingMode.INDEXED);
            ArrayList<Integer> out = new ArrayList<>();

            writeOpcode(out, opcode);
            // append postbyte+extra from tmp (skip tmp opcode low byte)
            int skip = opcodeByteCount(tmp); // 1 or 2 depending on 0x10/0x11 prefix
            for (int i = skip; i < tmp.size(); i++) {
                out.add(tmp.get(i) & 0xFF);
            }

            return out;
        }


        // DIRECT / EXTENDED (absolute)
        // label -> choose EXTENDED if supported, else DIRECT

        if (!looksLikeNumber(op)) {
            AddressingMode chosen = chooseAbsModeForLabel(m);
            int opcode = requiredOpcode(m, chosen);
            ArrayList<Integer> out = new ArrayList<>();
            writeOpcode(out, opcode);

            int opBytes = (chosen == AddressingMode.DIRECT) ? 1 : 2;
            for (int i = 0; i < opBytes; i++) out.add(0x00);
            return out;
        }

        // numeric absolute
        int value = parseNumber(op) & 0xFFFF;
        AddressingMode chosen = (value <= 0xFF) ? AddressingMode.DIRECT : AddressingMode.EXTENDED;

        // if not supported, fallback
        if (!supports(m, chosen)) {
            chosen = supports(m, AddressingMode.EXTENDED) ? AddressingMode.EXTENDED : AddressingMode.DIRECT;
        }

        int opcode = requiredOpcode(m, chosen);
        ArrayList<Integer> out = new ArrayList<>();
        writeOpcode(out, opcode);

        if (chosen == AddressingMode.DIRECT) {
            out.add(value & 0xFF);
        } else {
            out.add((value >> 8) & 0xFF);
            out.add(value & 0xFF);
        }
        return out;
    }


    // PASS 2 patching


    private void patchIfNeeded(LineEntry e, ArrayList<Integer> bytes) {

        String mnemonic = e.keyword.toUpperCase();
        String operand = e.operandText == null ? "" : e.operandText.trim();

        int opBytes = opcodeByteCount(bytes);


        // RELATIVE (branches)
        // operand is target address/label,encode offset (target - nextPc)

        if (e.mode == AddressingMode.RELATIVE) {
            if (operand.isEmpty()) throw new IllegalStateException("Branch missing operand: " + e.rawLine);

            int target;
            if (looksLikeNumber(operand)) {
                target = parseNumber(operand) & 0xFFFF;
            } else {
                target = symbols.resolve(operand);
            }

            int nextPc = e.pcAfter & 0xFFFF;
            int offset = (target - nextPc);

            int offBytes = bytes.size() - opBytes;
            if (offBytes == 1) {
                if (offset < -128 || offset > 127) {
                    throw new IllegalStateException("Branch offset out of range (8-bit): " + e.rawLine);
                }
                bytes.set(opBytes, offset & 0xFF);
            } else if (offBytes == 2) {
                if (offset < -32768 || offset > 32767) {
                    throw new IllegalStateException("Branch offset out of range (16-bit): " + e.rawLine);
                }
                bytes.set(opBytes, (offset >> 8) & 0xFF);
                bytes.set(opBytes + 1, offset & 0xFF);
            }
            return;
        }


        // IMMEDIATE with label

        if (e.mode == AddressingMode.IMMEDIATE) {
            if (operand.startsWith("#")) {
                String imm = operand.substring(1).trim();
                if (!imm.isEmpty() && !looksLikeNumber(imm)) {
                    int value = symbols.resolve(imm) & 0xFFFF;
                    int immBytes = bytes.size() - opBytes;
                    if (immBytes == 1) {
                        bytes.set(opBytes, value & 0xFF);
                    } else if (immBytes == 2) {
                        bytes.set(opBytes, (value >> 8) & 0xFF);
                        bytes.set(opBytes + 1, value & 0xFF);
                    }
                }
            }
            return;
        }


        // INDEXED PC-relative label: LABEL,PC or [LABEL,PC]
        //patch the extra bytes (after opcode+postbyte)

        if (e.mode == AddressingMode.INDEXED && operand.contains(",PC")) {

            String expr = operand.replace("[", "").replace("]", "").replace(",PC", "").trim();
            if (!expr.isEmpty() && !looksLikeNumber(expr)) {
                int targetAddr = symbols.resolve(expr) & 0xFFFF;
                int nextPc = e.pcAfter & 0xFFFF;
                int offset = targetAddr - nextPc;

                int postIndex = opBytes;          // opcode bytes then postbyte
                int extraStart = opBytes + 1;

                int extraCount = bytes.size() - extraStart;
                if (extraCount == 1) {
                    if (offset < -128 || offset > 127) {
                        throw new IllegalStateException("PC-relative offset out of range (8-bit): " + e.rawLine);
                    }
                    bytes.set(extraStart, offset & 0xFF);
                } else if (extraCount == 2) {
                    if (offset < -32768 || offset > 32767) {
                        throw new IllegalStateException("PC-relative offset out of range (16-bit): " + e.rawLine);
                    }
                    bytes.set(extraStart, (offset >> 8) & 0xFF);
                    bytes.set(extraStart + 1, offset & 0xFF);
                } else {
                    throw new IllegalStateException("Unexpected PC-relative indexed size: " + e.rawLine);
                }

                // (postIndex variable kept for readability; not used otherwise)
                @SuppressWarnings("unused")
                int ignored = postIndex;
            }

            return;
        }


        // INDEXED bracket-only label: [LABEL] (extended indirect)
        // layout: opcodeBytes + postbyte(0x9F) + addrHi + addrLo

        if (e.mode == AddressingMode.INDEXED && isBracketOnly(operand)) {
            String inner = stripOuterBrackets(operand).trim();
            if (!inner.contains(",") && !inner.isEmpty() && !looksLikeNumber(inner)) {
                int value = symbols.resolve(inner) & 0xFFFF;
                int postIndex = opBytes; // postbyte at this index
                if (bytes.size() < opBytes + 3 || (bytes.get(postIndex) & 0xFF) != 0x9F) {
                    throw new IllegalStateException("Invalid [LABEL] encoding shape: " + e.rawLine);
                }
                bytes.set(opBytes + 1, (value >> 8) & 0xFF);
                bytes.set(opBytes + 2, value & 0xFF);
            }
            return;
        }


        // Absolute label: LDA LABEL (direct/extended chosen in pass1)
        // Patch operand bytes at end

        if ((e.mode == AddressingMode.DIRECT || e.mode == AddressingMode.EXTENDED) && !operand.isEmpty()) {
            if (!looksLikeNumber(operand)) {
                int value = symbols.resolve(operand) & 0xFFFF;
                int operandBytes = bytes.size() - opBytes;
                if (operandBytes == 1) {
                    bytes.set(opBytes, value & 0xFF);
                } else if (operandBytes == 2) {
                    bytes.set(opBytes, (value >> 8) & 0xFF);
                    bytes.set(opBytes + 1, value & 0xFF);
                }
            }
        }
    }

    public int assembleToRom(Memory memory,
                             List<String> lines,
                             int defaultOrigin,
                             boolean writeResetVectorIfMissing) {

        if (memory == null) throw new IllegalArgumentException("memory is null");
        if (lines == null) throw new IllegalArgumentException("lines is null");
        int origin = defaultOrigin & 0xFFFF;
        // 1 Build sparse image directly (ORG/RMB already respected)
        Map<Integer, Integer> image = assembleToMemory(lines, origin);

        if (image.isEmpty()) {
            throw new IllegalStateException("No emitted bytes (empty program?)");
        }

        // 2 Choose entry point (best default: first emitted address after sorting)
        int entryPoint = image.keySet().stream()
                .mapToInt(a -> a & 0xFFFF)
                .min()
                .orElseThrow();

        // 3) Enforce ROM-only writes
        int romStart = memory.getROMstart() & 0xFFFF;
        int romEnd = memory.getROMend() & 0xFFFF;

        for (int addr : image.keySet()) {
            int a = addr & 0xFFFF;
            if (a < romStart || a > romEnd) {
                throw new IllegalStateException(String.format(
                        "Program emits byte outside ROM ($%04X). ROM range is $%04X-$%04X",
                        a, romStart, romEnd
                ));
            }
        }

        // 4) Load sparse image into ROM (ORG gaps kept, RMB writes nothing)
        loadSparseIntoMemoryRom(memory, image);

        // 5) write RESET vector if missing
        if (writeResetVectorIfMissing) {
            boolean hasResetVec = image.containsKey(0xFFFE) || image.containsKey(0xFFFF);//&&
            if (!hasResetVec) {
                int ep = entryPoint & 0xFFFF;
                byte hi = (byte) ((ep >> 8) & 0xFF);
                byte lo = (byte) (ep & 0xFF);
                memory.loadBytes(0xFFFE, new byte[]{hi, lo}, true); // force ROM write
            }
        }

        return entryPoint & 0xFFFF;
    }

    // Helpers

    private static void loadSparseIntoMemoryRom(Memory memory, Map<Integer, Integer> image) {
        // sort addresses and group into contiguous runs
        List<Integer> addrs = new ArrayList<>(image.keySet());
        addrs.sort(Comparator.comparingInt(a -> a & 0xFFFF));

        int i = 0;
        while (i < addrs.size()) {
            int start = addrs.get(i) & 0xFFFF;

            List<Byte> block = new ArrayList<>();
            int current = start;

            while (i < addrs.size()) {
                int a = addrs.get(i) & 0xFFFF;
                if (a != current) break;
                block.add((byte) (image.get(a) & 0xFF));
                current = (current + 1) & 0xFFFF;
                i++;
            }

            byte[] data = new byte[block.size()];
            for (int k = 0; k < block.size(); k++) data[k] = block.get(k);

            // allowROMWrite=true => writes into ROM region
            memory.loadBytes(start, data, true);
        }
    }

    // InstructionSet helpers

    private boolean isProbablyMnemonic(String maybeMnemonic) {
        if (instructionSet == null || maybeMnemonic == null) return false;
        return instructionSet.containsMnemonic(maybeMnemonic.trim().toUpperCase());
    }

    private boolean supports(String mnemonic, AddressingMode mode) {
        if (instructionSet == null) return false;
        InstructionDefinition def = instructionSet.getByMnemonic(mnemonic);
        return def != null && def.supports(mode);
    }

    private int requiredOpcode(String mnemonic, AddressingMode mode) {
        if (instructionSet == null) throw new IllegalStateException("InstructionSet is required (opcode lookup).");
        InstructionDefinition def = instructionSet.getByMnemonic(mnemonic);
        if (def == null) throw new IllegalStateException("Unknown mnemonic: " + mnemonic);
        if (!def.supports(mode)) throw new IllegalStateException("Mode " + mode + " not supported by " + mnemonic);
        return def.getOpcode(mode) & 0xFFFF;
    }

    private int requiredSize(String mnemonic, AddressingMode mode) {
        if (instructionSet == null) throw new IllegalStateException("InstructionSet is required (size lookup).");
        InstructionDefinition def = instructionSet.getByMnemonic(mnemonic);
        if (def == null) throw new IllegalStateException("Unknown mnemonic: " + mnemonic);
        if (!def.supports(mode)) throw new IllegalStateException("Mode " + mode + " not supported by " + mnemonic);
        return def.getSize(mode);
    }

    private AddressingMode chooseAbsModeForLabel(String mnemonic) {
        // Prefer EXTENDED for safety; fallback to DIRECT if EXTENDED not available
        if (supports(mnemonic, AddressingMode.EXTENDED)) return AddressingMode.EXTENDED;
        if (supports(mnemonic, AddressingMode.DIRECT)) return AddressingMode.DIRECT;
        // Some mnemonics might not support abs mem at all; keep EXTENDED to fail loudly later
        return AddressingMode.EXTENDED;
    }


    // Byte writing helpers

    private List<Integer> encodeFixedBytes(String mnemonic, AddressingMode mode, int operandValue) {
        int opcode = requiredOpcode(mnemonic, mode);
        int size = requiredSize(mnemonic, mode);

        ArrayList<Integer> out = new ArrayList<>(size);
        writeOpcode(out, opcode);

        int opBytes = opcodeByteCount(opcode);
        int operandBytes = size - opBytes;

        if (operandBytes == 0) return out;

        operandValue &= 0xFFFF;
        if (operandBytes == 1) {
            out.add(operandValue & 0xFF);
        } else if (operandBytes == 2) {
            out.add((operandValue >> 8) & 0xFF);
            out.add(operandValue & 0xFF);
        } else {
            for (int i = 0; i < operandBytes; i++) out.add(0x00);
        }

        return out;
    }

    private void writeOpcode(List<Integer> out, int opcode) {
        opcode &= 0xFFFF;
        if (opcode > 0xFF) {
            out.add((opcode >> 8) & 0xFF);
            out.add(opcode & 0xFF);
        } else {
            out.add(opcode & 0xFF);
        }
    }

    private int opcodeByteCount(int opcode) {
        return ((opcode & 0xFF00) != 0) ? 2 : 1;
    }

    private int opcodeByteCount(List<Integer> bytes) {
        if (bytes == null || bytes.isEmpty()) return 1;
        int b0 = bytes.get(0) & 0xFF;
        return (b0 == 0x10 || b0 == 0x11) ? 2 : 1;
    }


    // Parsing helpers

    private static String stripComment(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int semi = s.indexOf(';');
        if (semi >= 0) s = s.substring(0, semi).trim();
        return s;
    }

    private boolean isBranchMnemonic(String mnemonic) {
        return mnemonic.equals("BRA") ||
                mnemonic.equals("BEQ") ||
                mnemonic.equals("BNE") ||
                mnemonic.equals("BMI") ||
                mnemonic.equals("BPL") ||
                mnemonic.equals("BCC") ||
                mnemonic.equals("BCS") ||
                mnemonic.equals("BVC") ||
                mnemonic.equals("BVS") ||
                mnemonic.equals("BGE") ||
                mnemonic.equals("BLT") ||
                mnemonic.equals("BGT") ||
                mnemonic.equals("BLE") ||
                mnemonic.startsWith("LB");
    }

    private static boolean isDirective(String s) {
        if (s == null) return false;
        String k = s.trim().toUpperCase();
        return k.equals("ORG") || k.equals("EQU") || k.equals("SET")
                || k.equals("FCB") || k.equals("FDB") || k.equals("RMB") || k.equals("END");
    }

    private List<String> splitCommaList(String operandText) {
        String[] parts = operandText.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private boolean looksLikeNumber(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return false;
        char c = s.charAt(0);
        // $hex  %bin  @oct  digit  +  -
        return c == '$' || c == '%' || c == '@' || Character.isDigit(c) || c == '+' || c == '-';
    }

    private int resolveValueOrNumber(String token) {
        token = token.trim();
        if (token.isEmpty()) throw new IllegalStateException("Missing value");
        if (symbols.contains(token)) return symbols.resolve(token);
        return parseNumber(token);
    }

    /**
     * Supports:
     *   $FFFF, %1010, @377, 123, -10, +10, -$10, +$10, -%1010, +@77*/

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

        return (sign * v);
    }

    private boolean isBracketOnly(String op) {
        String t = op == null ? "" : op.trim();
        return t.startsWith("[") && t.endsWith("]") && t.length() >= 2;
    }

    private String stripOuterBrackets(String op) {
        String t = op.trim();
        return t.substring(1, t.length() - 1);
    }

    private boolean containsLabelReference(String mnemonic, String operandText) {
        String op = operandText == null ? "" : operandText.trim();
        if (op.isEmpty()) return false;

        // Branch always references something potentially symbolic
        if (isBranchMnemonic(mnemonic.toUpperCase())) return true;

        // Immediate label?
        if (op.startsWith("#")) {
            String imm = op.substring(1).trim();
            return !imm.isEmpty() && !looksLikeNumber(imm);
        }

        // PC-relative label?
        if (op.contains(",PC")) {
            String expr = op.replace("[", "").replace("]", "").replace(",PC", "").trim();
            return !expr.isEmpty() && !looksLikeNumber(expr);
        }

        // [LABEL] form
        if (isBracketOnly(op)) {
            String inner = stripOuterBrackets(op).trim();
            return !inner.contains(",") && !inner.isEmpty() && !looksLikeNumber(inner);
        }

        // absolute label (no comma/brackets)
        if (!op.contains(",") && !op.contains("[") && !op.contains("]")) {
            return !looksLikeNumber(op);
        }

        return false;
    }


}