package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;

public class VentaView {
    private final SesionBeta sesion;
    private final TableView<ItemOperacion> items=new TableView<>();
    private final ComboBox<Operacion.Modalidad> modo=new ComboBox<>();
    private final ComboBox<Documento.Tipo> documento=new ComboBox<>();
    private final TextField numero=Ui.campo("Número manual del documento"),prefijo=Ui.campo("Prefijo para sugerir, p. ej. B"),cliente=Ui.campo("Cliente (obligatorio en separación)"),contacto=Ui.campo("Contacto opcional");
    private final TextField sku=Ui.campo("Opcional para ítems libres"),almacen=Ui.campo("Almacén"),descripcion=Ui.campo("Descripción del artículo"),unidad=Ui.campo("unidad, bolsa, caja…"),cantidad=Ui.campo("Cantidad"),precio=Ui.campo("Precio unitario"),indicaciones=Ui.campo("Indicaciones adicionales opcionales");
    private final Label total=new Label("Total S/ 0,00");
    private final PagosForm pagos=new PagosForm();
    private UUID solicitud=UUID.randomUUID(),operacion=UUID.randomUUID();
    private Map<Integer,BigDecimal> aceptadas;
    private String sugerenciaActual;
    public VentaView(SesionBeta s) { sesion=s; }
    public Parent crear() {
        modo.getItems().setAll(Operacion.Modalidad.values());modo.setValue(Operacion.Modalidad.VENTA);documento.getItems().setAll(Documento.Tipo.values());documento.setValue(Documento.Tipo.BOLETA);prefijo.setText("B");almacen.setText("Surquillo");unidad.setText("unidad");cantidad.setText("1");
        modo.setOnAction(e->{if(modo.getValue()==Operacion.Modalidad.SEPARACION){documento.setValue(Documento.Tipo.NOTA_VENTA);prefijo.setText("N");}else{documento.setValue(Documento.Tipo.BOLETA);prefijo.setText("B");}});
        items.getColumns().add(Ui.columna("SKU",ItemOperacion::sku,130));items.getColumns().add(Ui.columna("Descripción",ItemOperacion::descripcion,260));items.getColumns().add(Ui.columna("Cantidad",ItemOperacion::cantidad,90));items.getColumns().add(Ui.columna("Precio",ItemOperacion::precio,90));items.getColumns().add(Ui.columna("Importe",ItemOperacion::total,100));items.setPrefHeight(220);
        var form=new GridPane();form.setHgap(10);form.setVgap(10);
        form.addRow(0,new Label("SKU"),sku,Ui.boton("Buscar en catálogo",this::buscar),new Label("Almacén"),almacen);
        form.addRow(1,new Label("Descripción"),descripcion,new Label("Unidad"),unidad);
        form.addRow(2,new Label("Cantidad"),cantidad,new Label("Precio"),precio);
        form.addRow(3,new Label("Indicaciones"),indicaciones);
        return Ui.desplazar(Ui.panel(new Label("Nueva venta o separación"),new HBox(10,modo,documento,prefijo,Ui.boton("Sugerir número",()->{sugerenciaActual=sesion.ventas.sugerirNumero(documento.getValue(),prefijo.getText()).orElseThrow(()->new IllegalArgumentException("Ingresa el primer número manualmente"));numero.setText(sugerenciaActual);})),numero,cliente,contacto,form,new HBox(10,Ui.boton("Añadir ítem",this::agregar),Ui.boton("Quitar seleccionado",()->{items.getItems().remove(items.getSelectionModel().getSelectedItem());aceptadas=null;actualizar();})),items,total,new Label("Pagos aplicados · en separación registra solo el abono de este momento"),pagos,new HBox(10,Ui.boton("Confirmar operación",this::confirmar),Ui.boton("Nueva operación",()->{if(Ui.confirmar("¿Limpiar el formulario? Si hubo un error de conexión, revisa primero Operaciones para comprobar si se guardó."))limpiar();}))));
    }
    private void buscar() {
        var v=sesion.catalogo.buscarSku(sku.getText()).orElseThrow(()->new IllegalArgumentException("SKU no encontrado. Puedes usar una línea libre dejando SKU vacío."));
        if(!v.activo())throw new IllegalArgumentException("El artículo está inactivo");sku.setText(v.sku());descripcion.setText(v.descripcion());unidad.setText(v.unidad());precio.setText(v.precioVenta()==null?"":v.precioVenta().toPlainString());
    }
    private void agregar() {
        items.getItems().add(new ItemOperacion(sku.getText(),almacen.getText(),descripcion.getText(),unidad.getText(),Ui.numero(cantidad.getText()),Ui.numero(precio.getText()),indicaciones.getText()));
        sku.clear();descripcion.clear();precio.clear();indicaciones.clear();cantidad.setText("1");aceptadas=null;actualizar();
    }
    private void actualizar(){total.setText("Total S/ "+items.getItems().stream().map(ItemOperacion::total).reduce(new BigDecimal("0.00"),BigDecimal::add).toPlainString());}
    private void confirmar() {
        if(items.getItems().isEmpty())throw new IllegalArgumentException("Añade al menos un ítem");
        UUID turno=sesion.exigirTurno();
        if(modo.getValue()==Operacion.Modalidad.SEPARACION && aceptadas==null) {
            Map<ClaveStock,BigDecimal> disponibles=new HashMap<>(); var decisiones=new HashMap<Integer,BigDecimal>();
            for(int i=0;i<items.getItems().size();i++) {
                var item=items.getItems().get(i);if(item.sku().isBlank())continue;var clave=new ClaveStock(item.sku(),item.almacen());var saldo=sesion.inventario.consultar(clave);if(saldo.isEmpty())continue;
                BigDecimal disponible=disponibles.getOrDefault(clave,saldo.get().disponible());BigDecimal ofrecida=disponible.min(item.cantidad());
                if(ofrecida.compareTo(item.cantidad())<0&&!Ui.confirmar(item.descripcion()+"\nSolicitado: "+item.cantidad().toPlainString()+"\nDisponible: "+ofrecida.toPlainString()+"\nFaltante: "+item.cantidad().subtract(ofrecida).toPlainString()+"\n¿Registrar la separación reservando solo lo disponible?"))return;
                decisiones.put(i,ofrecida);disponibles.put(clave,disponible.subtract(ofrecida));
            }
            aceptadas=Map.copyOf(decisiones);
        }
        var importes=pagos.valores();var recibido=pagos.recibido();
        sesion.ventas.registrar(solicitud,operacion,turno,modo.getValue(),new Documento(documento.getValue(),numero.getText(),!numero.getText().equals(sugerenciaActual)),cliente.getText(),contacto.getText(),List.copyOf(items.getItems()),importes,recibido,aceptadas==null?Map.of():aceptadas);
        String guardado=numero.getText();var vuelto=recibido.subtract(importes.getOrDefault(MedioPago.EFECTIVO,BigDecimal.ZERO));limpiar();Ui.informar("Operación "+guardado+" guardada. Vuelto S/ "+vuelto.toPlainString()+". Puedes consultarla en Operaciones.");
    }
    private void limpiar(){items.getItems().clear();numero.clear();cliente.clear();contacto.clear();pagos.limpiar();aceptadas=null;sugerenciaActual=null;solicitud=UUID.randomUUID();operacion=UUID.randomUUID();actualizar();}
}
