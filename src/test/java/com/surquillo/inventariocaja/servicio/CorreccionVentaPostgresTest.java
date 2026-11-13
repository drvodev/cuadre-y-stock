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
class CorreccionVentaPostgresTest {
    PGSimpleDataSource ds;String esquema;UUID original,turnoAnterior,turno;
    VentaService ventas;CajaService caja;CorreccionVentaService correcciones;StockPostgresRepository stock;CatalogoService catalogo;
    TurnoRepository.Estado cierreAnterior;
    Clock reloj=Clock.fixed(Instant.parse("2026-11-12T14:00:00Z"),ZoneOffset.UTC);
    BigDecimal n(String v){return new BigDecimal(v);}
    @BeforeEach void preparar()throws Exception {
        ds=new PGSimpleDataSource();ds.setURL(System.getenv("STOCK_TEST_URL"));ds.setUser(System.getenv("STOCK_TEST_USER"));ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        esquema="prueba_"+UUID.randomUUID().toString().replace("-","");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("CREATE SCHEMA "+esquema);}
        ds.setCurrentSchema(esquema);MigracionesPostgres.aplicar(ds);
        var ayer=Clock.fixed(Instant.parse("2026-11-11T14:00:00Z"),ZoneOffset.UTC);
        stock=new StockPostgresRepository(ds);catalogo=new CatalogoService(new ProductoPostgresRepository(ds));
        var inventario=new InventarioService(stock,ayer);caja=new CajaService(new TurnoPostgresRepository(ds),ayer);ventas=new VentaService(ds,ayer);
        for(String sku:List.of("ORIGINAL","NUEVO")){
            catalogo.crear(new Variante(sku,sku,"","","","","unidad",false,Variante.Origen.COMPRADO,null,"",n("50"),null,true,0));
            inventario.iniciar(UUID.randomUUID(),new ClaveStock(sku,"Surquillo"),false,n("10"),"");
        }
        turnoAnterior=UUID.randomUUID();caja.abrir(UUID.randomUUID(),turnoAnterior,"Surquillo",LocalDate.of(2026,11,11),"Anterior",n("0"));
        original=UUID.randomUUID();ventas.registrar(UUID.randomUUID(),original,turnoAnterior,Operacion.Modalidad.VENTA,new Documento(Documento.Tipo.NOTA_VENTA,"N1",true),"","",
            List.of(new ItemOperacion("ORIGINAL","Surquillo","Material original","unidad",n("2"),n("50"),"")),Map.of(MedioPago.EFECTIVO,n("100")),n("100"),Map.of());
        cierreAnterior=caja.cerrar(UUID.randomUUID(),turnoAnterior,caja.consultar(turnoAnterior).turno().version(),n("100"),"Anterior","");
        caja=new CajaService(new TurnoPostgresRepository(ds),reloj);ventas=new VentaService(ds,reloj);correcciones=new CorreccionVentaService(ds,reloj);
        turno=UUID.randomUUID();caja.abrir(UUID.randomUUID(),turno,"Surquillo",LocalDate.of(2026,11,12),"Actual",n("100"));
    }
    @AfterEach void limpiar()throws Exception{
        if(ds==null||esquema==null)return;if(!esquema.matches("prueba_[a-f0-9]{32}"))throw new IllegalStateException("Esquema inválido");
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+esquema+" CASCADE");}
    }
    void igual(String v,BigDecimal real){assertEquals(0,n(v).compareTo(real));}
    BigDecimal fisico(String sku){return stock.buscar(new ClaveStock(sku,"Surquillo")).orElseThrow().fisico();}
    List<DevolucionItem> devolver(String cantidad,boolean reingresar){return List.of(new DevolucionItem(0,n(cantidad),reingresar));}
    CorreccionVenta devolucion(UUID solicitud,UUID id,String nota,String cantidad,String importe,boolean reingresar){
        return correcciones.registrar(solicitud,id,original,turno,CorreccionVenta.Tipo.DEVOLUCION,nota,"Acuerdo con el cliente",devolver(cantidad,reingresar),
            n(importe),List.of(),null,n(importe).signum()==0?Map.of():Map.of(MedioPago.EFECTIVO,n(importe)),n("0"));
    }
    CorreccionVenta cambio(String nuevo,String reconocido,String pago,String recibido,String documento){
        return correcciones.registrar(UUID.randomUUID(),UUID.randomUUID(),original,turno,CorreccionVenta.Tipo.CAMBIO,"CAMBIO-"+documento,"Cambio acordado",devolver("2",true),n(reconocido),
            List.of(new ItemOperacion("NUEVO","Surquillo","Reemplazo","unidad",n("1"),n(nuevo),"")),new Documento(Documento.Tipo.NOTA_VENTA,documento,true),
            n(pago).signum()==0?Map.of():Map.of(MedioPago.EFECTIVO,n(pago)),n(recibido));
    }
    @Test void devuelveNoventaDeCienConNotaSinAlterarCierreYRecuperaReintentoCerrado(){
        var solicitud=UUID.randomUUID();var id=UUID.randomUUID();var nota=devolucion(solicitud,id,"DEV-1","2","90",true);
        igual("100",nota.valorOriginal());igual("90",nota.reconocido());igual("10",nota.noDevuelto());igual("-90",nota.diferencia());
        igual("10",caja.efectivoEsperado(turno));igual("10",fisico("ORIGINAL"));
        assertEquals(Operacion.Estado.ANULADA,ventas.consultar(original).estado());igual("0",ventas.consultar(original).saldo());
        assertEquals(cierreAnterior,caja.consultar(turnoAnterior));assertEquals("Actual",nota.encargado());
        assertEquals("2 unidad · Material original",new LibroCajaRepository(ds).documentos(turno).get(solicitud).items());
        caja.cerrar(UUID.randomUUID(),turno,caja.consultar(turno).turno().version(),n("10"),"Actual","");
        correcciones=new CorreccionVentaService(ds,Clock.systemUTC());
        assertEquals(nota,devolucion(solicitud,id,"DEV-1","2.000","90.00",true));
        assertEquals(1,correcciones.listar(original,0,100).size());
        assertThrows(ValidacionException.class,()->devolucion(solicitud,id,"DEV-1","2","80",true));
    }
    @Test void cantidadesParcialesNoPermitenDevolverDosVecesNiUsarAnulacionCompleta(){
        devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","1","45",true);
        igual("1",correcciones.pendientes(original).getFirst().cantidad());igual("55",caja.efectivoEsperado(turno));igual("9",fisico("ORIGINAL"));
        assertEquals(Operacion.Estado.DEVOLUCION_PARCIAL,ventas.consultar(original).estado());igual("0",ventas.consultar(original).saldo());
        assertThrows(ValidacionException.class,()->ventas.anular(UUID.randomUUID(),original,turno,"Duplicar",Map.of(MedioPago.EFECTIVO,n("100")),Set.of(0)));
        assertThrows(ValidacionException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D2","2","50",true));
        assertThrows(ValidacionException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D2","1","55",true));
        devolucion(UUID.randomUUID(),UUID.randomUUID(),"D2","1","40",true);
        igual("15",caja.efectivoEsperado(turno));igual("10",fisico("ORIGINAL"));assertTrue(correcciones.pendientes(original).isEmpty());
        assertEquals(cierreAnterior,caja.consultar(turnoAnterior));assertEquals(2,correcciones.listar(original,0,100).size());
    }
    @Test void productoNoVendibleNoSeReingresaYLimitaElImporteReconocido(){
        assertThrows(ValidacionException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","2","101",false));
        assertTrue(correcciones.listar(original,0,100).isEmpty());igual("100",caja.efectivoEsperado(turno));
        devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","2","90",false);igual("8",fisico("ORIGINAL"));igual("10",caja.efectivoEsperado(turno));
    }
    @Test void cambioConAdicionalSoloCobraDiferenciaYConservaCredito(){
        var nota=cambio("120","90","30","50","NC1");
        igual("30",nota.diferencia());igual("130",caja.efectivoEsperado(turno));igual("10",fisico("ORIGINAL"));igual("9",fisico("NUEVO"));
        var nueva=ventas.consultar(nota.reemplazo());igual("120",nueva.total());igual("120",nueva.abonado());igual("0",nueva.saldo());
        assertEquals(Operacion.Estado.CAMBIADA,ventas.consultar(original).estado());assertEquals(1,caja.consultar(turno).movimientos().size());
        igual("30",caja.consultar(turno).movimientos().getFirst().importes().get(MedioPago.EFECTIVO));
        assertEquals(cierreAnterior,caja.consultar(turnoAnterior));
    }
    @Test void cambioConReintegroSoloDevuelveLaDiferencia(){
        var nota=cambio("60","90","30","0","NC1");
        igual("-30",nota.diferencia());igual("70",caja.efectivoEsperado(turno));igual("60",ventas.consultar(nota.reemplazo()).total());
        assertEquals(MovimientoCaja.Tipo.DEVOLUCION,caja.consultar(turno).movimientos().getFirst().tipo());assertEquals(cierreAnterior,caja.consultar(turnoAnterior));
    }
    @Test void cambioSinDiferenciaConservaNotaSinInventarMovimientoDeDinero(){
        var nota=cambio("90","90","0","0","NC1");
        igual("0",nota.diferencia());igual("100",caja.efectivoEsperado(turno));assertTrue(caja.consultar(turno).movimientos().isEmpty());
        igual("9",fisico("NUEVO"));assertNotNull(nota.reemplazo());assertEquals(1,correcciones.listar(original,0,100).size());
    }
    @Test void falloSqlTardioRevierteNotaEstadoDineroYReingreso()throws Exception{
        try(var c=ds.getConnection();var s=c.createStatement()){s.execute("ALTER TABLE venta_correccion_item ADD CONSTRAINT rechazo_prueba CHECK(cantidad<1)");}
        assertThrows(PersistenciaException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","2","90",true));
        igual("100",caja.efectivoEsperado(turno));igual("8",fisico("ORIGINAL"));assertTrue(correcciones.listar(original,0,100).isEmpty());
        assertEquals(Operacion.Estado.ENTREGADA,ventas.consultar(original).estado());assertTrue(caja.consultar(turno).movimientos().isEmpty());
    }
    @Test void notaODocumentoDuplicadosNoDejanEfectosParciales(){
        assertThrows(ValidacionException.class,()->cambio("120","90","30","30","N1"));
        igual("100",caja.efectivoEsperado(turno));igual("8",fisico("ORIGINAL"));igual("10",fisico("NUEVO"));
        devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","1","45",true);
        assertThrows(ValidacionException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","1","45",true));
        igual("55",caja.efectivoEsperado(turno));igual("9",fisico("ORIGINAL"));assertEquals(1,correcciones.listar(original,0,100).size());
    }
    @Test void dosDevolucionesConcurrentesNoReutilizanLaMismaCantidad()throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var inicio=new java.util.concurrent.CountDownLatch(1);
        try{
            java.util.concurrent.Callable<Boolean> accion=()->{inicio.await();try{devolucion(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID().toString(),"2","90",true);return true;}catch(ValidacionException esperado){return false;}};
            var a=pool.submit(accion);var b=pool.submit(accion);inicio.countDown();
            assertNotEquals(a.get(30,java.util.concurrent.TimeUnit.SECONDS),b.get(30,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        igual("10",caja.efectivoEsperado(turno));igual("10",fisico("ORIGINAL"));assertEquals(1,correcciones.listar(original,0,100).size());
    }
    @Test void fraccionesDistribuyenCentimosSinDuplicarElValorOriginal(){
        UUID libre=UUID.randomUUID();ventas.registrar(UUID.randomUUID(),libre,turno,Operacion.Modalidad.VENTA,new Documento(Documento.Tipo.BOLETA,"MICRO",true),"","",
            List.of(new ItemOperacion("","","Ítem fraccionado","metro",n("1"),n("0.01"),"")),Map.of(MedioPago.EFECTIVO,n("0.01")),n("0.01"),Map.of());
        for(int i=0;i<3;i++){
            var seleccion=List.of(new DevolucionItem(0,n(i==2?"0.334":"0.333"),false));
            BigDecimal valor=correcciones.valorarDevolucion(libre,seleccion);igual(i==1?"0.01":"0",valor);
            correcciones.registrar(UUID.randomUUID(),UUID.randomUUID(),libre,turno,CorreccionVenta.Tipo.DEVOLUCION,"MICRO-"+i,"Devolución fraccionada",seleccion,valor,List.of(),null,
                valor.signum()==0?Map.of():Map.of(MedioPago.EFECTIVO,valor),n("0"));
        }
        igual("100",caja.efectivoEsperado(turno));assertEquals(Operacion.Estado.ANULADA,ventas.consultar(libre).estado());
        assertTrue(correcciones.pendientes(libre).isEmpty());igual("8",fisico("ORIGINAL"));
    }
    @Test void turnoCerradoYPagosPorImporteCompletoSeRechazan(){
        assertThrows(ValidacionException.class,()->cambio("120","90","120","120","NC1"));
        caja.cerrar(UUID.randomUUID(),turno,caja.consultar(turno).turno().version(),n("100"),"Actual","");
        assertThrows(ValidacionException.class,()->devolucion(UUID.randomUUID(),UUID.randomUUID(),"D1","2","90",true));
        assertTrue(correcciones.listar(original,0,100).isEmpty());igual("8",fisico("ORIGINAL"));
    }
    @Test void cambioParcialPermiteDevolverDespuesSoloLoRestante(){
        var nota=correcciones.registrar(UUID.randomUUID(),UUID.randomUUID(),original,turno,CorreccionVenta.Tipo.CAMBIO,"C1","Cambio parcial",devolver("1",true),n("45"),
            List.of(new ItemOperacion("NUEVO","Surquillo","Reemplazo","unidad",n("1"),n("60"),"")),new Documento(Documento.Tipo.NOTA_VENTA,"NC1",true),
            Map.of(MedioPago.EFECTIVO,n("15")),n("15"));
        assertEquals(Operacion.Estado.CAMBIO_PARCIAL,ventas.consultar(original).estado());
        igual("1",correcciones.pendientes(original).getFirst().cantidad());igual("115",caja.efectivoEsperado(turno));
        devolucion(UUID.randomUUID(),UUID.randomUUID(),"D2","1","40",true);
        igual("75",caja.efectivoEsperado(turno));igual("10",fisico("ORIGINAL"));igual("9",fisico("NUEVO"));
        igual("60",ventas.consultar(nota.reemplazo()).total());assertEquals(2,correcciones.listar(original,0,100).size());
    }
    @Test void separacionNoEntregadaSeRechazaYTrasEntregaAdmiteImporteManual(){
        UUID separacion=UUID.randomUUID();
        ventas.registrar(UUID.randomUUID(),separacion,turno,Operacion.Modalidad.SEPARACION,new Documento(Documento.Tipo.NOTA_VENTA,"SEP1",true),"Cliente ficticio","",
            List.of(new ItemOperacion("ORIGINAL","Surquillo","Material separado","unidad",n("1"),n("50"),"")),Map.of(MedioPago.EFECTIVO,n("50")),n("50"),Map.of());
        var seleccion=List.of(new DevolucionItem(0,n("1"),true));var solicitud=UUID.randomUUID();var id=UUID.randomUUID();
        assertThrows(ValidacionException.class,()->correcciones.registrar(solicitud,id,separacion,turno,CorreccionVenta.Tipo.DEVOLUCION,"DS1","Devolución",seleccion,n("40"),List.of(),null,Map.of(MedioPago.EFECTIVO,n("40")),n("0")));
        igual("150",caja.efectivoEsperado(turno));
        ventas.entregar(UUID.randomUUID(),separacion,null);
        correcciones.registrar(solicitud,id,separacion,turno,CorreccionVenta.Tipo.DEVOLUCION,"DS1","Devolución",seleccion,n("40"),List.of(),null,Map.of(MedioPago.EFECTIVO,n("40")),n("0"));
        igual("110",caja.efectivoEsperado(turno));igual("8",fisico("ORIGINAL"));assertEquals(cierreAnterior,caja.consultar(turnoAnterior));
    }
}
