package com.simulator.moto6809.Debugger;

public class DebugController {

    public enum Mode {
        STOPPED,
        RUNNING,
        PAUSED
    }

    private Mode mode = Mode.STOPPED;

    private boolean stepRequested = false;

    private final BreakpointManager breakpoints;

    public DebugController(BreakpointManager breakpoints) {
        this.breakpoints = breakpoints;
    }

    public Mode mode() {
        return mode;
    }

    public void run() {
        stepRequested = false;
        mode = Mode.RUNNING;
    }

    public void pause() {
        stepRequested = false;
        mode = Mode.PAUSED;
    }

    public void stop() {
        stepRequested = false;
        mode = Mode.STOPPED;
    }

    /** Request exactly one instruction step (CPU will pause after it). */
    public void requestStep() {
        stepRequested = true;
        mode = Mode.RUNNING;
    }

    public boolean stepRequested() {
        return stepRequested;
    }

    public void clearStepRequest() {
        stepRequested = false;
    }

    public boolean shouldBreakAt(int pc) {
        return breakpoints != null && breakpoints.hasEnabledAt(pc);
    }

    public BreakpointManager breakpoints() {
        return breakpoints;
    }
}

