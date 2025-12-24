package com.simulator.moto6809.UI;

import com.simulator.moto6809.Controller.MainController;
import com.simulator.moto6809.Execution.CPU.CpuStateSnapshot;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Errors.Response;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Scanner;

public final class ConsoleUI {

    public static void main(String[] args) throws Exception {
        // Usage:
        //  java ... ConsoleUI program.asm
        //  java ... ConsoleUI program.asm 20000
        if (args.length < 1) {
            System.out.println("Usage: java ... ConsoleUI <program.asm> [maxInstructions]");
            return;
        }

        Path asmPath = Path.of(args[0]);
        int max = (args.length >= 2) ? Integer.parseInt(args[1]) : 20000;

        ILogger logger = new StdoutLogger();
        MainController ctrl = new MainController(logger);

        // 1) Load program
        int entry = ctrl.loadAsmFileToRom(asmPath);
        logger.log("Loaded ASM. EntryPoint=" + hex4(entry), LogLevel.INFO);

        // 2) Reset CPU (reads RESET vector)
        ctrl.reset();
        printSnapshot(ctrl.snapshot());

        // 3) Auto-run once (optional)
        ctrl.run(max);
        System.out.println("\nAfter RUN(" + max + "):");
        printSnapshot(ctrl.snapshot());

        // 4) Interactive commands
        repl(ctrl);
    }


    // REPL


    private static void repl(MainController ctrl) {
        System.out.println("\nCommands:");
        System.out.println("  s | step                 : execute 1 instruction");
        System.out.println("  r <n> | run <n>          : run n instructions");
        System.out.println("  reset                    : CPU reset");
        System.out.println("  bp <addrHex>             : add breakpoint (ex: bp E030)");
        System.out.println("  rbp <addrHex>            : remove breakpoint");
        System.out.println("  m <addrHex> <lenDec>     : dump memory bytes (ex: m 0100 16)");
        System.out.println("  w <addrHex>              : read word at address (big-endian)");
        System.out.println("  q                        : quit");

        Scanner sc = new Scanner(System.in);
        sc.useLocale(Locale.ROOT);

        while (true) {
            System.out.print("\n> ");
            if (!sc.hasNextLine()) break;
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "q":
                    case "quit":
                    case "exit":
                        return;

                    case "s":
                    case "step": {
                        int pc = ctrl.step();
                        System.out.println("Step done. PC=" + hex4(pc));
                        printSnapshot(ctrl.snapshot());
                        break;
                    }

                    case "r":
                    case "run": {
                        int n = (parts.length >= 2) ? Integer.parseInt(parts[1]) : 20000;
                        ctrl.run(n);
                        System.out.println("Run done. n=" + n);
                        printSnapshot(ctrl.snapshot());
                        break;
                    }

                    case "reset": {
                        ctrl.reset();
                        System.out.println("CPU Reset.");
                        printSnapshot(ctrl.snapshot());
                        break;
                    }

                    case "bp": {
                        requireArgs(parts, 2);
                        int addr = parseHex16(parts[1]);
                        ctrl.addBreakpoint(addr);
                        System.out.println("Breakpoint added at " + hex4(addr));
                        break;
                    }

                    case "rbp": {
                        requireArgs(parts, 2);
                        int addr = parseHex16(parts[1]);
                        ctrl.removeBreakpoint(addr);
                        System.out.println("Breakpoint removed at " + hex4(addr));
                        break;
                    }

                    case "m": {
                        requireArgs(parts, 3);
                        int addr = parseHex16(parts[1]);
                        int len = Integer.parseInt(parts[2]);
                        dumpMemory(ctrl, addr, len);
                        break;
                    }

                    case "w": {
                        requireArgs(parts, 2);
                        int addr = parseHex16(parts[1]);
                        int w = ctrl.readWord(addr);
                        System.out.println("WORD[" + hex4(addr) + "]=" + hex4(w));
                        break;
                    }

                    default:
                        System.out.println("Unknown command: " + cmd);
                }
            } catch (Exception e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    private static void requireArgs(String[] parts, int n) {
        if (parts.length < n) throw new IllegalArgumentException("Missing args.");
    }


    // Snapshot display


    private static void printSnapshot(CpuStateSnapshot s) {
        if (s == null) {
            System.out.println("(snapshot is null)");
            return;
        }

        // 8-bit registers: A,B,DP,CC
        int A  = s.A  & 0xFF;
        int B  = s.B  & 0xFF;
        int DP = s.DP & 0xFF;
        int CC = s.CC & 0xFF;

        // 16-bit registers: D,X,Y,S,U,PC
        int D  = s.D  & 0xFFFF;
        int X  = s.X  & 0xFFFF;
        int Y  = s.Y  & 0xFFFF;
        int S  = s.S  & 0xFFFF;
        int U  = s.U  & 0xFFFF;
        int PC = s.PC & 0xFFFF;

        System.out.println("REGS:  A=" + hex2(A) + "  B=" + hex2(B) + "  D=" + hex4(D)
                + "  X=" + hex4(X) + "  Y=" + hex4(Y));
        System.out.println("       S=" + hex4(S) + "  U=" + hex4(U) + "  PC=" + hex4(PC)
                + "  DP=" + hex2(DP) + "  CC=" + hex2(CC));

        System.out.println("FLAGS: " +
                (s.E ? "E" : "e") +
                (s.F ? "F" : "f") +
                (s.H ? "H" : "h") +
                (s.I ? "I" : "i") +
                (s.N ? "N" : "n") +
                (s.Z ? "Z" : "z") +
                (s.V ? "V" : "v") +
                (s.C ? "C" : "c"));

        System.out.println("CYCLES: " + s.totalCycles);

        // On n'assume pas la structure de DecodedInstruction:
        // on affiche son toString
        System.out.println("LAST: " + String.valueOf(s.lastInstruction));
    }


    // Memory dump


    private static void dumpMemory(MainController ctrl, int startAddr, int len) {
        if (len <= 0) return;

        int addr = startAddr & 0xFFFF;
        int remaining = len;

        while (remaining > 0) {
            int lineCount = Math.min(16, remaining);
            StringBuilder sb = new StringBuilder();
            sb.append(hex4(addr)).append(": ");

            for (int i = 0; i < lineCount; i++) {
                int b = ctrl.readByte((addr + i) & 0xFFFF) & 0xFF;
                sb.append(hex2(b)).append(' ');
            }
            System.out.println(sb);

            addr = (addr + lineCount) & 0xFFFF;
            remaining -= lineCount;
        }
    }


    // Helpers hex


    private static int parseHex16(String s) {
        String t = s.trim().toUpperCase();
        if (t.startsWith("$")) t = t.substring(1);
        return Integer.parseInt(t, 16) & 0xFFFF;
    }

    private static String hex2(int v) {
        return String.format("$%02X", v & 0xFF);
    }

    private static String hex4(int v) {
        return String.format("$%04X", v & 0xFFFF);
    }


    // Minimal logger


    private static final class StdoutLogger implements ILogger {
        @Override
        public void log(String message, LogLevel level) {
            String msg = "[" + level + "] " + message;
            if (level == LogLevel.ERROR) System.err.println(msg);
            else System.out.println(msg);
        }

        @Override
        public void log(Response response, LogLevel level) {
            log(String.valueOf(response), level);
        }

        @Override
        public void clear() { }

        @Override
        public void setLogFilePath(java.nio.file.Path logFilePath) { }
    }


}
