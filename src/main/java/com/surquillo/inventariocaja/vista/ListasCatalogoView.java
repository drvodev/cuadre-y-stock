package com.surquillo.inventariocaja.vista;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.servicio.CatalogoService;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

/** Alta, edición y desactivación paginadas; una opción no necesita tener artículos. */
public final class ListasCatalogoView {
    private final CatalogoService servicio;
    private final Runnable cambios;
    private final ComboBox<ListaCatalogo> lista=new ComboBox<>();
    private final TextField consulta=Ui.campo("Buscar en la lista"),nombre=Ui.campo("Nombre de la opción");
    private final CheckBox incluirInactivos=new CheckBox("Mostrar inactivas"),activo=new CheckBox("Opción activa");
    private final TableView<OpcionCatalogo> tabla=new TableView<>();
    private OpcionCatalogo seleccionada;
    private String cursor="";
    public ListasCatalogoView(CatalogoService servicio,Runnable cambios) { this.servicio=servicio;this.cambios=cambios; }
    public Parent crear() {
        lista.getItems().setAll(ListaCatalogo.values());lista.setValue(ListaCatalogo.CATEGORIA);activo.setSelected(true);
        tabla.getColumns().add(Ui.columna("Nombre",OpcionCatalogo::nombre,360));
        tabla.getColumns().add(Ui.columna("Estado",o->o.activo()?"Activa":"Inactiva",120));tabla.setPrefHeight(260);
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,n)->{if(n!=null){seleccionada=n;nombre.setText(n.nombre());activo.setSelected(n.activo());}});
        lista.setOnAction(e->Ui.accion(()->{limpiar();buscar();}));
        incluirInactivos.setOnAction(e->Ui.accion(this::buscar));consulta.setOnAction(e->Ui.accion(this::buscar));
        consulta.textProperty().addListener((o,a,n)->cursor="");
        var raiz=Ui.panel(new Label("Listas del catálogo"),
            new Label("Crea las categorías y opciones que necesites. Renombrarlas no cambia artículos ni ventas anteriores."),
            new HBox(8,lista,consulta,Ui.boton("Buscar",this::buscar),incluirInactivos),
            tabla,Ui.boton("Siguiente página",()->{if(!tabla.getItems().isEmpty()){cursor=tabla.getItems().getLast().clave();refrescar();}}),
            new HBox(8,nombre,activo,Ui.boton("Nueva opción",this::limpiar),Ui.boton("Guardar opción",this::guardar)));
        Ui.accion(this::buscar);return Ui.desplazar(raiz);
    }
    private void buscar() { cursor="";refrescar(); }
    private void refrescar() { tabla.getItems().setAll(servicio.listarOpciones(lista.getValue(),consulta.getText(),cursor,100,incluirInactivos.isSelected())); }
    private void guardar() {
        if(seleccionada==null) {
            if(!activo.isSelected())throw new IllegalArgumentException("Crea primero la opción activa; después puedes desactivarla");
            servicio.crearOpcion(lista.getValue(),nombre.getText());
        }else servicio.actualizarOpcion(new OpcionCatalogo(seleccionada.id(),seleccionada.lista(),nombre.getText(),activo.isSelected(),seleccionada.version()));
        limpiar();buscar();cambios.run();Ui.informar("Opción guardada.");
    }
    private void limpiar() { seleccionada=null;tabla.getSelectionModel().clearSelection();nombre.clear();activo.setSelected(true); }
}
