package com.simulator.moto6809.Bootstrap;

import com.simulator.moto6809.Assembler.Assembler;
import com.simulator.moto6809.Assembler.AssemblerProgram;
import com.simulator.moto6809.Assembler.OpcodeSelector;

import com.simulator.moto6809.Debugger.BreakpointManager;
import com.simulator.moto6809.Debugger.DebugController;

import com.simulator.moto6809.Decoder.InstructionSet;

import com.simulator.moto6809.Execution.CPU.CPU;

import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;

import com.simulator.moto6809.Memory.Memory;
import com.simulator.moto6809.Memory.MemoryBus;

import com.simulator.moto6809.Registers.RegisterFunctions;

import com.simulator.moto6809.Resource.InstructionCsvLoader;
import com.simulator.moto6809.Resource.InstructionCsvRow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Bootstrap {

    private final ILogger logger;

    private final Memory memory;
    private final MemoryBus bus;

    private final InstructionSet instructionSet;
    private final RegisterFunctions registers;

    private final BreakpointManager breakpointManager;
    private final DebugController debugController;

    private final CPU cpu;

    private final AssemblerProgram assemblerProgram;

    public Bootstrap(ILogger logger) {
        this.logger = (logger != null) ? logger : new StdoutLogger();

        this.memory = new Memory(this.logger);
        this.bus = new MemoryBus(this.memory);

        this.instructionSet = new InstructionSet(this.logger);
        this.registers = new RegisterFunctions(this.logger);

        this.breakpointManager = new BreakpointManager();
        this.debugController = new DebugController(this.breakpointManager);
        this.debugController.stop();

        this.cpu = new CPU(this.bus, this.registers, this.instructionSet, this.logger, this.debugController);

        this.assemblerProgram = buildAssemblerProgram(this.logger, this.instructionSet);
    }

    public Memory memory() { return memory; }
    public MemoryBus bus() { return bus; }
    public InstructionSet instructionSet() { return instructionSet; }
    public RegisterFunctions registers() { return registers; }
    public DebugController debug() { return debugController; }
    public CPU cpu() { return cpu; }
    public AssemblerProgram assemblerProgram() { return assemblerProgram; }

    public int loadAsmToRom(List<String> asmLines, Integer defaultOrigin, boolean writeResetVectorIfMissing) {
        int origin = (defaultOrigin != null) ? (defaultOrigin & 0xFFFF) : (memory.getROMstart() & 0xFFFF);
        bus.syncRomRangeFrom(memory);
        return assemblerProgram.assembleToRom(memory, asmLines, origin, writeResetVectorIfMissing);
    }

    public void resetCpu() {
        bus.syncRomRangeFrom(memory);
        cpu.reset();
    }

    public void run(int maxInstructions) {
        cpu.run(maxInstructions);
    }

    public int stepOnce() {
        return cpu.stepOnce();
    }

    public void addBreakpoint(int address) { breakpointManager.add(address & 0xFFFF); }
    public void removeBreakpoint(int address) { breakpointManager.remove(address & 0xFFFF); }
    public void clearBreakpoints() { breakpointManager.clear(); }

    public void clearRam() { bus.clearRamOnly(); }

    public void clearRom(boolean keepVectors) {
        memory.clearRom(keepVectors);
        bus.syncRomRangeFrom(memory);
    }


    /** Listing for UI mapping PC <-> line. */
    public List<AssemblerProgram.ListingRow> assembleListing(List<String> asmLines, int origin) {
        return assemblerProgram.assembleListing(asmLines, origin & 0xFFFF);
    }

    private AssemblerProgram buildAssemblerProgram(ILogger logger, InstructionSet instructionSet) {
        InstructionCsvLoader loader = new InstructionCsvLoader(logger);
        Map<String, InstructionCsvRow> opcodeTable = loader.loadOpcodeTable();

        OpcodeSelector selector = new OpcodeSelector(opcodeTable);
        Assembler assembler = new Assembler(selector, instructionSet);
        return new AssemblerProgram(assembler, instructionSet);
    }

    private static final class StdoutLogger implements ILogger {
        @Override public void log(String message, LogLevel level) {
            String msg = "[" + level + "] " + message;
            if (level == LogLevel.ERROR) System.err.println(msg);
            else System.out.println(msg);
        }
        @Override public void log(Response response, LogLevel level) { log(String.valueOf(response), level); }
        @Override public void clear() {}
        @Override public void setLogFilePath(Path logFilePath) {}
    }
    public int loadAsmFileToRom(Path asmFile, Integer defaultOrigin, boolean writeResetVectorIfMissing) throws IOException {
        if (asmFile == null) throw new IllegalArgumentException("asmFile is null");
        List<String> lines = Files.readAllLines(asmFile);
        return loadAsmToRom(lines, defaultOrigin, writeResetVectorIfMissing);
    }

    public int loadAsmFileToRom(Path asmFile) throws IOException {
        return loadAsmFileToRom(asmFile, null, true);
    }

}