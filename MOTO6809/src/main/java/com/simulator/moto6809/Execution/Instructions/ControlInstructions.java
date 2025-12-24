package com.simulator.moto6809.Execution.Instructions;

import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Execution.CPU.InterruptType;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.Flag;
import com.simulator.moto6809.Registers.Register;
import com.simulator.moto6809.Registers.RegisterFunctions;

import java.util.Set;

import static com.simulator.moto6809.Execution.Instructions.Mnemonics.*;
/*
NOP,SYNC,CWAI,SEX,SWI,SWI2,SWI3,
 */
/*
nop();
sync();
cwai(int mask);
sex();
swi(int vectorAddress);
 */
public final class ControlInstructions {

    private static final Set<String> SUPPORTED = Set.of(
            NOP, SYNC, CWAI,
            SWI, SWI2, SWI3,
            RTS, RTI
    );

    private ControlInstructions() {}

    public static boolean supports(String mnemonic) {
        return SUPPORTED.contains(mnemonic);
    }

    /**
     * Exécution instruction control.
     * NOTE: SYNC/CWAI demandent un état WAIT dans CPU. On le déclenche via PC inchangé + flag externe.
     * -> On recommande de faire gérer l'état WAIT dans CPU via un champ CPU.state.
     */
    public static int execute(DecodedInstruction instr, RegisterFunctions regs, MemoryBus mem) {

        String m = instr.mnemonic().toUpperCase();

        return switch (m) {

            case NOP -> instr.cycles();

            case RTS -> {
                int newPc = StackHelpers.pullWordS(regs, mem);
                regs.setRegister(Register.PC, newPc);
                yield instr.cycles();
            }

            case RTI -> {
                // RTI dépend de E dans CC
                boolean e = regs.getFlag(Flag.E);
                if (e) StackHelpers.pullEntireStateS(regs, mem);
                else   StackHelpers.pullMinimalStateS(regs, mem);
                yield instr.cycles();
            }

            case SWI -> {
                // SWI force entire state
                // Met E=1 et masque I/F
                regs.setFlag(Flag.E, true);
                StackHelpers.pushEntireStateS(regs, mem);
                regs.setFlag(Flag.I, true);
                regs.setFlag(Flag.F, true);
                // PC sera chargé par CPU via vecteur SWI ($FFFA)
                yield instr.cycles();
            }

            case SWI2 -> {
                regs.setFlag(Flag.E, true);
                StackHelpers.pushEntireStateS(regs, mem);
                // SWI2 ne force pas forcément I/F comme SWI (mais on laisse I/F inchangés)
                yield instr.cycles();
            }

            case SWI3 -> {
                regs.setFlag(Flag.E, true);
                StackHelpers.pushEntireStateS(regs, mem);
                yield instr.cycles();
            }

            case CWAI -> {
                // CWAI: CC = CC & imm (masquage bits)
                int mask = instr.operand() & 0xFF;
                regs.setRegister(Register.CC, regs.getRegister(Register.CC, false) & mask);

                // CWAI passe en wait; au réveil, l'interruption empile ENTIRE state.
                // On force E=1 (comportement pratique)
                regs.setFlag(Flag.E, true);

                // cycles = ceux de CWAI de la table (20 en général)
                yield instr.cycles();
            }

            case SYNC -> {
                // SYNC attend une interruption; aucun changement registre immédiat
                yield instr.cycles();
            }

            default -> throw new UnsupportedOperationException("Control not supported: " + m);
        };
    }

    /**
     * Entrée d'interruption exacte (niveau A).
     * CPU appelle ça AVANT de charger PC depuis le vecteur.
     */
    public static int enterInterrupt(InterruptType type, RegisterFunctions regs, MemoryBus mem) {

        return switch (type) {

            case NMI -> {
                // NMI empile entire state
                regs.setFlag(Flag.E, true);
                StackHelpers.pushEntireStateS(regs, mem);
                // NMI n'est pas masquée, mais beaucoup d'implémentations mettent I=1
                regs.setFlag(Flag.I, true);
                yield 19; // ordre de grandeur : similaire IRQ (dépend tableau précis)
            }

            case IRQ -> {
                regs.setFlag(Flag.E, true);
                StackHelpers.pushEntireStateS(regs, mem);
                regs.setFlag(Flag.I, true);
                yield 19;
            }

            case FIRQ -> {
                // FIRQ minimal si E=0
                regs.setFlag(Flag.E, false);
                StackHelpers.pushMinimalStateS(regs, mem);
                regs.setFlag(Flag.F, true);
                yield 10; // FIRQ plus court
            }

            case SWI -> {
                // SWI déjà push dans execute(), ici on ne fait rien
                yield 0;
            }
            case SWI2, SWI3 -> {
                // idem
                yield 0;
            }

            default -> 0;
        };
    }
}
