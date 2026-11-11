package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.TurnoRepository;
import javafx.scene.Parent;
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
    private final TextArea historial=new TextArea();
    private final TextField contado=Ui.campo("Efectivo contado"),firma=Ui.campo("Firma del cierre"),nota=Ui.campo("Observación de diferencias");
    private final Map<MedioPago,List<TextField>> digitales=new EnumMap<>(MedioPago.class);
    private TurnoRepository.Estado mostrado;
    private UUID aperturaId=UUID.randomUUID(),turnoNuevo=UUID.randomUUID(),movimientoId=UUID.randomUUID(),cierreId=UUID.randomUUID();
    public CajaView(SesionBeta s) { sesion=s; }
    public Parent crear() {
        caja.setText("Surquillo"); fecha.setValue(LocalDate.now(sesion.reloj)); fondo.setText("0");
        turnos.setPrefWidth(620); turnos.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Turno t) { return t==null?"":t.fechaOperativa()+" · "+t.caja()+" · "+t.encargado()+" · "+(t.cerrado()?"Cerrado":"Abierto"); }
            public Turno fromString(String s) { return null; }
        });
        turnos.setOnAction(e->{var t=turnos.getValue();if(t!=null){sesion.turno.set(t.id());Ui.accion(this::refrescar);}});
        var apertura=new GridPane(); apertura.setHgap(10);apertura.setVgap(10);apertura.addRow(0,new Label("Caja"),caja,new Label("Fecha operativa"),fecha);apertura.addRow(1,new Label("Encargado"),nombre,new Label("Fondo inicial"),fondo);
        var conciliacion=new GridPane(); conciliacion.setHgap(10);conciliacion.setVgap(8);conciliacion.addRow(0,new Label("Medio"),new Label("Cobros verificados"),new Label("Devoluciones"),new Label("Egresos"),new Label("Otros ingresos"));
        int fila=1;for(var medio:MedioPago.values())if(medio!=MedioPago.EFECTIVO){var campos=List.of(Ui.campo("0"),Ui.campo("0"),Ui.campo("0"),Ui.campo("0"));digitales.put(medio,campos);conciliacion.addRow(fila++,new Label(medio.name()),campos.get(0),campos.get(1),campos.get(2),campos.get(3));}
        historial.setEditable(false);historial.setPrefRowCount(8);
        var egresoPagos=new PagosForm();var egresoTipo=new ComboBox<MovimientoCaja.Tipo>();egresoTipo.getItems().setAll(MovimientoCaja.Tipo.GASTO,MovimientoCaja.Tipo.RETIRO,MovimientoCaja.Tipo.OTRO_INGRESO);egresoTipo.setValue(MovimientoCaja.Tipo.GASTO);
        var fuente=new ComboBox<MovimientoCaja.Fuente>();fuente.getItems().setAll(MovimientoCaja.Fuente.values());fuente.setValue(MovimientoCaja.Fuente.CAJA);var referencia=Ui.campo("Descripción y referencia del movimiento");
        var egresos=new TitledPane("Registrar gasto, retiro u otro ingreso",Ui.panel(new HBox(10,egresoTipo,fuente),referencia,egresoPagos,Ui.boton("Registrar movimiento",()->{
            sesion.caja.registrar(movimientoId,sesion.exigirTurno(),egresoTipo.getValue(),fuente.getValue(),egresoPagos.valores(),referencia.getText());movimientoId=UUID.randomUUID();egresoPagos.limpiar();referencia.clear();refrescar();
        })));egresos.setExpanded(false);
        var raiz=Ui.panel(new Label("Caja y cierre"),apertura,new HBox(10,Ui.boton("Abrir turno",this::abrir),Ui.boton("Actualizar turnos",this::cargarTurnos)),turnos,resumen,historial,egresos,new Label("Cierre · verifica los importes y confirma tu conteo"),contado,firma,nota,conciliacion,Ui.boton("Cerrar caja",this::cerrar));
        cargarTurnos();return Ui.desplazar(raiz);
    }
    public void cargarTurnos() {
        UUID id=sesion.turno.get();turnos.getItems().setAll(sesion.turnos.listar(100));
        if(id!=null) turnos.getItems().stream().filter(t->t.id().equals(id)).findFirst().ifPresent(t->turnos.setValue(t));
    }
    private void abrir() {
        sesion.caja.abrir(aperturaId,turnoNuevo,caja.getText(),fecha.getValue(),nombre.getText(),Ui.numero(fondo.getText()));sesion.turno.set(turnoNuevo);aperturaId=UUID.randomUUID();turnoNuevo=UUID.randomUUID();cargarTurnos();refrescar();
    }
    public void refrescar() {
        if(sesion.turno.get()==null)return;mostrado=sesion.caja.consultar(sesion.turno.get());
        var t=mostrado.turno();resumen.setText((t.cerrado()?"Turno cerrado":"Turno abierto")+" · "+t.caja()+" · "+t.fechaOperativa()+" · Efectivo esperado S/ "+sesion.caja.efectivoEsperado(t.id()).toPlainString());
        var texto=new StringBuilder();for(var m:mostrado.movimientos())texto.append(m.tipo()).append(" · ").append(m.fuente()).append(" · ").append(m.importes()).append(" · ").append(m.referencia()).append('\n');
        if(mostrado.cierre()!=null)texto.append("CIERRE · contado ").append(mostrado.cierre().efectivoContado()).append(" · diferencia ").append(mostrado.cierre().diferencia()).append(" · ").append(mostrado.cierre().observacion());historial.setText(texto.toString());
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
    private BigDecimal valor(TextField c){return c.getText().isBlank()?BigDecimal.ZERO:Ui.numero(c.getText());}
}
