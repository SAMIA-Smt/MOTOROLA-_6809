package com.simulator.moto6809.UI;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.layout.FlowPane;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

public final class MainView {

    private final CentralController controller = new CentralController();
    public CentralController controller() { return controller; }

    private final BorderPane root = new BorderPane();

    private final CodeArea codeArea = new CodeArea();

    private Integer highlightedLine = null;

    private final TextField originField = new TextField("$E000");
    private final TextField maxField = new TextField("200000");

    private final ListView<String> console = new ListView<>();

    private java.util.function.IntFunction<javafx.scene.Node> gutterFactory;

    private AnimationTimer timer;

    public MainView() {
        buildLayout();
        wireActions();
        setupEditorGutterWithBreakpoints();
        setupConsole();
        setupRegistersPanel();
        setupCenterTabs();
        //  refresh memory once after a successful Assemble/Load
        controller.programLoadedProperty().addListener((obs, was, is) -> {
            if (!is) return;

            Platform.runLater(() -> {
                Object rp = root.getProperties().get("ramPane");
                if (rp instanceof MemoryGridPane mg) mg.refresh();

                Object op = root.getProperties().get("romPane");
                if (op instanceof MemoryGridPane mg) mg.refresh();
            });
        });


    }

    public Parent getRoot() { return root; }

    private void buildLayout() {
        root.setPadding(new Insets(8));

        ToolBar toolBar = new ToolBar();
        Button btnAssemble = new Button("Assemble / Load");
        Button btnRun = new Button("Run");
        Button btnPause = new Button("Pause");
        Button btnStep = new Button("Step");
        Button btnResetCpu = new Button("Reset CPU");
        Button btnClearRam = new Button("Clear RAM");
        //Button btnClearRom = new Button("Clear ROM");
        Button btnClearConsole = new Button("Clear Console");

        originField.setPrefColumnCount(8);
        maxField.setPrefColumnCount(8);

        toolBar.getItems().addAll(
                btnAssemble,
                new Separator(),
                btnRun, btnPause, btnStep, btnResetCpu,
                new Separator(),
                new Label("Origin:"), originField,
                new Label("Max:"), maxField,
                new Separator(),
                btnClearRam, //btnClearRom,
                new Separator(),
                btnClearConsole
        );
        root.setTop(toolBar);

        // Editor (left)
        codeArea.setPrefWidth(520);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        codeArea.replaceText(defaultProgram());

        // Editor node
        var editorNode = new VirtualizedScrollPane<>(codeArea);

// Center tabs
        TabPane centerTabs = new TabPane();
        centerTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

// Right registers
        VBox right = new VBox(10);
        right.setPadding(new Insets(8));
        right.setPrefWidth(320);
        right.setStyle("-fx-background-color: #0f172a10; -fx-background-radius: 10;");

//  SplitPane (3 columns)
        SplitPane split = new SplitPane(editorNode, centerTabs, right);
        split.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

// min widths to avoid collapsing
        editorNode.setMinWidth(300);
        centerTabs.setMinWidth(520);
        right.setMinWidth(280);

// initial divider positions (0..1)
        split.setDividerPositions(0.35, 0.78);

// Put split pane in the CENTER of the BorderPane
        root.setCenter(split);

//  store for later use (important)
        root.getProperties().put("rightPane", right);


        // Console bottom
        console.setPrefHeight(220);
        root.setBottom(console);

        // Store
        root.getProperties().put("btnAssemble", btnAssemble);
        root.getProperties().put("btnRun", btnRun);
        root.getProperties().put("btnPause", btnPause);
        root.getProperties().put("btnStep", btnStep);
        root.getProperties().put("btnResetCpu", btnResetCpu);
        root.getProperties().put("btnClearRam", btnClearRam);
        //root.getProperties().put("btnClearRom", btnClearRom);
        root.getProperties().put("btnClearConsole", btnClearConsole);
        root.getProperties().put("centerTabs", centerTabs);
    }

    private void wireActions() {
        Button btnAssemble = (Button) root.getProperties().get("btnAssemble");
        Button btnRun = (Button) root.getProperties().get("btnRun");
        Button btnPause = (Button) root.getProperties().get("btnPause");
        Button btnStep = (Button) root.getProperties().get("btnStep");
        Button btnResetCpu = (Button) root.getProperties().get("btnResetCpu");
        Button btnClearRam = (Button) root.getProperties().get("btnClearRam");
        //Button btnClearRom = (Button) root.getProperties().get("btnClearRom");
        Button btnClearConsole = (Button) root.getProperties().get("btnClearConsole");

        // Disable run/step/reset/clear while not loaded OR busy
        btnRun.disableProperty().bind(controller.programLoadedProperty().not().or(controller.busyProperty()));
        btnStep.disableProperty().bind(controller.programLoadedProperty().not().or(controller.busyProperty()));
        btnResetCpu.disableProperty().bind(controller.busyProperty());
        btnClearRam.disableProperty().bind(controller.busyProperty());
        //btnClearRom.disableProperty().bind(controller.busyProperty());

        btnAssemble.disableProperty().bind(controller.busyProperty());

        btnAssemble.setOnAction(e -> {
            Integer origin = parseHexOrNull(originField.getText());
            controller.assembleAndLoad(codeArea.getText(), origin);
        });

        btnRun.setOnAction(e -> controller.run(parseIntSafe(maxField.getText(), 200000)));
        btnPause.setOnAction(e -> controller.pause());
        btnStep.setOnAction(e -> controller.step());
        btnResetCpu.setOnAction(e -> controller.resetCpu());

        btnClearRam.setOnAction(e -> controller.clearRam());
        //btnClearRom.setOnAction(e -> controller.clearRom());
        btnClearConsole.setOnAction(e -> controller.consoleLines().clear());
    }

    private void setupCenterTabs() {
        TabPane centerTabs = (TabPane) root.getProperties().get("centerTabs");

        MemoryGridPane ramPane = new MemoryGridPane(
                "RAM",
                addr -> controller.peekByte(addr),
                (a, v) -> controller.pokeByte(a, v),
                controller.ramStart(),
                controller.ramEnd(),
                addr -> (controller.peekByte(addr) & 0xFF) != 0
        );

        MemoryGridPane romPane = new MemoryGridPane(
                "ROM",
                addr -> controller.peekByte(addr),
                null,
                controller.romStart(),
                controller.romEnd(),
                addr -> controller.isRomProgramByte(addr)
        );

        ProgramPane progPane = new ProgramPane(controller.programRows());
        Tab program = new Tab("Program", progPane);
        Tab ram = new Tab("RAM", ramPane);
        Tab rom = new Tab("ROM", romPane);

        centerTabs.getTabs().setAll(ram, rom, program);

        root.getProperties().put("ramPane", ramPane);
        root.getProperties().put("romPane", romPane);
        root.getProperties().put("programPane", progPane);

    }

    private void setupRegistersPanel() {
        VBox right = (VBox) root.getProperties().get("rightPane");
        right.getChildren().clear();

        Label title = new Label("CPU State");
        title.setFont(Font.font(16));

        Label title1 = new Label("Last instruction:");
        title.setFont(Font.font(14));

        Label title2 = new Label("Registers");
        title2.setFont(Font.font(14));

        Label title3 = new Label("Flags");
        title3.setFont(Font.font(14));

        HBox pcAndCycles = new HBox(10, pcCard(), cyclesCard());
        pcAndCycles.setAlignment(Pos.CENTER_LEFT);


        Label last = new Label();
        last.setWrapText(true);
        last.textProperty().bind(controller.lastInstructionProperty());

        FlowPane cards = new FlowPane();
        cards.setHgap(10);
        cards.setVgap(10);
        cards.setPrefWrapLength(280); // wrap in right panel

        cards.getChildren().add(regCard("A", 8, controller.regA, com.simulator.moto6809.Registers.Register.A));
        cards.getChildren().add(regCard("B", 8, controller.regB, com.simulator.moto6809.Registers.Register.B));
        cards.getChildren().add(regCard("D", 16, controller.regD, com.simulator.moto6809.Registers.Register.D));
        cards.getChildren().add(regCard("X", 16, controller.regX, com.simulator.moto6809.Registers.Register.X));
        cards.getChildren().add(regCard("Y", 16, controller.regY, com.simulator.moto6809.Registers.Register.Y));
        cards.getChildren().add(regCard("S", 16, controller.regS, com.simulator.moto6809.Registers.Register.S));
        cards.getChildren().add(regCard("U", 16, controller.regU, com.simulator.moto6809.Registers.Register.U));
        cards.getChildren().add(regCard("DP", 8, controller.regDP, com.simulator.moto6809.Registers.Register.DP));
        cards.getChildren().add(regCard("CC", 8, controller.regCC, com.simulator.moto6809.Registers.Register.CC));


        HBox flagsRow = new HBox(14,
                flagDot("E", controller.fE),
                flagDot("F", controller.fF),
                flagDot("H", controller.fH),
                flagDot("I", controller.fI),
                flagDot("N", controller.fN),
                flagDot("Z", controller.fZ),
                flagDot("V", controller.fV),
                flagDot("C", controller.fC)
        );
        flagsRow.setAlignment(Pos.CENTER);
        flagsRow.setPadding(new Insets(6, 0, 0, 0));

// pour qu'ils prennent toute la largeur (et réduire la hauteur globale)
        for (var n : flagsRow.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            ((Region)n).setMaxWidth(Double.MAX_VALUE);
        }

        right.getChildren().addAll(
                title,
                pcAndCycles,//Pc,Cycle
                new Separator(),
                title2,
                cards,//33resgs
                new Separator(),
                title3,
                flagsRow,
                new Separator(),
                lastInstructionCard()//flags

        );
    }

    private void setupConsole() {
        console.setItems(controller.consoleLines());
        console.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        console.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTextFill(null);
                    return;
                }
                setText(item);

                if (item.startsWith("[ERROR]")) setTextFill(javafx.scene.paint.Color.RED);
                else if (item.startsWith("[WARN]")) setTextFill(javafx.scene.paint.Color.ORANGE);
                else setTextFill(javafx.scene.paint.Color.BLACK);
            }
        });

    }

    // Breakpoints gutter
    private void setupEditorGutterWithBreakpoints() {

        final var lineNumberFactory = LineNumberFactory.get(codeArea);

        gutterFactory = lineIndex -> {
            var ln = lineNumberFactory.apply(lineIndex);
            ln.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

            // ✅ REAL circle: cannot become oval
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(6);

            boolean enabled = controller.breakpointLines().contains(lineIndex);

            if (enabled) {
                dot.setFill(javafx.scene.paint.Color.web("#ef4444"));
                dot.setStroke(javafx.scene.paint.Color.web("#ef4444"));
                dot.setEffect(new javafx.scene.effect.DropShadow(
                        10,
                        javafx.scene.paint.Color.rgb(239, 68, 68, 0.8)
                ));
            } else {
                dot.setFill(javafx.scene.paint.Color.TRANSPARENT);
                dot.setStroke(javafx.scene.paint.Color.TRANSPARENT);
            }

            // Wrap to keep a fixed click area
            StackPane dotWrap = new StackPane(dot);
            dotWrap.setMinSize(16, 16);
            dotWrap.setPrefSize(16, 16);
            dotWrap.setMaxSize(16, 16);

            dotWrap.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY) {
                    controller.toggleBreakpointLine(lineIndex);
                    applyLineStyle(lineIndex);   //  highlight this line
                    refreshGutter();
                    ev.consume();
                }
            });

            HBox box = new HBox(6, dotWrap, ln);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setPadding(new Insets(0, 6, 0, 6));
            return box;
        };

        codeArea.setParagraphGraphicFactory(gutterFactory);

        controller.breakpointLines().addListener(
                (javafx.collections.SetChangeListener<Integer>) change -> {
                    Integer line = change.wasAdded() ? change.getElementAdded() : change.getElementRemoved();
                    if (line != null) applyLineStyle(line);
                    refreshGutter();
                }
        );
    }

    private void refreshGutter() {
        if (gutterFactory == null) return;
        codeArea.setParagraphGraphicFactory(i -> null);
        codeArea.setParagraphGraphicFactory(gutterFactory);
    }

    private void highlightLine(Integer lineIndex) {

        // re-apply style on old highlighted line (keep breakpoint style if any)
        if (highlightedLine != null) {
            int old = highlightedLine;
            highlightedLine = null;
            applyLineStyle(old);
        }

        highlightedLine = lineIndex;

        if (lineIndex == null || lineIndex < 0) return;

        int lineCount = codeArea.getText().split("\\R", -1).length;
        if (lineIndex >= lineCount) return;

        applyLineStyle(lineIndex);  //  applies pc-line (+ bp-line if exists)
        codeArea.showParagraphAtTop(Math.max(0, lineIndex - 3));
    }


    public void startUiPump() {
        timer = new AnimationTimer() {
            private long lastUi = 0;
            private long lastMem = 0;

            @Override public void handle(long now) {
                // UI pump ~60fps
                if (lastUi != 0 && (now - lastUi) < 16_000_000) return;
                lastUi = now;

                controller.pumpUi();

                // refresh memory فقط كل 250ms (not 60fps)
                if (lastMem == 0 || (now - lastMem) > 250_000_000) {
                    lastMem = now;

                    Object rp = root.getProperties().get("ramPane");
                    if (rp instanceof MemoryGridPane mg) {
                        if (!mg.isEditing()) mg.refresh();
                    }

                    Object op = root.getProperties().get("romPane");
                    if (op instanceof MemoryGridPane mg) {
                        if (!mg.isEditing()) mg.refresh();
                    }
                }

                Integer line = controller.lineForPc(controller.pcProperty().get());
                if (line != null && !line.equals(highlightedLine)) highlightLine(line);
            }
        };
        timer.start();
    }


    // Helpers
// BREAKPOINT HELPERS FOR LINE SHADOW
    private void applyLineStyle(int lineIndex) {
        if (lineIndex < 0) return;

        java.util.List<String> styles = new java.util.ArrayList<>();

        // breakpoint highlight
        if (controller.breakpointLines().contains(lineIndex)) styles.add("bp-line");

        // pc highlight
        if (highlightedLine != null && highlightedLine.equals(lineIndex)) styles.add("pc-line");

        codeArea.setParagraphStyle(lineIndex, styles);
    }



    //registres

    private Pane regCard(String name,
                         int bits,
                         javafx.beans.property.IntegerProperty valueProp,
                         com.simulator.moto6809.Registers.Register reg) {

        final int digits = (bits == 8) ? 2 : 4;
        final int mask   = (bits == 8) ? 0xFF : 0xFFFF;

        Label lbl = new Label(name);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField field = new TextField();
        field.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        field.setPrefColumnCount(digits + 1);

        // display initial "$00" or "$0000"
        field.setText(formatDollarHex(valueProp.get(), digits));

        // allow only "$" + up to N hex digits, never block typing
        installDollarHexFormatter(field, digits);

        // update from CPU only when not editing
        valueProp.addListener((obs, oldV, newV) -> {
            if (!field.isFocused()) {
                field.setText(formatDollarHex(newV.intValue() & mask, digits));
            }
        });

        // click/focus: keep "$", remove zeros after it
        field.focusedProperty().addListener((o, was, is) -> {
            if (is) {
                prepareEdit(field); // "$0000" -> "$"
                // select everything after "$"
                field.selectRange(1, field.getText().length());
            } else {
                commitRegister(field, valueProp, reg, digits, mask);
            }
        });

        // Enter commits too
        field.setOnAction(e -> commitRegister(field, valueProp, reg, digits, mask));

        VBox box = new VBox(6, lbl, field);
        box.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #cbd5e1;
            -fx-border-radius: 10;
            -fx-background-radius: 10;
            -fx-padding: 10;
            -fx-min-width: 90;
            """);

        return box;
    }
   // end registers helper
    private void addReg(GridPane grid, int row, String name, javafx.beans.property.IntegerProperty value, int bits) {
        Label k = new Label(name + ":");
        Label v = new Label();
        v.setStyle("-fx-font-family: 'Consolas';");
        v.textProperty().bind(Bindings.createStringBinding(
                () -> (bits == 8 ? hex8(value.get()) : hex16(value.get())),
                value
        ));
        grid.add(k, 0, row);
        grid.add(v, 1, row);
    }

    private HBox flagChip(String name, javafx.beans.property.BooleanProperty on) {
        Label l = new Label(name);
        l.setMinWidth(18);
        l.setAlignment(Pos.CENTER);

        Region dot = new Region();
        dot.setMinSize(10, 10);
        dot.setPrefSize(10, 10);

        dot.styleProperty().bind(Bindings.createStringBinding(() -> {
            if (on.get()) return "-fx-background-color: #22c55e; -fx-background-radius: 5;";
            return "-fx-background-color: #94a3b8; -fx-background-radius: 5;";
        }, on));

        HBox box = new HBox(6, dot, l);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Integer parseHexOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        t = t.replace("_", "");
        if (t.startsWith("$")) t = t.substring(1);
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        return Integer.parseInt(t, 16) & 0xFFFF;
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return def; }
    }

    private static String hex8(int v) { return String.format("$%02X", v & 0xFF); }
    private static String hex16(int v) { return String.format("$%04X", v & 0xFFFF); }

    private static String defaultProgram() {
        return """
                ORG $E000
                START:
                    LDA #$42
                    STA $0100
                    BRA START
                END
                """;
    }

    // REGISTER HELPERS
    private static String formatDollarHex(int v, int digits) {
        int mask = (digits == 2) ? 0xFF : 0xFFFF;
        return "$" + String.format("%0" + digits + "X", v & mask);
    }

    private static void prepareEdit(TextField field) {
        String t = field.getText() == null ? "" : field.getText().trim().toUpperCase();
        if (!t.startsWith("$")) t = "$" + t;

        String raw = t.substring(1);     // part after $
        raw = raw.replaceFirst("^0+", ""); // remove ALL leading zeros
        if (raw.isEmpty()) {
            field.setText("$");           // keep only $
        } else {
            field.setText("$" + raw);
        }
    }

    private void commitRegister(TextField field,
                                javafx.beans.property.IntegerProperty valueProp,
                                com.simulator.moto6809.Registers.Register reg,
                                int digits,
                                int mask) {

        String t = field.getText() == null ? "" : field.getText().trim().toUpperCase();
        if (!t.startsWith("$")) t = "$" + t;

        String raw = t.substring(1).trim();   // digits only

        //if user left it empty ("$") -> revert to current register value
        if (raw.isEmpty()) {
            field.setText(formatDollarHex(valueProp.get(), digits));
            return;
        }

        // validate
        if (!raw.matches("[0-9A-F]{1," + digits + "}")) {
            field.setText(formatDollarHex(valueProp.get(), digits));
            return;
        }

        int v = Integer.parseInt(raw, 16) & mask;

        controller.setRegister(reg, v);

        // display padded with zeros again
        field.setText(formatDollarHex(v, digits));
    }

    private static void installDollarHexFormatter(TextField field, int digits) {
        field.setTextFormatter(new TextFormatter<String>(change -> {

            // Uppercase what user inserts
            if (change.getText() != null) {
                change.setText(change.getText().toUpperCase());
            }

            String newText = change.getControlNewText();
            if (newText == null) return change;

            newText = newText.toUpperCase();

            // Always keep at least "$"
            if (newText.isEmpty()) {
                change.setRange(0, change.getControlText().length());
                change.setText("$");
                return change;
            }

            // If user types without "$" (ex: selects all and types "AB"), we auto-add "$"
            if (!newText.startsWith("$")) {
                String raw = newText.replaceAll("[^0-9A-F]", "");
                if (raw.length() > digits) raw = raw.substring(0, digits);

                change.setRange(0, change.getControlText().length());
                change.setText("$" + raw);
                return change;
            }

            // After "$": only hex and max N digits
            String raw = newText.substring(1);

            if (raw.length() > digits) return null;
            if (!raw.matches("[0-9A-F]*")) return null;

            return change;
        }));

        // Optional safety: prevent caret before "$"
        field.caretPositionProperty().addListener((obs, oldV, newV) -> {
            if (field.isFocused() && newV.intValue() < 1) {
                field.positionCaret(1);
            }
        });
    }

    //PC AND CYCLE HELPER
    private Pane pcCard() {
        Label lbl = new Label("PC");
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField field = new TextField();
        field.setEditable(false);
        field.setFocusTraversable(false);
        field.setMouseTransparent(true); // empêche le clic (optionnel)
        field.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");

        field.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("$%04X", controller.pcProperty().get() & 0xFFFF),
                controller.pcProperty()
        ));

        VBox box = new VBox(6, lbl, field);
        box.setStyle("""
        -fx-background-color: white;
        -fx-border-color: #cbd5e1;
        -fx-border-radius: 10;
        -fx-background-radius: 10;
        -fx-padding: 10;
        -fx-min-width: 130;
        """);

        return box;
    }

    private Pane cyclesCard() {
        Label lbl = new Label("Cycles");
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label value = new Label();
        value.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 18px; -fx-font-weight: bold;");
        value.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(controller.cyclesProperty().get()),
                controller.cyclesProperty()
        ));

        VBox box = new VBox(6, lbl, value);
        box.setStyle("""
        -fx-background-color: #f1f5f9;
        -fx-border-color: #cbd5e1;
        -fx-border-radius: 10;
        -fx-background-radius: 10;
        -fx-padding: 10;
        -fx-min-width: 130;
        """);

        return box;
    }

    private Region flagDot(String name, BooleanProperty on) {
        Label lbl = new Label(name);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569; -fx-font-size: 11px;");

        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(6);

        // default OFF
        dot.setFill(javafx.scene.paint.Color.web("#94a3b8"));
        dot.setStroke(javafx.scene.paint.Color.web("#94a3b8"));

        on.addListener((obs, was, is) -> {
            if (is) {
                dot.setFill(javafx.scene.paint.Color.web("#22c55e"));
                dot.setStroke(javafx.scene.paint.Color.web("#22c55e"));
                dot.setEffect(new javafx.scene.effect.DropShadow(
                        10, javafx.scene.paint.Color.rgb(34, 197, 94, 0.8)
                ));
            } else {
                dot.setFill(javafx.scene.paint.Color.web("#94a3b8"));
                dot.setStroke(javafx.scene.paint.Color.web("#94a3b8"));
                dot.setEffect(null);
            }
        });

        VBox box = new VBox(4, lbl, dot);
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(30);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // helper last instruction view
    private static final String CHIP_STYLE = """
    -fx-font-size: 11px;
    -fx-text-fill: #0f172a;
    -fx-background-color: #e2e8f0;
    -fx-background-radius: 999;
    -fx-padding: 4 10 4 10;
    -fx-border-color: #cbd5e1;
    -fx-border-radius: 999;
""";

    private Label chip(String key, String value) {
        Label l = new Label();
        l.setStyle(CHIP_STYLE);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setWrapText(true);
        setChip(l, key, value);
        return l;
    }

    private void setChip(Label chip, String key, String value) {
        chip.setText(key + ": " + (value == null || value.isBlank() ? "—" : value));
    }

    private String extractTokenAfter(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = s.indexOf(' ', start);
        if (end < 0) end = s.length();
        return s.substring(start, end).trim();
    }

    // for bytes=86 42 (has spaces) -> take to end
    private String extractRestAfter(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        return s.substring(start).trim();
    }

    private String buildAsmLine(String mnem, String mode, String operandRaw) {
        if (mnem == null || mnem.isBlank()) return "—";

        String op = (operandRaw == null) ? "" : operandRaw.trim();
        if (op.isBlank() || op.equalsIgnoreCase("null")) return mnem;

        if ("IMMEDIATE".equalsIgnoreCase(mode)) {
            if (!op.startsWith("#")) op = "#" + op;
        }
        return mnem + " " + op;
    }

    /** Updates the last-instruction UI safely on FX thread. */
    private void applyLastInstructionToUi(
            String text,
            Label asmLineLbl,
            Label mnemChip, Label opcodeChip, Label modeChip,
            Label operandChip, Label bytesChip, Label eaChip,
            Label sizeChip, Label cyclesChip
    ) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applyLastInstructionToUi(
                    text, asmLineLbl,
                    mnemChip, opcodeChip, modeChip,
                    operandChip, bytesChip, eaChip,
                    sizeChip, cyclesChip
            ));
            return;
        }

        String s = (text == null) ? "" : text.trim();
        if (s.isEmpty()) {
            asmLineLbl.setText("—");
            setChip(mnemChip, "mnemonic", "—");
            setChip(opcodeChip, "opcode", "—");
            setChip(modeChip, "mode", "—");
            setChip(operandChip, "operand", "—");
            setChip(bytesChip, "bytes", "—");
            setChip(eaChip, "EA", "—");
            setChip(sizeChip, "size", "—");
            setChip(cyclesChip, "cycles", "—");
            return;
        }

        // expected: $E002 STA EXTENDED opcode=$00B7 size=3 cyc=5 operand=$0100 ea=null bytes=B7 01 00
        String[] parts = s.split("\\s+");
        String mnem = (parts.length >= 2) ? parts[1] : "—";
        String mode = (parts.length >= 3) ? parts[2] : "—";

        String opcode = extractTokenAfter(s, "opcode=");
        String size   = extractTokenAfter(s, "size=");
        String cyc    = extractTokenAfter(s, "cyc=");
        String operand= extractTokenAfter(s, "operand=");
        String ea     = extractTokenAfter(s, "ea=");
        String bytes  = extractRestAfter(s, "bytes=");

        // 1) Top line = only assembly-like instruction text
        asmLineLbl.setText(buildAsmLine(mnem, mode, operand));

        // 2) Chips (same style)
        setChip(mnemChip,   "mnemonic", mnem);
        setChip(opcodeChip, "opcode",   opcode);
        setChip(modeChip,   "mode",     mode);

        // operand chip: show immediate with #
        String operandDisplay = operand;
        if ("IMMEDIATE".equalsIgnoreCase(mode) && operandDisplay != null && !operandDisplay.startsWith("#")) {
            operandDisplay = "#" + operandDisplay;
        }
        setChip(operandChip, "operand", operandDisplay);

        setChip(bytesChip, "bytes", bytes);

        String eaDisplay = (ea == null || ea.equalsIgnoreCase("null")) ? "—" : ea;
        setChip(eaChip, "EA", eaDisplay);

        setChip(sizeChip, "size", size);
        setChip(cyclesChip, "cycles", cyc);
    }

    private Pane lastInstructionCard() {

        // --- Top line: "Last instruction: LDA #$42"
        Label title = new Label("Last instruction:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #0f172a;");

        Label asmLineLbl = new Label("—");
        asmLineLbl.setStyle("""
        -fx-font-family: 'Consolas';
        -fx-font-size: 13px;
        -fx-font-weight: bold;
        -fx-text-fill: #0f172a;
    """);
        asmLineLbl.setWrapText(true);

        HBox topLine = new HBox(8, title, asmLineLbl);
        topLine.setAlignment(Pos.CENTER_LEFT);

        // --- Chips (same style for all)
        Label mnemChip   = chip("mnemonic", "—");
        Label opcodeChip = chip("opcode", "—");
        Label modeChip   = chip("mode", "—");

        Label operandChip= chip("operand", "—");
        Label bytesChip  = chip("bytes", "—");
        Label eaChip     = chip("EA", "—");

        Label sizeChip   = chip("size", "—");
        Label cyclesChip = chip("cycles", "—");

        // --- Organized grid (not messy)
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        ColumnConstraints c2 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        c1.setHgrow(Priority.ALWAYS);
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0, c1, c2);

        grid.add(mnemChip,   0, 0);
        grid.add(opcodeChip, 1, 0);
        grid.add(modeChip,   2, 0);

        grid.add(operandChip,0, 1);
        grid.add(bytesChip,  1, 1);
        grid.add(eaChip,     2, 1);

        grid.add(sizeChip,   0, 2);
        grid.add(cyclesChip, 1, 2);

        VBox box = new VBox(10, topLine, grid);
        box.setStyle("""
        -fx-background-color: white;
        -fx-border-color: #cbd5e1;
        -fx-border-radius: 12;
        -fx-background-radius: 12;
        -fx-padding: 10;
    """);

        // ✅ Listener + init (NO .set(init) !!)
        controller.lastInstructionProperty().addListener((obs, oldV, newV) -> {
            applyLastInstructionToUi(
                    newV,
                    asmLineLbl,
                    mnemChip, opcodeChip, modeChip,
                    operandChip, bytesChip, eaChip,
                    sizeChip, cyclesChip
            );
        });

        // initialize once
        applyLastInstructionToUi(
                controller.lastInstructionProperty().get(),
                asmLineLbl,
                mnemChip, opcodeChip, modeChip,
                operandChip, bytesChip, eaChip,
                sizeChip, cyclesChip
        );

        return box;
    }
}
