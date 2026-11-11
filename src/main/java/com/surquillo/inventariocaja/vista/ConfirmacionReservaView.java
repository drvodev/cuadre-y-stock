package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.stock.Reserva;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import java.util.Objects;

/** Pregunta antes de registrar una reserva incompleta; no guarda datos. */
public final class ConfirmacionReservaView {
    private ConfirmacionReservaView() {}
    public static boolean confirmar(Window ventana, Reserva propuesta) {
        Objects.requireNonNull(propuesta);
        if (propuesta.suficiente()) return true;
        Alert dialogo = new Alert(propuesta.disponible().signum() == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.CONFIRMATION);
        if (ventana != null) dialogo.initOwner(ventana);
        dialogo.setTitle("Stock insuficiente");
        dialogo.setHeaderText("No hay stock suficiente para la separación");
        String detalle = "Solicitado: " + propuesta.solicitada().toPlainString() + "\nDisponible: "
                + propuesta.disponible().toPlainString() + "\nFaltante: " + propuesta.faltante().toPlainString();
        if (propuesta.disponible().signum() == 0) {
            dialogo.setContentText(detalle + "\nNo hay unidades disponibles para reservar.");
            Ui.tema(dialogo).showAndWait();
            return false;
        }
        dialogo.setContentText(detalle + "\n¿Desea separar solo las unidades disponibles?");
        return Ui.tema(dialogo).showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
}
