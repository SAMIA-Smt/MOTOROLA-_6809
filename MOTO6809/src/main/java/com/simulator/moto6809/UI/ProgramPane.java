package com.simulator.moto6809.UI;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public final class ProgramPane extends BorderPane {

    private final TableView<ProgramRow> table = new TableView<>();

    public ProgramPane(ObservableList<ProgramRow> rows) {
        setPadding(new Insets(8));
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ProgramRow, Number> cLine = new TableColumn<>("Line");
        cLine.setMaxWidth(70);
        cLine.setCellValueFactory(v -> v.getValue().lineProperty());

        TableColumn<ProgramRow, Number> cPc = new TableColumn<>("PC");
        cPc.setMaxWidth(90);
        cPc.setCellValueFactory(v -> v.getValue().pcProperty());
        cPc.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.format("$%04X", item.intValue() & 0xFFFF));
            }
        });

        TableColumn<ProgramRow, String> cBytes = new TableColumn<>("Bytes");
        cBytes.setMaxWidth(220);
        cBytes.setCellValueFactory(v -> v.getValue().bytesProperty());

        TableColumn<ProgramRow, String> cSrc = new TableColumn<>("Source");
        cSrc.setCellValueFactory(v -> v.getValue().sourceProperty());

        table.getColumns().setAll(cLine, cPc, cBytes, cSrc);
        setCenter(table);

        setTop(new Label("Program listing (line → PC address)"));
    }
}
