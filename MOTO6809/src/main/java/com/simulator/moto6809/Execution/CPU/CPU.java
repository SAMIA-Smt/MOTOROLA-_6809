package com.simulator.moto6809.Execution.CPU;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Decoder.Decoder;
import com.simulator.moto6809.Decoder.InstructionSet;
import com.simulator.moto6809.Debugger.DebugController;
import com.simulator.moto6809.Execution.Instructions.ControlInstructions;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;

public class CPU {


    // Vectors (6809 memory map)

    private static final int VEC_SWI3  = 0xFFF2;
    private static final int VEC_SWI2  = 0xFFF4;
    private static final int VEC_FIRQ  = 0xFFF6;
    private static final int VEC_IRQ   = 0xFFF8;
    private static final int VEC_SWI   = 0xFFFA;
    private static final int VEC_NMI   = 0xFFFC;
    private static final int VEC_RESET = 0xFFFE;


    // Core components

    private final MemoryBus bus;
    private final RegisterFunctions regs;
    private final Decoder decoder;
    private final InstructionExecutor executor;
    private final CycleCounter cycles = new CycleCounter();
    private final InterruptController interrupts = new InterruptController();

    private final ILogger logger;
    private final DebugController debug;

    private CpuMode mode = CpuMode.RUNNING;
    private DecodedInstruction lastInstruction = null;

    // Construction

    public CPU(MemoryBus bus,
               RegisterFunctions regs,
               InstructionSet instructionSet,
               ILogger logger,
               DebugController debugController) {

        this.bus = bus;
        this.regs = regs;
        this.decoder = new Decoder(instructionSet, logger);
        this.executor = new InstructionExecutor(regs, bus);
        this.logger = logger;
        this.debug = debugController;
    }

    //
    public interface CpuListener {
        void onAfterInstruction(CpuStateSnapshot snap);
    }

    private CpuListener listener;

    public void setListener(CpuListener listener) {
        this.listener = listener;
    }


    // External signals (hardware/UI can call later)

    public InterruptController interrupts() { return interrupts; }


    // Reset behavior (REAL 6809)

    public void reset() {
        mode = CpuMode.RUNNING;
        cycles.reset();
        interrupts.clearAll();
        lastInstruction = null;

        int pc = readVector(VEC_RESET);
        regs.setRegister(Register.PC, pc);

        if (logger != null)
            logger.log(String.format("CPU RESET -> PC loaded from vector $%04X = $%04X", VEC_RESET, pc),
                    LogLevel.INFO);
    }

    public void halt() {
        mode = CpuMode.HALTED;
        if (logger != null) logger.log("CPU halted", LogLevel.WARNING);
    }

    public boolean isHalted() {
        return mode == CpuMode.HALTED;
    }

    public CpuMode mode() { return mode; }

    public long totalCycles() { return cycles.getTotalCycles(); }

    public CpuStateSnapshot snapshot() {
        return CpuStateSnapshot.from(regs, cycles, lastInstruction);
    }


    // Step one instruction (6809-correct)

    public int stepOnce()
    {


        // 0) Stop conditions
        if (mode == CpuMode.HALTED)
            return 0;

        // 1) If waiting (SYNC/CWAI), only resume on interrupt
        if (mode == CpuMode.WAIT_SYNC || mode == CpuMode.WAIT_CWAI) {
            InterruptType t = interrupts.next(regs);
            if (t == null) {
                // still waiting, consume 0 cycles (UI will show frozen state)
                return 0;
            }
            // resume by taking interrupt
            return takeInterrupt(t);
        }

        int pc = regs.getRegister(Register.PC);

        // breakpoint check BEFORE executing instruction at PC
        if (debug != null && debug.shouldBreakAt(pc)) {
            if (logger != null)
                logger.log(String.format("Breakpoint hit at PC=$%04X", pc), LogLevel.INFO);
            debug.pause();
            return 0;
        }

        // 2) Asynchronous interrupts can be taken between instructions
        InterruptType pending = interrupts.next(regs);
        if (pending != null) {
            return takeInterrupt(pending);
        }

        // 3) Decode
        DecodedInstruction instr = decoder.decodeAt(bus, pc);

        // 4) Execute
        int used = executor.execute(instr);

        // 5) Add cycles
        cycles.add(used);

        // 6) Advance PC if instruction didn't change it
        int pcAfter = regs.getRegister(Register.PC);
        if (pcAfter == pc) {
            regs.setRegister(Register.PC, instr.nextPc());
        }

        lastInstruction = instr;

        // 7) Handle SYNC / CWAI WAIT state (REAL behavior: CPU stops until interrupt)
        String m = instr.mnemonic().toUpperCase();
        if (m.equals(SYNC)) {
            mode = CpuMode.WAIT_SYNC;
        } else if (m.equals(CWAI)) {
            mode = CpuMode.WAIT_CWAI;
        }

        // 8) Handle SWI/SWI2/SWI3 vector entry (PC loaded from vector AFTER state push)
        // In ControlInstructions.execute(), we pushed state but did not load PC.
        // Here, we request the SWI interrupt and take it immediately (synchronous).
        if (m.equals(SWI)) {
            interrupts.requestSWI();
            InterruptType t = interrupts.next(regs);
            if (t != null) return takeInterrupt(t);
        } else if (m.equals(SWI2)) {
            interrupts.requestSWI2();
            InterruptType t = interrupts.next(regs);
            if (t != null) return takeInterrupt(t);
        } else if (m.equals(SWI3)) {
            interrupts.requestSWI3();
            InterruptType t = interrupts.next(regs);
            if (t != null) return takeInterrupt(t);
        }
        if (listener != null) listener.onAfterInstruction(snapshot());
        return used;
    }


    // Run loop (UI Run)

    public void run(int maxInstructions) {
        if (mode == CpuMode.HALTED)
            return;

        if (debug != null) debug.run();

        int executed = 0;
        while (mode != CpuMode.HALTED) {

            if (debug != null) {
                if (debug.mode() == DebugController.Mode.PAUSED ||
                        debug.mode() == DebugController.Mode.STOPPED) {
                    break;
                }
            }

            stepOnce();
            executed++;

            if (debug != null && debug.stepRequested()) {
                debug.clearStepRequest();
                debug.pause();
                break;
            }

            if (maxInstructions > 0 && executed >= maxInstructions) {
                if (logger != null)
                    logger.log("Run stopped: maxInstructions reached (" + maxInstructions + ")", LogLevel.WARNING);
                break;
            }
        }
    }


    // Interrupt entry (REAL 6809)

    private int takeInterrupt(InterruptType type) {

        // Acknowledge line now (latched)
        interrupts.acknowledge(type);

        // If we were waiting, resume execution
        if (mode == CpuMode.WAIT_SYNC || mode == CpuMode.WAIT_CWAI) {
            mode = CpuMode.RUNNING;
        }

        // Let ControlInstructions do the correct stacking + flags timing
        int entryCycles = ControlInstructions.enterInterrupt(type, regs, bus);

        // Load PC from correct vector
        int vectorAddr = vectorAddress(type);
        int newPc = readVector(vectorAddr);
        regs.setRegister(Register.PC, newPc);

        // Add cycles
        cycles.add(entryCycles);

        if (logger != null) {
            logger.log(String.format("INTERRUPT %-4s -> vector $%04X => PC=$%04X (cycles +%d)",
                    type, vectorAddr, newPc, entryCycles), LogLevel.INFO);
        }

        // return cycles used by interrupt entry (useful for stepOnce)
        return entryCycles;
    }

    private int vectorAddress(InterruptType type) {
        return switch (type) {
            case RESET -> VEC_RESET;
            case NMI   -> VEC_NMI;
            case IRQ   -> VEC_IRQ;
            case FIRQ  -> VEC_FIRQ;
            case SWI   -> VEC_SWI;
            case SWI2  -> VEC_SWI2;
            case SWI3  -> VEC_SWI3;
        };
    }

    private int readVector(int address) {
        // Vectors are big-endian 16-bit addresses in memory
        return bus.readWord(address) & 0xFFFF;
    }
}

