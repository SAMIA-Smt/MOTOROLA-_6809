package com.simulator.moto6809.Assembler;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.InstructionDefinition;
import com.simulator.moto6809.Decoder.InstructionSet;
import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Resource.InstructionCsvLoader;
import com.simulator.moto6809.Resource.InstructionCsvRow;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssemblerSelfTest {


    // Logger silencieux (pour ne pas polluer la console pendant les tests)

    static final class SilentLogger implements ILogger {
        @Override public void log(String message, LogLevel level) {}
        @Override public void log(Response response, LogLevel level) {}
        @Override public void clear() {}
        @Override public void setLogFilePath(Path path) {}
    }

    public static void main(String[] args) {

        ILogger logger = new SilentLogger();

        // InstructionSet = source de vérité (opcodes/tailles/cycles)
        InstructionSet instructionSet = new InstructionSet(logger);

        // Assembler actuel (OpcodeSelector + AssemblerProgram)
        InstructionCsvLoader loader = new InstructionCsvLoader(logger);
        Map<String, InstructionCsvRow> opcodeTable = loader.loadOpcodeTable();
        OpcodeSelector selector = new OpcodeSelector(opcodeTable);
        Assembler assembler = new Assembler(selector, instructionSet);
        AssemblerProgram program = new AssemblerProgram(assembler, instructionSet);


        // Run all tests

        run("Immediate size + prefixed opcode", () -> testImmediateAndPrefixed(instructionSet, program));
        run("Long branch (LBEQ) must include prefix + correct offset", () -> testLongBranchPrefix(instructionSet, program));
        run("Short branch offset (BRA)", () -> testShortBranch(instructionSet, program));
        run("PC-relative label (LABEL,PC)", () -> testPcRelativeLabel(instructionSet, program));
        run("Indexed indirect + extended indirect", () -> testIndirectForms(instructionSet, program));
        run("EQU + immediate label", () -> testEquImmediateLabel(instructionSet, program));
        run("Undefined label must fail", () -> testUndefinedLabelMustFail(program));

        System.out.println("\n ALL ASSEMBLER TESTS PASSED");
    }

    // Tests
    // 1) Vérifie:
    // - LDX #$12 => immédiat 16-bit (donc 2 octets d’opérande)
    // - LDY #$1234 => opcode préfixé (0x10xx) => 2 octets d’opcode
    // - LDA #$7F => immédiat 8-bit
    private static void testImmediateAndPrefixed(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "LDX #$12",
                "LDY #$1234",
                "LDA #$7F",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> expected = new ArrayList<>();

        // LDX #$0012 (toujours 16-bit sur 6809)
        expected.addAll(opcodeBytes(is, "LDX", AddressingMode.IMMEDIATE));
        expected.add(0x00);
        expected.add(0x12);

        // LDY #$1234 (opcode préfixé généralement)
        expected.addAll(opcodeBytes(is, "LDY", AddressingMode.IMMEDIATE));
        expected.add(0x12);
        expected.add(0x34);

        // LDA #$7F (8-bit)
        expected.addAll(opcodeBytes(is, "LDA", AddressingMode.IMMEDIATE));
        expected.add(0x7F);

        assertBytesEqual("Immediate+Prefixed", expected, actual);
    }

    // 2) Vérifie LBEQ (long branch) :
    // doit sortir 2 bytes d’opcode si opcode=0x10xx, puis 2 bytes offset.
    private static void testLongBranchPrefix(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "ORG $0100",
                "LBEQ TARGET",
                "NOP",
                "TARGET: NOP",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> expected = new ArrayList<>();

        // LBEQ TARGET : offset = TARGET - nextPc
        List<Integer> op = opcodeBytes(is, "LBEQ", AddressingMode.RELATIVE);

        int lbeqSize = op.size() + 2;          // opcode bytes + 16-bit offset
        int nopSize  = opcodeBytes(is, "NOP", AddressingMode.INHERENT).size();

        int base = 0x0100;
        int nextPc = base + lbeqSize;
        int targetAddr = base + lbeqSize + nopSize; // après le NOP
        int offset = targetAddr - nextPc;           // ici attendu = 1

        expected.addAll(op);
        expected.add((offset >> 8) & 0xFF);
        expected.add(offset & 0xFF);

        expected.addAll(opcodeBytes(is, "NOP", AddressingMode.INHERENT));
        expected.addAll(opcodeBytes(is, "NOP", AddressingMode.INHERENT));

        assertBytesEqual("LBEQ prefix+offset", expected, actual);
    }

    // 3) BRA SKIP + FCB $AA + SKIP: NOP
    // BRA offset doit être correct (8-bit)
    private static void testShortBranch(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "ORG $0100",
                "BRA SKIP",
                "FCB $AA",
                "SKIP: NOP",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> expected = new ArrayList<>();

        List<Integer> bra = opcodeBytes(is, "BRA", AddressingMode.RELATIVE);
        int braSize = bra.size() + 1; // opcode + 8-bit offset

        int base = 0x0100;
        int nextPc = base + braSize;
        int skipAddr = base + braSize + 1; // +1 pour FCB
        int offset = skipAddr - nextPc;    // attendu = 1

        expected.addAll(bra);
        expected.add(offset & 0xFF);

        expected.add(0xAA);
        expected.addAll(opcodeBytes(is, "NOP", AddressingMode.INHERENT));

        assertBytesEqual("BRA offset", expected, actual);
    }

    // 4) LEAX LABEL,PC ; NOP ; LABEL: FCB 0
    // vérifie que l’offset PC-relative est bien résolu
    private static void testPcRelativeLabel(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "ORG $0200",
                "LEAX LABEL,PC",
                "NOP",
                "LABEL: FCB $00",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> leaxOp = opcodeBytes(is, "LEAX", AddressingMode.INDEXED);
        int nopSize = opcodeBytes(is, "NOP", AddressingMode.INHERENT).size();

        int base = 0x0200;

        // We don't know yet if assembler chose 8-bit or 16-bit.
        // But offset should be +1 in this program.
        int offset = 1;

        // Build expected (8-bit form)
        List<Integer> expected8 = new ArrayList<>();
        expected8.addAll(leaxOp);
        expected8.add(0x8C);           // 8-bit PC-relative postbyte
        expected8.add(offset & 0xFF);  // 8-bit offset
        expected8.addAll(opcodeBytes(is, "NOP", AddressingMode.INHERENT));
        expected8.add(0x00);

        // Build expected (16-bit form)
        List<Integer> expected16 = new ArrayList<>();
        expected16.addAll(leaxOp);
        expected16.add(0x8D);                 // 16-bit PC-relative postbyte
        expected16.add((offset >> 8) & 0xFF); // high
        expected16.add(offset & 0xFF);        // low
        expected16.addAll(opcodeBytes(is, "NOP", AddressingMode.INHERENT));
        expected16.add(0x00);

        // Accept either encoding
        if (actual.size() == expected8.size()) {
            assertBytesEqual("LABEL,PC (8-bit)", expected8, actual);
        } else if (actual.size() == expected16.size()) {
            assertBytesEqual("LABEL,PC (16-bit)", expected16, actual);
        } else {
            throw new AssertionError("LABEL,PC unexpected size: got " + actual.size()
                    + "\nexpected8=" + hex(expected8)
                    + "\nexpected16=" + hex(expected16)
                    + "\nactual   =" + hex(actual));
        }
    }


    // 5) LDA [5,X] et JMP [$1234]
    private static void testIndirectForms(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "ORG $0300",
                "LDA [5,X]",
                "JMP [$1234]",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> expected = new ArrayList<>();

        // LDA [5,X]
        expected.addAll(opcodeBytes(is, "LDA", AddressingMode.INDEXED));
        IndexedOperand ldaIo = IndexedEncoder.encodeConstantOffset(5, "X", true);
        expected.add(ldaIo.postbyte);
        expected.add(ldaIo.extra & 0xFF);

        // JMP [$1234] => postbyte 0x9F + addr16
        expected.addAll(opcodeBytes(is, "JMP", AddressingMode.INDEXED));
        IndexedOperand jmpIo = IndexedEncoder.encodeExtendedIndirect(0x1234);
        expected.add(jmpIo.postbyte);
        expected.add((jmpIo.extra >> 8) & 0xFF);
        expected.add(jmpIo.extra & 0xFF);

        assertBytesEqual("Indirect forms", expected, actual);
    }

    // 6) VALUE EQU $34 ; LDA #VALUE
    private static void testEquImmediateLabel(InstructionSet is, AssemblerProgram prog) {

        List<String> lines = List.of(
                "ORG $0400",
                "VALUE EQU $34",
                "LDA #VALUE",
                "END"
        );

        List<Integer> actual = prog.assemble(lines, 0x0000);

        List<Integer> expected = new ArrayList<>();
        expected.addAll(opcodeBytes(is, "LDA", AddressingMode.IMMEDIATE));
        expected.add(0x34);

        assertBytesEqual("EQU immediate", expected, actual);
    }

    // 7) BRA MISSING => doit lancer une exception (label non défini)
    private static void testUndefinedLabelMustFail(AssemblerProgram prog) {
        List<String> lines = List.of(
                "BRA MISSING",
                "END"
        );

        try {
            prog.assemble(lines, 0x0000);
            throw new AssertionError("Expected failure for undefined label, but assemble() succeeded.");
        } catch (RuntimeException ex) {
            // OK
        }
    }


    // Helpers


    private static void run(String name, Runnable test) {
        try {
            test.run();
            System.out.println("DONE " + name);
        } catch (Throwable t) {
            System.err.println("\nNOT DONE " + name);
            System.err.println("   " + t.getClass().getSimpleName() + ": " + t.getMessage());
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    private static List<Integer> opcodeBytes(InstructionSet is, String mnemonic, AddressingMode mode) {
        InstructionDefinition def = is.getByMnemonic(mnemonic);
        int opcode = def.getOpcode(mode);

        // 6809: si opcode a un préfixe, il est dans le byte haut (souvent 0x10 ou 0x11)
        if ((opcode & 0xFF00) != 0) {
            return List.of((opcode >> 8) & 0xFF, opcode & 0xFF);
        }
        return List.of(opcode & 0xFF);
    }

    private static void assertBytesEqual(String label, List<Integer> expected, List<Integer> actual) {
        if (expected.size() != actual.size()) {
            throw new AssertionError(label + " size mismatch: expected " + expected.size()
                    + " but got " + actual.size()
                    + "\nexpected=" + hex(expected)
                    + "\nactual  =" + hex(actual));
        }

        for (int i = 0; i < expected.size(); i++) {
            int e = expected.get(i) & 0xFF;
            int a = actual.get(i) & 0xFF;
            if (e != a) {
                throw new AssertionError(label + " mismatch at index " + i
                        + ": expected " + String.format("%02X", e)
                        + " but got " + String.format("%02X", a)
                        + "\nexpected=" + hex(expected)
                        + "\nactual  =" + hex(actual));
            }
        }
    }

    private static String hex(List<Integer> bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes.get(i) & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }
}
