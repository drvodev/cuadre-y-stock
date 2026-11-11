package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.servicio.*;
import com.surquillo.inventariocaja.excepcion.PersistenciaException;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Requiere una instancia desechable y credenciales de prueba configuradas por entorno. */
@EnabledIfEnvironmentVariable(named="STOCK_TEST_DB",matches="true")
class PostgresIntegracionTest {
    private PGSimpleDataSource ds;
    private String esquema;
    private StockPostgresRepository stock;
    private InventarioService inventario;
    private ClaveStock clave;
    private BigDecimal n(String numero) { return new BigDecimal(numero); }
    private void igual(String esperado, BigDecimal valor) { assertEquals(0,n(esperado).compareTo(valor)); }

    @BeforeEach void preparar() throws Exception {
        ds=new PGSimpleDataSource();
        ds.setURL(System.getenv("STOCK_TEST_URL"));
        ds.setUser(System.getenv("STOCK_TEST_USER"));
        ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        esquema="prueba_"+UUID.randomUUID().toString().replace("-","");
        try (Connection c=ds.getConnection(); var s=c.createStatement()) { s.execute("CREATE SCHEMA "+esquema); }
        ds.setCurrentSchema(esquema);
        MigracionesPostgres.aplicar(ds);
        MigracionesPostgres.aplicar(ds);
        stock=new StockPostgresRepository(ds); inventario=new InventarioService(stock,Clock.systemUTC());
        clave=new ClaveStock("SKU-001","almacen-prueba");
    }
    @AfterEach void limpiar() throws Exception {
        if (ds == null || esquema == null) return;
        if (!esquema.matches("prueba_[a-f0-9]{32}")) throw new IllegalStateException("Esquema de prueba inválido");
        try (Connection c=ds.getConnection(); var s=c.createStatement()) { s.execute("DROP SCHEMA IF EXISTS "+esquema+" CASCADE"); }
    }
    @Test void persisteCatalogoStockReservasYReintentos() {
        var catalogo=new CatalogoService(new ProductoPostgresRepository(ds));
        var producto=new Variante("sku-001","Bolsa","Material","Marca","25 kg","Gris","bolsa",false,
                Variante.Origen.COMPRADO,n("25"),"kg",n("20"),null,true,0);
        catalogo.crear(producto);
        assertEquals("SKU-001",catalogo.buscarSku("sku-001").orElseThrow().sku());
        assertEquals(1,catalogo.buscar("bolsa","",10,false).size());
        inventario.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        var reserva=new ReservaService(stock,Clock.systemUTC(),id->{});
        var reservaId=UUID.randomUUID(); var separacionId=UUID.randomUUID(); var solicitud=UUID.randomUUID();
        var primera=reserva.crear(solicitud,reservaId,separacionId,clave,n("5"),n("2"));
        var repetida=reserva.crear(solicitud,reservaId,separacionId,clave,n("5"),n("2"));
        igual("2",repetida.saldo().reservado()); igual("3",primera.reserva().faltante());
        inventario.entrada(UUID.randomUUID(),clave,MovimientoStock.Tipo.COMPRA,n("3"),"prueba");
        reserva.ampliar(UUID.randomUUID(),reservaId,clave,n("3"));
        reserva.despachar(UUID.randomUUID(),reservaId,clave);
        igual("0",stock.buscar(clave).orElseThrow().fisico());
        assertEquals(5,stock.historial(clave).size());
        assertEquals(ReservaOperacion.Estado.DESPACHADA,stock.buscarReserva(reservaId).orElseThrow().estado());
    }
    @Test void transaccionesConcurrentesNoDuplicanNiProducenNegativos() throws Exception {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("1"),"");
        var pool=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        try {
            Callable<Void> vender=()->{ inicio.await(); inventario.venta(UUID.randomUUID(),clave,n("1"),"venta"); return null; };
            var a=pool.submit(vender); var b=pool.submit(vender); inicio.countDown(); a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
            igual("0",stock.buscar(clave).orElseThrow().fisico());
            var h=stock.historial(clave); assertEquals(3,h.size()); igual("-1",h.get(1).cambioFisico().add(h.get(2).cambioFisico()));
        } finally { pool.shutdownNow(); }
    }
    @Test void falloAlGuardarMovimientoRevierteElSaldo() throws Exception {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        try (Connection c=ds.getConnection(); var s=c.createStatement()) {
            s.execute("ALTER TABLE stock_movimiento ADD CONSTRAINT prueba_rechazo CHECK (tipo <> 'COMPRA')");
        }
        assertThrows(PersistenciaException.class,()->inventario.entrada(UUID.randomUUID(),clave,MovimientoStock.Tipo.COMPRA,n("3"),"prueba"));
        igual("2",stock.buscar(clave).orElseThrow().fisico());
        assertEquals(1,stock.historial(clave).size());
    }
    @Test void busquedaConCaracteresEspecialesNoAlteraConsultas() {
        var repo=new ProductoPostgresRepository(ds);
        var catalogo=new CatalogoService(repo);
        var v=new Variante("SKU-A","Pieza 50%_!","Material","Marca","","","unidad",false,
                Variante.Origen.COMPRADO,null,"",n("20"),null,true,0);
        catalogo.crear(v);
        assertEquals(1,catalogo.buscar("50%_!","",10,false).size());
        assertTrue(catalogo.buscar("' OR 1=1 --","",10,false).isEmpty());
        assertTrue(catalogo.buscarSku("SKU-A").isPresent());
    }
    @Test void catalogoMantieneUnidadYEvitaActualizacionesObsoletas() {
        var catalogo=new CatalogoService(new ProductoPostgresRepository(ds));
        var base=new Variante("SKU-C","Bolsa","","","","","bolsa",false,
                Variante.Origen.COMPRADO,null,"",n("20"),null,true,0);
        catalogo.crear(base);
        var otraUnidad=new Variante("SKU-C","Bolsa","","","","","caja",false,
                Variante.Origen.COMPRADO,null,"",n("20"),null,true,0);
        assertThrows(ValidacionException.class,()->catalogo.actualizar(otraUnidad));
        assertEquals(1,catalogo.actualizar(base).version());
        assertThrows(ValidacionException.class,()->catalogo.actualizar(base));
        assertEquals("bolsa",catalogo.buscarSku("SKU-C").orElseThrow().unidad());
    }
    @Test void guardaMinimosEHistorialYDetectaMigracionAlterada() throws Exception {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        inventario.configurarMinimo(clave,n("3"));
        assertTrue(new InventarioService(new StockPostgresRepository(ds),Clock.systemUTC()).stockBajo(clave));
        assertThrows(ValidacionException.class,()->inventario.configurarMinimo(clave,n("0.5")));
        var catalogo=new CatalogoService(new ProductoPostgresRepository(ds));
        var v=new Variante("SKU-H","Bolsa","","","","","bolsa",false,Variante.Origen.COMPRADO,null,"",n("20"),null,true,0);
        catalogo.crear(v); catalogo.actualizar(v);
        assertEquals(2,catalogo.historial("SKU-H",0,10).size());
        assertEquals(1,catalogo.historial("SKU-H",catalogo.historial("SKU-H",0,1).get(0).secuencia(),10).size());
        assertEquals(java.util.List.of("bolsa"),catalogo.opciones(ProductoRepository.Lista.UNIDAD,10));
        try(Connection c=ds.getConnection(); var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT count(*) FROM catalogo_historial WHERE sku='SKU-H'")) { r.next(); assertEquals(2,r.getInt(1)); }
            s.executeUpdate("UPDATE app_migracion SET hash='alterado' WHERE version=1");
        }
        assertThrows(java.sql.SQLException.class,()->MigracionesPostgres.aplicar(ds));
    }
    @Test void cajaPersisteCierreYRecuperaReintentosOriginales() {
        var caja=new CajaService(new TurnoPostgresRepository(ds),Clock.systemUTC());
        var turno=UUID.randomUUID(); var abrir=UUID.randomUUID();
        var inicial=caja.abrir(abrir,turno,"Caja A",java.time.LocalDate.of(2026,1,1),"Encargado",n("100"));
        var cobroId=UUID.randomUUID(); var pagos=java.util.Map.of(com.surquillo.inventariocaja.modelo.caja.MedioPago.EFECTIVO,n("50"),com.surquillo.inventariocaja.modelo.caja.MedioPago.YAPE,n("100"));
        var cobro=caja.cobrar(cobroId,turno,n("150"),pagos,n("100"),"B3847");
        long version=caja.consultar(turno).turno().version();
        var cierre=caja.cerrar(UUID.randomUUID(),turno,version,n("150"),"Encargado","",java.util.Map.of(com.surquillo.inventariocaja.modelo.caja.MedioPago.YAPE,new com.surquillo.inventariocaja.modelo.caja.VerificacionMedio(n("100"),n("0"),n("0"),n("0"))));
        var reinicio=new CajaService(new TurnoPostgresRepository(ds),Clock.systemUTC());
        assertEquals(cierre,reinicio.consultar(turno));
        assertEquals(inicial,reinicio.abrir(abrir,turno,"Caja A",java.time.LocalDate.of(2026,1,1),"Encargado",n("100")));
        assertEquals(cobro,reinicio.cobrar(cobroId,turno,n("150"),pagos,n("100"),"B3847"));
        assertThrows(ValidacionException.class,()->reinicio.cobrar(UUID.randomUUID(),turno,n("150"),pagos,n("100"),"otra"));
    }
    @Test void cajaRevierteMovimientoCuandoFallaUnaLinea() throws Exception {
        var caja=new CajaService(new TurnoPostgresRepository(ds),Clock.systemUTC()); var turno=UUID.randomUUID();
        caja.abrir(UUID.randomUUID(),turno,"Caja A",java.time.LocalDate.now(),"Encargado",n("0"));
        try(Connection c=ds.getConnection();var s=c.createStatement()) { s.execute("ALTER TABLE caja_linea ADD CONSTRAINT prueba_linea CHECK (medio <> 'VISA')"); }
        assertThrows(PersistenciaException.class,()->caja.cobrar(UUID.randomUUID(),turno,n("150"),java.util.Map.of(com.surquillo.inventariocaja.modelo.caja.MedioPago.EFECTIVO,n("50"),com.surquillo.inventariocaja.modelo.caja.MedioPago.VISA,n("100")),n("50"),"venta"));
        assertTrue(caja.consultar(turno).movimientos().isEmpty()); assertEquals(0,caja.consultar(turno).turno().version());
    }
    @Test void cajaYStockSeConfirmanOReviertenJuntos() throws Exception {
        var unidad=new UnidadTrabajoPostgres(ds);
        var caja=new CajaService(new TurnoPostgresRepository(unidad),Clock.systemUTC());
        var inventarioCompartido=new InventarioService(new StockPostgresRepository(unidad),Clock.systemUTC());
        var turno=UUID.randomUUID(); var cobro=UUID.randomUUID();
        caja.abrir(UUID.randomUUID(),turno,"Caja A",java.time.LocalDate.now(),"Encargado",n("0"));
        inventarioCompartido.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        var pagos=java.util.Map.of(com.surquillo.inventariocaja.modelo.caja.MedioPago.EFECTIVO,n("20"));
        assertThrows(ValidacionException.class,()->unidad.ejecutar(()-> {
            caja.cobrar(cobro,turno,n("20"),pagos,n("20"),"B1");
            inventarioCompartido.venta(UUID.randomUUID(),clave,n("-1"),"B1"); return null;
        }));
        assertTrue(caja.consultar(turno).movimientos().isEmpty()); igual("2",inventarioCompartido.consultar(clave).orElseThrow().fisico());
        unidad.ejecutar(()-> {
            caja.cobrar(cobro,turno,n("20"),pagos,n("20"),"B1");
            inventarioCompartido.venta(UUID.randomUUID(),clave,n("1"),"B1"); return null;
        });
        igual("1",inventarioCompartido.consultar(clave).orElseThrow().fisico()); assertEquals(1,caja.consultar(turno).movimientos().size());
    }
}
