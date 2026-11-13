package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.repositorio.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EntradasInvalidasTest {
    private static BigDecimal n(String valor) { return new BigDecimal(valor); }

    @Test void cobrosInvalidosNoAlteranCajaYSePuedeCorregirLaSolicitud() {
        var caja = new CajaService(new TurnoMemoriaRepository(), Clock.systemUTC());
        var turno = UUID.randomUUID();
        caja.abrir(UUID.randomUUID(), turno, "Principal", LocalDate.of(2026, 1, 1), "Ana", n("10"));
        var solicitud = UUID.randomUUID();
        assertThrows(ValidacionException.class, () -> caja.cobrar(solicitud, turno, n("10"), Map.of(), n("10"), "venta"));
        assertThrows(ValidacionException.class, () -> caja.cobrar(solicitud, turno, n("10"), Map.of(MedioPago.EFECTIVO, n("9")), n("10"), "venta"));
        assertThrows(ValidacionException.class, () -> caja.cobrar(solicitud, turno, n("10"), Map.of(MedioPago.EFECTIVO, n("10")), n("9"), "venta"));
        assertThrows(ValidacionException.class, () -> caja.cobrar(solicitud, turno, n("10.001"), Map.of(MedioPago.EFECTIVO, n("10.001")), n("11"), "venta"));
        assertEquals(0, caja.consultar(turno).movimientos().size());
        assertEquals(0, n("10").compareTo(caja.efectivoEsperado(turno)));
        var cobro = caja.cobrar(solicitud, turno, n("10"), Map.of(MedioPago.EFECTIVO, n("10")), n("20"), "venta");
        assertEquals(0, n("10").compareTo(cobro.vuelto()));
        assertEquals(cobro, caja.cobrar(solicitud, turno, n("10.00"), Map.of(MedioPago.EFECTIVO, n("10.00")), n("20"), "venta"));
        assertEquals(1, caja.consultar(turno).movimientos().size());
    }

    @Test void stockInvalidoNoDejaHistorialYElMismoIdPuedeCorregirse() {
        var servicio = new InventarioService(new StockMemoriaRepository(), Clock.systemUTC());
        var clave = new ClaveStock("A", "Principal");
        servicio.iniciar(UUID.randomUUID(), clave, false, n("3"), "");
        var solicitud = UUID.randomUUID();
        for (String invalida : new String[]{"0", "-1", "0.5"}) {
            assertThrows(ValidacionException.class, () -> servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, n(invalida), "compra"));
        }
        assertEquals(1, servicio.historial(clave).size());
        assertEquals(0, n("3").compareTo(servicio.consultar(clave).orElseThrow().fisico()));
        servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, n("2"), "compra");
        assertEquals(2, servicio.historial(clave).size());
        assertEquals(0, n("5").compareTo(servicio.consultar(clave).orElseThrow().fisico()));
        assertThrows(ValidacionException.class, () -> servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, n("3"), "compra"));
        assertEquals(2, servicio.historial(clave).size());
    }

    @Test void duplicadosNormalizadosYCaracteristicasInvalidasNoCreanRegistros() {
        var catalogo = new CatalogoService(new ProductoMemoriaRepository());
        var original = catalogo.crearOpcion(ListaCatalogo.MARCA, " Marca   Uno ");
        assertThrows(ValidacionException.class, () -> catalogo.crearOpcion(ListaCatalogo.MARCA, "marca uno"));
        assertThrows(ValidacionException.class, () -> catalogo.crearOpcion(ListaCatalogo.MARCA, "   "));
        assertThrows(ValidacionException.class, () -> AtributosCatalogo.normalizar(Map.of("Color", "Azul", " color ", "Rojo")));
        assertEquals(1, catalogo.listarOpciones(ListaCatalogo.MARCA, "", "", 10, true).size());
        assertEquals(1, catalogo.historialOpcion(original.id(), 0, 10).size());
    }

    @Test void itemsConDatosIncorrectosSeRechazanAntesDeUnaOperacion() {
        assertThrows(ValidacionException.class, () -> new ItemOperacion("", "", " ", "unidad", n("1"), n("1"), ""));
        assertThrows(ValidacionException.class, () -> new ItemOperacion("A", "", "Artículo", "unidad", n("1"), n("1"), ""));
        assertThrows(ValidacionException.class, () -> new ItemOperacion("", "", "Artículo", "unidad", n("0"), n("1"), ""));
        assertThrows(ValidacionException.class, () -> new ItemOperacion("", "", "Artículo", "unidad", n("0.0001"), n("1"), ""));
        assertThrows(ValidacionException.class, () -> new ItemOperacion("", "", "Artículo", "unidad", n("1"), n("1.001"), ""));
        assertThrows(ValidacionException.class, () -> new ItemOperacion("", "", "Artículo", "unidad", n("1"), null, ""));
    }
}
