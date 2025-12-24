package com.simulator.moto6809.Assembler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Case-insensitive symbol table for labels/constants.
 * Values are always stored as 16-bit (0..65535).
 */
public class SymbolTable {

    private final Map<String, Integer> symbols = new HashMap<>();

    public void define(String label, int value) {
        String k = normalize(label);
        symbols.put(k, value & 0xFFFF);
    }

    /** Define only if absent (useful for EQU rules in pass1). */
    public void defineIfAbsent(String label, int value) {
        String k = normalize(label);
        symbols.putIfAbsent(k, value & 0xFFFF);
    }

    public boolean contains(String label) {
        if (label == null) return false;
        String k = label.trim().toUpperCase();
        return !k.isEmpty() && symbols.containsKey(k);
    }

    public int resolve(String label) {
        String k = normalize(label);
        Integer value = symbols.get(k);
        if (value == null) {
            throw new IllegalStateException("Undefined label: " + label);
        }
        return value & 0xFFFF;
    }

    /** Optional: useful for debugging / UI. */
    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(symbols));
    }

    private String normalize(String label) {
        if (label == null) throw new IllegalArgumentException("label is null");
        String k = label.trim().toUpperCase();
        if (k.isEmpty()) throw new IllegalArgumentException("label is empty");
        return k;
    }

    public void clear()
    {
        symbols.clear();
    }
}