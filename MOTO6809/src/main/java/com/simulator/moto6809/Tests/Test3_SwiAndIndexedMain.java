package com.simulator.moto6809.Tests;
import com.simulator.moto6809.Bootstrap.Bootstrap;
import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Registers.Register;

import java.nio.file.Path;

public class Test3_SwiAndIndexedMain {

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

        Path asm = Path.of("C:\\Users\\hp\\IdeaProjects\\MOTO6809\\src\\main\\resources\\com\\simulator\\moto6809\\asm-tests\\test3.asm");
        boot.loadAsmFileToRom(asm, null, false);

        boot.addBreakpoint(0xE050); // DONE
        boot.resetCpu();
        boot.run(200000);

        int pc = boot.registers().getRegister(Register.PC, false);

        int swiVec = boot.bus().readWord(0xFFFA);
        int b0 = boot.bus().read(0x0100); // 12
        int b1 = boot.bus().read(0x0101); // 34
        int b2 = boot.bus().read(0x0102); // 99 (AFTER)
        int b3 = boot.bus().read(0x0103); // 77 (handler)

        System.out.printf("PC=$%04X (expected E050)%n", pc);
        System.out.printf("SWI vector @FFFA=$%04X (expected E100)%n", swiVec);
        System.out.printf("BUF[0..3]=%02X %02X %02X %02X (expected 12 34 99 77)%n", b0,b1,b2,b3);

        boolean ok =
                pc == 0xE050 &&
                        swiVec == 0xE100 &&
                        b0 == 0x12 && b1 == 0x34 &&
                        b2 == 0x99 && b3 == 0x77;

        System.out.println(ok ? " TEST3 PASS" : " TEST3 FAIL");
    }
}
