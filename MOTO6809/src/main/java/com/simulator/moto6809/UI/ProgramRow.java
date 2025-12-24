package com.simulator.moto6809.UI;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;

public final class ProgramRow {
    private final IntegerProperty line = new SimpleIntegerProperty();
    private final IntegerProperty pc = new SimpleIntegerProperty();
    private final StringProperty bytes = new SimpleStringProperty();
    private final StringProperty source = new SimpleStringProperty();

    public ProgramRow(int line, int pc, String bytes, String source) {
        this.line.set(line);
        this.pc.set(pc & 0xFFFF);
        this.bytes.set(bytes);
        this.source.set(source);
    }

    public IntegerProperty lineProperty() { return line; }
    public IntegerProperty pcProperty() { return pc; }
    public StringProperty bytesProperty() { return bytes; }
    public StringProperty sourceProperty() { return source; }
}
