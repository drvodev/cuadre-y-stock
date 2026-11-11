package com.surquillo.inventariocaja;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import com.surquillo.inventariocaja.vista.*;
import com.surquillo.inventariocaja.configuracion.PreparacionBeta;
import com.surquillo.inventariocaja.servicio.DatosDemostracionService;
import javafx.concurrent.Task;

/** Aplicación de pruebas; cada sesión se conecta al esquema elegido por el operador. */
public class Aplicacion extends Application {
    private Stage escenario;
    @Override
    public void start(Stage escenario) {
        this.escenario=escenario;escenario.setTitle("Cuadre y Stock · Beta de pruebas 0.1.2");
        var host=Ui.campo("127.0.0.1");host.setText("127.0.0.1");var puerto=Ui.campo("5432");puerto.setText("5432");var base=Ui.campo("postgres");base.setText("postgres");var usuario=Ui.campo("postgres");usuario.setText("postgres");var clave=new PasswordField();var esquema=Ui.campo("cuadre_pruebas");esquema.setText("cuadre_pruebas");var estado=new Label("La contraseña se usa solo en esta sesión. No la escribas en el chat.");
        var conectar=new Button("Conectar e iniciar pruebas");
        conectar.setOnAction(e->{
            if(!host.getText().matches("[a-zA-Z0-9.:-]+")||!puerto.getText().matches("[0-9]{1,5}")||!base.getText().matches("[a-zA-Z0-9_-]+")){Ui.informar("Revisa servidor, puerto y base de datos.");return;}
            conectar("jdbc:postgresql://"+host.getText()+":"+puerto.getText()+"/"+base.getText(),usuario.getText(),clave.getText(),esquema.getText(),conectar,estado);
        });
        var form=new GridPane();form.setHgap(12);form.setVgap(12);form.addRow(0,new Label("Servidor"),host,new Label("Puerto"),puerto);form.addRow(1,new Label("Base de datos"),base,new Label("Usuario PostgreSQL"),usuario);form.addRow(2,new Label("Contraseña"),clave);form.addRow(3,new Label("Esquema de tus pruebas"),esquema);
        escena(Ui.panel(new Label("Cuadre y Stock"),new Label("Beta de pruebas · Surquillo"),new Label("Usa datos de demostración. Se creará el esquema elegido sin borrar otras tablas."),form,conectar,estado));
        escenario.show();
        var env=System.getenv();if(env.containsKey("APP_DB_URL")&&env.containsKey("APP_DB_PASSWORD"))conectar(env.get("APP_DB_URL"),env.get("APP_DB_USER"),env.get("APP_DB_PASSWORD"),env.getOrDefault("APP_DB_SCHEMA","cuadre_pruebas"),conectar,estado);
    }
    private void conectar(String url,String usuario,String clave,String esquema,Button boton,Label estado){
        boton.setDisable(true);estado.setText("Conectando y verificando las tablas…");
        Task<javax.sql.DataSource> tarea=new Task<>() {protected javax.sql.DataSource call() throws Exception{return PreparacionBeta.preparar(url,usuario,clave,esquema);}};
        tarea.setOnSucceeded(e->{try{principal(new SesionBeta(tarea.getValue()),esquema);}catch(Exception error){boton.setDisable(false);estado.setText("No se pudo abrir la sesión.");Ui.accion(()->{throw new IllegalArgumentException(error.getMessage());});}});
        tarea.setOnFailed(e->{boton.setDisable(false);estado.setText("No se pudo conectar. Revisa usuario, contraseña, puerto y esquema. No se guardó la contraseña.");Ui.informar("No se pudo preparar la base de pruebas. Revisa la conexión y que el usuario pueda crear un esquema nuevo. Si el esquema tenía tablas antiguas, usa otro nombre.");});
        var hilo=new Thread(tarea,"conexion-beta");hilo.setDaemon(true);hilo.start();
    }
    private void principal(SesionBeta sesion,String esquema){
        var tabs=new TabPane();tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        var catalogo=new CatalogoView(sesion);var caja=new CajaView(sesion);var operaciones=new OperacionesView(sesion);
        tabs.getTabs().addAll(new Tab("Catálogo",catalogo.crear()),new Tab("Stock",new StockView(sesion.stock).crear()),new Tab("Caja y cierre",caja.crear()),new Tab("Nueva operación",new VentaView(sesion).crear()),new Tab("Operaciones",operaciones.crear()));
        tabs.getSelectionModel().selectedItemProperty().addListener((o,a,n)->Ui.accion(()->{if(n.getText().equals("Operaciones"))operaciones.refrescar();if(n.getText().equals("Caja y cierre"))caja.cargarTurnos();}));
        var cargar=Ui.boton("Cargar datos de demostración",()->{if(!Ui.confirmar("¿Añadir artículos DEMO y sus existencias iniciales? No se sobrescribirán los artículos existentes."))return;try{new DatosDemostracionService(sesion.ds,sesion.reloj).cargar();catalogo.refrescar();Ui.informar("Datos DEMO listos. Almacén: Surquillo.");}catch(Exception e){throw new IllegalArgumentException("No se pudo cargar la demostración");}});
        var barra=new HBox(16,new Label("BETA DE PRUEBAS · "+esquema),cargar,Ui.boton("Funciones pendientes",()->Ui.informar("Aún faltan compras documentadas, cambios de producto, reportes Excel, respaldos y restauración. Usa esta versión para probar catálogo, stock, ventas, separaciones y cierre con datos de demostración.")));barra.setPadding(new javafx.geometry.Insets(12));
        var raiz=new BorderPane(tabs,barra,null,null,null);escena(raiz);escenario.setMaximized(true);
    }
    private void escena(javafx.scene.Parent raiz){var marco=new BorderPane(raiz,BarraVentana.crear(escenario),null,null,null);var s=new Scene(marco,1240,850);var css=getClass().getResource("/ui/beta.css");if(css!=null)s.getStylesheets().add(css.toExternalForm());escenario.setScene(s);}
    public static void main(String[] args) {
        launch(args);
    }
}
