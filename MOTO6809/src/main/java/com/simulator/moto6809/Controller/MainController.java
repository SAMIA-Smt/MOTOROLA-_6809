package com.simulator.moto6809.Controller;

import com.simulator.moto6809.Bootstrap.Bootstrap;
import com.simulator.moto6809.Execution.CPU.CpuStateSnapshot;
import com.simulator.moto6809.Logger.ILogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class MainController {

    private final Bootstrap boot;
    private Integer entryPoint; // null tant que pas chargé

    public MainController(ILogger logger) {
        this.boot = new Bootstrap(logger);
    }


    // Program loading


    public int loadAsmFileToRom(Path asmFile) throws IOException {
        int ep = boot.loadAsmFileToRom(asmFile, null, true);
        this.entryPoint = ep;
        return ep;
    }

    public int loadAsmLinesToRom(List<String> asmLines) {
        int ep = boot.loadAsmToRom(asmLines, null, true);
        this.entryPoint = ep;
        return ep;
    }

    public Integer entryPoint() {
        return entryPoint;
    }


    // CPU control


    public void reset() {
        boot.resetCpu();
    }

    public int step() {
        return boot.stepOnce();
    }

    public void run(int maxInstructions) {
        boot.run(maxInstructions);
    }


    // Debug / breakpoints


    public void addBreakpoint(int address) {
        boot.addBreakpoint(address);
    }

    public void removeBreakpoint(int address) {
        boot.removeBreakpoint(address);
    }

    public void runMode() {
        boot.debug().run();
    }

    public void pauseMode() {
        boot.debug().pause();
    }

    public void stopMode() {
        boot.debug().stop();
    }

    public void requestStepMode() {
        boot.debug().requestStep();
    }


    // Inspection (UI)


    public CpuStateSnapshot snapshot() {
        // IMPORTANT: ton CPU doit avoir cpu.snapshot()
        return boot.cpu().snapshot();
    }

    public int readByte(int address) {
        return boot.bus().read(address);
    }

    public int readWord(int address) {
        return boot.bus().readWord(address);
    }
}

