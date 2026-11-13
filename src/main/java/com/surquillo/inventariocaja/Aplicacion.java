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
        this.escenario=escenario;escenario.setTitle("Cuadre y Stock · Beta en desarrollo 0.1.4");
        var host=Ui.campo("127.0.0.1");host.setText("127.0.0.1");var puerto=Ui.campo("5432");puerto.setText("5432");var base=Ui.campo("postgres");base.setText("postgres");var usuario=Ui.campo("postgres");usuario.setText("postgres");var clave=new PasswordField();var esquema=Ui.campo("cuadre_pruebas");esquema.setText("cuadre_pruebas");var estado=new Label("La contraseña se usa solo en esta sesión. No la escribas en el chat.");
        var conectar=new Button("Conectar e iniciar pruebas");
        conectar.setOnAction(e->{
            if(!host.getText().matches("[a-zA-Z0-9.:-]+")||!puerto.getText().matches("[0-9]{1,5}")||!base.getText().matches("[a-zA-Z0-9_-]+")){Ui.informar("Revisa servidor, puerto y base de datos.");return;}
            conectar("jdbc:postgresql://"+host.getText()+":"+puerto.getText()+"/"+base.getText(),usuario.getText(),clave.getText(),esquema.getText(),conectar,estado);
        });
        var form=new GridPane();form.setHgap(12);form.setVgap(12);form.addRow(0,new Label("Servidor"),host,new Label("Puerto"),puerto);form.addRow(1,new Label("Base de datos"),base,new Label("Usuario PostgreSQL"),usuario);form.addRow(2,new Label("Contraseña"),clave);form.addRow(3,new Label("Esquema de tus pruebas"),esquema);
        conectar.getStyleClass().add("primary-action");
        var titulo = new Label("Conectar a la tienda");
        titulo.getStyleClass().add("page-title");
        var panel = Ui.panel(titulo, new Label("Beta de pruebas · Surquillo"), form, conectar, estado);
        panel.getStyleClass().add("connection-card");
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        escena(new StackPane(panel));
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
        var nueva = new Tab("Nueva operación", new VentaView(sesion).crear());
        var historial = new Tab("Operaciones", operaciones.crear());
        var cierre = new Tab("Cierre de caja", caja.crear(() -> tabs.getSelectionModel().select(nueva),
                () -> tabs.getSelectionModel().select(historial)));
        var existencias = new ListaStockView(sesion);
        var stock = new Tab("Stock", existencias.crear());
        var productos = new Tab("Catálogo", catalogo.crear());
        tabs.getTabs().addAll(cierre, stock, productos, nueva, new Tab("Compras",new CompraView(sesion).crear()),
            new Tab("Devoluciones y cambios",new CorreccionesView(sesion).crear()), historial);
        tabs.getSelectionModel().selectedItemProperty().addListener((o, anterior, actual) -> Ui.accion(() -> {
            if (actual == historial) operaciones.refrescar();
            if (actual == cierre) caja.cargarTurnos();
            if (actual == stock) existencias.refrescar();
            if (actual == productos) catalogo.refrescar();
        }));
        var demo = new MenuItem("Cargar datos de demostración");
        demo.setOnAction(event -> Ui.accion(()->{if(!Ui.confirmar("¿Añadir artículos DEMO y sus existencias iniciales? No se sobrescribirán los artículos existentes."))return;try{new DatosDemostracionService(sesion.ds,sesion.reloj).cargar();catalogo.refrescar();Ui.informar("Datos DEMO listos. Almacén: Surquillo.");}catch(Exception e){throw new IllegalArgumentException("No se pudo cargar la demostración");}}));

        var ayuda = new MenuItem("Funciones pendientes");
        ayuda.setOnAction(e -> Ui.informar("Esta versión permite probar catálogo, stock, compras, ventas, separaciones, devoluciones, cambios y cierre. Reportes Excel y respaldos manuales siguen pendientes de implementación."));
        var opciones = new MenuButton("Pruebas", null, demo, ayuda);
        var entorno = new Label("Esquema de pruebas · " + esquema);
        entorno.getStyleClass().add("muted");
        var barra = BarraVentana.crear(escenario);
        barra.getChildren().addAll(2, java.util.List.of(entorno, opciones));
        escena(tabs, barra);
        escenario.setMaximized(true);
    }
    private void escena(javafx.scene.Parent raiz) {
        escena(raiz, BarraVentana.crear(escenario));
    }
    private void escena(javafx.scene.Parent raiz, HBox barra) {
        var marco = new BorderPane(raiz, barra, null, null, null);
        var escena = new Scene(marco, 1200, 790);
        escena.getStylesheets().add(getClass().getResource("/ui/beta.css").toExternalForm());
        escenario.setScene(escena);
    }
    public static void main(String[] args) {
        launch(args);
    }
}
