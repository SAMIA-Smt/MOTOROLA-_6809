package com.simulator.moto6809.Problems;

public record Problem(ProblemType problemType, String message, String file, int line)
{
    @Override
    public String toString()
    {
        return problemType + ": " + message + ". In " +
                file + ". At line: " + line;
    }
}
