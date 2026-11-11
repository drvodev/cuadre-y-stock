package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.repositorio.TurnoMemoriaRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CajaServiceTest {
    final CajaService caja=new CajaService(new TurnoMemoriaRepository(),Clock.fixed(Instant.parse("2026-01-02T12:00:00Z"),ZoneOffset.UTC));
    final UUID turno=UUID.randomUUID();
    BigDecimal n(String s) { return new BigDecimal(s); }
    void abrir() { caja.abrir(UUID.randomUUID(),turno,"Caja A",LocalDate.of(2026,1,1),"Encargado",n("100")); }
    void egreso(MovimientoCaja.Tipo tipo,String importe) { caja.registrar(UUID.randomUUID(),turno,tipo,MovimientoCaja.Fuente.CAJA,Map.of(MedioPago.EFECTIVO,n(importe)),"referencia"); }
    void igual(String s,BigDecimal v) { assertEquals(0,n(s).compareTo(v)); }

    @Test void ejemploDelPlanCierraEn410ConDiferenciaMenos5() {
        abrir(); caja.cobrar(UUID.randomUUID(),turno,n("500"),Map.of(MedioPago.EFECTIVO,n("500")),n("500"),"cobro");
        egreso(MovimientoCaja.Tipo.COMPRA,"120"); egreso(MovimientoCaja.Tipo.GASTO,"20"); egreso(MovimientoCaja.Tipo.RETIRO,"50");
        igual("410",caja.efectivoEsperado(turno));
        var version=caja.consultar(turno).turno().version();
        var cierre=caja.cerrar(UUID.randomUUID(),turno,version,n("405"),"Encargado","Faltan cinco soles");
        igual("-5",cierre.cierre().diferencia()); assertTrue(cierre.turno().cerrado());
        assertEquals(LocalDate.of(2026,1,1),cierre.turno().fechaOperativa());
    }
    @Test void cobroCombinadoCalculaVueltoYExcluyeCuentaExterna() {
        abrir(); var solicitud=UUID.randomUUID();
        var pagos=Map.of(MedioPago.EFECTIVO,n("50"),MedioPago.VISA,n("100"));
        var cobro=caja.cobrar(solicitud,turno,n("150"),pagos,n("100"),"venta"); igual("50",cobro.vuelto());
        caja.cobrar(solicitud,turno,n("150"),pagos,n("100"),"venta");
        caja.registrar(UUID.randomUUID(),turno,MovimientoCaja.Tipo.GASTO,MovimientoCaja.Fuente.EXTERNA,Map.of(MedioPago.EFECTIVO,n("20")),"gasto externo");
        igual("150",caja.efectivoEsperado(turno)); igual("100",caja.flujo(caja.consultar(turno)).get(MedioPago.VISA));
        assertEquals(2,caja.consultar(turno).movimientos().size());
    }
    @Test void validaSobrepagoDigitalCantidadesYContenidoDelReintento() {
        abrir();
        assertThrows(ValidacionException.class,()->caja.cobrar(UUID.randomUUID(),turno,n("20"),Map.of(MedioPago.YAPE,n("21")),n("0"),"venta"));
        assertThrows(ValidacionException.class,()->caja.cobrar(UUID.randomUUID(),turno,n("0.001"),Map.of(MedioPago.EFECTIVO,n("0.001")),n("1"),"venta"));
        var id=UUID.randomUUID(); caja.registrar(id,turno,MovimientoCaja.Tipo.GASTO,MovimientoCaja.Fuente.CAJA,Map.of(MedioPago.EFECTIVO,n("1")),"gasto");
        assertThrows(ValidacionException.class,()->caja.registrar(id,turno,MovimientoCaja.Tipo.GASTO,MovimientoCaja.Fuente.CAJA,Map.of(MedioPago.EFECTIVO,n("2")),"gasto"));
        assertEquals(1,caja.consultar(turno).movimientos().size());
    }
    @Test void cierreObsoletoSeRechazaYCierreConfirmadoNoSeModifica() {
        abrir(); long version=caja.consultar(turno).turno().version(); egreso(MovimientoCaja.Tipo.GASTO,"5");
        assertThrows(ValidacionException.class,()->caja.cerrar(UUID.randomUUID(),turno,version,n("95"),"Encargado",""));
        long actual=caja.consultar(turno).turno().version();
        assertThrows(ValidacionException.class,()->caja.cerrar(UUID.randomUUID(),turno,actual,n("94"),"Encargado",""));
        var id=UUID.randomUUID(); var cierre=caja.cerrar(id,turno,actual,n("95"),"Encargado","");
        assertEquals(cierre,caja.cerrar(id,turno,actual,n("95"),"Encargado",""));
        assertThrows(ValidacionException.class,()->egreso(MovimientoCaja.Tipo.GASTO,"1"));
        assertEquals(cierre,caja.consultar(turno));
        caja.abrir(UUID.randomUUID(),UUID.randomUUID(),"Caja A",LocalDate.of(2026,1,2),"Otro",n("95"));
    }
    @Test void aperturaConcurrenteAdmiteSoloUnTurnoPorCaja() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> tarea=()->{ inicio.await(); try { caja.abrir(UUID.randomUUID(),UUID.randomUUID(),"Caja A",LocalDate.of(2026,1,1),"Encargado",n("0")); return true; } catch(ValidacionException e) { return false; } };
            var a=pool.submit(tarea); var b=pool.submit(tarea); inicio.countDown(); assertNotEquals(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void conciliaCobrosDevolucionesYEgresosDigitalesPorSeparado() {
        abrir();
        caja.cobrar(UUID.randomUUID(),turno,n("100"),Map.of(MedioPago.VISA,n("100")),n("0"),"venta");
        caja.registrar(UUID.randomUUID(),turno,MovimientoCaja.Tipo.DEVOLUCION,MovimientoCaja.Fuente.CAJA,Map.of(MedioPago.VISA,n("20")),"devolución");
        caja.registrar(UUID.randomUUID(),turno,MovimientoCaja.Tipo.GASTO,MovimientoCaja.Fuente.CAJA,Map.of(MedioPago.VISA,n("10")),"gasto");
        var esperado=new VerificacionMedio(n("100"),n("20"),n("10"),n("0"));
        assertEquals(esperado,caja.resumenDigital(turno).get(MedioPago.VISA));
        long v=caja.consultar(turno).turno().version();
        assertThrows(ValidacionException.class,()->caja.cerrar(UUID.randomUUID(),turno,v,n("100"),"Encargado",""));
        var distinta=new VerificacionMedio(n("99"),n("20"),n("10"),n("0"));
        assertThrows(ValidacionException.class,()->caja.cerrar(UUID.randomUUID(),turno,v,n("100"),"Encargado","",Map.of(MedioPago.VISA,distinta)));
        var cierre=caja.cerrar(UUID.randomUUID(),turno,v,n("100"),"Encargado","Operación Visa pendiente de cotejo",Map.of(MedioPago.VISA,distinta));
        assertTrue(cierre.cierre().digitales().get(MedioPago.VISA).tieneDiferencia()); igual("100",cierre.cierre().efectivoEsperado());
    }
    @Test void reintentoNoPuedeCambiarEfectivoRecibidoNiVuelto() {
        abrir(); var id=UUID.randomUUID(); var pagos=Map.of(MedioPago.EFECTIVO,n("10"));
        var original=caja.cobrar(id,turno,n("10"),pagos,n("10"),"venta");
        assertEquals(original,caja.cobrar(id,turno,n("10.00"),pagos,n("10.0"),"venta"));
        assertThrows(ValidacionException.class,()->caja.cobrar(id,turno,n("10"),pagos,n("20"),"venta"));
        assertEquals(1,caja.consultar(turno).movimientos().size()); igual("110",caja.efectivoEsperado(turno));
    }
}
