package com.surquillo.inventariocaja.vista;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.beans.property.ReadOnlyStringWrapper;
import java.math.BigDecimal;
import java.util.function.Function;

public final class Ui {
    private Ui() {}
    public static TextField campo(String pista) { var t=new TextField(); t.setPromptText(pista); return t; }
    public static BigDecimal numero(String texto) {
        try { return new BigDecimal(texto.strip().replace(',','.')); }
        catch(Exception e) { throw new IllegalArgumentException("Ingresa un número válido, sin separador de miles"); }
    }
    public static BigDecimal opcional(String texto) { return texto==null||texto.isBlank()?null:numero(texto); }
    public static void accion(Runnable tarea) {
        try { tarea.run(); }
        catch(Exception e) {
            var a=new Alert(Alert.AlertType.ERROR); a.setTitle("Revisar operación"); a.setHeaderText("No se completó la operación");
            a.setContentText(e.getMessage()==null?"Revisa los datos y la conexión.":e.getMessage()); a.showAndWait();
        }
    }
    public static boolean confirmar(String mensaje) {
        var a=new Alert(Alert.AlertType.CONFIRMATION,mensaje,ButtonType.YES,ButtonType.NO); a.setTitle("Confirmar"); a.setHeaderText(null);
        return a.showAndWait().filter(ButtonType.YES::equals).isPresent();
    }
    public static void informar(String mensaje) { var a=new Alert(Alert.AlertType.INFORMATION,mensaje,ButtonType.OK); a.setHeaderText(null); a.showAndWait(); }
    public static Button boton(String texto,Runnable tarea) { var b=new Button(texto); b.setOnAction(e->accion(tarea)); return b; }
    public static VBox panel(javafx.scene.Node... nodos) { var v=new VBox(12,nodos); v.setPadding(new Insets(18)); return v; }
    public static ScrollPane desplazar(javafx.scene.Node nodo) { var p=new ScrollPane(nodo); p.setFitToWidth(true); return p; }
    public static <T> TableColumn<T,String> columna(String titulo,Function<T,Object> lector,double ancho) {
        var c=new TableColumn<T,String>(titulo); c.setCellValueFactory(d->new ReadOnlyStringWrapper(String.valueOf(lector.apply(d.getValue())))); c.setPrefWidth(ancho); return c;
    }
}
