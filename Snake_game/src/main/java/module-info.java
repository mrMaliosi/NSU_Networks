module ru.nsu.ccfit.malinovskii {
    requires protobuf.java;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires java.net.http;
    //requires com.almasb.fxgl.all;

    opens ru.nsu.ccfit.malinovskii.Model to javafx.base;
    opens ru.nsu.ccfit.malinovskii.Model.Object to javafx.base;
    opens ru.nsu.ccfit.malinovskii.Model.Context to javafx.base;
    opens ru.nsu.ccfit.malinovskii.Model.Message to javafx.base;
    opens ru.nsu.ccfit.malinovskii to javafx.fxml;
    opens ru.nsu.ccfit.malinovskii.Controller to javafx.fxml;

    exports ru.nsu.ccfit.malinovskii.Model.Object;
    exports ru.nsu.ccfit.malinovskii.Model.Context;
    exports ru.nsu.ccfit.malinovskii.Model;
    exports ru.nsu.ccfit.malinovskii to javafx.graphics;
    exports ru.nsu.ccfit.malinovskii.Controller;
    exports ru.nsu.ccfit.malinovskii.proto;
}