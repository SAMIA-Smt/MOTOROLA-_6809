package com.simulator.moto6809.Execution.CPU;

import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.RegisterFunctions;

public final class InterruptController {

    // latched requests (pending)
    private boolean nmi;
    private boolean irq;
    private boolean firq;

    // software interrupts (latched by CPU after executing SWI/SWI2/SWI3)
    private boolean swi;
    private boolean swi2;
    private boolean swi3;

    public void requestNMI()  { nmi = true; }
    public void requestIRQ()  { irq = true; }
    public void requestFIRQ() { firq = true; }

    public void requestSWI()  { swi = true; }
    public void requestSWI2() { swi2 = true; }
    public void requestSWI3() { swi3 = true; }

    public void clearAll() {
        nmi = irq = firq = false;
        swi = swi2 = swi3 = false;
    }

    /**
     * Decide which interrupt (if any) should be taken NOW.
     * Priority (typical 6809): NMI > FIRQ > IRQ > SWI/SWI2/SWI3
     * SWI/SWI2/SWI3 are synchronous, but we handle them here as pending requests.
     */
    public InterruptType next(RegisterFunctions regs) {

        // NMI is non-maskable
        if (nmi) return InterruptType.NMI;

        // FIRQ masked by F flag
        if (firq && !regs.getFlag(Flag.F)) return InterruptType.FIRQ;

        // IRQ masked by I flag
        if (irq && !regs.getFlag(Flag.I)) return InterruptType.IRQ;

        // Software interrupts (always taken when requested)
        if (swi)  return InterruptType.SWI;
        if (swi2) return InterruptType.SWI2;
        if (swi3) return InterruptType.SWI3;

        return null;
    }

    public void acknowledge(InterruptType t) {
        if (t == null) return;
        switch (t) {
            case NMI -> nmi = false;
            case FIRQ -> firq = false;
            case IRQ -> irq = false;
            case SWI -> swi = false;
            case SWI2 -> swi2 = false;
            case SWI3 -> swi3 = false;
            default -> {}
        }
    }
}
