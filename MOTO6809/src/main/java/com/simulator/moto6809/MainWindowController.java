package com.simulator.moto6809;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class MainWindowController
{
    @FXML private TextArea outputBox;
    boolean isCompiled = false;
    @FXML private TextArea codeArea;

    @FXML
    protected void execute()
    {
        if (!isCompiled)
            compile();

        String result = "";
        for (int i = 0; i < 10; i++)
        {
            result += "Code execute instruction " + i + "\n";
        }

        outputBox.setText(result);
    }

    @FXML
    protected void compile()
    {
        String text = codeArea.getText();
        outputBox.setText("Code compile \n" + text);
        isCompiled = true;
    }
}