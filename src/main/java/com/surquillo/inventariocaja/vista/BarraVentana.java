package com.surquillo.inventariocaja.vista;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Controles visibles de la ventana, compartidos por conexión y sesión principal. */
public final class BarraVentana {
    private BarraVentana() {}

    public static HBox crear(Stage escenario) {
        var minimizar=Ui.boton("Minimizar",()->escenario.setIconified(true));
        minimizar.setTooltip(new Tooltip("Ocultar la ventana en la barra de tareas"));
        var maximizar=Ui.boton("Maximizar",()->escenario.setMaximized(!escenario.isMaximized()));
        maximizar.textProperty().bind(Bindings.when(escenario.maximizedProperty()).then("Restaurar").otherwise("Maximizar"));
        maximizar.setTooltip(new Tooltip("Alternar entre ventana maximizada y tamaño anterior"));
        var cerrar=Ui.boton("Cerrar",()->escenario.fireEvent(new WindowEvent(escenario,WindowEvent.WINDOW_CLOSE_REQUEST)));
        cerrar.getStyleClass().add("cerrar-ventana");
        var espacio=new Region();HBox.setHgrow(espacio,Priority.ALWAYS);
        var barra=new HBox(8,new Label("Cuadre y Stock"),espacio,minimizar,maximizar,cerrar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(8,12,8,16));
        barra.getStyleClass().add("barra-ventana");
        return barra;
    }
}
