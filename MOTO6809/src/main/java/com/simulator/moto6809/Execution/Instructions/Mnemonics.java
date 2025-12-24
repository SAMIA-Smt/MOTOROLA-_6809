package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Registers.Register;

import java.util.Set;

public class Mnemonics {
    /** */
    public static final String  ADDA= "ADDA",  ADDB= "ADDB",  ADDD= "ADDD";
    public static final String  ADCA= "ADCA",  ADCB= "ADCB";
    public static final String  SUBA= "SUBA",  SUBB= "SUBB",  SUBD= "SUBD";
    public static final String  SBCA= "SBCA",  SBCB= "SBCB";
    public static final String  MUL= "MUL",  DAA= "DAA";

    /** Branch INST */
    public static final String  BRA= "BRA",  BRN= "BRN",  BHI= "BHI",  BLS= "BLS";
    public static final String  BCC= "BCC",  BCS= "BCS",  BNE= "BNE";
    public static final String  BEQ= "BEQ",  BVC= "BVC",  BVS  = "BVS";
    public static final String  BPL= "BPL",  BMI= "BMI",  BGE= "BGE";
    public static final String  BLT= "BLT",  BGT= "BGT",  BLE= "BLE";
    public static final String  LBRA= "LBRA",  LBRN= "LBRN",  LBHI= "LBHI";
    public static final String  LBLS= "LBLS",  LBCC= "LBCC",  LBCS= "LBCS";
    public static final String  LBNE= "LBNE",  LBEQ= "LBEQ",  LBVC= "LBVC";
    public static final String  LBVS= "LBVS",  LBPL= "LBPL",   LBMI= "LBMI",  LBGE= "LBGE";
    public static final String  LBLT= "LBLT",  LBGT= "LBGT",   LBLE= "LBLE";

    /** Compare INST */
    public static final String  CMPA= "CMPA",  CMPB= "CMPB",   CMPD= "CMPD",  CMPX= "CMPX";
    public static final String  CMPY= "CMPY",  CMPU= "CMPU",   CMPS= "CMPS";

    /** Controlle INST */
    public static final String  NOP= "NOP",  SYNC= "SYNC",   CWAI= "CWAI",  SEX= "SEX";
    public static final String  SWI= "SWI",  SWI2= "SWI2",   SWI3= "SWI3";

    /** Jump INST JMP */
    public static final String  JMP= "JMP",  JSR= "JSR",   BSR= "BSR",  LBSR= "LBSR";
    public static final String  RTS= "RTS",  RTI= "RTI";

    /** Load Store INST */
    public static final String  LDA= "LDA",  LDB= "LDB",   LDD= "LDD",  LDX= "LDX";
    public static final String  LDY= "LDY",  LDU= "LDU",   LDS= "LDS";

    public static final String  STA= "STA",  STB= "STB",   STD= "STD",  STX= "STX";
    public static final String  STY= "STY",  STU= "STU",   STS= "STS";
    /** Logic INST */

    public static final String  ANDA= "ANDA",  ANDB= "ANDB",   ORA= "ORA",  ORB= "ORB";
    public static final String  EORA= "EORA",  EORB= "EORB",   BITA= "BITA",  BITB= "BITB";
    public static final String  ANDCC= "ANDCC",  ORCC= "ORCC";

    /** Registre Transfer INST */
    public static final String  TFR= "TFR",  EXG= "EXG",   ABX= "ABX";

    /** Shift Rotate INST */
    public static final String  ASL= "ASL",  ASLA= "ASLA",   ASLB= "ASLB";
    public static final String  LSL= "LSL",  LSLA= "LSLA",   LSLB= "LSLB";
    public static final String  ASR= "ASR",  ASRA= "ASRA",   ASRB= "ASRB";
    public static final String  LSR= "LSR",  LSRA= "LSRA",   LSRB= "LSRB";
    public static final String  ROLA= "ROLA",  ROLB= "ROLB",   ROL= "ROL";
    public static final String  ROR= "ROR",  RORA= "RORA",   RORB= "RORB";


    /**  Stack INST */
    public static final String  PSHS= "PSHS",  PSHU= "PSHU";
    public static final String  PULS= "PULS",  PULU= "PULU";

    /** Unary INST */
    public static final String  INC= "INC",  INCA= "INCA",   INCB= "INCB";
    public static final String  DEC= "DEC",  DECA= "DECA",   DECB= "DECB";
    public static final String  NEG= "NEG",  NEGA= "NEGA",   NEGB= "NEGB";
    public static final String  CLR= "CLR",  CLRA= "CLRA",   CLRB= "CLRB";
    public static final String  COM= "COM",  COMA= "COMA",   COMB= "COMB";
    public static final String  TST= "TST",  TSTA= "TSTA",   TSTB= "TSTB";

    private static final Set<String> mnemonics = Set.of(
            ADDA, ADDB, ADDD,
            ADCA, ADCB,
            SUBA, SUBB, SUBD,
            SBCA, SBCB,
            MUL, DAA,

            BRA, BRN,BHI, BLS,BCC, BCS,BNE, BEQ,BVC, BVS,BPL, BMI,BGE, BLT,BGT, BLE,

            LBRA, LBRN,LBHI, LBLS,LBCC, LBCS,LBNE, LBEQ,LBVC, LBVS,LBPL, LBMI,LBGE, LBLT,LBGT, LBLE,

            CMPA, CMPB, CMPD,CMPX, CMPY,CMPU, CMPS,

            NOP,SYNC,CWAI,SEX,SWI,SWI2,SWI3,
            JMP,JSR,BSR,LBSR,RTS,RTI,

            LDA, LDB, LDD,LDX, LDY, LDU, LDS,
            STA, STB, STD,STX, STY, STU, STS,

            ANDA,ANDB,ORA, ORB,EORA, EORB,BITA, BITB,ANDCC,ORCC,

            TFR,EXG,ABX,

            ASL, ASLA, ASLB,
            LSL, LSLA, LSLB,
            ASR, ASRA, ASRB,
            LSR, LSRA, LSRB,
            ROL, ROLA, ROLB,
            ROR, RORA, RORB,

            PSHS, PULS,PSHU, PULU,

            INC, INCA, INCB,
            DEC, DECA, DECB,
            NEG, NEGA, NEGB,
            CLR, CLRA, CLRB,
            COM, COMA, COMB,
            TST, TSTA, TSTB
    );

    private static final Set<String> AccaMnemonics = Set.of(
            ADDA,ADCA,SUBA,SBCA, CMPA, LDA,STA, ANDA,ORA,EORA,BITA,
            ASLA,LSLA,ASRA,LSRA,ROLA,RORA,
            INCA,DECA,NEGA,CLRA,COMA,TSTA
    );

    private static final Set<String> AccbMnemonics = Set.of(
            ADDB,ADCB,SUBB,SBCB, CMPB, LDB,STB, ANDB,ORB,EORB,BITB,
            ASLB,LSLB,ASRB,LSRB,ROLB,RORB,
            INCB,DECB,NEGB,CLRB,COMB,TSTB
    );
    // get list
    public static Set<String> mnemonics()
    {
        return mnemonics;
    }

    public static Set<String> AccbMnemonics()
    {
        return AccbMnemonics;
    }

    public static Set<String> AccaMnemonics()
    {
        return AccaMnemonics;
    }


    public static Register getMnemonicRegister(String mnemonic)
    {
        mnemonic = mnemonic.toUpperCase();
        if (AccaMnemonics.contains(mnemonic))
            return Register.A;
        if (AccbMnemonics.contains(mnemonic))
            return Register.B;

        return switch (mnemonic)
        {
            case LDD, STD, ADDD, SUBD, CMPD  -> Register.D;
            case LDX, STX, CMPX -> Register.X;
            case LDY, STY, CMPY -> Register.Y;
            case LDS, STS, CMPS, PSHS, PULS -> Register.S;
            case LDU, STU, CMPU, PSHU, PULU -> Register.U;
            case ANDCC, ORCC -> Register.CC;
            default -> null;
        };
    }
    public static boolean isMnemonic(String mnemonic)
    {
        if (mnemonic == null || mnemonic.isBlank())
            return false;

        mnemonic = mnemonic.toUpperCase();
        return mnemonics.contains(mnemonic);
    }

}
