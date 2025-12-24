package com.simulator.moto6809.Tests;

import com.simulator.moto6809.Bootstrap.Bootstrap;
import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Registers.Register;

import java.nio.file.Path;

public class Test2_LoopBranchBreakpointMain {

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

        Path asm = Path.of("C:\\Users\\hp\\IdeaProjects\\MOTO6809\\src\\main\\resources\\com\\simulator\\moto6809\\asm-tests\\test2.asm");
        boot.loadAsmFileToRom(asm, null, false);

        boot.addBreakpoint(0xE050); // DONE
        boot.resetCpu();
        boot.run(100000); // run stops when breakpoint hit. :contentReference[oaicite:3]{index=3}

        int pc = boot.registers().getRegister(Register.PC, false);
        int count = boot.bus().read(0x0100);
        int a = boot.registers().getRegister(Register.A, false);

        System.out.printf("PC=$%04X (expected E050)%n", pc);
        System.out.printf("COUNT($0100)=$%02X (expected 0A)%n", count);
        System.out.printf("A=$%02X (often 0A at end)%n", a);

        boolean ok = (pc == 0xE050) && (count == 0x0A);
        System.out.println(ok ? " TEST2 PASS" : " TEST2 FAIL");
    }
}
