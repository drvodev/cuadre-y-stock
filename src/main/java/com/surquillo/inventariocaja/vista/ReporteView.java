package com.surquillo.inventariocaja.vista;

import com.surquillo.inventariocaja.controlador.ReporteController;
import com.surquillo.inventariocaja.servicio.ReporteService;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.FileChooser;
import java.time.*;

public final class ReporteView {
    private final SesionBeta sesion;
    public ReporteView(SesionBeta sesion) { this.sesion=sesion; }
    public Parent crear() {
        var fecha=new DatePicker(LocalDate.now(sesion.reloj));
        var periodo=new ComboBox<String>(); periodo.getItems().setAll("Día seleccionado","Mes seleccionado");periodo.setValue("Día seleccionado");
        var estado=new Label("El archivo contendrá información del negocio. Elige una ubicación protegida.");estado.setWrapText(true);
        var generar=new Button("Guardar reporte Excel (.xls)");
        generar.setOnAction(e->Ui.accion(()->{
            if(fecha.getValue()==null)throw new IllegalArgumentException("Selecciona una fecha.");
            LocalDate dia=fecha.getValue();boolean mensual=periodo.getValue().equals("Mes seleccionado");
            var elegir=new FileChooser();elegir.setTitle("Guardar reporte");
            elegir.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel 97-2003 (*.xls)","*.xls"));
            elegir.setInitialFileName("cuadre-"+(mensual?YearMonth.from(dia):dia)+".xls");
            var archivo=elegir.showSaveDialog(generar.getScene().getWindow());if(archivo==null)return;
            var tarea=new Task<Void>() { protected Void call() throws Exception {
                var controlador=new ReporteController(new ReporteService(sesion.ds));
                if(mensual)controlador.mensual(YearMonth.from(dia),archivo.toPath());else controlador.diario(dia,archivo.toPath());return null;
            }};
            generar.setDisable(true);estado.setText("Generando reporte…");
            tarea.setOnSucceeded(x->{generar.setDisable(false);estado.setText("Reporte guardado: "+archivo.getAbsolutePath());});
            tarea.setOnFailed(x->{generar.setDisable(false);estado.setText("No se pudo guardar el reporte. Comprueba el nombre, los permisos y la conexión.");});
            var hilo=new Thread(tarea,"reporte-excel");hilo.setDaemon(true);hilo.start();
        }));
        var titulo=new Label("Reportes");titulo.getStyleClass().add("page-title");
        return Ui.desplazar(Ui.panel(titulo,new Label("Selecciona un día o un mes. Los movimientos de caja usan la fecha operativa del turno."),
                new FlowPane(10,10,periodo,fecha,generar),estado));
    }
}
