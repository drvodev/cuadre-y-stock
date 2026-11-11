package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.repositorio.StockMemoriaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResistenciaInventarioTest {
    private final StockMemoriaRepository repo = new StockMemoriaRepository();
    private final InventarioService inventario = new InventarioService(repo, Clock.systemUTC());
    private final ClaveStock clave = new ClaveStock("SKU", "Almacén");

    @Test void muchosReintentosSimultaneosAplicanUnaSolaEntrada() throws Exception {
        inventario.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ZERO, "");
        UUID solicitud = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(12);
        var inicio = new CountDownLatch(1);
        try {
            var resultados = new ArrayList<Future<?>>();
            for (int i = 0; i < 60; i++) resultados.add(pool.submit(() -> {
                inicio.await();
                return inventario.entrada(solicitud, clave, com.surquillo.inventariocaja.modelo.stock.MovimientoStock.Tipo.COMPRA, BigDecimal.ONE, "compra");
            }));
            inicio.countDown();
            for (var resultado : resultados) resultado.get(10, TimeUnit.SECONDS);
            assertEquals(0, BigDecimal.ONE.compareTo(inventario.consultar(clave).orElseThrow().fisico()));
            assertEquals(2, inventario.historial(clave).size());
            assertThrows(ValidacionException.class, () -> inventario.venta(solicitud, clave, BigDecimal.ONE, "compra"));
            assertEquals(2, inventario.historial(clave).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void despachoCompetitivoSoloSeAutorizaYRegistraUnaVez() throws Exception {
        inventario.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ONE, "");
        var autorizaciones = new AtomicInteger();
        var reservas = new ReservaService(repo, Clock.systemUTC(), id -> autorizaciones.incrementAndGet());
        var reserva = UUID.randomUUID();
        reservas.crear(UUID.randomUUID(), reserva, UUID.randomUUID(), clave, BigDecimal.ONE, BigDecimal.ONE);
        var pool = Executors.newFixedThreadPool(12);
        var inicio = new CountDownLatch(1);
        try {
            var resultados = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 40; i++) resultados.add(pool.submit(() -> {
                inicio.await();
                try { reservas.despachar(UUID.randomUUID(), reserva, clave); return true; }
                catch (ValidacionException esperado) { return false; }
            }));
            inicio.countDown();
            int exitos = 0;
            for (var resultado : resultados) if (resultado.get(10, TimeUnit.SECONDS)) exitos++;
            assertEquals(1, exitos);
            assertEquals(1, autorizaciones.get());
            var saldo = inventario.consultar(clave).orElseThrow();
            assertEquals(0, saldo.fisico().signum());
            assertEquals(0, saldo.reservado().signum());
            assertEquals(3, inventario.historial(clave).size());
        } finally { pool.shutdownNow(); }
    }

    @Test void falloDeAutorizacionNoConsumeReservaNiRegistraMovimiento() {
        inventario.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ONE, "");
        var reservas = new ReservaService(repo, Clock.systemUTC(), id -> { throw new ValidacionException("Pago rechazado"); });
        var reserva = UUID.randomUUID();
        reservas.crear(UUID.randomUUID(), reserva, UUID.randomUUID(), clave, BigDecimal.ONE, BigDecimal.ONE);
        UUID solicitud = UUID.randomUUID();
        for (int i = 0; i < 3; i++) assertThrows(ValidacionException.class, () -> reservas.despachar(solicitud, reserva, clave));
        var saldo = inventario.consultar(clave).orElseThrow();
        assertEquals(0, BigDecimal.ONE.compareTo(saldo.fisico()));
        assertEquals(0, BigDecimal.ONE.compareTo(saldo.reservado()));
        assertEquals(2, inventario.historial(clave).size());
    }
}
