package com.simulator.moto6809.Console;

public interface IOutputStream
{
    /**
     * Prints standard output text.
     * @param text The text to print.
     */
    void print(String text);

    /**
     * Prints standard output text followed by a newline.
     * @param text The text to print.
     */
    void println(String text);

    /**
     * Prints an error message
     * @param text The error message to print.
     */
    void printError(String text);

    /**
     * Prints a warning message
     * @param text The warning message to print.
     */
    void printWarning(String text);

    /**
     * Appends a command line prompt for simulation
     */
    void printPrompt();

    /**
     * Stops the console log timeline
     */
    void stop();

    /**
     * Clears the output console
     */
    void clearConsole();
}
