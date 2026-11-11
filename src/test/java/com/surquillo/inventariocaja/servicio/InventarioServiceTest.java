package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock.Tipo;
import com.surquillo.inventariocaja.repositorio.StockMemoriaRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventarioServiceTest {
    private final InventarioService servicio = new InventarioService(new StockMemoriaRepository(), Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC));
    private final ClaveStock clave = new ClaveStock(" abc ", "Principal");
    private BigDecimal n(String v) { return new BigDecimal(v); }
    private void igual(String valor, BigDecimal actual) { assertEquals(0, n(valor).compareTo(actual)); }

    @Test void historialRegistraEntradasAjustesYDescuentoReal() {
        servicio.iniciar(UUID.randomUUID(), clave, false, n("0"), "inicial");
        servicio.entrada(UUID.randomUUID(), clave, Tipo.COMPRA, n("5"), "compra-1");
        servicio.entrada(UUID.randomUUID(), clave, Tipo.FABRICACION, n("2"), "fabricación-1");
        servicio.ajustar(UUID.randomUUID(), clave, n("3"), "conteo");
        var venta = servicio.venta(UUID.randomUUID(), clave, n("5"), "venta-1");
        igual("0", venta.saldo().fisico()); igual("-3", venta.movimiento().cambioFisico());
        igual("5", venta.movimiento().cantidadSolicitada());
        assertEquals(5, servicio.historial(clave).size());
        assertTrue(servicio.stockBajo(clave, n("1")));
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), venta.movimiento().fecha());
    }
    @Test void reintentoEsIdempotenteYReutilizacionDistintaSeRechaza() {
        servicio.iniciar(UUID.randomUUID(), clave, true, n("2"), "");
        var id = UUID.randomUUID();
        var primera = servicio.venta(id, clave, n("1.0"), "venta");
        assertEquals(primera, servicio.venta(id, clave, n("1.00"), "venta"));
        assertThrows(ValidacionException.class, () -> servicio.venta(id, clave, n("2"), "venta"));
        igual("1", servicio.consultar(clave).orElseThrow().fisico());
        assertEquals(2, servicio.historial(clave).size());
    }
    @Test void almacenesAisladosYErroresNoRegistranMovimiento() {
        var otra = new ClaveStock("ABC", "Otro");
        servicio.iniciar(UUID.randomUUID(), clave, false, n("1"), "");
        servicio.iniciar(UUID.randomUUID(), otra, false, n("8"), "");
        assertThrows(ValidacionException.class, () -> servicio.ajustar(UUID.randomUUID(), clave, n("2"), ""));
        assertThrows(ValidacionException.class, () -> servicio.entrada(UUID.randomUUID(), clave, Tipo.COMPRA, n("-1"), ""));
        servicio.venta(UUID.randomUUID(), clave, n("2"), "");
        igual("8", servicio.consultar(otra).orElseThrow().fisico());
        assertEquals(2, servicio.historial(clave).size());
    }
    @Test void ventasConcurrentesNoGeneranStockNegativo() throws Exception {
        servicio.iniciar(UUID.randomUUID(), clave, false, n("1"), "");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            Callable<Void> venta = () -> { inicio.await(); servicio.venta(UUID.randomUUID(), clave, n("1"), ""); return null; };
            var a = pool.submit(venta); var b = pool.submit(venta); inicio.countDown(); a.get(); b.get();
            igual("0", servicio.consultar(clave).orElseThrow().fisico());
            var movimientos = servicio.historial(clave);
            igual("-1", movimientos.get(1).cambioFisico().add(movimientos.get(2).cambioFisico()));
        } finally { pool.shutdownNow(); }
    }
}
