package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.repositorio.ListaStockRepository;
import com.surquillo.inventariocaja.repositorio.ListaStockRepository.Articulo;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;

public class ListaStockView {
    private final SesionBeta sesion;
    private final ListaStockRepository lista;
    private final TableView<Articulo> tabla = new TableView<>();
    private final TextField buscar = Ui.campo("SKU o producto");
    private final TextField almacen = Ui.campo("Almacén");
    private final ComboBox<String> precio = new ComboBox<>();
    private final ComboBox<ListaStockRepository.Estado> estado=new ComboBox<>();
    private final Button mas = new Button("Mostrar más");
    private String cursor = "";
    public ListaStockView(SesionBeta sesion) {
        this.sesion = sesion;
        lista = new ListaStockRepository(sesion.ds);
    }
    public Parent crear() {
        var titulo = new Label("Stock"); titulo.getStyleClass().add("page-title");
        almacen.setText("Surquillo"); precio.getItems().setAll("Precio sugerido", "Costo"); precio.getSelectionModel().selectFirst();
        estado.getItems().setAll(ListaStockRepository.Estado.values());estado.setValue(ListaStockRepository.Estado.TODOS);
        estado.setOnAction(e->Ui.accion(this::refrescar));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.getColumns().add(Ui.columna("SKU", Articulo::sku, 180));
        tabla.getColumns().add(Ui.columna("Producto", Articulo::producto, 420));
        tabla.getColumns().add(Ui.columna("Unidad", Articulo::unidad, 120));
        tabla.getColumns().add(Ui.columna("Disponibilidad", a -> a.disponible() == null ? "Sin stock inicial" : a.disponible().stripTrailingZeros().toPlainString(), 150));
        var columnaPrecio = Ui.<Articulo>columna("", a -> soles(esCosto() ? a.costo() : a.sugerido()), 150);
        columnaPrecio.textProperty().bind(precio.valueProperty()); tabla.getColumns().add(columnaPrecio);
        precio.setOnAction(e -> tabla.refresh());
        tabla.setPrefHeight(320); tabla.setPlaceholder(new Label("Sin productos para esta búsqueda."));
        var editar = Ui.boton("Editar precio", this::editar);
        editar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        var gestion = new TitledPane("Movimientos y ajustes de stock", new StockView(sesion.stock).crear());
        gestion.setExpanded(false);
        buscar.setOnAction(e -> Ui.accion(this::refrescar)); almacen.setOnAction(e -> Ui.accion(this::refrescar));
        mas.setOnAction(e -> Ui.accion(() -> cargar(true)));
        var filtros = new FlowPane(10, 8, buscar, new Label("Almacén"), almacen, Ui.boton("Buscar", this::refrescar), estado, precio);
        refrescar();
        return Ui.desplazar(Ui.panel(titulo, filtros, tabla, new FlowPane(8, 8, editar, mas), gestion));
    }
    private boolean esCosto() { return precio.getSelectionModel().getSelectedIndex() == 1; }
    private static String soles(BigDecimal n) { return n == null ? "Por definir" : "S/ " + n.setScale(2).toPlainString(); }
    public void refrescar() { cargar(false); }
    private String textoAplicado, almacenAplicado;
    private ListaStockRepository.Estado estadoAplicado;
    private void cargar(boolean continuar) {
        String texto = buscar.getText().strip(), ubicacion = almacen.getText().strip();
        if (ubicacion.isEmpty()) throw new IllegalArgumentException("Indica el almacén.");
        boolean anexar = continuar && texto.equals(textoAplicado) && ubicacion.equals(almacenAplicado) && estado.getValue()==estadoAplicado;
        var resultados = lista.buscar(ubicacion, texto, anexar ? cursor : "",estado.getValue());
        if (!anexar) tabla.getItems().clear();
        int cantidad = Math.min(100, resultados.size());
        tabla.getItems().addAll(resultados.subList(0, cantidad));
        if (cantidad > 0) cursor = resultados.get(cantidad - 1).sku();
        textoAplicado = texto; almacenAplicado = ubicacion; estadoAplicado=estado.getValue(); mas.setVisible(resultados.size() > cantidad); mas.setManaged(mas.isVisible());
    }
    private void editar() {
        var articulo = tabla.getSelectionModel().getSelectedItem();
        var v = sesion.catalogo.buscarSku(articulo.sku()).orElseThrow();
        if (v.version() != articulo.version()) throw new IllegalArgumentException("El producto cambió. Pulsa Buscar para actualizar.");
        BigDecimal previo = esCosto() ? v.costo() : v.precioVenta();
        var dialogo = new TextInputDialog(previo == null ? "" : previo.toPlainString());
        dialogo.setTitle("Editar precio"); dialogo.setHeaderText(v.descripcion()); dialogo.setContentText(precio.getValue() + " (S/)");
        Ui.tema(dialogo).showAndWait().ifPresent(texto -> {
            var nuevo = Ui.opcional(texto);
            sesion.catalogo.actualizar(new Variante(v.sku(),v.descripcion(),v.categoria(),v.marca(),v.presentacion(),v.tipo(),
                    v.unidad(),v.permiteFraccion(),v.origen(),v.contenido(),v.unidadContenido(),esCosto()?v.precioVenta():nuevo,
                    esCosto()?nuevo:v.costo(),v.activo(),v.version(),v.atributos()));
            refrescar();
        });
    }
}
