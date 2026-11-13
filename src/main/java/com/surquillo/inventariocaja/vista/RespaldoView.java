package com.surquillo.inventariocaja.vista;
import com.surquillo.inventariocaja.servicio.RespaldoService;
import javafx.concurrent.Task;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import java.net.URI;
import java.nio.file.Path;

/** Copias manuales; recuperación en una base existente, diferente y vacía. */
public final class RespaldoView {
    private final String host,puerto,base,usuario,clave,esquema;
    private final boolean opcionesConexionEspeciales;
    private final TextField herramientas=Ui.campo("Carpeta bin de PostgreSQL");
    private final TextField destino=Ui.campo("Nombre de la base nueva y vacía");
    private final Label estado=new Label("La copia incluye los datos del esquema seleccionado.");
    private final Button crear=new Button("Crear copia manual"),restaurar=new Button("Restaurar en base vacía");
    private Path archivo;
    public RespaldoView(String url,String usuario,String clave,String esquema) {
        URI uri=URI.create(url.substring(5));
        this.opcionesConexionEspeciales=uri.getRawQuery()!=null&&!uri.getRawQuery().isBlank();
        this.host=uri.getHost();this.puerto=Integer.toString(uri.getPort()<0?5432:uri.getPort());
        this.base=uri.getPath().substring(1);this.usuario=usuario;this.clave=clave;this.esquema=esquema;
        herramientas.setText("C:\\Program Files\\PostgreSQL\\17\\bin");
    }
    public Parent crear(){
        var seleccionar=new Button("Elegir herramientas");
        seleccionar.setOnAction(e->{var d=new DirectoryChooser();d.setTitle("Carpeta bin de PostgreSQL");var f=d.showDialog(seleccionar.getScene().getWindow());if(f!=null)herramientas.setText(f.toPath().toString());});
        crear.setOnAction(e->Ui.accion(()->{var d=new DirectoryChooser();d.setTitle("Carpeta para guardar la copia");var f=d.showDialog(crear.getScene().getWindow());if(f!=null){var configuracion=servicio();var carpeta=f.toPath();ejecutar(()->"Copia guardada: "+configuracion.crear(carpeta));}}));
        var elegir=new Button("Elegir copia");
        elegir.setOnAction(e->{var d=new FileChooser();d.setTitle("Seleccionar respaldo");d.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldo PostgreSQL","*.backup"));var f=d.showOpenDialog(elegir.getScene().getWindow());if(f!=null){archivo=f.toPath();estado.setText("Copia seleccionada: "+f.getName());}});
        restaurar.setOnAction(e->Ui.accion(()->{
            if(archivo==null)throw new IllegalArgumentException("Elige una copia");
            String nombre=destino.getText().strip();
            if(!Ui.confirmar("¿Restaurar la copia en la base «"+nombre+"»? Debe existir, ser nueva y estar vacía."))return;
            var configuracion=servicio();var copia=archivo;
            ejecutar(()->{configuracion.restaurar(copia,nombre);return "Recuperación terminada en la base "+nombre+".";});
        }));
        return Ui.desplazar(Ui.panel(new Label("Respaldos y recuperación"),
            new Label("Las copias se crean solo cuando lo solicitas. Incluyen información del negocio: elige una carpeta protegida."),
            new HBox(8,herramientas,seleccionar),crear,new HBox(8,elegir,destino,restaurar),estado,
            new Label("Para recuperar, prepara primero otra base vacía en el mismo servidor y cierra cualquier conexión a esa base. Al abrirla, usa el nombre del esquema original.")));
    }
    private RespaldoService servicio(){
        if(opcionesConexionEspeciales)throw new IllegalArgumentException("Esta versión admite solo conexión local sin opciones adicionales");
        return new RespaldoService(host,puerto,base,usuario,clave,esquema,Path.of(herramientas.getText()));
    }
    private void ejecutar(java.util.concurrent.Callable<String> accion){
        crear.setDisable(true);restaurar.setDisable(true);estado.setText("Procesando…");
        Task<String> tarea=new Task<>(){@Override protected String call()throws Exception{return accion.call();}};
        tarea.setOnSucceeded(e->{crear.setDisable(false);restaurar.setDisable(false);estado.setText(tarea.getValue());});
        tarea.setOnFailed(e->{crear.setDisable(false);restaurar.setDisable(false);estado.setText("No se completó. Revisa archivo, conexión, permisos y destino.");Ui.informar("No se pudo completar la operación. La base original no se modifica. Revisa que las herramientas PostgreSQL sean compatibles y que el destino esté vacío.");});
        var hilo=new Thread(tarea,"respaldo-manual");hilo.start();
    }
}
