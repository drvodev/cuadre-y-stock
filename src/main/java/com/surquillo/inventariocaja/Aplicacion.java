package com.surquillo.inventariocaja;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Punto de entrada visual provisional de la beta. */
public class Aplicacion extends Application {
    @Override
    public void start(Stage escenario) {
        var mensaje = new Label("Stock y cuadre de caja — estructura inicial en desarrollo");
        escenario.setTitle("Inventario y caja - Surquillo");
        escenario.setScene(new Scene(new StackPane(mensaje), 800, 500));
        escenario.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
