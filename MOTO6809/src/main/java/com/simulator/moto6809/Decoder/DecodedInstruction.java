package com.simulator.moto6809.Decoder;

/**
 * Represents one decoded Motorola 6809 instruction instance.
 * Created by Decoder, consumed by InstructionExecutor / CPU.
 */
public final class DecodedInstruction {
    // Core identity

    /** Program Counter at instruction start */
    /**
    This PC is:
    the PC value at the moment the instruction
    “Where this instruction came from”
     */
    private final int pc;// to verify later

    /** Full opcode (0x00–0xFF, or 0x10xx / 0x11xx for page 2/3) */
    private final int opcode;

    /** Instruction mnemonic (e.g. "LDA", "ADDA") */
    private final String mnemonic;

    /** Addressing mode used by this instruction */
    private final AddressingMode addressingMode;

    // Timing & size

    /** Base CPU cycles (without indexed penalties) */
    private final int cycles;

    /** Total instruction size in bytes */
    private final int size;

    // Operand & addressing

    /** Raw operand value (unsigned, big-endian) */
    private final int operand;

    /** Effective address (used for DIRECT / INDEXED / EXTENDED) */
    private final Integer effectiveAddress;

    // Debug / UI support

    /** Raw instruction bytes (optional, may be null) */
    private final byte[] bytes;

    // Constructor (private, use Builder)

    private DecodedInstruction(Builder b) {
        this.pc = b.pc & 0xFFFF;
        this.opcode = b.opcode & 0xFFFF;
        this.mnemonic = b.mnemonic;
        this.addressingMode = b.addressingMode;
        this.cycles = b.cycles;
        this.size = b.size;
        this.operand = b.operand & 0xFFFF;
        this.effectiveAddress =
                b.effectiveAddress == null ? null : (b.effectiveAddress & 0xFFFF);
        this.bytes = b.bytes;
    }


    // Getters


    public int pc() { return pc; }
    public int opcode() { return opcode; }
    public String mnemonic() { return mnemonic; }
    public AddressingMode addressingMode() { return addressingMode; }
    public int cycles() { return cycles; }
    public int size() { return size; }
    public int operand() { return operand; }
    public Integer effectiveAddress() { return effectiveAddress; }
    public byte[] bytes() { return bytes; }

    // Opcode helpers


    /** True if opcode uses page-2 or page-3 prefix */
    public boolean isPrefixedOpcode() {
        return (opcode & 0xFF00) == 0x1000 || (opcode & 0xFF00) == 0x1100;
    }

    /** Number of opcode bytes (1 or 2) */
    public int opcodeByteCount() {
        return isPrefixedOpcode() ? 2 : 1;// if else
    }

    /** Number of operand bytes */
    public int operandByteCount() {
        return Math.max(0, size - opcodeByteCount());
    }


    // Branch helpers (Relative addressing)

    /** Signed relative offset (8-bit or 16-bit) */
    public int signedRelativeOffset() {
        int len = operandByteCount();
        if (len == 1)
            return (byte) (operand & 0xFF);
        if (len == 2)
            return (short) (operand & 0xFFFF);
        return 0;
    }

    /** Target address for branch instructions */
    public int relativeTargetAddress() {
        int base = (pc + size) & 0xFFFF;
        return (base + signedRelativeOffset()) & 0xFFFF;
    }

    /** PC after instruction */
    public int nextPc() {
        return (pc + size) & 0xFFFF;
    }


    // Debug output
    @Override
    public String toString() {
        return String.format(
                "$%04X  %-6s %-9s opcode=$%04X size=%d cyc=%d operand=$%04X ea=%s bytes=%s",
                pc,
                mnemonic,
                addressingMode,
                opcode,
                size,
                cycles,
                operand,
                effectiveAddress == null ? "null" : String.format("$%04X", effectiveAddress),
                bytesToHex(bytes)
        );
    }
    // Builder

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private int pc;
        private int opcode;
        private String mnemonic;
        private AddressingMode addressingMode;
        private int cycles;
        private int size;
        private int operand;
        private Integer effectiveAddress;
        private byte[] bytes;

        public Builder pc(int pc)
        {
            this.pc = pc;
            return this;
        }

        public Builder opcode(int opcode)
        {
            this.opcode = opcode;
            return this;
        }

        public Builder mnemonic(String mnemonic)
        {
            this.mnemonic = mnemonic;
            return this;
        }

        public Builder addressingMode(AddressingMode addressingMode)
        {
            this.addressingMode = addressingMode;
            return this;
        }

        public Builder cycles(int cycles)
        {
            this.cycles = cycles;
            return this;
        }

        public Builder size(int size)
        {
            this.size = size;
            return this;
        }

        public Builder operand(int operand)
        {
            this.operand = operand;
            return this;
        }

        public Builder effectiveAddress(Integer effectiveAddress)
        {
            this.effectiveAddress = effectiveAddress;
            return this;
        }

        public Builder bytes(byte[] bytes)
        {
            this.bytes = bytes;
            return this;
        }

        public DecodedInstruction build()
        {
            if (bytes == null || bytes.length != size)
                throw new IllegalStateException("DecodedInstruction.bytes must be non-null and match size");
            if (mnemonic == null || mnemonic.isBlank())
                throw new IllegalStateException("Mnemonic is required");
            if (addressingMode == null)
                throw new IllegalStateException("AddressingMode is required");
            if (size <= 0)
                throw new IllegalStateException("Instruction size must be > 0");
            return new DecodedInstruction(this);
        }
    }


    private static String bytesToHex(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }


}
