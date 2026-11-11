package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.modelo.caja.*;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public class OperacionesView {
    private final SesionBeta sesion;
    private final TableView<Operacion> tabla=new TableView<>();
    private final TextArea detalle=new TextArea();
    private final ComboBox<Documento.Tipo> tipo=new ComboBox<>(),tipoFinal=new ComboBox<>();
    private final TextField numero=Ui.campo("Número del documento"),numeroFinal=Ui.campo("Opcional: número del documento final"),motivo=Ui.campo("Motivo de la anulación completa");
    private final PagosForm pagos=new PagosForm();
    private final CheckBox reingresar=new CheckBox("Todos los productos entregados se devolvieron y pueden venderse de nuevo");
    private UUID abono=UUID.randomUUID(),entrega=UUID.randomUUID(),anulacion=UUID.randomUUID();
    public OperacionesView(SesionBeta s){sesion=s;}
    public Parent crear(){
        tabla.getColumns().add(Ui.columna("Documento",o->o.documento().numero(),130));tabla.getColumns().add(Ui.columna("Tipo",Operacion::modalidad,130));tabla.getColumns().add(Ui.columna("Cliente",Operacion::cliente,200));tabla.getColumns().add(Ui.columna("Total",Operacion::total,100));tabla.getColumns().add(Ui.columna("Saldo",Operacion::saldo,100));tabla.getColumns().add(Ui.columna("Estado",Operacion::estado,150));tabla.setPrefHeight(250);
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,n)->{if(n!=null){mostrar(n);abono=UUID.randomUUID();entrega=UUID.randomUUID();anulacion=UUID.randomUUID();pagos.limpiar();}});
        tipo.getItems().setAll(Documento.Tipo.values());tipo.setValue(Documento.Tipo.BOLETA);tipoFinal.getItems().setAll(Documento.Tipo.values());tipoFinal.setValue(Documento.Tipo.BOLETA);
        detalle.setEditable(false);detalle.setPrefRowCount(7);
        var cobros=new TitledPane("Registrar abono",Ui.panel(pagos,Ui.boton("Confirmar abono",()->{
            var op=seleccionada();sesion.ventas.abonar(abono,op.id(),sesion.exigirTurno(),pagos.valores(),pagos.recibido());abono=UUID.randomUUID();pagos.limpiar();actualizarSeleccion(op.id());Ui.informar("Abono guardado en el turno actual.");
        })));cobros.setExpanded(false);
        var despacho=new TitledPane("Entregar separación pagada",Ui.panel(new HBox(10,tipoFinal,numeroFinal),Ui.boton("Confirmar entrega",()->{
            var op=seleccionada();if(!Ui.confirmar("¿Confirmar la entrega completa? Se descontará el stock reservado una sola vez."))return;
            sesion.ventas.entregar(entrega,op.id(),numeroFinal.getText().isBlank()?null:new Documento(tipoFinal.getValue(),numeroFinal.getText(),true));entrega=UUID.randomUUID();numeroFinal.clear();actualizarSeleccion(op.id());Ui.informar("Entrega guardada.");
        })));despacho.setExpanded(false);
        var devolver=new TitledPane("Anular con devolución completa",Ui.panel(new Label("Devuelve el total abonado. Indica abajo los medios realmente utilizados para devolver el dinero."),motivo,reingresar,new Label("La anulación afecta el turno actual y conserva los cierres anteriores.")));
        var pagosDevolucion=new PagosForm();((VBox)devolver.getContent()).getChildren().addAll(pagosDevolucion,Ui.boton("Confirmar anulación",()->{
            var op=seleccionada();if(!Ui.confirmar("¿Anular "+op.documento().numero()+" y registrar la devolución de S/ "+op.abonado().toPlainString()+"?"))return;
            Set<Integer> indices=new HashSet<>();if(reingresar.isSelected())for(int i=0;i<op.items().size();i++)indices.add(i);
            sesion.ventas.anular(anulacion,op.id(),sesion.exigirTurno(),motivo.getText(),pagosDevolucion.valores(),indices);anulacion=UUID.randomUUID();pagosDevolucion.limpiar();motivo.clear();actualizarSeleccion(op.id());Ui.informar("Anulación guardada en el turno actual.");
        }));devolver.setExpanded(false);
        var raiz=Ui.panel(new Label("Operaciones · últimas 100"),new HBox(10,tipo,numero,Ui.boton("Buscar documento",()->{var op=sesion.ventas.buscarDocumento(tipo.getValue(),numero.getText());tabla.getItems().setAll(op);tabla.getSelectionModel().select(op);}),Ui.boton("Actualizar lista",this::refrescar)),tabla,detalle,cobros,despacho,devolver);
        refrescar();return Ui.desplazar(raiz);
    }
    public void refrescar(){tabla.getItems().setAll(sesion.ventas.listar(100));}
    private Operacion seleccionada(){var op=tabla.getSelectionModel().getSelectedItem();if(op==null)throw new IllegalArgumentException("Selecciona una operación");return sesion.ventas.consultar(op.id());}
    private void actualizarSeleccion(UUID id){refrescar();tabla.getItems().stream().filter(o->o.id().equals(id)).findFirst().ifPresent(o->tabla.getSelectionModel().select(o));}
    private void mostrar(Operacion op){var s=new StringBuilder(op.documento().tipo()+" "+op.documento().numero()+" · "+op.estado()+"\nCliente: "+op.cliente()+" · Contacto: "+op.contacto()+"\nAbonado: "+op.abonado()+" · Saldo: "+op.saldo()+"\n");for(var i:op.items())s.append(i.cantidad().toPlainString()).append(' ').append(i.unidad()).append(" · ").append(i.descripcion()).append(" · S/ ").append(i.total()).append(" · ").append(i.indicaciones()).append('\n');detalle.setText(s.toString());}
}
