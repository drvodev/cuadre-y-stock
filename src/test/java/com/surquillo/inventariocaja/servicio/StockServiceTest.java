package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.SaldoStock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockServiceTest {
    private final StockService servicio = new StockService();
    private BigDecimal n(String valor) { return new BigDecimal(valor); }
    private SaldoStock saldo(String fisico, String reservado, boolean fraccion) {
        return new SaldoStock(" sku-1 ", "Surquillo", fraccion, n(fisico), n(reservado));
    }
    private void igual(String esperado, BigDecimal real) { assertEquals(0, n(esperado).compareTo(real)); }

    @Test void entradaYVentaNoAlteranElSaldoOriginal() {
        var original = saldo("5", "0", false);
        igual("6", servicio.entrada(original, n("1")).fisico());
        igual("4", servicio.descontarVenta(original, n("1")).fisico());
        igual("5", original.fisico());
        assertEquals("SKU-1", original.sku());
    }
    @Test void ventaNuncaBajaDeCeroYRespetaReservas() {
        igual("0", servicio.descontarVenta(saldo("0", "0", false), n("1")).fisico());
        igual("0", servicio.descontarVenta(saldo("1", "0", false), n("2")).fisico());
        var resultado = servicio.descontarVenta(saldo("5", "3", false), n("4"));
        igual("3", resultado.fisico()); igual("0", resultado.disponible());
    }
    @Test void ajusteRequiereMotivoYNoReduceReservas() {
        var original = saldo("5", "2", false);
        igual("3", servicio.ajustar(original, n("3"), "conteo").fisico());
        assertThrows(ValidacionException.class, () -> servicio.ajustar(original, n("1"), "conteo"));
        assertThrows(ValidacionException.class, () -> servicio.ajustar(original, n("3"), " "));
    }
    @Test void validaCantidadesYPrecision() {
        var entero = saldo("1", "0", false);
        assertThrows(ValidacionException.class, () -> servicio.entrada(entero, n("0")));
        assertThrows(ValidacionException.class, () -> servicio.descontarVenta(entero, n("-1")));
        assertThrows(ValidacionException.class, () -> servicio.entrada(entero, n("0.5")));
        var fraccion = saldo("1.250", "0", true);
        igual("1.375", servicio.entrada(fraccion, n("0.125")).fisico());
        assertThrows(ValidacionException.class, () -> servicio.entrada(fraccion, n("0.0001")));
        assertThrows(ValidacionException.class, () -> saldo("1", "2", true));
    }
    @Test void propuestaInsuficienteRequiereAceptacionYDespachoDescuentaReserva() {
        var stock = saldo("2", "0", false);
        var propuesta = servicio.proponerReserva(stock, n("5"));
        igual("2", propuesta.disponible()); igual("3", propuesta.faltante());
        assertFalse(propuesta.suficiente()); igual("0", stock.reservado());
        var reservado = servicio.reservar(stock, propuesta.disponible());
        igual("2", reservado.fisico()); igual("2", reservado.reservado());
        var entregado = servicio.despacharReserva(reservado, n("2"));
        igual("0", entregado.fisico()); igual("0", entregado.reservado());
        assertThrows(ValidacionException.class, () -> servicio.reservar(stock, n("5")));
        assertThrows(ValidacionException.class, () -> servicio.despacharReserva(entregado, n("1")));
    }
    @Test void liberaReservaSinCambiarFisico() {
        var liberado = servicio.liberarReserva(saldo("5", "3", false), n("2"));
        igual("5", liberado.fisico()); igual("1", liberado.reservado()); igual("4", liberado.disponible());
    }
}
