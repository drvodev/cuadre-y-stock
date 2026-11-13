package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.TurnoRepository;
import com.surquillo.inventariocaja.repositorio.LibroCajaRepository;
import javafx.scene.Parent;
import javafx.scene.Node;
import java.util.stream.Collectors;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class CajaView {
    private final SesionBeta sesion;
    private final TextField nombre=Ui.campo("Encargado del turno"),fondo=Ui.campo("0,00"),caja=Ui.campo("Nombre de caja");
    private final DatePicker fecha=new DatePicker();
    private final ComboBox<Turno> turnos=new ComboBox<>();
    private final Label resumen=new Label("Abre un turno para comenzar");
    private record Fila(LibroCajaRepository.Documento documento, String comprobante, String descripcion, String pago, String ganancia) {}
    private final TableView<Fila> libro = new TableView<>();
    private final LibroCajaRepository documentos;
    private final Label cierreGuardado = new Label();
    private TitledPane abrirTurno, conteo, tienda, personal, otros;
    private boolean cargando;
    private final TextField contado=Ui.campo("Efectivo contado"),firma=Ui.campo("Firma del cierre"),nota=Ui.campo("Observación de diferencias");
    private final Map<MedioPago,List<TextField>> digitales=new EnumMap<>(MedioPago.class);
    private TurnoRepository.Estado mostrado;
    private UUID aperturaId=UUID.randomUUID(),turnoNuevo=UUID.randomUUID(),cierreId=UUID.randomUUID();
    public CajaView(SesionBeta s) { sesion=s; documentos=new LibroCajaRepository(s.ds); }
    public Parent crear() { return crear(null, null); }
    public Parent crear(Runnable nuevaVenta, Runnable registrarAbono) {
        caja.setText("Surquillo"); fecha.setValue(LocalDate.now(sesion.reloj)); fondo.setText("0");
        turnos.setPrefWidth(620); turnos.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Turno t) { return t==null?"":t.fechaOperativa()+" · "+t.caja()+" · "+t.encargado()+" · "+(t.cerrado()?"Cerrado":"Abierto"); }
            public Turno fromString(String s) { return null; }
        });
        turnos.setOnAction(e->{var t=turnos.getValue();if(!cargando && t!=null){sesion.turno.set(t.id());Ui.accion(this::refrescar);}});
        var apertura=new GridPane(); apertura.setHgap(10);apertura.setVgap(10);apertura.addRow(0,new Label("Caja"),caja,new Label("Fecha operativa"),fecha);apertura.addRow(1,new Label("Encargado"),nombre,new Label("Fondo inicial"),fondo);
        var conciliacion=new GridPane(); conciliacion.setHgap(10);conciliacion.setVgap(8);conciliacion.addRow(0,new Label("Medio"),new Label("Cobros verificados"),new Label("Devoluciones"),new Label("Egresos"),new Label("Otros ingresos"));
        int fila=1;for(var medio:MedioPago.values())if(medio!=MedioPago.EFECTIVO){var campos=List.of(Ui.campo("0"),Ui.campo("0"),Ui.campo("0"),Ui.campo("0"));digitales.put(medio,campos);conciliacion.addRow(fila++,new Label(etiqueta(medio)),campos.get(0),campos.get(1),campos.get(2),campos.get(3));}
        var titulo = new Label("Cierre de caja");
        titulo.getStyleClass().add("page-title");
        abrirTurno = seccion("Abrir un turno", Ui.panel(apertura, Ui.boton("Abrir turno", this::abrir)));
        libro.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        columna("Boleta / factura / nota", Fila::comprobante, 200);
        columna("Descripción", Fila::descripcion, 430);
        columna("Pago", Fila::pago, 240);
        columna("Ganancia", Fila::ganancia, 145);
        libro.setPrefHeight(275);
        libro.setPlaceholder(new Label("Los movimientos del turno aparecerán aquí."));
        var editar = Ui.boton("Anotar ganancia", this::editarGanancia);
        editar.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            var seleccion = libro.getSelectionModel().getSelectedItem();
            return seleccion == null || seleccion.documento() == null;
        }, libro.getSelectionModel().selectedItemProperty()));
        tienda = movimiento("Gastos de tienda o compras", "Gasto de tienda", "Compra");
        personal = movimiento("Pagos de sueldo o comisiones", "Sueldo", "Comisión");
        otros = movimiento("Otros movimientos", "Retiro", "Otro ingreso");
        var cerrar = Ui.boton("Confirmar cierre", this::cerrar);
        cerrar.getStyleClass().add("primary-action");
        var datos = new GridPane();
        datos.setVgap(8); datos.setHgap(12);
        datos.addRow(0, new Label("Efectivo contado (S/)"), contado, new Label("Firma"), firma);
        datos.addRow(1, new Label("Observación"), nota);
        conteo = seccion("Contar y cerrar caja", Ui.panel(datos, new Label("Verifica los medios con actividad"), conciliacion, cerrar));
        var acciones = new FlowPane(8, 8);
        if (nuevaVenta != null) acciones.getChildren().add(Ui.boton("Registrar venta", nuevaVenta));
        if (registrarAbono != null) acciones.getChildren().add(Ui.boton("Registrar abono", registrarAbono));
        acciones.getChildren().add(editar);
        var raiz = Ui.panel(titulo, new FlowPane(8, 8, turnos, Ui.boton("Actualizar turno", this::cargarTurnos)),
                abrirTurno, resumen, libro, acciones, tienda, personal, otros, conteo, cierreGuardado);
        cargarTurnos();
        return Ui.desplazar(raiz);
    }
    private static TitledPane seccion(String titulo, Node contenido) {
        var panel = new TitledPane(titulo, contenido);
        panel.setExpanded(false);
        return panel;
    }
    private void columna(String nombre, java.util.function.Function<Fila,Object> valor, double ancho) {
        var col = Ui.columna(nombre, valor, ancho);
        col.setReorderable(false);
        col.setCellFactory(c -> new TableCell<>() {
            final javafx.scene.text.Text texto = new javafx.scene.text.Text();
            { texto.getStyleClass().add("ledger-text"); texto.wrappingWidthProperty().bind(javafx.beans.binding.Bindings.max(1, col.widthProperty().subtract(24))); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                texto.setText(empty ? "" : item);
                setGraphic(empty ? null : texto);
            }
        });
        libro.getColumns().add(col);
    }
    private TitledPane movimiento(String titulo, String... opciones) {
        var concepto = new ComboBox<String>();
        concepto.getItems().setAll(opciones); concepto.setValue(opciones[0]);
        var medio = new ComboBox<MedioPago>(); medio.getItems().setAll(MedioPago.values()); medio.setValue(MedioPago.EFECTIVO);
        var fuente = new ComboBox<MovimientoCaja.Fuente>(); fuente.getItems().setAll(MovimientoCaja.Fuente.values()); fuente.setValue(MovimientoCaja.Fuente.CAJA);
        var importe = Ui.campo("0,00"); var adicionales = new PagosForm(false); var detalle = Ui.campo("Motivo o referencia");
        var campos = new GridPane(); campos.setHgap(12); campos.setVgap(8);
        campos.addRow(0, new Label("Concepto"), concepto, new Label("Origen del dinero"), fuente);
        campos.addRow(1, new Label("Importe (S/)"), importe, new Label("Medio de pago"), medio);
        campos.addRow(2, new Label("Descripción"), detalle);
        var combinado = seccion("Dividir entre varios medios", adicionales);
        var guardar = new Button("Registrar movimiento");
        guardar.setOnAction(new javafx.event.EventHandler<>() {
            UUID solicitud = UUID.randomUUID();
            public void handle(javafx.event.ActionEvent e) { Ui.accion(() -> {
                if (detalle.getText().isBlank()) throw new IllegalArgumentException("Escribe el motivo o la referencia.");
                var tipo = switch (concepto.getValue()) {
                    case "Compra" -> MovimientoCaja.Tipo.COMPRA;
                    case "Retiro" -> MovimientoCaja.Tipo.RETIRO;
                    case "Otro ingreso" -> MovimientoCaja.Tipo.OTRO_INGRESO;
                    default -> MovimientoCaja.Tipo.GASTO;
                };
                var pagos = new EnumMap<MedioPago,BigDecimal>(MedioPago.class);
                if (combinado.isExpanded()) pagos.putAll(adicionales.valores());
                else pagos.put(medio.getValue(), Ui.numero(importe.getText()));
                sesion.caja.registrar(solicitud, sesion.exigirTurno(), tipo, fuente.getValue(),
                        pagos, concepto.getValue()+" · "+detalle.getText().strip());
                solicitud = UUID.randomUUID(); importe.clear(); adicionales.limpiar(); detalle.clear(); refrescar();
            }); }
        });
        importe.disableProperty().bind(combinado.expandedProperty());
        medio.disableProperty().bind(combinado.expandedProperty());
        return seccion(titulo, Ui.panel(campos, combinado, guardar));
    }
    private void editarGanancia() {
        var doc = libro.getSelectionModel().getSelectedItem().documento();
        var dialogo = new TextInputDialog(doc.ganancia() == null ? "" : doc.ganancia().toPlainString());
        dialogo.setTitle("Ganancia del documento");
        dialogo.setHeaderText(doc.numero() + " · Ganancia total del documento");
        dialogo.setContentText("Importe (S/)");
        Ui.tema(dialogo).showAndWait().ifPresent(valor -> {
            documentos.guardarGanancia(doc.id(), Ui.numero(valor), doc.ganancia());
            refrescar();
        });
    }
    public void cargarTurnos() {
        UUID id = sesion.turno.get();
        cargando = true;
        try {
            turnos.getItems().setAll(sesion.turnos.listar(100));
            var turno = turnos.getItems().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
            turnos.setValue(turno);
            sesion.turno.set(turno == null ? null : turno.id());
        } finally { cargando = false; }
        refrescar();
    }
    private void abrir() {
        sesion.caja.abrir(aperturaId,turnoNuevo,caja.getText(),fecha.getValue(),nombre.getText(),Ui.numero(fondo.getText()));sesion.turno.set(turnoNuevo);aperturaId=UUID.randomUUID();turnoNuevo=UUID.randomUUID();cargarTurnos();refrescar();
    }
    public void refrescar() {
        UUID id = sesion.turno.get();
        boolean cambio = mostrado == null || !mostrado.turno().id().equals(id);
        if (cambio) { contado.clear(); firma.clear(); nota.clear(); conteo.setExpanded(false); }
        mostrado = id == null ? null : sesion.caja.consultar(id);
        boolean abierto = mostrado != null && !mostrado.turno().cerrado();
        for (var panel : List.of(tienda, personal, otros, conteo)) panel.setDisable(!abierto);
        abrirTurno.setExpanded(mostrado == null);
        cierreGuardado.setText("");
        if (mostrado == null) {
            libro.getItems().clear(); resumen.setText("Abre un turno o selecciona uno anterior."); return;
        }
        var t = mostrado.turno();
        var efectivo = t.fondoInicial().add(sesion.caja.flujo(mostrado).get(MedioPago.EFECTIVO));
        resumen.setText((abierto ? "Turno abierto" : "Turno cerrado") + "   ·   Efectivo esperado   S/ " + efectivo.toPlainString());
        resumen.getStyleClass().remove("section-title"); resumen.getStyleClass().add("section-title");
        Map<UUID, LibroCajaRepository.Documento> docs = documentos.documentos(id);
        var filas = new ArrayList<Fila>();
        Set<UUID> vistos = new HashSet<>();
        for (var m : mostrado.movimientos()) {
            var doc = docs.get(m.id());
            var concepto = switch(m.tipo()) {
                case DEVOLUCION -> "Devolución"; case COBRO -> "Cobro"; case COMPRA -> "Compra";
                case GASTO -> "Gasto"; case RETIRO -> "Retiro"; case OTRO_INGRESO -> "Otro ingreso";
            };
            String pago = m.importes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> etiqueta(e.getKey()) + "   " + (m.ingreso() ? "" : "−") + "S/ " + e.getValue().toPlainString())
                    .collect(Collectors.joining("\n"));
            if (m.fuente() == MovimientoCaja.Fuente.EXTERNA) pago += "\nFuente externa";
            if (doc != null && m.ingreso() && doc.separacion()) pago = "Abono · " + pago;
            String comprobante = doc == null ? concepto : switch (doc.tipo()) {
                case "BOLETA" -> "Boleta "; case "FACTURA" -> "Factura "; default -> "Nota de venta ";
            } + doc.numero();
            String descripcion = doc == null ? m.referencia() : doc.items();
            if (doc != null && !m.ingreso()) descripcion = m.referencia() + "\n" + descripcion;
            String ganancia = "—";
            if (doc != null && vistos.add(doc.id())) ganancia = doc.ganancia() == null ? "Por definir" : "S/ " + doc.ganancia().toPlainString();
            filas.add(new Fila(doc, comprobante, descripcion, pago, ganancia));
        }
        libro.getItems().setAll(filas);
        if (mostrado.cierre() != null) {
            var cierre = mostrado.cierre();
            cierreGuardado.setText("Contado S/ " + cierre.efectivoContado() + "   ·   Diferencia S/ " + cierre.diferencia() + "   " + cierre.observacion());
        }
        var esperados=sesion.caja.resumenDigital(t.id());esperados.forEach((medio,v)->{
            var campos=digitales.get(medio); var valores=List.of(v.cobros(),v.devoluciones(),v.egresos(),v.otrosIngresos());
            for(int i=0;i<4;i++){campos.get(i).clear();campos.get(i).setPromptText("Esperado "+valores.get(i).toPlainString());}
        });
    }
    private void cerrar() {
        if(mostrado==null||mostrado.turno().cerrado())throw new IllegalArgumentException("Selecciona un turno abierto");
        var verificados=new EnumMap<MedioPago,VerificacionMedio>(MedioPago.class);
        digitales.forEach((medio,campos)->{if(campos.stream().anyMatch(c->!c.getText().isBlank()))verificados.put(medio,new VerificacionMedio(valor(campos.get(0)),valor(campos.get(1)),valor(campos.get(2)),valor(campos.get(3))));});
        if(!Ui.confirmar("¿Confirmar el cierre? Los movimientos y el conteo de este turno quedarán bloqueados."))return;
        sesion.caja.cerrar(cierreId,mostrado.turno().id(),mostrado.turno().version(),Ui.numero(contado.getText()),firma.getText(),nota.getText(),verificados);cierreId=UUID.randomUUID();cargarTurnos();refrescar();Ui.informar("Caja cerrada correctamente.");
    }
    private static String etiqueta(MedioPago medio) {
        String nombre = medio.name().toLowerCase(Locale.ROOT);
        if (nombre.equals("deposito")) return "Depósito";
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }
    private BigDecimal valor(TextField c){return c.getText().isBlank()?BigDecimal.ZERO:Ui.numero(c.getText());}
}
