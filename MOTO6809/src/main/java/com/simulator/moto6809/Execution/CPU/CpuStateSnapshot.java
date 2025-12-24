package com.simulator.moto6809.Execution.CPU;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

/**
 * Immutable snapshot of CPU state for debugger/UI.
 */
public final class CpuStateSnapshot {

    public final int A, B, D, X, Y, S, U, PC, DP, CC;
    public final boolean E, F, H, I, N, Z, V, C;

    public final long totalCycles;
    public final DecodedInstruction lastInstruction;

    private CpuStateSnapshot(int a, int b, int d,
                             int x, int y, int s, int u,
                             int pc, int dp, int cc,
                             boolean e, boolean f, boolean h, boolean i,
                             boolean n, boolean z, boolean v, boolean c,
                             long totalCycles,
                             DecodedInstruction lastInstruction) {
        this.A = a; this.B = b; this.D = d;
        this.X = x; this.Y = y; this.S = s; this.U = u;
        this.PC = pc; this.DP = dp; this.CC = cc;
        this.E = e; this.F = f; this.H = h; this.I = i;
        this.N = n; this.Z = z; this.V = v; this.C = c;
        this.totalCycles = totalCycles;
        this.lastInstruction = lastInstruction;
    }

    public static CpuStateSnapshot from(RegisterFunctions regs, CycleCounter cycles, DecodedInstruction last) {
        int a  = regs.getRegister(Register.A, false);
        int b  = regs.getRegister(Register.B, false);
        int d  = regs.getRegister(Register.D, false);
        int x  = regs.getRegister(Register.X, false);
        int y  = regs.getRegister(Register.Y, false);
        int s  = regs.getRegister(Register.S, false);
        int u  = regs.getRegister(Register.U, false);
        int pc = regs.getRegister(Register.PC, false);
        int dp = regs.getRegister(Register.DP, false);
        int cc = regs.getRegister(Register.CC, false);

        boolean eFlag = regs.getFlag(Flag.E);
        boolean fFlag = regs.getFlag(Flag.F);
        boolean hFlag = regs.getFlag(Flag.H);
        boolean iFlag = regs.getFlag(Flag.I);
        boolean nFlag = regs.getFlag(Flag.N);
        boolean zFlag = regs.getFlag(Flag.Z);
        boolean vFlag = regs.getFlag(Flag.V);
        boolean cFlag = regs.getFlag(Flag.C);

        return new CpuStateSnapshot(
                a, b, d, x, y, s, u, pc, dp, cc,
                eFlag, fFlag, hFlag, iFlag, nFlag, zFlag, vFlag, cFlag,
                cycles.getTotalCycles(),
                last
        );
    }
}

