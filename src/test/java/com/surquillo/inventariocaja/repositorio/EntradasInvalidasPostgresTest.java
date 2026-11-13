package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import com.surquillo.inventariocaja.servicio.InventarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "STOCK_TEST_DB", matches = "true")
class EntradasInvalidasPostgresTest {
    @Test void erroresYReintentosNoDuplicanStockNiMovimientos() throws Exception {
        var ds = new PGSimpleDataSource();
        ds.setURL(System.getenv("STOCK_TEST_URL")); ds.setUser(System.getenv("STOCK_TEST_USER")); ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        String esquema = "prueba_" + UUID.randomUUID().toString().replace("-", "");
        boolean creado = false;
        try {
            try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + esquema); }
            creado = true; ds.setCurrentSchema(esquema); MigracionesPostgres.aplicar(ds);
            var repo = new StockPostgresRepository(ds);
            var servicio = new InventarioService(repo, Clock.systemUTC());
            var clave = new ClaveStock("A", "Almacén");
            servicio.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ONE, "");
            var solicitud = UUID.randomUUID();
            assertThrows(ValidacionException.class, () -> servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, new BigDecimal("0.5"), "compra"));
            assertThrows(ValidacionException.class, () -> servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, BigDecimal.ZERO, "compra"));
            assertEquals(1, repo.historial(clave).size());
            servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, new BigDecimal("2"), "compra");
            servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, new BigDecimal("2.0"), "compra");
            assertThrows(ValidacionException.class, () -> servicio.entrada(solicitud, clave, MovimientoStock.Tipo.COMPRA, new BigDecimal("3"), "compra"));
            assertEquals(2, repo.historial(clave).size());
            assertEquals(0, new BigDecimal("3").compareTo(repo.buscar(clave).orElseThrow().fisico()));
        } finally {
            if (creado) {
                if (!esquema.matches("prueba_[a-f0-9]{32}")) throw new IllegalStateException("Esquema inválido");
                try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + esquema + " CASCADE"); }
            }
        }
    }
}
