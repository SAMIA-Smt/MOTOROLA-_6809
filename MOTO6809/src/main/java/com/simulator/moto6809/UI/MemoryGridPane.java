package com.simulator.moto6809.UI;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public final class MemoryGridPane extends BorderPane {

    private final IntUnaryOperator peek;
    private final BiConsumer<Integer, Integer> poke; // null => read-only
    private final boolean editable;

    private final int rangeStart;
    private final int rangeEnd;

    private int base;
    private int rowsCount = 16; // 16 rows => 256 bytes per page

    private final ObservableList<MemRow> rows = FXCollections.observableArrayList();
    private final TableView<MemRow> table = new TableView<>(rows);
    private TableColumn<MemRow, String> baseCol;
    @SuppressWarnings("unchecked")
    private final TableColumn<MemRow, String>[] byteCols = new TableColumn[16];

    private final TextField baseField = new TextField();
    private final TextField rowsField = new TextField();
    private IntPredicate highlightAddr=null;
    private boolean responsiveInstalled = false;
    public MemoryGridPane(String title,
                          IntUnaryOperator peek,
                          BiConsumer<Integer, Integer> pokeOrNull,
                          int rangeStart,
                          int rangeEnd,
                          IntPredicate highlightAddr)
    {

        this.peek = peek;
        this.poke = pokeOrNull;
        this.editable = (pokeOrNull != null);

        this.rangeStart = rangeStart & 0xFFFF;
        this.rangeEnd   = rangeEnd & 0xFFFF;

        this.base = this.rangeStart;
        this.highlightAddr = (highlightAddr != null) ? highlightAddr : (a -> false);
        setPadding(new Insets(8));

        Label lbl = new Label(title + String.format("  (Range $%04X-$%04X)", this.rangeStart, this.rangeEnd));

        Button btnPrev = new Button("Prev");
        Button btnNext = new Button("Next");
        Button btnRefresh = new Button("Refresh");

        baseField.setPrefColumnCount(6);
        rowsField.setPrefColumnCount(4);

        baseField.setText(String.format("$%04X", base));
        rowsField.setText(String.valueOf(rowsCount));

        btnPrev.setOnAction(e -> { setBase(base - rowsCount * 16); refresh(); });
        btnNext.setOnAction(e -> { setBase(base + rowsCount * 16); refresh(); });
        btnRefresh.setOnAction(e -> refresh());

        baseField.setOnAction(e -> { Integer v = parseHexOrNull(baseField.getText()); if (v != null) setBase(v); refresh(); });
        rowsField.setOnAction(e -> { setRowsCount(rowsField.getText()); refresh(); });

        HBox top = new HBox(10, lbl, new Label("Base:"), baseField, new Label("Rows:"), rowsField, btnPrev, btnNext, btnRefresh);
        top.setPadding(new Insets(0, 0, 8, 0));
        setTop(top);

        buildTable();
        setCenter(table);
        // make table not grow too tall
        //table.setMinHeight(Control.USE_PREF_SIZE);
        //table.setMaxHeight(Control.USE_PREF_SIZE);
        //table.setPrefHeight((rowsCount + 1) * table.getFixedCellSize() + 40);
        // keep it at top (no vertical centering)
        BorderPane.setAlignment(table, Pos.TOP_LEFT);
        refresh();
        installResponsiveSizing();
        //updateTableHeightToRows();
    }

    private void setRowsCount(String txt) {
        try {
            int v = Integer.parseInt(txt.trim());
            if (v < 4) v = 4;
            if (v > 64) v = 64;
            rowsCount = v;
            rowsField.setText(String.valueOf(rowsCount));
        } catch (Exception ignored) {}
    }

    private void setBase(int addr) {
        int v = addr & 0xFFFF;
        v = v & 0xFFF0; // align to 16
        if (v < rangeStart) v = rangeStart & 0xFFF0;
        if (v > rangeEnd) v = (rangeEnd & 0xFFF0);
        base = v;
        baseField.setText(String.format("$%04X", base));
    }

    private void buildTable()
    {

        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setEditable(editable);

        baseCol = new TableColumn<>("R/C");
        baseCol.setPrefWidth(80);
        baseCol.setMinWidth(70);
        baseCol.setMaxWidth(140);
        baseCol.setResizable(false);
        baseCol.setCellValueFactory(v -> v.getValue().baseTextProperty());
        baseCol.setStyle("-fx-font-family: 'Consolas'; -fx-alignment: CENTER-LEFT;");

        table.getColumns().add(baseCol);


        for (int i = 0; i < 16; i++)
        {
            final int colIndex = i;
            TableColumn<MemRow, String> c = new TableColumn<>(String.format("%02X", i));
            c.setMinWidth(48);     // limite basse (2 hex)
            c.setMaxWidth(110);    // limite haute (évite trop gros)
            c.setResizable(false);
            c.setCellValueFactory(v -> v.getValue().cellProperty(colIndex));
            c.setStyle("-fx-alignment: CENTER; -fx-font-family: 'Consolas';");
            byteCols[colIndex] = c;


            if (editable)
            {
                c.setEditable(true);

                c.setCellFactory(col ->
                {
                    HexCell cell = new HexCell(colIndex);
                    //  start edit on SINGLE click
                    cell.setOnMouseClicked(e -> {
                        if (e.getClickCount() == 1 && !cell.isEmpty()) {
                            table.edit(cell.getIndex(), c);
                        }
                    });
                    return cell;
                });

                c.setOnEditCommit(ev -> {
                    MemRow row = ev.getRowValue();
                    if (row == null) return;

                    String t = (ev.getNewValue() == null) ? "" : ev.getNewValue().trim();
                    if (t.isEmpty()) { refresh(); return; }

                    // accept 1 or 2 hex digits
                    if (!t.matches("[0-9A-Fa-f]{1,2}")) { refresh(); return; }

                    int value = Integer.parseInt(t, 16) & 0xFF;
                    int addr  = (row.base + colIndex) & 0xFFFF;

                    if (addr < rangeStart || addr > rangeEnd) { refresh(); return; }

                    //  WRITE
                    poke.accept(addr, value);

                    //  update model immediately (no waiting for refresh)
                    row.setCell(colIndex, String.format("%02X", value));
                    table.refresh();
                });
            } else {
                c.setCellFactory(col -> new TableCell<MemRow, String>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);

                        if (empty || item == null || item.isEmpty()) {
                            setText(null);
                            setStyle("");
                            return;
                        }

                        setText(item);

                        MemRow row = (getTableRow() == null) ? null : getTableRow().getItem();
                        if (row == null) {
                            setStyle("");
                            return;
                        }

                        int addr = (row.base + colIndex) & 0xFFFF;
                        boolean hi = highlightAddr.test(addr);

                        if (hi) {
                            setStyle("-fx-text-fill: #1e3a8a; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
                        }
                    }
                });
            }

            table.getColumns().add(c);
        }

        table.setFixedCellSize(26);
    }
   // taBLE SIZ E
    private void updateTableHeightToRows() {
        // header height (approx). If your theme changes, 28-32 is fine.
        double header = 30;
        double h = header + rows.size() * table.getFixedCellSize() + 2;

        table.setPrefHeight(h);
        table.setMinHeight(h);
        table.setMaxHeight(h);
    }

    public void refresh()
    {
        rows.clear();

        int addr = base;
        for (int r = 0; r < rowsCount; r++) {
            if (addr > rangeEnd) break;

            MemRow row = new MemRow(addr & 0xFFFF);
            for (int i = 0; i < 16; i++) {
                int a = (addr + i) & 0xFFFF;
                if (a > rangeEnd) {
                    row.setCell(i, "");
                } else {
                    int b = peek.applyAsInt(a) & 0xFF;
                    row.setCell(i, String.format("%02X", b));
                }
            }
            rows.add(row);
            addr = (addr + 16) & 0xFFFF;
        }
        // adjust height to exactly visible rows
        //table.setPrefHeight((rows.size() + 1) * table.getFixedCellSize() + 40);
        //updateTableHeightToRows();
        //installResponsiveSizing();
    }

    private static Integer parseHexOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("$")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        return Integer.parseInt(t, 16) & 0xFFFF;
    }

    //
    // Row model
    //
    public static final class MemRow {
        final int base;
        final StringProperty baseText = new SimpleStringProperty();
        final StringProperty[] cells = new StringProperty[16];

        MemRow(int base) {
            this.base = base & 0xFFFF;
            baseText.set(String.format("%04X", this.base));
            for (int i = 0; i < 16; i++) cells[i] = new SimpleStringProperty("00");
        }

        StringProperty baseTextProperty() { return baseText; }
        StringProperty cellProperty(int i) { return cells[i]; }
        void setCell(int i, String v) { cells[i].set(v); }
    }

    //
    // Editable hex cell
    //
    private final class HexCell extends TableCell<MemRow, String>
    {

        private final int colIndex;
        private final TextField tf = new TextField();

        HexCell(int colIndex) {

            this.colIndex = colIndex;
            tf.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
            tf.setTextFormatter(new TextFormatter<String>(change -> {
                String nt = change.getControlNewText().toUpperCase();
                if (nt.isEmpty()) return change;
                if (nt.length() > 2) return null;
                if (!nt.matches("[0-9A-F]*")) return null;
                return change;

            }));

            // Enter => commit
            tf.setOnAction(e -> commitFromTextField());

            // focus lost => commit (or revert)
            tf.focusedProperty().addListener((obs, was, is) -> {
                if (!is && isEditing()) {
                    commitFromTextField();
                }
            });
        }

        @Override public void startEdit() {
            if (!editable) return;
            super.startEdit();

            String cur = getItem() == null ? "00" : getItem().toUpperCase();

            // remove zeros when clicking:
            // if it's "00" show empty, otherwise show the value
            tf.setText(cur.equals("00") ? "" : cur);

            setText(null);
            setGraphic(tf);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            Platform.runLater(() -> {
                tf.requestFocus();
                tf.selectAll();
            });
        }

        @Override public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            setContentDisplay(ContentDisplay.TEXT_ONLY);
            setText(getItem());
        }

        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null || item.isEmpty()) {
                setText(null);
                setGraphic(null);
                setStyle(""); // important reset
                return;
            }

            if (isEditing()) {
                setText(null);
                setGraphic(tf);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            } else {
                setGraphic(null);
                setContentDisplay(ContentDisplay.TEXT_ONLY);
                setText(item);
            }

            MemRow row = (getTableRow() == null) ? null : getTableRow().getItem();
            if (row == null) {
                setStyle("");
                return;
            }

            int addr = (row.base + colIndex) & 0xFFFF;
            boolean hi = highlightAddr.test(addr);

            if (hi) {
                setStyle("-fx-text-fill: #1e3a8a; -fx-font-weight: bold;");
            } else {
                setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
            }
        }

        private void commitFromTextField() {
            String t = tf.getText() == null ? "" : tf.getText().trim().toUpperCase();

            // if user leaves it empty => revert to old value (00 stays)
            if (t.isEmpty()) {
                cancelEdit();
                return;
            }

            // valid 1-2 digits
            if (!t.matches("[0-9A-F]{1,2}")) {
                cancelEdit();
                return;
            }

            // normalize to 2 digits
            int v = Integer.parseInt(t, 16) & 0xFF;
            String normalized = String.format("%02X", v);

            commitEdit(normalized); // fires onEditCommit handler
        }
    }

    public boolean isEditing() {
        return table.getEditingCell() != null;
    }

    private void installResponsiveSizing() {if (responsiveInstalled) return;
    responsiveInstalled = true;
        Runnable update = () -> {
            double w = table.getWidth();
            double h = table.getHeight();
            if (w <= 0 || h <= 0 || baseCol == null) return;

            // ---- WIDTH: expand/shrink columns within min/max ----
            double baseW = clamp(w * 0.08, 70, 140);
            baseCol.setPrefWidth(baseW);

            double remaining = w - baseW - 22; // small margin (scrollbar/borders)
            double colW = remaining / 16.0;
            colW = clamp(colW, 48, 110);

            for (int i = 0; i < 16; i++) {
                if (byteCols[i] != null) byteCols[i].setPrefWidth(colW);
            }

            // ---- HEIGHT: expand/shrink row height within min/max ----
            // approx header height
            double header = 28;
            double rowH = (h - header) / Math.max(1, rowsCount);
            rowH = clamp(rowH, 22, 34);

            table.setFixedCellSize(rowH);

            // ---- FONT: scale with row height (limits) ----
            double font = clamp(rowH * 0.50, 11, 16);
            table.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: " + String.format("%.1f", font) + "px;");
        };

        table.widthProperty().addListener((obs, o, n) -> update.run());
        table.heightProperty().addListener((obs, o, n) -> update.run());
        Platform.runLater(update);
        //updateTableHeightToRows();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }


}
