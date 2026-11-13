package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.MedioPago;
import javafx.scene.layout.GridPane;
import javafx.scene.control.*;
import java.math.BigDecimal;
import java.util.*;

public class PagosForm extends GridPane {
    private final Map<MedioPago,TextField> campos=new EnumMap<>(MedioPago.class);
    private final TextField recibido=Ui.campo("Vacío = pago efectivo");
    private final Label etiquetaRecibido=new Label("Efectivo recibido");
    public PagosForm() { this(true); }
    public PagosForm(boolean incluirRecibido) {
        setHgap(10); setVgap(8); int fila=0;
        for(var medio:MedioPago.values()) { var t=Ui.campo("0,00"); campos.put(medio,t); addRow(fila++,new Label(medio.name()),t); }
        if (incluirRecibido) addRow(fila,etiquetaRecibido,recibido);
    }
    public Map<MedioPago,BigDecimal> valores() {
        Map<MedioPago,BigDecimal> valores=new EnumMap<>(MedioPago.class);
        campos.forEach((medio,t)->{ if(!t.getText().isBlank()) { var n=Ui.numero(t.getText()); if(n.signum()<0) throw new IllegalArgumentException("El pago no puede ser negativo"); if(n.signum()>0) valores.put(medio,n); } });
        return Map.copyOf(valores);
    }
    public BigDecimal recibido() { return recibido.getText().isBlank()?valores().getOrDefault(MedioPago.EFECTIVO,BigDecimal.ZERO):Ui.numero(recibido.getText()); }
    public void limpiar() { campos.values().forEach(TextField::clear); recibido.clear(); }
    public javafx.beans.property.ReadOnlyStringProperty efectivoTextoProperty(){return campos.get(MedioPago.EFECTIVO).textProperty();}
    public void mostrarRecibido(boolean mostrar){
        etiquetaRecibido.setVisible(mostrar);etiquetaRecibido.setManaged(mostrar);
        recibido.setVisible(mostrar);recibido.setManaged(mostrar);
    }
}
