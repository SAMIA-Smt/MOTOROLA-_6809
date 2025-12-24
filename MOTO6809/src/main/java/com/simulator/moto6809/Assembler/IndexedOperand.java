package com.simulator.moto6809.Assembler;

/**
 * Result of indexed-addressing encoding:
 *  - postbyte: always present (0..255)
 *  - extra: null (no extra), or 8-bit value (0..255), or 16-bit value (0..65535)
 */
public final class IndexedOperand {

    public final int postbyte;
    public final Integer extra;

    public IndexedOperand(int postbyte, Integer extra) {
        this.postbyte = postbyte & 0xFF;
        this.extra = (extra == null) ? null : (extra & 0xFFFF);
    }
}
