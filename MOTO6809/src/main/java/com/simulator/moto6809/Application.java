package com.simulator.moto6809;

import com.simulator.moto6809.UI.MainView;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Application extends javafx.application.Application {

    private MainView view;

    @Override
    public void start(Stage stage) {
        view = new MainView();

        Scene scene = new Scene(view.getRoot(), 1450, 900);

        var css = Application.class.getResource("/com/simulator/moto6809/UI/BreakPoint.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        else System.err.println("CSS not found: /com/simulator/moto6809/UI/BreakPoint.css");

        stage.setTitle("Moto 6809 Simulator");
        stage.setScene(scene);

        stage.setMinWidth(1450);  // empêche fenêtre trop petite
        stage.setMinHeight(900);
        stage.show();

        view.startUiPump();

        stage.setOnCloseRequest(e -> {
            if (view != null) view.controller().shutdown();
        });
    }

    @Override
    public void stop() {
        if (view != null) view.controller().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}



/*package com.simulator.moto6809;
import com.simulator.moto6809.UI.MainView;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Application extends javafx.application.Application {

    @Override
    public void start(Stage stage) {

        MainView view = new MainView();

        Scene scene = new Scene(view.getRoot(), 1400, 900);

        stage.setTitle("Moto 6809 Simulator");
        stage.setScene(scene);
        stage.show();

        view.startUiPump(); // AnimationTimer
    }

    @Override
    public void stop() {
        // propre shutdown si tu veux (MainView le fait déjà)
    }

    public static void main(String[] args) {
        launch(args);
    }
}*/
