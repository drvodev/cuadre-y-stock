package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import com.surquillo.inventariocaja.repositorio.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.UUID;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import static org.junit.jupiter.api.Assertions.*;

/** Esquemas propios y aleatorios; no utiliza la base habitual de la aplicación. */
@EnabledIfEnvironmentVariable(named="STOCK_TEST_DB",matches="true")
class CatalogoPostgresAmpliadoTest extends CatalogoContratoTest {
    private PGSimpleDataSource ds;
    private String esquema;
    @Override protected ProductoRepository nuevoRepositorio() throws Exception {
        ds=new PGSimpleDataSource();ds.setURL(System.getenv("STOCK_TEST_URL"));ds.setUser(System.getenv("STOCK_TEST_USER"));ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        esquema="prueba_"+UUID.randomUUID().toString().replace("-","");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA "+esquema);}
        ds.setCurrentSchema(esquema);MigracionesPostgres.aplicar(ds);MigracionesPostgres.aplicar(ds);
        return new ProductoPostgresRepository(ds);
    }
    @AfterEach void limpiar() throws Exception {
        if(ds==null||esquema==null)return;
        if(!esquema.matches("prueba_[a-f0-9]{32}"))throw new IllegalStateException("Esquema inválido");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+esquema+" CASCADE");}
    }
    @Test void dosEdicionesConcurrentesConservanUnaSolaVersionGanadora() throws Exception {
        var opcion=servicio.crearOpcion(ListaCatalogo.MARCA,"Original");
        var inicio=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> editar=()->{
                inicio.await();
                try {
                    servicio.actualizarOpcion(new OpcionCatalogo(opcion.id(),opcion.lista(),UUID.randomUUID().toString(),true,0));return true;
                }catch(ValidacionException esperado){return false;}
            };
            var a=pool.submit(editar);var b=pool.submit(editar);inicio.countDown();
            assertNotEquals(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1,servicio.buscarOpcion(opcion.id()).orElseThrow().version());
            assertEquals(2,servicio.historialOpcion(opcion.id(),0,100).size());
        }finally{pool.shutdownNow();}
    }
    @Test void migraDesdeVersionSieteSinPerderArticulosNiHistorial() throws Exception {
        if(!esquema.matches("prueba_[a-f0-9]{32}"))throw new IllegalStateException("Esquema inválido");
        String[] archivos={"catalogo-v1.sql","stock-v1.sql","stock-catalogo-v3.sql","caja-v4.sql",
            "caja-conciliacion-v5.sql","operaciones-v6.sql","anulaciones-v7.sql"};
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("DROP SCHEMA "+esquema+" CASCADE");s.execute("CREATE SCHEMA "+esquema);
            s.execute("CREATE TABLE app_migracion(version integer PRIMARY KEY,hash text NOT NULL,aplicada timestamptz NOT NULL DEFAULT now())");
            for(int i=0;i<archivos.length;i++) {
                byte[] contenido;
                try(var recurso=getClass().getResourceAsStream("/db/"+archivos[i])){contenido=recurso.readAllBytes();}
                s.execute(new String(contenido,java.nio.charset.StandardCharsets.UTF_8));
                try(var p=c.prepareStatement("INSERT INTO app_migracion(version,hash) VALUES(?,?)")) {
                    String alternativo=new String(contenido,java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n");
                    if(i%2==0)alternativo=alternativo.replace("\n","\r\n");
                    p.setInt(1,i+1);p.setString(2,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(alternativo.getBytes(java.nio.charset.StandardCharsets.UTF_8))));p.executeUpdate();
                }
            }
            s.execute("INSERT INTO catalogo_variante VALUES('ANTERIOR','Lavadero anterior','Baños','Marca anterior','Caja','','caja',false,'COMPRADO',NULL,'',25,NULL,true,0)");
        }
        MigracionesPostgres.aplicar(ds);MigracionesPostgres.aplicar(ds);
        var recuperado=servicio.buscarSku("ANTERIOR").orElseThrow();
        assertEquals("Lavadero anterior",recuperado.descripcion());assertTrue(recuperado.atributos().isEmpty());
        assertEquals("Baños",servicio.opciones(ListaCatalogo.CATEGORIA,100).getFirst());
        var historia=servicio.historial("ANTERIOR",0,100);
        assertEquals(1,historia.size());assertTrue(historia.getFirst().variante().atributos().isEmpty());
        try(var c=ds.getConnection();var s=c.createStatement();var filas=s.executeQuery("SELECT count(*) FROM app_migracion WHERE version<=8")){filas.next();assertEquals(8,filas.getInt(1));}
    }
}
