package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.controlador.StockController;
import com.surquillo.inventariocaja.excepcion.AplicacionException;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Vista de Stock. El controlador se proporciona al configurar la aplicación. */
public class StockView {
    private final StockController controlador;
    private final TextField sku = new TextField();
    private final TextField almacen = new TextField();
    private final TextField cantidad = new TextField();
    private final TextField minimo = Ui.campo("Mínimo para alertas");
    private final Label saldo = new Label("Sin consultar");
    private final TextArea movimientos = new TextArea();
    private UUID inicialId=UUID.randomUUID(),entradaId=UUID.randomUUID(),ajusteId=UUID.randomUUID();
    public StockView(StockController controlador) { this.controlador=Objects.requireNonNull(controlador); }

    public Parent crear() {
        almacen.setText("Surquillo");
        var formulario = new GridPane(); formulario.setHgap(10); formulario.setVgap(10);
        formulario.addRow(0,new Label("SKU"),sku,new Label("Almacén"),almacen);
        formulario.addRow(1,new Label("Cantidad"),cantidad);
        formulario.addRow(2,new Label("Stock mínimo"),minimo,Ui.boton("Guardar mínimo",()->{controlador.configurarMinimo(sku.getText(),almacen.getText(),Ui.numero(minimo.getText()));actualizar();}));
        var consultar=new Button("Consultar"); consultar.setOnAction(e->ejecutar(this::actualizar));
        var inicial=new Button("Stock inicial"); inicial.setOnAction(e->ejecutar(()->{
            controlador.iniciar(inicialId,sku.getText(),almacen.getText(),numero()); inicialId=UUID.randomUUID();cantidad.clear();actualizar();
        }));
        var compra=new Button("Entrada por compra"); compra.setOnAction(e->entrada(MovimientoStock.Tipo.COMPRA));
        var fabricacion=new Button("Entrada por fabricación"); fabricacion.setOnAction(e->entrada(MovimientoStock.Tipo.FABRICACION));
        var ajustar=new Button("Ajustar stock"); ajustar.setOnAction(e->{
            var motivo=new TextInputDialog(); motivo.setTitle("Ajuste de stock"); motivo.setHeaderText("Indica el motivo del ajuste");
            Ui.tema(motivo).showAndWait().ifPresent(texto->ejecutar(()->{
                controlador.ajustar(ajusteId,sku.getText(),almacen.getText(),numero(),texto); ajusteId=UUID.randomUUID();cantidad.clear();actualizar();
            }));
        });
        movimientos.setEditable(false); movimientos.setWrapText(true);
        var raiz=new VBox(12,new Label("Stock"),formulario,new HBox(8,consultar,inicial,compra,fabricacion,ajustar),saldo,new Label("Movimientos"),movimientos);
        raiz.setPadding(new Insets(16)); VBox.setVgrow(movimientos,Priority.ALWAYS); return raiz;
    }
    private void entrada(MovimientoStock.Tipo tipo) {
        var referencia=new TextInputDialog(); referencia.setTitle("Entrada de stock"); referencia.setHeaderText("Referencia de la entrada (opcional)");
        Ui.tema(referencia).showAndWait().ifPresent(texto->ejecutar(()->{
            controlador.entrada(entradaId,sku.getText(),almacen.getText(),tipo,numero(),texto); entradaId=UUID.randomUUID();cantidad.clear();actualizar();
        }));
    }
    private void actualizar() {
        var actual=controlador.consultar(sku.getText(),almacen.getText());
        saldo.setText(actual.map(s->"Físico: "+s.fisico().toPlainString()+" | Reservado: "+s.reservado().toPlainString()+" | Disponible: "+s.disponible().toPlainString()).orElse("Aún no hay stock inicial"));
        if(actual.isPresent()&&controlador.stockBajo(sku.getText(),almacen.getText()))saldo.setText(saldo.getText()+" · STOCK BAJO / AGOTADO");
        var texto=new StringBuilder();
        for (var m:controlador.historial(sku.getText(),almacen.getText()))
            texto.append(m.fecha()).append(" | ").append(m.tipo()).append(" | Cambio: ").append(m.cambioFisico().toPlainString()).append(" | ").append(m.motivo()).append('\n');
        movimientos.setText(texto.toString());
    }
    private BigDecimal numero() {
        try { return new BigDecimal(cantidad.getText().strip().replace(',','.')); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Ingresa una cantidad válida"); }
    }
    private void ejecutar(Runnable accion) {
        try { accion.run(); }
        catch (AplicacionException | IllegalArgumentException e) {
            var aviso=new Alert(Alert.AlertType.ERROR); aviso.setTitle("Stock"); aviso.setHeaderText("No se pudo completar la operación"); aviso.setContentText(e.getMessage()); Ui.tema(aviso).showAndWait();
        }
    }
}
