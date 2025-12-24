package com.simulator.moto6809.Resource;

public class InstructionCsvRow {

    private final String mnemonic;
    private final String imm;   // Immediate
    private final String drt;   // Direct
    private final String idx;   // Indexed
    private final String etd;   // Extended
    private final String inh;   // Inherent
    private final String rlv;   // Relative / Long Relative

    public InstructionCsvRow(
            String mnemonic,
            String imm,
            String drt,
            String idx,
            String etd,
            String inh,
            String rlv
    ) {
        this.mnemonic = mnemonic;
        this.imm = imm;
        this.drt = drt;
        this.idx = idx;
        this.etd = etd;
        this.inh = inh;
        this.rlv = rlv;
    }

    public String mnemonic() { return mnemonic; }

    public String imm() { return imm; }
    public String drt() { return drt; }
    public String idx() { return idx; }
    public String etd() { return etd; }
    public String inh() { return inh; }
    public String rlv() { return rlv; }


    // IMPORTANT HELPERS (FIX)


    public boolean hasImmediate() { return !imm.isEmpty(); }
    public boolean hasDirect()    { return !drt.isEmpty(); }
    public boolean hasIndexed()   { return !idx.isEmpty(); }
    public boolean hasExtended()  { return !etd.isEmpty(); }
    public boolean hasInherent()  { return !inh.isEmpty(); }
    public boolean hasRelative()  { return !rlv.isEmpty(); }

    @Override
    public String toString() {
        return "InstructionCsvRow{" +
                "mnemonic='" + mnemonic + '\'' +
                ", imm='" + imm + '\'' +
                ", drt='" + drt + '\'' +
                ", idx='" + idx + '\'' +
                ", etd='" + etd + '\'' +
                ", inh='" + inh + '\'' +
                ", rlv='" + rlv + '\'' +
                '}';
    }
}