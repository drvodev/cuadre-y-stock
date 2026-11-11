package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.modelo.stock.MovimientoStock;
import com.surquillo.inventariocaja.repositorio.StockMemoriaRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InvariantesInventarioTest {
    @Test void secuenciaReproducibleMantieneSaldoEHistorialInclusoConSolicitudesInvalidas() {
        var repo = new StockMemoriaRepository();
        var servicio = new InventarioService(repo, Clock.systemUTC());
        var clave = new ClaveStock("articulo", "almacen");
        servicio.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ZERO, "");
        var random = new Random(20261110L);
        int esperado = 0;
        int movimientos = 1;
        for (int i = 0; i < 1000; i++) {
            int cantidad = random.nextInt(12) - 3;
            int tipo = random.nextInt(3);
            int saldoAnterior = esperado;
            int registrosAnteriores = movimientos;
            BigDecimal valor = BigDecimal.valueOf(cantidad);
            if (cantidad < 0 || (cantidad == 0 && tipo != 2)) {
                assertThrows(ValidacionException.class, () -> aplicar(servicio, clave, tipo, valor));
            } else {
                aplicar(servicio, clave, tipo, valor);
                esperado = switch (tipo) {
                    case 0 -> esperado + cantidad;
                    case 1 -> Math.max(0, esperado - cantidad);
                    default -> cantidad;
                };
                movimientos++;
            }
            var saldo = servicio.consultar(clave).orElseThrow();
            assertEquals(0, BigDecimal.valueOf(esperado).compareTo(saldo.fisico()), "paso " + i);
            assertTrue(saldo.disponible().signum() >= 0, "paso " + i);
            assertEquals(movimientos, servicio.historial(clave).size(), "paso " + i);
            if (movimientos == registrosAnteriores) assertEquals(saldoAnterior, esperado);
        }
        var historial = servicio.historial(clave);
        BigDecimal suma = historial.stream().map(MovimientoStock::cambioFisico).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, suma.compareTo(servicio.consultar(clave).orElseThrow().fisico()));
        for (int i = 1; i < historial.size(); i++) {
            assertEquals(0, historial.get(i - 1).fisicoPosterior().compareTo(historial.get(i).fisicoAnterior()));
        }
    }

    private static void aplicar(InventarioService s, ClaveStock clave, int tipo, BigDecimal cantidad) {
        switch (tipo) {
            case 0 -> s.entrada(UUID.randomUUID(), clave, MovimientoStock.Tipo.COMPRA, cantidad, "compra");
            case 1 -> s.venta(UUID.randomUUID(), clave, cantidad, "venta");
            default -> s.ajustar(UUID.randomUUID(), clave, cantidad, "conteo");
        }
    }
}
