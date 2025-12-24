package com.simulator.moto6809.Logger;

import com.simulator.moto6809.Errors.Response;

import java.nio.file.Path;

    public interface ILogger
    {
        /**
         * Logging the message into the output console or the log file if enabled
         * @param message The message to log
         * @param level The log level in which the message will be logged
         */
        void log(String message, LogLevel level);

        /**
         * Logging the response into the output console or the log file if enabled
         * @param response The response to log
         * @param level The log level in which the response will be logged
         */
        void log(Response response, LogLevel level);

        /**
         * Clear the log console
         */
        void clear();

        /**
         * Set the log file path
         * @param logFilePath The new log file path
         */
        void setLogFilePath(Path logFilePath);
    }

