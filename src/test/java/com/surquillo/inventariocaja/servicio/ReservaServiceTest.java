package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.StockMemoriaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservaServiceTest {
    final StockMemoriaRepository repo = new StockMemoriaRepository();
    final Clock clock = Clock.systemUTC();
    final InventarioService inventario = new InventarioService(repo,clock);
    final AtomicBoolean pagado = new AtomicBoolean(false);
    final ReservaService reservas = new ReservaService(repo,clock,id -> { if (!pagado.get()) throw new ValidacionException("Pago pendiente"); });
    final ClaveStock clave = new ClaveStock("SKU", "A");
    BigDecimal n(String s) { return new BigDecimal(s); }
    void igual(String s, BigDecimal valor) { assertEquals(0,n(s).compareTo(valor)); }

    @Test void cicloDeFaltanteIngresoPagoYDespachoUnico() {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        var propuesta = reservas.proponer(clave,n("5"));
        igual("2",propuesta.disponible()); igual("3",propuesta.faltante());
        var reservaId=UUID.randomUUID(); var separacion=UUID.randomUUID(); var crear=UUID.randomUUID();
        var r = reservas.crear(crear,reservaId,separacion,clave,n("5"),n("2"));
        assertEquals(r,reservas.crear(crear,reservaId,separacion,clave,n("5.0"),n("2")));
        igual("3",r.reserva().faltante()); igual("2",r.saldo().reservado());
        assertThrows(ValidacionException.class,()->reservas.despachar(UUID.randomUUID(),reservaId,clave));
        inventario.entrada(UUID.randomUUID(),clave,MovimientoStock.Tipo.COMPRA,n("3"),"compra");
        r = reservas.ampliar(UUID.randomUUID(),reservaId,clave,n("3"));
        igual("0",r.reserva().faltante()); igual("5",r.saldo().reservado());
        assertThrows(ValidacionException.class,()->reservas.despachar(UUID.randomUUID(),reservaId,clave));
        pagado.set(true);
        var despacho=UUID.randomUUID();
        r=reservas.despachar(despacho,reservaId,clave);
        igual("0",r.saldo().fisico()); igual("0",r.saldo().reservado());
        assertEquals(r,reservas.despachar(despacho,reservaId,clave));
        assertThrows(ValidacionException.class,()->reservas.despachar(UUID.randomUUID(),reservaId,clave));
        assertEquals(5,inventario.historial(clave).size());
    }
    @Test void cancelacionLiberaReservaYNoSeDuplica() {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("3"),"");
        var id=UUID.randomUUID(); reservas.crear(UUID.randomUUID(),id,UUID.randomUUID(),clave,n("3"),n("2"));
        var cancelacion=UUID.randomUUID(); var r=reservas.cancelar(cancelacion,id,clave);
        igual("3",r.saldo().disponible());
        assertEquals(r,reservas.cancelar(cancelacion,id,clave));
        assertThrows(ValidacionException.class,()->reservas.cancelar(UUID.randomUUID(),id,clave));
    }
    @Test void confirmarConStockCambiadoNoModificaDatos() {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("2"),"");
        var propuesta=reservas.proponer(clave,n("5"));
        inventario.venta(UUID.randomUUID(),clave,n("1"),"venta");
        var id=UUID.randomUUID();
        assertThrows(ValidacionException.class,()->reservas.crear(UUID.randomUUID(),id,UUID.randomUUID(),clave,n("5"),propuesta.disponible()));
        assertTrue(reservas.consultar(id).isEmpty()); igual("0",inventario.consultar(clave).orElseThrow().reservado());
    }
    @Test void solicitudesSimultaneasNoReservanElMismoStock() throws Exception {
        inventario.iniciar(UUID.randomUUID(),clave,false,n("1"),"");
        var inicio=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> trabajo=()->{ inicio.await(); try { reservas.crear(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),clave,n("1"),n("1")); return true; } catch (ValidacionException e) { return false; } };
            var a=pool.submit(trabajo); var b=pool.submit(trabajo); inicio.countDown();
            assertNotEquals(a.get(),b.get()); igual("1",inventario.consultar(clave).orElseThrow().reservado());
        } finally { pool.shutdownNow(); }
    }
}
