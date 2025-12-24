package com.simulator.moto6809.Assembler;

/**
 * Motorola 6809 indexed addressing postbyte encoder.
 *
 * This class only encodes the indexed *postbyte + extra bytes*.
 */
public final class IndexedEncoder {

    private IndexedEncoder() {}

    /**
     * Encode ",R" or "[,R]"
     * baseCode=0x04 for ",R"
     */
    public static IndexedOperand encodeZeroOffset(String indexReg, boolean indirect) {
        int post = makePostbyte(indexReg, 0x04);
        if (indirect) post |= 0x10;
        return new IndexedOperand(post, null);
    }

    /**
     * Encode auto inc/dec:
     *  delta = +1 => ,R+
     *  delta = +2 => ,R++
     *  delta = -1 => ,-R
     *  delta = -2 => ,--R
     */
    public static IndexedOperand encodeAuto(String indexReg, int delta, boolean indirect) {

        int baseCode = switch (delta) {
            case +1 -> 0x00; // ,R+
            case +2 -> 0x01; // ,R++
            case -1 -> 0x02; // ,-R
            case -2 -> 0x03; // ,--R
            default -> throw new IllegalArgumentException("Invalid auto delta: " + delta);
        };

        int post = makePostbyte(indexReg, baseCode);
        if (indirect) post |= 0x10;

        return new IndexedOperand(post, null);
    }

    /**
     * Encode accumulator offset:
     *  A,R => baseCode=0x06
     *  B,R => baseCode=0x05
     *  D,R => baseCode=0x0B
     */
    public static IndexedOperand encodeRegisterOffset(String offsetReg, String indexReg, boolean indirect) {

        int baseCode = switch (offsetReg.toUpperCase()) {
            case "A" -> 0x06;
            case "B" -> 0x05;
            case "D" -> 0x0B;
            default -> throw new IllegalArgumentException("Invalid offset register: " + offsetReg);
        };

        int post = makePostbyte(indexReg, baseCode);
        if (indirect) post |= 0x10;

        return new IndexedOperand(post, null);
    }

    /**
     * Encode constant offset n,R.
     *
     * Non-indirect:
     *  -16..+15  => 5-bit form (bit7=0, no extra bytes)
     *  else if fits 8-bit  => baseCode=0x08 + 1 extra byte
     *  else => baseCode=0x09 + 2 extra bytes
     *
     * Indirect:
     *  5-bit form is NOT allowed on 6809, so:
     *   if fits 8-bit  => 0x08 + indirect bit + extra8
     *   else => 0x09 + indirect bit + extra16
     */
    public static IndexedOperand encodeConstantOffset(int n, String indexReg, boolean indirect) {

        if (!indirect) {
            // 5-bit signed offset: -16..+15
            if (n >= -16 && n <= 15) {
                int regBits = regBits(indexReg);
                int off5 = n & 0x1F; // two's complement in 5 bits
                int post = ((regBits & 0b11) << 5) | off5; // bit7=0
                return new IndexedOperand(post, null);
            }

            // 8-bit signed offset
            if (n >= -128 && n <= 127) {
                int post = makePostbyte(indexReg, 0x08);
                return new IndexedOperand(post, n & 0xFF);
            }

            // 16-bit signed offset
            if (n >= -32768 && n <= 32767) {
                int post = makePostbyte(indexReg, 0x09);
                return new IndexedOperand(post, n & 0xFFFF);
            }

            throw new IllegalArgumentException("Offset out of range (n,R): " + n);
        }

        // indirect forms (force bit7=1 + indirect bit)
        if (n >= -128 && n <= 127) {
            int post = makePostbyte(indexReg, 0x08) | 0x10; // [n,R]
            return new IndexedOperand(post, n & 0xFF);
        }

        if (n >= -32768 && n <= 32767) {
            int post = makePostbyte(indexReg, 0x09) | 0x10; // [nn,R]
            return new IndexedOperand(post, n & 0xFFFF);
        }

        throw new IllegalArgumentException("Offset out of range ([n,R]): " + n);
    }

    /**
     * Encode n,PC or [n,PC]
     *  8-bit: postbyte 0x8C (or 0x9C if indirect)
     * 16-bit: postbyte 0x8D (or 0x9D if indirect)
     */
    public static IndexedOperand encodePcRelative(int n, boolean indirect) {

        if (n >= -128 && n <= 127) {
            int post = 0x8C; // n,PC
            if (indirect) post |= 0x10; // 0x9C
            return new IndexedOperand(post, n & 0xFF);
        }

        if (n >= -32768 && n <= 32767) {
            int post = 0x8D; // nn,PC
            if (indirect) post |= 0x10; // 0x9D
            return new IndexedOperand(post, n & 0xFFFF);
        }

        throw new IllegalArgumentException("PC-relative offset out of range: " + n);
    }

    /**
     * Encode [$nnnn] extended indirect:
     * always postbyte 0x9F + 16-bit address
     */
    public static IndexedOperand encodeExtendedIndirect(int address) {
        return new IndexedOperand(0x9F, address & 0xFFFF);
    }


    // Internals


    /**
     * Build postbyte for bit7=1 forms:
     * post = 0x80 | (regBits<<5) | baseCode
     */
    private static int makePostbyte(String indexReg, int baseCode) {
        int regBits = regBits(indexReg);
        return (0x80 | ((regBits & 0b11) << 5) | (baseCode & 0x1F)) & 0xFF;
    }

    /**
     * X=00, Y=01, U=10, S=11
     */
    private static int regBits(String r) {
        if (r == null) throw new IllegalArgumentException("Index register is null");
        return switch (r.trim().toUpperCase()) {
            case "X" -> 0b00;
            case "Y" -> 0b01;
            case "U" -> 0b10;
            case "S" -> 0b11;
            default -> throw new IllegalArgumentException("Invalid index register: " + r);
        };
    }

    /** Force 16-bit PC-relative placeholder (for forward labels) */
    public static IndexedOperand encodePcRelative16(boolean indirect) {
            int post = 0x8D; // nn,PC
            if (indirect) post |= 0x10; // 0x9D
            return new IndexedOperand(post, 0x0000); // 2 bytes placeholder
    }

}