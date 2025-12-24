module com.simulator.moto6809 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;
    requires java.logging;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires javafx.graphics;


    opens com.simulator.moto6809 to javafx.fxml;
    exports com.simulator.moto6809;
}