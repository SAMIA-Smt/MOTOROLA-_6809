package com.simulator.moto6809.Problems;

import java.util.ArrayList;
import java.util.List;

public class CompilationProblems {
    private final ArrayList<Problem> problems;

    public CompilationProblems()
    {
        problems = new ArrayList<>();
    }

    /**
     * Get the list of compilation problems
     * return List of all problems during compilation
     */
    public List<Problem> problems()
    {
        return problems;
    }

    /**
     * Add a problem to the problem list
     */
    public void addProblem(Problem problem)
    {
        problems.add(problem);
    }

    /**
     * Add a list of problems to the problem list
     */
    public void addProblems(List<Problem> problems)
    {
        this.problems.addAll(problems);
    }

    /**
     * Checks if there was no error during compilation
     * return True if there was no error, false otherwise
     */
    public boolean compiledWithoutErrors()
    {
        return problems.stream()
                .noneMatch(problem -> problem.problemType() == ProblemType.ERROR);
    }

    /**
     * Checks if there was no warning during compilation
     */
    public boolean compiledWithoutWarnings()
    {
        return problems.stream()
                .noneMatch(problem -> problem.problemType() == ProblemType.WARNING);
    }

    @Override
    public String toString()
    {
        return String.join("\n", problems.stream().map(Problem::toString).toList());
    }

}
