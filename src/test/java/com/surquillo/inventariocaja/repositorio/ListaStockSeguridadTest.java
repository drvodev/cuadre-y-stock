package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.configuracion.MigracionesPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Usa exclusivamente un esquema desechable de una base de pruebas. */
@EnabledIfEnvironmentVariable(named = "STOCK_TEST_DB", matches = "true")
class ListaStockSeguridadTest {
    @Test void consultasMaliciosasNoAmplianResultadosNiModificanDatos() throws Exception {
        var ds = new PGSimpleDataSource();
        ds.setURL(System.getenv("STOCK_TEST_URL"));
        ds.setUser(System.getenv("STOCK_TEST_USER"));
        ds.setPassword(System.getenv("STOCK_TEST_PASSWORD"));
        String esquema = "prueba_" + UUID.randomUUID().toString().replace("-", "");
        boolean creado = false;
        try {
            try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + esquema); }
            creado = true;
            ds.setCurrentSchema(esquema);
            MigracionesPostgres.aplicar(ds);
            try (var c = ds.getConnection(); var s = c.createStatement()) {
                s.executeUpdate("INSERT INTO catalogo_variante(sku,descripcion,categoria,marca,presentacion,tipo,unidad,permite_fraccion,origen,unidad_contenido,activo,version) VALUES ('A','Producto uno','','','','','unidad',false,'COMPRADO','',true,0),('B','Producto dos','','','','','unidad',false,'COMPRADO','',true,0)");
                s.executeUpdate("INSERT INTO stock_saldo(sku,almacen,permite_fraccion,fisico,reservado) VALUES ('A','Principal',false,3,0)");
            }
            var repo = new ListaStockRepository(ds);
            assertEquals(2, repo.buscar("Principal", "Producto", "").size());
            for (String carga : new String[]{"' OR '1'='1", "x'; DROP TABLE catalogo_variante; --", "%_!"}) {
                assertTrue(repo.buscar("Principal", carga, "").isEmpty());
                long esperadas = java.util.List.of("A", "B").stream().filter(sku -> sku.compareTo(carga) > 0).count();
                assertEquals(esperadas, repo.buscar("Principal", "Producto", carga).size());
                var filas = repo.buscar(carga, "Producto uno", "");
                assertEquals(1, filas.size());
                assertNull(filas.get(0).disponible());
            }
            assertEquals(2, repo.buscar("Principal", "Producto", "").size());
            assertEquals(0, repo.buscar("Principal", "Producto uno", "").get(0).disponible().compareTo(new java.math.BigDecimal("3")));
        } finally {
            if (creado) {
                if (!esquema.matches("prueba_[a-f0-9]{32}")) throw new IllegalStateException("Esquema inválido");
                try (var c = ds.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + esquema + " CASCADE"); }
            }
        }
    }
}
