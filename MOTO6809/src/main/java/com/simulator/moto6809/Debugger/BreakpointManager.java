package com.simulator.moto6809.Debugger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class BreakpointManager {

    private final Map<Integer, Breakpoint> breakpoints = new HashMap<>();

    public void add(int address) {
        breakpoints.put(address & 0xFFFF, new Breakpoint(address));
    }

    public void remove(int address) {
        breakpoints.remove(address & 0xFFFF);
    }

    public void clear() {
        breakpoints.clear();
    }

    public boolean hasEnabledAt(int address) {
        Breakpoint bp = breakpoints.get(address & 0xFFFF);
        return bp != null && bp.isEnabled();
    }

    public Collection<Breakpoint> all() {
        return breakpoints.values();
    }
}

