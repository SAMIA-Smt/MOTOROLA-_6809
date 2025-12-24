package com.simulator.moto6809.Logger;

import com.simulator.moto6809.Console.IOutputStream;
import com.simulator.moto6809.Errors.Response;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class Logger implements ILogger{
    private Path logFilePath;
    private final Object lock = new Object();
    private final IOutputStream outputStream;

    public Logger(IOutputStream outputStream, Path logFilePath)
    {
        this.logFilePath = logFilePath;
        this.outputStream = outputStream;
    }

    @Override
    public void log(String message, LogLevel level)
    {
        message = level.toString() + ": " + message;
        switch (level) {
            case ERROR:
                outputStream.printError(message);
                break;
            case WARNING:
                outputStream.printWarning(message);
                break;
            default:
                outputStream.println(message);
        }

        if (logFilePath == null)
            return;

        synchronized (lock) {
            try (FileWriter fw = new FileWriter(logFilePath.toFile(), true))
            {
                fw.write(message + "\n");
            }
            catch (IOException e)
            {
                outputStream.print("An error occurred while logging to the file: " + e.getMessage());
            }
        }
    }

    @Override
    public void log(Response response, LogLevel level)
    {
        log(response.toString(), level);
    }

    @Override
    public void clear()
    {
        outputStream.clearConsole();
    }

    @Override
    public void setLogFilePath(Path logFilePath)
    {
        this.logFilePath = logFilePath;
    }
}
