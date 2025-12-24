package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

public final class StackHelpers {
    private StackHelpers() {}

    // 6809: stack descendante
    public static void pushByteS(RegisterFunctions regs, MemoryBus mem, int v) {
        int s = (regs.getRegister(Register.S, false) - 1) & 0xFFFF;
        regs.setRegister(Register.S, s, false);
        mem.write(s, v & 0xFF);
    }

    public static void pushWordS(RegisterFunctions regs, MemoryBus mem, int v16) {
        // big-endian: push high then low (via 2 pushByte)
        pushByteS(regs, mem, (v16 >> 8) & 0xFF);
        pushByteS(regs, mem, v16 & 0xFF);
    }

    public static int pullByteS(RegisterFunctions regs, MemoryBus mem) {
        int s = regs.getRegister(Register.S, false) & 0xFFFF;
        int v = mem.read(s) & 0xFF;
        regs.setRegister(Register.S, (s + 1) & 0xFFFF, false);
        return v;
    }

    public static int pullWordS(RegisterFunctions regs, MemoryBus mem) {
        // pull low then high? Non: comme on a push high puis low,
        // en pull on récupère low puis high en inversant via 2 pulls et recomposition.
        int lo = pullByteS(regs, mem);
        int hi = pullByteS(regs, mem);
        return ((hi << 8) | lo) & 0xFFFF;
    }

    /** Push "entire state" comme le 6809 : PC,U,Y,X,DP,B,A,CC (sur S) */
    public static void pushEntireStateS(RegisterFunctions regs, MemoryBus mem) {
        pushWordS(regs, mem, regs.getRegister(Register.PC, false));
        pushWordS(regs, mem, regs.getRegister(Register.U,  false));
        pushWordS(regs, mem, regs.getRegister(Register.Y,  false));
        pushWordS(regs, mem, regs.getRegister(Register.X,  false));
        pushByteS(regs, mem, regs.getRegister(Register.DP, false));
        pushByteS(regs, mem, regs.getRegister(Register.B,  false));
        pushByteS(regs, mem, regs.getRegister(Register.A,  false));
        pushByteS(regs, mem, regs.getRegister(Register.CC, false));
    }

    /** Push minimal state (FIRQ) : PC,CC */
    public static void pushMinimalStateS(RegisterFunctions regs, MemoryBus mem) {
        pushWordS(regs, mem, regs.getRegister(Register.PC, false));
        pushByteS(regs, mem, regs.getRegister(Register.CC, false));
    }

    /** Pull entire state (RTI quand E=1) : CC,A,B,DP,X,Y,U,PC */
    public static void pullEntireStateS(RegisterFunctions regs, MemoryBus mem) {
        regs.setRegister(Register.CC, pullByteS(regs, mem), false);
        regs.setRegister(Register.A,  pullByteS(regs, mem), false);
        regs.setRegister(Register.B,  pullByteS(regs, mem), false);
        regs.setRegister(Register.DP, pullByteS(regs, mem), false);
        regs.setRegister(Register.X,  pullWordS(regs, mem), false);
        regs.setRegister(Register.Y,  pullWordS(regs, mem), false);
        regs.setRegister(Register.U,  pullWordS(regs, mem), false);
        regs.setRegister(Register.PC, pullWordS(regs, mem), false);
    }

    /** Pull minimal state (RTI quand E=0) : CC,PC */
    public static void pullMinimalStateS(RegisterFunctions regs, MemoryBus mem) {
        regs.setRegister(Register.CC, pullByteS(regs, mem), false);
        regs.setRegister(Register.PC, pullWordS(regs, mem), false);
    }


    // USER STACK (U)


    public static void pushByteU(RegisterFunctions regs, MemoryBus mem, int v) {
        int u = (regs.getRegister(Register.U, false) - 1) & 0xFFFF;
        regs.setRegister(Register.U, u, false);
        mem.write(u, v & 0xFF);
    }

    public static void pushWordU(RegisterFunctions regs, MemoryBus mem, int v16) {
        pushByteU(regs, mem, (v16 >> 8) & 0xFF);
        pushByteU(regs, mem, v16 & 0xFF);
    }

    public static int pullByteU(RegisterFunctions regs, MemoryBus mem) {
        int u = regs.getRegister(Register.U, false) & 0xFFFF;
        int v = mem.read(u) & 0xFF;
        regs.setRegister(Register.U, (u + 1) & 0xFFFF, false);
        return v;
    }

    public static int pullWordU(RegisterFunctions regs, MemoryBus mem) {
        int lo = pullByteU(regs, mem);
        int hi = pullByteU(regs, mem);
        return ((hi << 8) | lo) & 0xFFFF;
    }

}

