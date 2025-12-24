package com.simulator.moto6809.Tests;

import com.simulator.moto6809.Bootstrap.Bootstrap;
import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Memory.Memory;
import com.simulator.moto6809.Registers.Register;
import java.nio.file.Path;
import java.util.Arrays;

public class Test1_DirectivesAndResetMain {

    // Quiet logger (avoid huge DEBUG spam)
    private static final class QuietLogger implements ILogger {
        @Override public void log(String message, LogLevel level) {
            if (level == LogLevel.DEBUG) return;
            System.out.println("[" + level + "] " + message);
        }
        @Override public void log(Response response, LogLevel level) { log(String.valueOf(response), level); }
        @Override public void clear() {}
        @Override public void setLogFilePath(java.nio.file.Path logFilePath) {}
    }

    public static void main(String[] args) throws Exception {
        Bootstrap boot = new Bootstrap(new QuietLogger());

        // Fill ROM with sentinel 0xCC to verify ORG gaps + RMB keep bytes unchanged
        Memory mem = boot.memory();
        byte[] raw = mem.getMemory();
        int romStart = mem.getROMstart() & 0xFFFF;
        int romEnd   = mem.getROMend() & 0xFFFF;
        for (int a = romStart; a <= romEnd; a++) raw[a] = (byte) 0xCC;

        Path asm = Path.of("C:\\Users\\hp\\IdeaProjects\\MOTO6809\\src\\main\\resources\\com\\simulator\\moto6809\\asm-tests\\test1.asm");
        boot.loadAsmFileToRom(asm, null, false);

        // Break at DONE ($E030). CPU checks breakpoint BEFORE executing at PC. :contentReference[oaicite:0]{index=0}
        boot.addBreakpoint(0xE030);

        boot.resetCpu(); // RESET reads vector at $FFFE and sets PC. :contentReference[oaicite:1]{index=1}
        boot.run(100000); // stops when breakpoint pauses debugger. :contentReference[oaicite:2]{index=2}

        // Assertions (manual checks)
        int vReset = boot.bus().readWord(0xFFFE);
        int pc = boot.registers().getRegister(Register.PC, false);

        int ram0100 = boot.bus().read(0x0100);
        int ram0101 = boot.bus().read(0x0101);

        int d0 = boot.bus().read(0xE010);
        int d1 = boot.bus().read(0xE011);
        int d2 = boot.bus().read(0xE012);

        int gap = boot.bus().read(0xE00F);     // should still be 0xCC (ORG gap)
        int r0  = boot.bus().read(0xE020);     // should still be 0xCC (RMB reserved)
        int r3  = boot.bus().read(0xE023);     // should still be 0xCC (RMB reserved)
        int afterRmb = boot.bus().read(0xE024);// should be 0xDD

        System.out.printf("RESET vector @FFFE = $%04X%n", vReset);
        System.out.printf("PC after run (break) = $%04X%n", pc);

        System.out.printf("RAM[$0100]=$%02X (expected 42)%n", ram0100);
        System.out.printf("RAM[$0101]=$%02X (expected 99)%n", ram0101);

        System.out.printf("ROM[$E010..12]=%02X %02X %02X (expected AA BB CC)%n", d0, d1, d2);
        System.out.printf("ROM gap byte [$E00F]=$%02X (expected CC)%n", gap);
        System.out.printf("RMB region [$E020]=$%02X, [$E023]=$%02X (expected CC CC)%n", r0, r3);
        System.out.printf("After RMB [$E024]=$%02X (expected DD)%n", afterRmb);

        // Minimal pass/fail
        boolean ok =
                vReset == 0xE000 &&
                        pc == 0xE030 &&
                        ram0100 == 0x42 &&
                        ram0101 == 0x99 &&
                        d0 == 0xAA && d1 == 0xBB && d2 == 0xCC &&
                        gap == 0xCC &&
                        r0 == 0xCC && r3 == 0xCC &&
                        afterRmb == 0xDD;

        System.out.println(ok ? " TEST1 PASS" : " TEST1 FAIL");
    }
}
