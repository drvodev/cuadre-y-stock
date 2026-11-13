package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import com.surquillo.inventariocaja.excepcion.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="STOCK_TEST_DB",matches="true")
class CompraPostgresTest {
    PGSimpleDataSource ds;String esquema;UUID turno;CompraService compras;CajaService caja;InventarioService inventario;StockPostgresRepository stock;CatalogoService catalogo;
    Clock reloj=Clock.fixed(Instant.parse("2026-11-12T14:00:00Z"),ZoneOffset.UTC);
    BigDecimal n(String v){return new BigDecimal(v);}
    @BeforeEach void preparar()throws Exception {
        ds=new PGSimpleDataSource();ds.setURL(System.getenv("STOCK_TEST_URL"));ds.setUser(System.getenv("STOCK_TEST_USER"));ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        esquema="prueba_"+UUID.randomUUID().toString().replace("-","");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA "+esquema);}
        ds.setCurrentSchema(esquema);MigracionesPostgres.aplicar(ds);
        compras=new CompraService(ds,reloj);stock=new StockPostgresRepository(ds);inventario=new InventarioService(stock,reloj);
        catalogo=new CatalogoService(new ProductoPostgresRepository(ds));caja=new CajaService(new TurnoPostgresRepository(ds),reloj);
        catalogo.crear(new Variante("BOLSA","Bolsa ficticia","Materiales","","25 kg","","bolsa",false,Variante.Origen.COMPRADO,n("25"),"kg",n("20"),null,true,0,Map.of("color","Gris")));
        inventario.iniciar(UUID.randomUUID(),new ClaveStock("BOLSA","Surquillo"),false,n("5"),"");
        turno=UUID.randomUUID();caja.abrir(UUID.randomUUID(),turno,"Surquillo",LocalDate.of(2026,11,12),"Pruebas",n("100"));
    }
    @AfterEach void limpiar()throws Exception{
        if(ds==null||esquema==null)return;if(!esquema.matches("prueba_[a-f0-9]{32}"))throw new IllegalStateException("Esquema inválido");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+esquema+" CASCADE");}
    }
    DetalleCompra bolsa(String cantidad,String costo){return new DetalleCompra("BOLSA","Surquillo","Bolsa ficticia","bolsa",n(cantidad),costo==null?null:n(costo));}
    Compra registrar(UUID solicitud,UUID id,String proveedor,String numero,MovimientoCaja.Fuente fuente,List<DetalleCompra> items,BigDecimal total,Map<MedioPago,BigDecimal> pagos){
        return compras.registrar(solicitud,id,turno,proveedor,new Documento(Documento.Tipo.FACTURA,numero,true),fuente,total,items,pagos);
    }
    Compra comprar(String numero){return registrar(UUID.randomUUID(),UUID.randomUUID(),"Proveedor",numero,MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10")),n("20"),Map.of(MedioPago.EFECTIVO,n("20")));}
    void igual(String esperado,BigDecimal real){assertEquals(0,n(esperado).compareTo(real));}
    BigDecimal fisico(){return stock.buscar(new ClaveStock("BOLSA","Surquillo")).orElseThrow().fisico();}
    @Test void recibePagaYReintentaTrasCerrarSinDuplicarNiAlterarPrecioManual() {
        var solicitud=UUID.randomUUID();var id=UUID.randomUUID();
        var original=registrar(solicitud,id,"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10")),n("20"),Map.of(MedioPago.EFECTIVO,n("20")));
        igual("7",fisico());igual("80",caja.efectivoEsperado(turno));
        assertNull(catalogo.buscarSku("BOLSA").orElseThrow().costo());
        assertEquals(Map.of("color","Gris"),catalogo.buscarSku("BOLSA").orElseThrow().atributos());
        caja.cerrar(UUID.randomUUID(),turno,caja.consultar(turno).turno().version(),n("80"),"Pruebas","");
        compras=new CompraService(ds,Clock.systemUTC());
        var repetida=registrar(solicitud,id,"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2.000","10.00")),n("20.00"),Map.of(MedioPago.EFECTIVO,n("20.00")));
        assertEquals(original,repetida);igual("7",fisico());igual("80",caja.efectivoEsperado(turno));
        assertEquals(1,caja.consultar(turno).movimientos().size());assertEquals(1,compras.listar(0,100).size());
        assertThrows(ValidacionException.class,()->registrar(solicitud,id,"Proveedor","F2",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10")),n("20"),Map.of(MedioPago.EFECTIVO,n("20"))));
    }
    @Test void pagoExternoYCostoDesconocidoSeConservanSinReducirLaCaja() {
        var c=registrar(UUID.randomUUID(),UUID.randomUUID(),"Proveedor","F1",MovimientoCaja.Fuente.EXTERNA,List.of(bolsa("2",null)),n("35"),Map.of(MedioPago.TRANSFERENCIA,n("35")));
        igual("7",fisico());igual("100",caja.efectivoEsperado(turno));
        assertNull(new CompraService(ds,reloj).consultar(c.id()).items().getFirst().costoUnitario());
        assertEquals(MovimientoCaja.Fuente.EXTERNA,caja.consultar(turno).movimientos().getFirst().fuente());
    }
    @Test void documentoDuplicadoRevierteEntradaYPagoPeroDistintoProveedorEsValido() {
        comprar("F1");
        assertThrows(ValidacionException.class,()->registrar(UUID.randomUUID(),UUID.randomUUID(),"  PROVEEDOR "," f1 ",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10")),n("20"),Map.of(MedioPago.EFECTIVO,n("20"))));
        igual("7",fisico());igual("80",caja.efectivoEsperado(turno));assertEquals(1,compras.listar(0,100).size());
        registrar(UUID.randomUUID(),UUID.randomUUID(),"Otro proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10")),n("20"),Map.of(MedioPago.EFECTIVO,n("20")));
        igual("9",fisico());igual("60",caja.efectivoEsperado(turno));assertEquals(2,compras.listar(0,100).size());
    }
    @Test void falloDeSegundaLineaRevierteTodoYPermiteCorregirLaMismaSolicitud() {
        var solicitud=UUID.randomUUID();var id=UUID.randomUUID();
        var mala=new DetalleCompra("NO-EXISTE","Surquillo","Flete","unidad",n("1"),n("5"));
        assertThrows(ValidacionException.class,()->registrar(solicitud,id,"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10"),mala),n("25"),Map.of(MedioPago.EFECTIVO,n("25"))));
        igual("5",fisico());igual("100",caja.efectivoEsperado(turno));assertTrue(compras.listar(0,100).isEmpty());
        var libre=new DetalleCompra("","","Flete","unidad",n("1"),n("5"));
        registrar(solicitud,id,"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("2","10"),libre),n("25"),Map.of(MedioPago.EFECTIVO,n("25")));
        igual("7",fisico());igual("75",caja.efectivoEsperado(turno));
    }
    @Test void falloSqlTardioNoDejaDocumentoDineroNiEntrada()throws Exception {
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("ALTER TABLE compra_item ADD CONSTRAINT rechazo_prueba CHECK(descripcion<>'Bolsa ficticia')");}
        assertThrows(PersistenciaException.class,()->comprar("F1"));
        igual("5",fisico());igual("100",caja.efectivoEsperado(turno));assertTrue(compras.listar(0,100).isEmpty());assertTrue(caja.consultar(turno).movimientos().isEmpty());
    }
    @Test void unidadYFraccionSeValidanAntesDeConfirmarLaCompra() {
        var cambiada=new DetalleCompra("BOLSA","Surquillo","Bolsa","caja",n("1"),n("10"));
        assertThrows(ValidacionException.class,()->registrar(UUID.randomUUID(),UUID.randomUUID(),"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(cambiada),n("10"),Map.of(MedioPago.EFECTIVO,n("10"))));
        assertThrows(ValidacionException.class,()->registrar(UUID.randomUUID(),UUID.randomUUID(),"Proveedor","F1",MovimientoCaja.Fuente.CAJA,List.of(bolsa("1.5","10")),n("15"),Map.of(MedioPago.EFECTIVO,n("15"))));
        igual("5",fisico());igual("100",caja.efectivoEsperado(turno));assertTrue(compras.listar(0,100).isEmpty());
    }
    @Test void confirmacionesConcurrentesInicializanUnaVezYConservanPagosMixtos()throws Exception {
        catalogo.crear(new Variante("CAJA","Caja","","","1,5 m²","","caja",false,Variante.Origen.COMPRADO,n("1.5"),"m²",null,null,true,0));
        var solicitud=UUID.randomUUID();var id=UUID.randomUUID();var inicio=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Compra> accion=()->{inicio.await();return registrar(solicitud,id,"Proveedor","F1",MovimientoCaja.Fuente.CAJA,
                List.of(new DetalleCompra("CAJA","Surquillo","Caja","caja",n("3"),n("10"))),n("30"),Map.of(MedioPago.EFECTIVO,n("10"),MedioPago.DEPOSITO,n("20")));};
            var a=pool.submit(accion);var b=pool.submit(accion);inicio.countDown();
            assertEquals(a.get(30,java.util.concurrent.TimeUnit.SECONDS),b.get(30,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        igual("3",stock.buscar(new ClaveStock("CAJA","Surquillo")).orElseThrow().fisico());igual("90",caja.efectivoEsperado(turno));
        assertEquals(2,stock.historial(new ClaveStock("CAJA","Surquillo")).size());assertEquals(1,compras.listar(0,100).size());assertEquals(1,caja.consultar(turno).movimientos().size());
    }
    @Test void consultaPaginadaConservaDocumentoEItemsLibresConTextoEspecial() {
        var libre=new DetalleCompra("","","Flete 50%_! '","unidad",n("1"),n("10"));
        var c=registrar(UUID.randomUUID(),UUID.randomUUID(),"Proveedor ' OR 1=1 --","F1",MovimientoCaja.Fuente.CAJA,List.of(libre),n("10"),Map.of(MedioPago.EFECTIVO,n("10")));
        comprar("F2");
        var primera=compras.listar(0,1);assertEquals(1,primera.size());assertEquals(c.id(),primera.getFirst().id());
        var segunda=compras.listar(primera.getFirst().secuencia(),1);assertEquals(1,segunda.size());assertNotEquals(primera.getFirst().id(),segunda.getFirst().id());
        assertTrue(compras.listar(segunda.getFirst().secuencia(),1).isEmpty());
        assertEquals("Flete 50%_! '",compras.consultar(c.id()).items().getFirst().descripcion());
        igual("7",fisico());igual("70",caja.efectivoEsperado(turno));
    }
}
