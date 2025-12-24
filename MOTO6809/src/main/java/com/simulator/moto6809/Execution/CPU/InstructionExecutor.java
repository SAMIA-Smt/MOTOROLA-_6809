package com.simulator.moto6809.Execution.CPU;

import com.simulator.moto6809.Decoder.AddressingMode;
import com.simulator.moto6809.Decoder.DecodedInstruction;
import com.simulator.moto6809.Execution.Instructions.*;
import com.simulator.moto6809.Memory.MemoryBus;
import com.simulator.moto6809.Registers.RegisterFunctions;

/**
 * Rôle exact de InstructionExecutor
 * InstructionExecutor est le cœur fonctionnel du CPU.
 * Il prend :
 * une DecodedInstruction
 * les registres
 * la mémoire
 * les helpers d’adressage
 * Et il :
 * choisit le bon groupe d’instructions
 * exécute le comportement réel 6809
 * met à jour :
 * registres
 * mémoire
 * flags
 * retourne le nombre de cycles consommés
 */
public class InstructionExecutor {

    private final RegisterFunctions registers;
    private final MemoryBus memory;

    public InstructionExecutor(RegisterFunctions registers, MemoryBus memory) {
        this.registers = registers;
        this.memory = memory;
    }

    /**
     * Execute one decoded instruction.
     * @return number of cycles consumed
     */
    public int execute(DecodedInstruction instr) {
        try {
        String m = instr.mnemonic();


        // LOAD / STORE

        if (LoadStoreInstructions.supports(m)) {
            int base = LoadStoreInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // ARITHMETIC

        if (ArithmeticInstructions.supports(m)) {
            int base = ArithmeticInstructions.execute(instr, registers, memory);// execute return le nombre  de cycle
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // LOGICAL

        if (LogicalInstructions.supports(m)) {
            int base = LogicalInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // SHIFT / ROTATE

        if (ShiftRotateInstructions.supports(m)) {
            int base = ShiftRotateInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // UNARY

        if (UnaryInstructions.supports(m)) {
            int base = UnaryInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // COMPARE

        if (CompareInstructions.supports(m)) {
            int base = CompareInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // BRANCH

        if (BranchInstructions.supports(m)) {
            return BranchInstructions.execute(instr, registers);
        }


        // JUMP

        if (JumpInstructions.supports(m)) {
            int base = JumpInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // STACK

        if (StackInstructions.supports(m)) {
            int base = StackInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // REGISTER TRANSFER

        if (RegisterTransferInstructions.supports(m)) {
            int base =RegisterTransferInstructions.execute(instr, registers, memory);
            if (instr.addressingMode() == AddressingMode.INDEXED) {
                base += IndexedCycleCalculator.computePenalty(instr);
            }

            return base;
        }


        // CONTROL

        if (ControlInstructions.supports(m)) {
            return ControlInstructions.execute(instr, registers, memory);
        }

        throw new IllegalStateException("Instruction not implemented: " + m);

        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Execution failed: mnemonic=" + instr.mnemonic()
                            + ", mode=" + instr.addressingMode()
                            + ", opcodeBytes=" + instr.opcodeByteCount(),
                    ex
            );
        }
    }
}

