package com.simulator.moto6809.Logger;

import com.simulator.moto6809.Errors.Response;

import java.nio.file.Path;

public class ConsoleLogger implements ILogger {

    @Override
    public void log(String message, LogLevel level) {
        System.out.println("[" + level + "] " + message);
    }

    @Override
    public void log(Response response, LogLevel level) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void setLogFilePath(Path logFilePath) {

    }
}
