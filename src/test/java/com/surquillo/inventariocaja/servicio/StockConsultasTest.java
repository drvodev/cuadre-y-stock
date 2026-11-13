package com.surquillo.inventariocaja.servicio;
import com.surquillo.inventariocaja.repositorio.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StockConsultasTest {
    @Test void memoriaPaginaYReconstruyeFisicoYReservaSinDuplicarReintentos() {
        verificarCorteYPaginas(new StockMemoriaRepository());
    }
    public static void verificarCorteYPaginas(StockRepository repositorio) {
        var clave=new ClaveStock("HISTORICO","Surquillo");var base=Instant.parse("2026-11-01T12:00:00Z");
        var inicial=new InventarioService(repositorio,Clock.fixed(base,ZoneOffset.UTC));
        inicial.iniciar(UUID.randomUUID(),clave,false,new BigDecimal("10"),"inicial");
        var reservas=new ReservaService(repositorio,Clock.fixed(base.plusSeconds(10),ZoneOffset.UTC),id->{});
        var solicitud=UUID.randomUUID();var reserva=UUID.randomUUID();var separacion=UUID.randomUUID();
        reservas.crear(solicitud,reserva,separacion,clave,new BigDecimal("3"),new BigDecimal("3"));
        new InventarioService(repositorio,Clock.fixed(base.plusSeconds(20),ZoneOffset.UTC))
            .entrada(UUID.randomUUID(),clave,MovimientoStock.Tipo.COMPRA,new BigDecimal("2"),"compra");
        new ReservaService(repositorio,Clock.fixed(base.plusSeconds(30),ZoneOffset.UTC),id->{})
            .cancelar(UUID.randomUUID(),reserva,clave);
        reservas.crear(solicitud,reserva,separacion,clave,new BigDecimal("3"),new BigDecimal("3"));
        assertTrue(inicial.consultarAlCorte(clave,base).isEmpty());
        var antesReserva=inicial.consultarAlCorte(clave,base.plusSeconds(10)).orElseThrow();
        igual("10",antesReserva.fisico());igual("0",antesReserva.reservado());
        var reservado=inicial.consultarAlCorte(clave,base.plusSeconds(11)).orElseThrow();
        igual("10",reservado.fisico());igual("3",reservado.reservado());igual("7",reservado.disponible());
        var entrada=inicial.consultarAlCorte(clave,base.plusSeconds(21)).orElseThrow();
        igual("12",entrada.fisico());igual("3",entrada.reservado());
        var cancelado=inicial.consultarAlCorte(clave,base.plusSeconds(31)).orElseThrow();
        igual("12",cancelado.fisico());igual("0",cancelado.reservado());
        var primera=inicial.historial(clave,0,2);assertEquals(2,primera.size());
        var segunda=inicial.historial(clave,primera.getLast().secuencia(),2);assertEquals(2,segunda.size());
        var ids=new HashSet<UUID>();primera.forEach(m->assertTrue(ids.add(m.movimiento().id())));segunda.forEach(m->assertTrue(ids.add(m.movimiento().id())));
        assertEquals(4,ids.size());assertTrue(inicial.historial(clave,segunda.getLast().secuencia(),2).isEmpty());
        assertThrows(com.surquillo.inventariocaja.excepcion.ValidacionException.class,()->inicial.historial(clave,-1,10));
        assertThrows(com.surquillo.inventariocaja.excepcion.ValidacionException.class,()->inicial.historial(clave,0,101));
        assertTrue(inicial.consultarAlCorte(new ClaveStock("OTRO","Surquillo"),base.plusSeconds(40)).isEmpty());
    }
    private static void igual(String esperado,BigDecimal real) { assertEquals(0,new BigDecimal(esperado).compareTo(real)); }
}
