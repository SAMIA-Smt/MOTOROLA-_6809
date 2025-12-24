package com.simulator.moto6809.Registers;

import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;

import java.util.ArrayList;
import java.util.List;

public class RegisterFunctions {

    private final ILogger logger;

    // 8-bit registers
    private int A;
    private int B;
    private int DP;
    private int CC;

    // 16-bit registers
    private int X;
    private int Y;
    private int S;   // system stack
    private int U;   // user stack
    private int PC;

    private final List<RegisterListener> listeners = new ArrayList<>();

    public RegisterFunctions(ILogger logger) {
        this.logger = logger;
    }

    // LISTENERS


    public void addListener(RegisterListener listener) {
        if (listener != null) listeners.add(listener);
    }

    private void notifyListeners(Register reg, int value) {
        for (RegisterListener l : listeners) {
            l.onRegisterChanged(reg, value);
        }
    }

    // GET REGISTER


    public int getRegister(Register register, boolean notify) {
        if (notify && logger != null) {
            logger.log("Register " + register + " was read", LogLevel.DEBUG);
        }

        return switch (register) {
            case A -> A & 0xFF;
            case B -> B & 0xFF;
            case D -> ((A & 0xFF) << 8) | (B & 0xFF);

            case X -> X & 0xFFFF;
            case Y -> Y & 0xFFFF;
            case S -> S & 0xFFFF;
            case U -> U & 0xFFFF;
            case PC -> PC & 0xFFFF;

            case DP -> DP & 0xFF;
            case CC -> CC & 0xFF;
        };
    }

    public int getRegister(Register register) {
        return getRegister(register, true);
    }


    // SET REGISTER


    public void setRegister(Register register, int regValue, boolean notify) {

        boolean is16 = is16BitRegister(register);
        if (is16) regValue &= 0xFFFF;
        else regValue &= 0xFF;

        switch (register) {
            case A -> A = regValue;
            case B -> B = regValue;

            case D -> {
                // D writes A and B
                A = (regValue >> 8) & 0xFF;
                B = regValue & 0xFF;
            }

            case X -> X = regValue;
            case Y -> Y = regValue;
            case S -> S = regValue;
            case U -> U = regValue;
            case PC -> PC = regValue;

            case DP -> DP = regValue;
            case CC -> CC = regValue;
        }

        if (notify && logger != null) {
            logger.log(String.format("Register %s updated to $%04X", register, regValue),
                    LogLevel.DEBUG);
        }


        // Notifications (CRITICAL FIX for D/A/B consistency)

        // Always notify the written register first
        notifyListeners(register, getRegister(register, false));

        // If A or B changed => D changed too
        if (register == Register.A || register == Register.B) {
            notifyListeners(Register.D, getRegister(Register.D, false));
        }

        // If D changed => A and B changed too
        if (register == Register.D) {
            notifyListeners(Register.A, getRegister(Register.A, false));
            notifyListeners(Register.B, getRegister(Register.B, false));
        }
    }

    public void setRegister(Register register, int regValue) {
        setRegister(register, regValue, true);
    }


    // FLAG OPERATIONS


    public void setFlag(Flag flag, boolean value) {
        int bit = flagBit(flag);
        if (value) CC |= (1 << bit);
        else CC &= ~(1 << bit);

        // CC changed => notify CC listeners
        notifyListeners(Register.CC, getRegister(Register.CC, false));
    }

    public boolean getFlag(Flag flag) {
        int bit = flagBit(flag);
        return (CC & (1 << bit)) != 0;
    }

    private int flagBit(Flag f) {
        return switch (f) {
            case E -> 7;
            case F -> 6;
            case H -> 5;
            case I -> 4;
            case N -> 3;
            case Z -> 2;
            case V -> 1;
            case C -> 0;
        };
    }


    // UPDATE NZ


    public void updateNZ(int value, boolean is16) {
        if (is16)
            setFlag(Flag.N, (value & 0x8000) != 0);
        else
            setFlag(Flag.N, (value & 0x80) != 0);

        setFlag(Flag.Z, (value & (is16 ? 0xFFFF : 0xFF)) == 0);
    }

    public void updateNZ(int value) {
        updateNZ(value, false);
    }

    public void updateNZ16(int value) {
        updateNZ(value, true);
    }


    // REGISTER TYPE HELPERS


    public boolean is16BitRegister(Register reg) {
        return switch (reg) {
            case X, Y, S, U, PC, D -> true;
            default -> false;
        };
    }
}
