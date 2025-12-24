package com.simulator.moto6809.UI;

import com.simulator.moto6809.Assembler.AssemblerProgram;
import com.simulator.moto6809.Bootstrap.Bootstrap;
import com.simulator.moto6809.Execution.CPU.CpuStateSnapshot;
import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;
import com.simulator.moto6809.Errors.Response;
import com.simulator.moto6809.Registers.Register;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class CentralController {

    // Console
    private final ObservableList<String> console = FXCollections.observableArrayList();
    public ObservableList<String> consoleLines() { return console; }

    // Bootstrap
    private final Bootstrap boot;

    // CPU thread
    private final ExecutorService cpuExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "cpu-thread");
        t.setDaemon(true);
        return t;
    });
    //Program
    private final javafx.collections.ObservableList<ProgramRow> programRows =
            javafx.collections.FXCollections.observableArrayList();

    public javafx.collections.ObservableList<ProgramRow> programRows() { return programRows; }


    private final AtomicReference<CpuStateSnapshot> pendingSnapshot = new AtomicReference<>(null);

    // Busy guard (atomic for logic) + JavaFX property for UI
    private final AtomicBoolean busyFlag = new AtomicBoolean(false);
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
    public ReadOnlyBooleanProperty busyProperty() { return busy.getReadOnlyProperty(); }

    // Program loaded
    private final ReadOnlyBooleanWrapper programLoaded = new ReadOnlyBooleanWrapper(false);
    public ReadOnlyBooleanProperty programLoadedProperty() { return programLoaded.getReadOnlyProperty(); }

    // JavaFX bindables
    private final IntegerProperty pc = new SimpleIntegerProperty(0);
    private final LongProperty cycles = new SimpleLongProperty(0);
    private final StringProperty lastInstruction = new SimpleStringProperty("");

    public final IntegerProperty regA  = new SimpleIntegerProperty();
    public final IntegerProperty regB  = new SimpleIntegerProperty();
    public final IntegerProperty regD  = new SimpleIntegerProperty();
    public final IntegerProperty regX  = new SimpleIntegerProperty();
    public final IntegerProperty regY  = new SimpleIntegerProperty();
    public final IntegerProperty regS  = new SimpleIntegerProperty();
    public final IntegerProperty regU  = new SimpleIntegerProperty();
    public final IntegerProperty regDP = new SimpleIntegerProperty();
    public final IntegerProperty regCC = new SimpleIntegerProperty();

    public final BooleanProperty fE = new SimpleBooleanProperty();
    public final BooleanProperty fF = new SimpleBooleanProperty();
    public final BooleanProperty fH = new SimpleBooleanProperty();
    public final BooleanProperty fI = new SimpleBooleanProperty();
    public final BooleanProperty fN = new SimpleBooleanProperty();
    public final BooleanProperty fZ = new SimpleBooleanProperty();
    public final BooleanProperty fV = new SimpleBooleanProperty();
    public final BooleanProperty fC = new SimpleBooleanProperty();

    public ReadOnlyIntegerProperty pcProperty() { return pc; }
    public ReadOnlyLongProperty cyclesProperty() { return cycles; }
    public ReadOnlyStringProperty lastInstructionProperty() { return lastInstruction; }

    // PC mapping for highlight + breakpoints (line->pc and pc->line)
    private volatile Map<Integer, Integer> pcToLine = Map.of();
    private volatile Map<Integer, Integer> lineToPc = Map.of();

    // Editor breakpoints (lines)
    private final ObservableSet<Integer> breakpointLines = FXCollections.observableSet();
    public ObservableSet<Integer> breakpointLines() { return breakpointLines; }
    private volatile int sourceLineCount = 0;

    // Installed breakpoints (addrs)
    private final Set<Integer> installedBreakpointAddrs = new HashSet<>();

    // Loaded ROM coverage (stop rule)
    private volatile boolean[] loadedRomMask = null; // size 65536
    private volatile int loadedRomStart = 0xE000;
    private volatile int loadedRomEnd = 0xFFFF;

    // ---------------------------------------
    public CentralController() {
        this.boot = new Bootstrap(new UiLogger());
        hookCpuListener();
        // start stopped
        boot.debug().stop();
        pendingSnapshot.set(boot.cpu().snapshot());
    }

    // -------------------------
    private final class UiLogger implements ILogger {
        @Override public void log(String message, LogLevel level) {
            String line = "[" + level + "] " + message;
            Platform.runLater(() -> console.add(line));
        }
        @Override public void log(Response response, LogLevel level) { log(String.valueOf(response), level); }
        @Override public void clear() { Platform.runLater(console::clear); }
        @Override public void setLogFilePath(Path logFilePath) {}
    }

    private void hookCpuListener() {
        boot.cpu().setListener(snap -> pendingSnapshot.set(snap));
    }

    /** Call from AnimationTimer */
    public void pumpUi() {
        CpuStateSnapshot snap = pendingSnapshot.getAndSet(null);
        if (snap != null) refreshFromSnapshot(snap);
    }

    private void refreshFromSnapshot(CpuStateSnapshot s) {
        pc.set(s.PC & 0xFFFF);
        cycles.set(s.totalCycles);

        regA.set(s.A & 0xFF);
        regB.set(s.B & 0xFF);
        regD.set(s.D & 0xFFFF);
        regX.set(s.X & 0xFFFF);
        regY.set(s.Y & 0xFFFF);
        regS.set(s.S & 0xFFFF);
        regU.set(s.U & 0xFFFF);
        regDP.set(s.DP & 0xFF);
        regCC.set(s.CC & 0xFF);

        fE.set(s.E); fF.set(s.F); fH.set(s.H); fI.set(s.I);
        fN.set(s.N); fZ.set(s.Z); fV.set(s.V); fC.set(s.C);

        lastInstruction.set(s.lastInstruction != null ? s.lastInstruction.toString() : "");
    }


    // Helpers (busy + logging)
    private void setBusyFx(boolean v) {
        if (Platform.isFxApplicationThread()) busy.set(v);
        else Platform.runLater(() -> busy.set(v));
    }

    private void logFx(String msg) {
        Platform.runLater(() -> console.add(msg));
    }

    private void submitCpuTask(Runnable r) {
        if (!busyFlag.compareAndSet(false, true)) return;
        setBusyFx(true);
        cpuExec.submit(() -> {
            try { r.run(); }
            finally {
                busyFlag.set(false);
                setBusyFx(false);
            }
        });
    }

    private boolean requireProgramLoaded() {
        if (!programLoaded.get()) {
            logFx("[WARN] Click 'Assemble / Load' first.");
            return false;
        }
        return true;
    }

    // Public API
    public void assembleAndLoad(String asmText, Integer defaultOrigin) {
        Objects.requireNonNull(asmText);

        submitCpuTask(() -> {
            try {
                boot.debug().stop();
                boot.clearBreakpoints();
                installedBreakpointAddrs.clear();

                setProgramLoadedFx(false);//programLoaded.set(false);

                loadedRomMask = null;

                List<String> lines = Arrays.asList(asmText.split("\\R", -1));
                sourceLineCount = lines.size();
                int origin = (defaultOrigin != null) ? (defaultOrigin & 0xFFFF) : (boot.memory().getROMstart() & 0xFFFF);

                int entry = boot.loadAsmToRom(lines, origin, true);

                // Build mappings + loaded ROM mask (for safe stop-at-end)
                List<AssemblerProgram.ListingRow> listing = boot.assembleListing(lines, origin);
                buildMappingsFromListing(listing);
                buildLoadedRomMaskFromListing(listing);
                //program table
                Platform.runLater(() ->
                {
                    programRows.clear();
                    for (AssemblerProgram.ListingRow row : listing) {
                        String bytesHex = "";
                        if (row.bytes != null && !row.bytes.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (int b : row.bytes) {
                                if (sb.length() > 0) sb.append(' ');
                                sb.append(String.format("%02X", b & 0xFF));
                            }
                            bytesHex = sb.toString();
                        }
                        programRows.add(new ProgramRow(
                                row.lineIndex,
                                row.pcBefore,
                                bytesHex,
                                row.source
                        ));
                    }
                });


                // Re-apply editor breakpoints (now mapping exists)
                applyEditorBreakpointsToCpu(snapshotBreakpointLinesFx());

                // Reset CPU (PC from RESET vector)
                boot.resetCpu();
                pendingSnapshot.set(boot.cpu().snapshot());

                //programLoaded.set(true);
                setProgramLoadedFx(true);
                logFx(String.format("[INFO] Program loaded. Entry=$%04X  (Reset PC=$%04X)",
                        entry & 0xFFFF,
                        boot.cpu().snapshot().PC & 0xFFFF));

            } catch (Exception ex) {
                setProgramLoadedFx(false);//programLoaded.set(false);
                logFx("[ERROR] Assemble/Load failed: " + ex.getMessage());
            }
        });
    }

    public void resetCpu() {
        submitCpuTask(() -> {
            boot.debug().stop();
            boot.resetCpu();
            pendingSnapshot.set(boot.cpu().snapshot());
        });
    }

    public void clearRam() {
        submitCpuTask(() -> {
            boot.clearRam();
            pendingSnapshot.set(boot.cpu().snapshot());
        });
    }
    // clear rom
    public void clearRom() {
        submitCpuTask(() -> {
            boot.debug().stop();
            boot.clearRom(true); // keep vectors
            setProgramLoadedFx(false);//programLoaded.set(false);
            loadedRomMask = null;
            pcToLine = Map.of();
            lineToPc = Map.of();
            installedBreakpointAddrs.clear();
            Platform.runLater(breakpointLines::clear);//breakpointLines.clear();

            pendingSnapshot.set(boot.cpu().snapshot());
            logFx("[INFO] ROM cleared.");
        });
    }


    public void step() {
        if (!requireProgramLoaded()) return;

        submitCpuTask(() -> {
            int pcNow = boot.cpu().snapshot().PC & 0xFFFF;

            // If we are ON a breakpoint, do NOT execute. Just pause.
            if (installedBreakpointAddrs.contains(pcNow)) {
                boot.debug().pause();
                pendingSnapshot.set(boot.cpu().snapshot());
                logFx(String.format("[INFO] Breakpoint at PC=$%04X", pcNow));
                return;
            }

            boot.cpu().stepOnce();

            // optional safety stop (your program-end logic)
            stopIfPcOutsideLoadedRom();

            pendingSnapshot.set(boot.cpu().snapshot());
        });
    }

    public void run(int maxInstructions) {
        if (!requireProgramLoaded()) return;

        submitCpuTask(() -> {
            boot.debug().run();
            boot.cpu().run(maxInstructions);

            // Stop rule: if we fell outside loaded ROM bytes, pause
            stopIfPcOutsideLoadedRom();

            pendingSnapshot.set(boot.cpu().snapshot());
        });
    }

    public void pause() {
        submitCpuTask(() -> boot.debug().pause());
    }

    // Manual edits from UI
    public void setRegister(Register r, int value) {
        submitCpuTask(() -> {
            boot.registers().setRegister(r, value);
            pendingSnapshot.set(boot.cpu().snapshot());
            logFx("[INFO] Register " + r + " set to " + String.format("$%04X", value & 0xFFFF));
        });
    }


    // Memory poke (for editable RAM table) - synchronous
    public void pokeByte(int address, int value) {
        int a = address & 0xFFFF;
        int v = value & 0xFF;

        // direct write; MemoryBus blocks ROM anyway
        boot.bus().write(a, v);

        // optional log (UI thread safe)
        logFx(String.format("[INFO] RAM[$%04X] = $%02X", a, v));
    }

    // Memory peek (for tables) - synchronous
    public int peekByte(int address) {
        return boot.bus().read(address & 0xFFFF) & 0xFF;
    }

    public int romStart() { return boot.memory().getROMstart() & 0xFFFF; }
    public int romEnd()   { return boot.memory().getROMend() & 0xFFFF; }
    public int ramStart() { return boot.memory().getRAMstart() & 0xFFFF; }
    public int ramEnd()   { return boot.memory().getRAMend() & 0xFFFF; }


    // Breakpoints (editor lines)
    public void toggleBreakpointLine(int lineIndex) {
        if (breakpointLines.contains(lineIndex)) breakpointLines.remove(lineIndex);
        else breakpointLines.add(lineIndex);

        // Copy on FX thread, then install on CPU thread
        Set<Integer> copy = new HashSet<>(breakpointLines);
        submitCpuTask(() -> applyEditorBreakpointsToCpu(copy));
    }

    private Set<Integer> snapshotBreakpointLinesFx() {
        if (Platform.isFxApplicationThread()) {
            return new HashSet<>(breakpointLines);
        }

        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Set<Integer>> ref = new java.util.concurrent.atomic.AtomicReference<>();

        Platform.runLater(() -> {
            ref.set(new HashSet<>(breakpointLines));
            latch.countDown();
        });

        try {
            latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}

        Set<Integer> out = ref.get();
        return (out != null) ? out : Set.of();
    }



    private void applyEditorBreakpointsToCpu(Set<Integer> lines) {
        // Remove old installed breakpoints
        for (int addr : installedBreakpointAddrs) boot.removeBreakpoint(addr);
        installedBreakpointAddrs.clear();

        // Install new ones
        for (int line : lines) {
            Integer addr = resolveBreakpointAddressForLine(line);
            if (addr != null) {
                int a = addr & 0xFFFF;
                boot.addBreakpoint(a);
                installedBreakpointAddrs.add(a);
            }
        }

        logFx("[DEBUG] Breakpoints installed at: " + installedBreakpointAddrs);
    }


    private Integer resolveBreakpointAddressForLine(int lineIndex) {
        if (lineIndex < 0) return null;

        Integer a = lineToPc.get(lineIndex);
        if (a != null) return a & 0xFFFF;

        // If the clicked line emits no bytes (label/ORG/empty),
        // search forward to next executable line.
        for (int l = lineIndex + 1; l < sourceLineCount; l++) {
            a = lineToPc.get(l);
            if (a != null) return a & 0xFFFF;
        }
        return null;
    }



    public Integer lineForPc(int pc) {
        return pcToLine.get(pc & 0xFFFF);
    }

    private void buildMappingsFromListing(List<AssemblerProgram.ListingRow> listing) {
        Map<Integer, Integer> p2l = new HashMap<>();
        Map<Integer, Integer> l2p = new HashMap<>();

        for (AssemblerProgram.ListingRow row : listing) {
            if (row.bytes != null && !row.bytes.isEmpty()) {
                int pcBefore = row.pcBefore & 0xFFFF;
                int li = row.lineIndex;
                p2l.putIfAbsent(pcBefore, li);
                l2p.put(li, pcBefore);
            }
        }
        pcToLine = Map.copyOf(p2l);
        lineToPc = Map.copyOf(l2p);
    }

    private void buildLoadedRomMaskFromListing(List<AssemblerProgram.ListingRow> listing) {
        boolean[] mask = new boolean[0x10000];

        loadedRomStart = romStart();
        loadedRomEnd = romEnd();

        for (AssemblerProgram.ListingRow row : listing) {
            if (row.bytes == null || row.bytes.isEmpty()) continue;
            int a = row.pcBefore & 0xFFFF;
            for (int i = 0; i < row.bytes.size(); i++) {
                int addr = (a + i) & 0xFFFF;
                mask[addr] = true;
            }
        }
        // also allow vectors region (so reset vector bytes don't immediately count as “outside”)
        mask[0xFFFE] = true;
        mask[0xFFFF] = true;

        loadedRomMask = mask;
    }

    private void stopIfPcOutsideLoadedRom() {
        boolean[] mask = loadedRomMask;
        if (mask == null) return;

        int pcNow = boot.cpu().snapshot().PC & 0xFFFF;

        // Only enforce rule inside ROM region
        if (pcNow >= loadedRomStart && pcNow <= loadedRomEnd) {
            if (!mask[pcNow]) {
                boot.debug().pause();
                boot.cpu().halt(); //  THIS makes Step stop forever
                logFx(String.format("[WARN] Program finished (PC left loaded ROM) at PC=$%04X -> HALTED", pcNow));
            }
        }
    }
    
    
    // helper bleu program bytes on ROM

    public boolean isRomProgramByte(int addr) {
        addr &= 0xFFFF;
        boolean b = (loadedRomMask != null) && loadedRomMask[addr];
        return b; // si BitSet
        // OU si boolean[] :
        // return loadedRomMask != null && loadedRomMask[addr];
    }
    public void shutdown() {
        cpuExec.shutdownNow();
    }

    private void setProgramLoadedFx(boolean v) {
        if (Platform.isFxApplicationThread()) programLoaded.set(v);
        else Platform.runLater(() -> programLoaded.set(v));
    }


}