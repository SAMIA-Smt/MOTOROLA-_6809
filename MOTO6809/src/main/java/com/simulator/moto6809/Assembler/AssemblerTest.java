package com.simulator.moto6809.Assembler;
import com.simulator.moto6809.Decoder.InstructionSet;
import com.simulator.moto6809.Resource.InstructionCsvLoader;
import com.simulator.moto6809.Logger.ConsoleLogger;
import com.simulator.moto6809.Resource.InstructionCsvRow;
import java.util.List;
import java.util.Map;
public class AssemblerTest {

    public static void main(String[] args) {


        // 1) Load CSV tables (same as CPU does)

        ConsoleLogger logger = new ConsoleLogger();

        InstructionCsvLoader loader = new InstructionCsvLoader(logger);

        Map<String, InstructionCsvRow> opcodeTable =
                loader.loadOpcodeTable();


        // 2) Create assembler

        OpcodeSelector selector = new OpcodeSelector(opcodeTable);
        InstructionSet m = null;
        Assembler assembler = new Assembler(selector, m);
        AssemblerProgram prog = new AssemblerProgram(assembler);


        // 3) Test assembly with labels

        List<String> lines = List.of(
                        "ORG $1000",

                        "START:",
                        "NOP",
                        "RTS",

                        "VALUE EQU $12",
                        "COUNT SET 1",
                        "COUNT SET 2",

                        "LDA #VALUE",
                        "LDD #$1234",

                        "ORG $1100",

                        "DATA1:",
                        "FCB $10, VALUE, COUNT",
                        "FDB $2000, DATA1",

                        "BUFFER:",
                        "RMB 4",

                        "ORG $1200",

                        "LDA $20",
                        "STA $80",
                        "LDA [$3000]",

                        "BRA TARGET",
                        "BEQ TARGET",

                        "TARGET:",
                        "NOP",

                        "END"

                        /*"LDA #$FF" ,"START:",
                "NOP",
                "RTS",
                "LDA #$12",
                "LDD #$1234",
                "LDA $20",
                "STA $80",
                "LDA $1234",
                "STA $2000",
                "LDA [$3000]",
                "BRA TARGET",
                "BEQ TARGET",
                "TARGET:",
                "NOP"
                ,"LDA ,X",
                "LDA ,Y+",
                "LDA ,--U",
                "ADDB -14,X",
                "LDA #30,X",
                "LDB $8000,Y",
                "LDA A,X",
                "LEAY D,X",
                "LDB $20,PC",
                "ADDA $2000,PC",
                "LDA [B,X]",
                "LEAY [D,X]",
                "LDA [$1234]",
                "LEAX TARGET,PC",
                "LEAX [TARGET,PC]"*/
        );
        List<Integer> bytes = prog.assemble(lines, 0x1000);


        // 4) PRINT RESULTS

        System.out.println("Assembled bytes:");
        int addr = 0x1000;
        for (int b : bytes) {
            System.out.printf("$%04X : %02X%n", addr++, b);
        }

        System.out.println();
        System.out.println("Label START = $" +
                Integer.toHexString(prog.getSymbolTable().resolve("START")));
    }
}
