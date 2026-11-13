package com.surquillo.inventariocaja.vista;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.CompraRepository;
import com.surquillo.inventariocaja.controlador.CompraController;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.math.BigDecimal;

/** Registro explícito de recepción y pago único; no permite compras a crédito. */
public class CompraView {
    private final SesionBeta sesion;
    private final CompraController controlador;
    private final TextField proveedor=Ui.campo("Nombre o RUC"),numero=Ui.campo("Número del documento"),
        sku=Ui.campo("SKU opcional"),almacen=Ui.campo("Almacén"),descripcion=Ui.campo("Descripción del ítem"),
        unidad=Ui.campo("Unidad"),cantidad=Ui.campo("Cantidad recibida"),costo=Ui.campo("Vacío si se desconoce"),total=Ui.campo("Total recibido y pagado");
    private final ComboBox<Documento.Tipo> tipo=new ComboBox<>();
    private final ComboBox<MovimientoCaja.Fuente> fuente=new ComboBox<>();
    private final TableView<DetalleCompra> items=new TableView<>();
    private final TableView<CompraRepository.Resumen> historial=new TableView<>();
    private final PagosForm pagos=new PagosForm(false);
    private UUID solicitud=UUID.randomUUID(),compra=UUID.randomUUID();
    private long cursor;
    public CompraView(SesionBeta sesion){this.sesion=sesion;controlador=new CompraController(sesion.compras);}
    public Parent crear() {
        tipo.getItems().setAll(Documento.Tipo.values());tipo.setValue(Documento.Tipo.FACTURA);
        fuente.getItems().setAll(MovimientoCaja.Fuente.values());fuente.setValue(MovimientoCaja.Fuente.CAJA);
        almacen.setText("Surquillo");unidad.setText("unidad");cantidad.setText("1");
        items.getColumns().add(Ui.columna("SKU",DetalleCompra::sku,130));
        items.getColumns().add(Ui.columna("Descripción",DetalleCompra::descripcion,300));
        items.getColumns().add(Ui.columna("Recibido",i->i.cantidad().toPlainString()+" "+i.unidad(),150));
        items.getColumns().add(Ui.columna("Costo unitario",i->dinero(i.costoUnitario()),140));
        items.getColumns().add(Ui.columna("Subtotal",i->dinero(i.totalConocido()),130));items.setPrefHeight(200);
        var cabecera=new GridPane();cabecera.setHgap(10);cabecera.setVgap(10);
        cabecera.addRow(0,new Label("Proveedor"),proveedor,new Label("Documento"),tipo,numero);
        cabecera.addRow(1,new Label("Fuente del dinero"),fuente,new Label("Total (S/)"),total);
        var detalle=new GridPane();detalle.setHgap(10);detalle.setVgap(10);
        detalle.addRow(0,new Label("SKU"),sku,Ui.boton("Consultar SKU",this::consultarSku),new Label("Almacén"),almacen);
        detalle.addRow(1,new Label("Descripción"),descripcion,new Label("Unidad"),unidad);
        detalle.addRow(2,new Label("Cantidad"),cantidad,new Label("Costo unitario"),costo);
        var registro=Ui.desplazar(Ui.panel(new Label("Compra recibida y pagada"),
            new Label("Registra aquí documento, recepción y pago juntos. No repitas después la entrada ni el egreso manualmente."),
            cabecera,detalle,new HBox(8,Ui.boton("Añadir ítem",this::agregar),Ui.boton("Quitar ítem",()->items.getItems().remove(items.getSelectionModel().getSelectedItem()))),
            items,Ui.boton("Completar total con costos conocidos",this::calcularTotal),new Label("Pago completo"),pagos,
            Ui.boton("Confirmar recepción y pago",this::guardar)));
        historial.getColumns().add(Ui.columna("Proveedor",CompraRepository.Resumen::proveedor,260));
        historial.getColumns().add(Ui.columna("Documento",v->v.documento().tipo()+" "+v.documento().numero(),220));
        historial.getColumns().add(Ui.columna("Total",v->dinero(v.total()),140));
        historial.getColumns().add(Ui.columna("Fuente",CompraRepository.Resumen::fuente,110));historial.setPrefHeight(350);
        var consulta=Ui.panel(new Label("Compras confirmadas"),historial,new HBox(8,
            Ui.boton("Actualizar",()->{cursor=0;cargarHistorial();}),Ui.boton("Siguiente página",()->{if(!historial.getItems().isEmpty()){cursor=historial.getItems().getLast().secuencia();cargarHistorial();}}),
            Ui.boton("Ver detalle",this::verDetalle)));
        var tabs=new TabPane(new Tab("Registrar compra",registro),new Tab("Historial",consulta));tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedIndexProperty().addListener((o,a,n)->{if(n.intValue()==1)Ui.accion(()->{cursor=0;cargarHistorial();});});
        return tabs;
    }
    private static String dinero(BigDecimal n){return n==null?"Desconocido":"S/ "+n.setScale(2).toPlainString();}
    private void consultarSku() {
        var v=sesion.catalogo.buscarSku(sku.getText()).orElseThrow(()->new IllegalArgumentException("SKU no encontrado"));
        descripcion.setText(v.descripcion());unidad.setText(v.unidad());
    }
    private void agregar() {
        items.getItems().add(new DetalleCompra(sku.getText(),sku.getText().isBlank()?"":almacen.getText(),descripcion.getText(),
            unidad.getText(),Ui.numero(cantidad.getText()),Ui.opcional(costo.getText())));
        sku.clear();descripcion.clear();unidad.setText("unidad");cantidad.setText("1");costo.clear();
    }
    private void calcularTotal() {
        if(items.getItems().isEmpty() || items.getItems().stream().anyMatch(i->i.costoUnitario()==null))
            throw new IllegalArgumentException("Si desconoces algún costo, escribe el total del documento manualmente");
        total.setText(items.getItems().stream().map(DetalleCompra::totalConocido).reduce(new BigDecimal("0.00"),BigDecimal::add).toPlainString());
    }
    private void guardar() {
        UUID turno=sesion.exigirTurno();
        if(!sku.getText().isBlank() || !descripcion.getText().isBlank() || !costo.getText().isBlank())
            throw new IllegalArgumentException("Añade el ítem que estás escribiendo antes de confirmar");
        if(!Ui.confirmar("¿Confirmar la compra recibida y su pago completo? Se guardarán documento, entradas y egreso juntos."))return;
        var resultado=controlador.registrar(solicitud,compra,turno,proveedor.getText(),new Documento(tipo.getValue(),numero.getText(),true),fuente.getValue(),
            Ui.numero(total.getText()),List.copyOf(items.getItems()),pagos.valores());
        solicitud=UUID.randomUUID();compra=UUID.randomUUID();items.getItems().clear();proveedor.clear();numero.clear();total.clear();pagos.limpiar();
        Ui.informar("Compra "+resultado.documento().numero()+" confirmada. No repitas su entrada ni su pago.");
    }
    private void cargarHistorial(){historial.getItems().setAll(controlador.listar(cursor,100));}
    private void verDetalle() {
        var fila=historial.getSelectionModel().getSelectedItem();if(fila==null)throw new IllegalArgumentException("Selecciona una compra");
        var c=controlador.consultar(fila.id());var texto=new StringBuilder(c.proveedor()+" · "+c.documento().numero()+"\n");
        for(var i:c.items())texto.append(i.cantidad()).append(" ").append(i.unidad()).append(" · ").append(i.descripcion()).append(" · costo ").append(dinero(i.costoUnitario())).append('\n');
        texto.append("Total: ").append(dinero(c.total())).append("\nPagos: ").append(c.pagos());Ui.informar(texto.toString());
    }
}
