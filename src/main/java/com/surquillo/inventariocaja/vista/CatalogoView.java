package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.stock.Variante;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class CatalogoView {
    private final SesionBeta sesion;
    private final TableView<Variante> tabla=new TableView<>();
    private final TextField buscar=Ui.campo("SKU, descripción, marca o presentación");
    private final TextField sku=Ui.campo("SKU único"),descripcion=Ui.campo("Descripción"),categoria=Ui.campo("Categoría"),marca=Ui.campo("Marca opcional"),presentacion=Ui.campo("Bolsa 25 kg, caja…"),tipo=Ui.campo("Modelo, color o tipo"),unidad=Ui.campo("bolsa, caja, unidad, metro…"),contenido=Ui.campo("Contenido opcional"),unidadContenido=Ui.campo("kg, m²…"),precio=Ui.campo("Precio de venta opcional"),costo=Ui.campo("Vacío si es desconocido");
    private final CheckBox fraccion=new CheckBox("Permite cantidades fraccionadas"),activo=new CheckBox("Artículo activo");
    private final ComboBox<Variante.Origen> origen=new ComboBox<>();
    private Variante seleccionado;
    private String cursor="";
    public CatalogoView(SesionBeta sesion) { this.sesion=sesion; }
    public Parent crear() {
        tabla.getColumns().add(Ui.columna("SKU",Variante::sku,150)); tabla.getColumns().add(Ui.columna("Descripción",Variante::descripcion,260));
        tabla.getColumns().add(Ui.columna("Unidad",Variante::unidad,100)); tabla.getColumns().add(Ui.columna("Presentación",Variante::presentacion,140));
        tabla.getColumns().add(Ui.columna("Precio",v->v.precioVenta()==null?"Por definir":v.precioVenta().toPlainString(),100));
        tabla.getColumns().add(Ui.columna("Estado",v->v.activo()?"Activo":"Inactivo",100)); tabla.setPrefHeight(270);
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,v)->{ if(v!=null) cargar(v); });
        origen.getItems().setAll(Variante.Origen.values()); origen.setValue(Variante.Origen.COMPRADO); activo.setSelected(true); unidad.setText("unidad");
        var form=new GridPane(); form.setHgap(12); form.setVgap(10);
        form.addRow(0,new Label("SKU"),sku,new Label("Descripción"),descripcion);
        form.addRow(1,new Label("Categoría"),categoria,new Label("Marca"),marca);
        form.addRow(2,new Label("Presentación"),presentacion,new Label("Tipo / modelo"),tipo);
        form.addRow(3,new Label("Unidad de stock"),unidad,new Label("Origen"),origen);
        form.addRow(4,new Label("Contenido de presentación"),contenido,new Label("Unidad del contenido"),unidadContenido);
        form.addRow(5,new Label("Precio de venta"),precio,new Label("Costo opcional"),costo);
        form.addRow(6,fraccion,activo);
        var status=new Label("Selecciona un artículo para editar. La unidad y precisión se conservan al editar.");
        var raiz=Ui.panel(new Label("Catálogo"),new HBox(10,buscar,Ui.boton("Buscar",()->{cursor="";refrescar();}),Ui.boton("Siguiente página",()->{ if(!tabla.getItems().isEmpty()) {cursor=tabla.getItems().get(tabla.getItems().size()-1).sku();refrescar();}})),tabla,status,form,new HBox(10,Ui.boton("Nuevo artículo",this::limpiar),Ui.boton("Guardar artículo",this::guardar)));
        Ui.accion(this::refrescar); return Ui.desplazar(raiz);
    }
    public void refrescar() { tabla.getItems().setAll(sesion.catalogo.buscar(buscar.getText(),cursor,100,true)); }
    private void guardar() {
        var v=new Variante(sku.getText(),descripcion.getText(),categoria.getText(),marca.getText(),presentacion.getText(),tipo.getText(),unidad.getText(),fraccion.isSelected(),origen.getValue(),Ui.opcional(contenido.getText()),unidadContenido.getText(),Ui.opcional(precio.getText()),Ui.opcional(costo.getText()),activo.isSelected(),seleccionado==null?0:seleccionado.version());
        if(seleccionado==null) sesion.catalogo.crear(v); else sesion.catalogo.actualizar(v);
        limpiar(); cursor="";refrescar(); Ui.informar("Artículo guardado.");
    }
    private void cargar(Variante v) {
        seleccionado=v; sku.setText(v.sku()); sku.setDisable(true); descripcion.setText(v.descripcion()); categoria.setText(v.categoria()); marca.setText(v.marca()); presentacion.setText(v.presentacion()); tipo.setText(v.tipo()); unidad.setText(v.unidad()); unidad.setDisable(true);
        fraccion.setSelected(v.permiteFraccion()); fraccion.setDisable(true); origen.setValue(v.origen()); contenido.setText(v.contenido()==null?"":v.contenido().toPlainString()); unidadContenido.setText(v.unidadContenido()); precio.setText(v.precioVenta()==null?"":v.precioVenta().toPlainString()); costo.setText(v.costo()==null?"":v.costo().toPlainString()); activo.setSelected(v.activo());
    }
    private void limpiar() {
        seleccionado=null; tabla.getSelectionModel().clearSelection();
        for(var t:new TextField[]{sku,descripcion,categoria,marca,presentacion,tipo,contenido,unidadContenido,precio,costo}) t.clear();
        unidad.setText("unidad"); sku.setDisable(false); unidad.setDisable(false); fraccion.setDisable(false);fraccion.setSelected(false);activo.setSelected(true);origen.setValue(Variante.Origen.COMPRADO);
    }
}
