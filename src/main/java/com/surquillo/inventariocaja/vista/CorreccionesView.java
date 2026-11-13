package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.*;

/** Nota consultable de devolución/cambio; las validaciones definitivas pertenecen al servicio. */
public final class CorreccionesView {
    private static final class Fila {
        final PendienteDevolucion pendiente;
        final TextField cantidad=Ui.campo("Cantidad");
        final CheckBox reingresar=new CheckBox();
        Fila(PendienteDevolucion pendiente){
            this.pendiente=pendiente;cantidad.setPrefWidth(100);cantidad.setAccessibleText("Cantidad a devolver: "+pendiente.item().descripcion());
            reingresar.setDisable(pendiente.item().sku().isEmpty());reingresar.setAccessibleText("Reingresar a stock: "+pendiente.item().descripcion());
        }
    }
    private final SesionBeta sesion;
    private final ComboBox<Documento.Tipo> tipoOriginal=new ComboBox<>(),tipoNuevo=new ComboBox<>();
    private final ComboBox<CorreccionVenta.Tipo> tipo=new ComboBox<>();
    private final TextField numeroOriginal=Ui.campo("Documento original"),nota=Ui.campo("Número de la nota"),motivo=Ui.campo("Motivo"),
        reconocido=Ui.campo("Importe reconocido (S/)"),numeroNuevo=Ui.campo("Nuevo documento");
    private final Label estado=new Label("Busca la operación original"),valor=new Label("Valor original: por calcular"),
        noDevuelto=new Label("Diferencia no devuelta: por calcular"),diferencia=new Label("Diferencia: por calcular");
    private final TableView<Fila> devoluciones=new TableView<>();
    private final TableView<ItemOperacion> nuevos=new TableView<>();
    private final TableView<CorreccionVenta> historial=new TableView<>();
    private final PagosForm pagos=new PagosForm();
    private final TextField sku=Ui.campo("SKU opcional"),almacen=Ui.campo("Almacén"),descripcion=Ui.campo("Descripción"),
        unidad=Ui.campo("Unidad"),cantidadNueva=Ui.campo("Cantidad"),precio=Ui.campo("Precio de reemplazo"),indicaciones=Ui.campo("Indicaciones");
    private Operacion original;
    private UUID solicitud=UUID.randomUUID(),correccion=UUID.randomUUID(),turnoConfirmacion;
    private long cursor;

    public CorreccionesView(SesionBeta sesion){this.sesion=sesion;}
    public Parent crear(){
        tipoOriginal.getItems().setAll(Documento.Tipo.values());tipoOriginal.setValue(Documento.Tipo.NOTA_VENTA);
        tipoNuevo.getItems().setAll(Documento.Tipo.values());tipoNuevo.setValue(Documento.Tipo.NOTA_VENTA);
        var documentos=new javafx.util.StringConverter<Documento.Tipo>(){
            @Override public String toString(Documento.Tipo t){return t==null?"":switch(t){case BOLETA->"Boleta";case FACTURA->"Factura";case NOTA_VENTA->"Nota de venta";};}
            @Override public Documento.Tipo fromString(String t){throw new UnsupportedOperationException();}
        };
        tipoOriginal.setConverter(documentos);tipoNuevo.setConverter(documentos);
        tipo.getItems().setAll(CorreccionVenta.Tipo.values());tipo.setValue(CorreccionVenta.Tipo.DEVOLUCION);
        tipo.setConverter(new javafx.util.StringConverter<>(){
            @Override public String toString(CorreccionVenta.Tipo t){return t==null?"":t==CorreccionVenta.Tipo.CAMBIO?"Cambio":"Devolución";}
            @Override public CorreccionVenta.Tipo fromString(String t){throw new UnsupportedOperationException();}
        });
        tipo.valueProperty().addListener((o,a,n)->actualizarRecibido());
        reconocido.textProperty().addListener((o,a,n)->actualizarRecibido());
        pagos.efectivoTextoProperty().addListener((o,a,n)->actualizarRecibido());
        nuevos.getItems().addListener((javafx.collections.ListChangeListener<ItemOperacion>)c->actualizarRecibido());
        actualizarRecibido();
        numeroOriginal.setOnAction(e->Ui.accion(this::buscar));
        devoluciones.getColumns().add(Ui.columna("Producto",f->f.pendiente.item().descripcion(),420));
        devoluciones.getColumns().add(Ui.columna("Pendiente de devolver",f->f.pendiente.cantidad().toPlainString()+" "+f.pendiente.item().unidad(),180));
        var columnaCantidad=new TableColumn<Fila,Fila>("Cantidad devuelta");columnaCantidad.setPrefWidth(160);
        columnaCantidad.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
        columnaCantidad.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(Fila fila,boolean vacia){super.updateItem(fila,vacia);setGraphic(vacia||fila==null?null:fila.cantidad);}
        });
        var columnaReingreso=new TableColumn<Fila,Fila>("Reingresar vendible");columnaReingreso.setPrefWidth(170);
        columnaReingreso.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
        columnaReingreso.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(Fila fila,boolean vacia){super.updateItem(fila,vacia);setGraphic(vacia||fila==null?null:fila.reingresar);}
        });
        devoluciones.getColumns().addAll(List.of(columnaCantidad,columnaReingreso));devoluciones.setPrefHeight(220);
        nuevos.getColumns().add(Ui.columna("Reemplazo",ItemOperacion::descripcion,360));
        nuevos.getColumns().add(Ui.columna("Cantidad",i->i.cantidad()+" "+i.unidad(),170));
        nuevos.getColumns().add(Ui.columna("Precio",ItemOperacion::precio,130));
        nuevos.getColumns().add(Ui.columna("Total",ItemOperacion::total,130));nuevos.setPrefHeight(170);
        almacen.setText("Surquillo");unidad.setText("unidad");cantidadNueva.setText("1");
        var entrada=new GridPane();entrada.setHgap(10);entrada.setVgap(8);
        entrada.addRow(0,new Label("SKU"),sku,Ui.boton("Consultar SKU",this::consultarSku),new Label("Almacén"),almacen);
        entrada.addRow(1,new Label("Descripción"),descripcion,new Label("Unidad"),unidad);
        entrada.addRow(2,new Label("Cantidad"),cantidadNueva,new Label("Precio"),precio);
        entrada.addRow(3,new Label("Indicaciones"),indicaciones);
        var reemplazos=new TitledPane("Productos de reemplazo",Ui.panel(new HBox(8,tipoNuevo,numeroNuevo),entrada,
            new HBox(8,Ui.boton("Añadir reemplazo",this::agregarReemplazo),Ui.boton("Quitar reemplazo",()->nuevos.getItems().remove(nuevos.getSelectionModel().getSelectedItem()))),nuevos));
        reemplazos.setExpanded(true);reemplazos.visibleProperty().bind(tipo.valueProperty().isEqualTo(CorreccionVenta.Tipo.CAMBIO));reemplazos.managedProperty().bind(reemplazos.visibleProperty());
        var datos=new GridPane();datos.setHgap(10);datos.setVgap(8);
        datos.addRow(0,new Label("Operación"),tipo,new Label("Nota"),nota);
        datos.addRow(1,new Label("Motivo"),motivo,new Label("Importe reconocido"),reconocido);
        var orientacion=new Label("Indica el valor acordado por lo devuelto: por ejemplo, de un material vendido a S/ 100 se pueden reintegrar S/ 90. En un cambio, ese importe se aplica como crédito a los reemplazos.");
        orientacion.setWrapText(true);
        var formulario=Ui.desplazar(Ui.panel(new Label("Devoluciones y cambios"),new HBox(8,tipoOriginal,numeroOriginal,Ui.boton("Buscar original",this::buscar)),estado,
            new Label("Indica la cantidad devuelta. Marca reingreso solo si el producto puede venderse de nuevo."),devoluciones,
            orientacion,datos,reemplazos,Ui.boton("Calcular importes",this::calcular),valor,noDevuelto,diferencia,
            new Label("Registra solo el dinero que entra o sale. Efectivo recibido se utiliza únicamente al cobrar un adicional."),
            pagos,new HBox(8,Ui.boton("Confirmar nota",this::guardar),Ui.boton("Nueva nota",this::nueva))));
        historial.getColumns().add(Ui.columna("Nota",CorreccionVenta::comprobante,150));
        historial.getColumns().add(Ui.columna("Tipo",c->c.tipo()==CorreccionVenta.Tipo.CAMBIO?"Cambio":"Devolución",120));
        historial.getColumns().add(Ui.columna("Valor original",CorreccionVenta::valorOriginal,140));
        historial.getColumns().add(Ui.columna("Reconocido",CorreccionVenta::reconocido,130));
        historial.getColumns().add(Ui.columna("No devuelto",CorreccionVenta::noDevuelto,130));
        historial.getColumns().add(Ui.columna("Diferencia",CorreccionVenta::diferencia,130));historial.setPrefHeight(350);
        var consulta=Ui.panel(new Label("Notas de la operación original buscada"),historial,new HBox(8,
            Ui.boton("Actualizar notas",()->{cursor=0;cargarHistorial();}),Ui.boton("Siguiente página",()->{if(!historial.getItems().isEmpty()){cursor=historial.getItems().getLast().secuencia();cargarHistorial();}}),
            Ui.boton("Ver nota",this::verNota)));
        var tabs=new TabPane(new Tab("Registrar nota",formulario),new Tab("Historial de notas",consulta));tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,n)->{if(n.intValue()==1&&original!=null)Ui.accion(()->{cursor=0;cargarHistorial();});});return tabs;
    }
    private void buscar(){
        var encontrada=sesion.ventas.buscarDocumento(tipoOriginal.getValue(),numeroOriginal.getText());
        if(original==null||!original.id().equals(encontrada.id()))limpiarFormulario();
        original=encontrada;devoluciones.getItems().setAll(sesion.correcciones.pendientes(original.id()).stream().map(Fila::new).toList());
        estado.setText(original.documento().tipo()+" "+original.documento().numero()+" · "+original.estado().name().replace('_',' ').toLowerCase(Locale.ROOT)+" · Total S/ "+original.total());
        cursor=0;cargarHistorial();
    }
    private List<DevolucionItem> seleccion(){
        var seleccion=new ArrayList<DevolucionItem>();
        for(var fila:devoluciones.getItems())if(!fila.cantidad.getText().isBlank())seleccion.add(new DevolucionItem(fila.pendiente.posicion(),Ui.numero(fila.cantidad.getText()),fila.reingresar.isSelected()));
        if(seleccion.isEmpty())throw new IllegalArgumentException("Indica al menos una cantidad devuelta");
        return List.copyOf(seleccion);
    }
    private BigDecimal totalNuevo(){return tipo.getValue()==CorreccionVenta.Tipo.CAMBIO?nuevos.getItems().stream().map(ItemOperacion::total).reduce(new BigDecimal("0.00"),BigDecimal::add):new BigDecimal("0.00");}
    private void actualizarRecibido(){
        boolean mostrar=false;
        try{
            var importe=Ui.opcional(reconocido.getText());var efectivo=Ui.opcional(pagos.efectivoTextoProperty().get());
            mostrar=tipo.getValue()==CorreccionVenta.Tipo.CAMBIO && importe!=null && totalNuevo().compareTo(importe)>0 && efectivo!=null && efectivo.signum()>0;
        }catch(IllegalArgumentException e){/* Una cifra incompleta mientras se escribe aún no requiere efectivo recibido. */}
        pagos.mostrarRecibido(mostrar);
    }
    private void calcular(){
        exigirOriginal();var maximo=sesion.correcciones.valorarDevolucion(original.id(),seleccion());valor.setText("Valor original de lo devuelto: S/ "+maximo);
        var importe=Ui.opcional(reconocido.getText());if(importe==null){noDevuelto.setText("Escribe el importe reconocido acordado con el cliente");diferencia.setText("Total de reemplazos: S/ "+totalNuevo());return;}
        if(importe.signum()<0||importe.compareTo(maximo)>0)throw new IllegalArgumentException("El importe reconocido debe estar entre cero y el valor original");
        noDevuelto.setText("Diferencia no devuelta: S/ "+maximo.subtract(importe)+" (se registra separada de ganancia)");
        var delta=totalNuevo().subtract(importe);diferencia.setText(delta.signum()>0?"Cobrar adicional: S/ "+delta:delta.signum()<0?"Reintegrar al cliente: S/ "+delta.abs():"Sin diferencia de dinero");
    }
    private void guardar(){
        exigirOriginal();var seleccion=seleccion();var importe=Ui.numero(reconocido.getText());var delta=totalNuevo().subtract(importe);
        boolean cambio=tipo.getValue()==CorreccionVenta.Tipo.CAMBIO;
        if(cambio&&(!sku.getText().isBlank()||!descripcion.getText().isBlank()||!precio.getText().isBlank()))throw new IllegalArgumentException("Añade el reemplazo que estás escribiendo antes de confirmar");
        if(turnoConfirmacion==null)turnoConfirmacion=sesion.exigirTurno();
        String accion=delta.signum()>0?"cobrar S/ "+delta:delta.signum()<0?"reintegrar S/ "+delta.abs():"no mover dinero";
        if(!Ui.confirmar("¿Guardar la nota "+nota.getText()+" y "+accion+" en el turno actual? Se conservará el cierre original."))return;
        var medios=pagos.valores();var entregado=delta.signum()>0?pagos.recibido():BigDecimal.ZERO;
        var resultado=sesion.correcciones.registrar(solicitud,correccion,original.id(),turnoConfirmacion,tipo.getValue(),nota.getText(),motivo.getText(),seleccion,importe,
            cambio?List.copyOf(nuevos.getItems()):List.of(),cambio?new Documento(tipoNuevo.getValue(),numeroNuevo.getText(),true):null,medios,entregado);
        var vuelto=delta.signum()>0?entregado.subtract(medios.getOrDefault(MedioPago.EFECTIVO,BigDecimal.ZERO)):BigDecimal.ZERO;
        limpiarFormulario();buscar();Ui.informar("Nota "+resultado.comprobante()+" guardada. Valor original S/ "+resultado.valorOriginal()+", reconocido S/ "+resultado.reconocido()+", no devuelto S/ "+resultado.noDevuelto()+"."+(vuelto.signum()>0?" Vuelto: S/ "+vuelto.toPlainString():""));
    }
    private void consultarSku(){
        var v=sesion.catalogo.buscarSku(sku.getText()).orElseThrow(()->new IllegalArgumentException("SKU no encontrado"));
        descripcion.setText(v.descripcion());unidad.setText(v.unidad());precio.setText(v.precioVenta()==null?"":v.precioVenta().toPlainString());
    }
    private void agregarReemplazo(){
        nuevos.getItems().add(new ItemOperacion(sku.getText(),sku.getText().isBlank()?"":almacen.getText(),descripcion.getText(),unidad.getText(),Ui.numero(cantidadNueva.getText()),Ui.numero(precio.getText()),indicaciones.getText()));
        limpiarReemplazo();
    }
    private void limpiarReemplazo(){sku.clear();descripcion.clear();precio.clear();indicaciones.clear();unidad.setText("unidad");cantidadNueva.setText("1");}
    private void limpiarFormulario(){
        solicitud=UUID.randomUUID();correccion=UUID.randomUUID();turnoConfirmacion=null;nota.clear();motivo.clear();reconocido.clear();numeroNuevo.clear();pagos.limpiar();nuevos.getItems().clear();limpiarReemplazo();
        valor.setText("Valor original: por calcular");noDevuelto.setText("Diferencia no devuelta: por calcular");diferencia.setText("Diferencia: por calcular");
    }
    private void nueva(){limpiarFormulario();if(original!=null)buscar();}
    private void exigirOriginal(){if(original==null)throw new IllegalArgumentException("Busca primero la operación original");}
    private void cargarHistorial(){exigirOriginal();historial.getItems().setAll(sesion.correcciones.listar(original.id(),cursor,100));}
    private void verNota(){
        var fila=historial.getSelectionModel().getSelectedItem();if(fila==null)throw new IllegalArgumentException("Selecciona una nota");
        var c=sesion.correcciones.consultar(fila.id());var texto=new StringBuilder("Nota "+c.comprobante()+" · "+c.tipo()+"\nMotivo: "+c.motivo()+"\nEncargado: "+c.encargado()+"\nFecha: "+c.fecha()+"\n");
        texto.append("Valor original: S/ ").append(c.valorOriginal()).append("\nReconocido: S/ ").append(c.reconocido()).append("\nNo devuelto: S/ ").append(c.noDevuelto())
            .append("\nReemplazos: S/ ").append(c.totalReemplazo()).append("\nDiferencia de dinero: S/ ").append(c.diferencia()).append("\nPagos: ").append(c.pagos());
        var op=sesion.ventas.consultar(c.original());
        for(var i:c.devueltos())texto.append("\n").append(i.cantidad()).append(" · ").append(op.items().get(i.posicion()).descripcion()).append(i.reingresar()?" · Reingresado":" · Sin reingreso");
        if(c.reemplazo()!=null)texto.append("\nDocumento de reemplazo: ").append(sesion.ventas.consultar(c.reemplazo()).documento().numero());
        Ui.informar(texto.toString());
    }
}
