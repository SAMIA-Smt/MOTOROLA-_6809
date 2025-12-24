package com.simulator.moto6809.Resource;

import com.simulator.moto6809.Logger.ILogger;
import com.simulator.moto6809.Logger.LogLevel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class InstructionCsvLoader {

    private static final String BASE_PATH = "/com/simulator/moto6809/";

    private final ILogger logger;

    public InstructionCsvLoader(ILogger logger) {
        this.logger = logger;
    }


    // Public API


    public Map<String, com.simulator.moto6809.Resource.InstructionCsvRow> loadOpcodeTable() {
        return loadTable("InstructionOpcode.csv");
    }

    public Map<String, InstructionCsvRow> loadCycleTable() {
        return loadTable("InstructionCycle.csv");
    }

    public Map<String, InstructionCsvRow> loadSizeTable() {
        return loadTable("InstructionSize.csv");
    }


    // Core loader


    private Map<String, InstructionCsvRow> loadTable(String fileName) {
        Map<String, InstructionCsvRow> table = new HashMap<>();

        String fullPath = BASE_PATH + fileName;
        logger.log("Loading table: " + fullPath, LogLevel.INFO);

        try (InputStream is = InstructionCsvLoader.class.getResourceAsStream(fullPath)) {
            if (is == null) {
                throw new IllegalStateException("Missing CSV resource: " + fullPath);
            }


            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                boolean firstLine = true;
                // boocle pour remplire le hachmap
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // Skip empty lines
                    if (line.isEmpty())
                        continue;

                    // Skip comments (optional, if i add '# ...' later)
                    if (line.startsWith("#") || line.startsWith("//"))
                        continue;

                    // Skip header row
                    if (firstLine) {
                        firstLine = false;
                        // If it starts with INST_ or Instruction, treat as header:
                        if (line.toUpperCase().startsWith("INST_") ||
                                line.toUpperCase().startsWith("INSTRUCTION")) {
                            continue;
                        }
                    }

                    String[] parts = line.split(",", -1); // -1 => keep trailing empty columns
                    if (parts.length < 7) {
                        logger.log("Skipping malformed line in " + fileName + ": " + line, LogLevel.WARNING);
                        continue;
                    }

                    String mnemonic = parts[0].trim();
                    if (mnemonic.isEmpty()) {
                        logger.log("Skipping line with empty mnemonic in " + fileName + ": " + line, LogLevel.WARNING);
                        continue;
                    }

                    InstructionCsvRow row = new InstructionCsvRow(
                            mnemonic,
                            parts[1].trim(),
                            parts[2].trim(),
                            parts[3].trim(),
                            parts[4].trim(),
                            parts[5].trim(),
                            parts[6].trim()
                    );

                    // Overwrites if mnemonic appears twice
                    table.put(mnemonic, row);
                }
            }

            logger.log("Loaded " + table.size() + " entries from " + fileName, LogLevel.INFO);
        } catch (IOException e) {
            logger.log("IOException while reading " + fullPath + ": " + e.getMessage(), LogLevel.ERROR);
        }

        return table;
    }
}