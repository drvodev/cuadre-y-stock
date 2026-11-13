package com.surquillo.inventariocaja.vista;
import com.surquillo.inventariocaja.modelo.stock.AtributosCatalogo;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import java.util.*;

/** Editor sencillo de características descriptivas y filtros; no interpreta medidas. */
public final class AtributosForm {
    private final TextField nombre=Ui.campo("Característica: color, medida, modelo…"),valor=Ui.campo("Valor");
    private final TableView<Map.Entry<String,String>> tabla=new TableView<>();
    private final Map<String,String> datos=new TreeMap<>();
    public Parent crear() {
        tabla.getColumns().add(Ui.columna("Característica",Map.Entry::getKey,180));
        tabla.getColumns().add(Ui.columna("Valor",Map.Entry::getValue,300));tabla.setPrefHeight(150);
        return Ui.panel(new HBox(8,nombre,valor,Ui.boton("Añadir",this::agregar)),tabla,
            Ui.boton("Quitar seleccionada",()->{var fila=tabla.getSelectionModel().getSelectedItem();if(fila!=null){datos.remove(fila.getKey());refrescar();}}));
    }
    private void agregar() {
        var una=AtributosCatalogo.normalizar(Collections.singletonMap(nombre.getText(),valor.getText()));
        var candidata=new LinkedHashMap<>(datos);
        for(var e:una.entrySet()) {
            if(candidata.containsKey(e.getKey()))throw new IllegalArgumentException("Quita la característica anterior antes de sustituirla");
            candidata.put(e.getKey(),e.getValue());
        }
        var validadas=AtributosCatalogo.normalizar(candidata);datos.clear();datos.putAll(validadas);
        nombre.clear();valor.clear();refrescar();
    }
    public Map<String,String> valores() {
        if(!nombre.getText().isBlank() || !valor.getText().isBlank())agregar();
        return AtributosCatalogo.normalizar(datos);
    }
    public void cargar(Map<String,String> valores) { datos.clear();datos.putAll(valores);nombre.clear();valor.clear();refrescar(); }
    private void refrescar() { tabla.getItems().setAll(datos.entrySet().stream().map(e->Map.entry(e.getKey(),e.getValue())).toList()); }
}
