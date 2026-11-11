package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.servicio.InventarioService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica la separación SQL/parámetros con JDBC simulado; no ejecuta PostgreSQL. */
class StockPostgresSqlInjectionTest {
    private record Sentencia(String sql, Map<Integer, Object> parametros) { }
    private final List<Sentencia> sentencias = new ArrayList<>();

    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> tipo, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler);
    }

    private DataSource baseSimulada() {
        return mock(DataSource.class, (ds, metodo, args) -> {
            if (!metodo.getName().equals("getConnection")) throw new UnsupportedOperationException(metodo.getName());
            return mock(Connection.class, (c, m, a) -> switch (m.getName()) {
                case "prepareStatement" -> preparar((String) a[0]);
                case "setAutoCommit", "commit", "rollback", "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            });
        });
    }

    private PreparedStatement preparar(String sql) {
        Map<Integer, Object> parametros = new HashMap<>();
        sentencias.add(new Sentencia(sql, parametros));
        return mock(PreparedStatement.class, (p, m, a) -> {
            if (m.getName().startsWith("set") && a != null && a.length == 2 && a[0] instanceof Integer) {
                parametros.put((Integer) a[0], a[1]);
                return null;
            }
            return switch (m.getName()) {
                case "executeQuery" -> mock(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
                    case "next" -> false;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(rm.getName());
                });
                case "execute" -> true;
                case "executeUpdate" -> 1;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            };
        });
    }

    @Test void busquedasMantienenLasEntradasComoParametros() {
        var repo = new StockPostgresRepository(baseSimulada());
        for (String carga : List.of("' OR '1'='1", "x'; DROP TABLE stock_saldo; --", "x' UNION SELECT NULL --")) {
            var clave = new ClaveStock(carga, carga);
            sentencias.clear();
            assertTrue(repo.buscar(clave).isEmpty());
            assertTrue(repo.historial(clave).isEmpty());
            assertEquals(2, sentencias.size());
            for (var sentencia : sentencias) {
                assertTrue(sentencia.sql().contains("WHERE sku=? AND almacen=?"));
                assertFalse(sentencia.sql().contains(clave.sku()));
                assertFalse(sentencia.sql().contains(clave.almacen()));
                assertEquals(clave.sku(), sentencia.parametros().get(1));
                assertEquals(clave.almacen(), sentencia.parametros().get(2));
            }
        }
    }

    @Test void escrituraMantieneReferenciaYClaveComoParametros() {
        String carga = "x'); DROP TABLE stock_movimiento; --";
        var clave = new ClaveStock(carga, carga);
        var servicio = new InventarioService(new StockPostgresRepository(baseSimulada()), Clock.systemUTC());
        servicio.iniciar(UUID.randomUUID(), clave, false, BigDecimal.ONE, carga);
        var saldo = sentencias.stream().filter(s -> s.sql().startsWith("INSERT INTO stock_saldo")).findFirst().orElseThrow();
        var movimiento = sentencias.stream().filter(s -> s.sql().startsWith("INSERT INTO stock_movimiento")).findFirst().orElseThrow();
        assertEquals(clave.sku(), saldo.parametros().get(1));
        assertEquals(clave.almacen(), saldo.parametros().get(2));
        assertEquals(clave.sku(), movimiento.parametros().get(5));
        assertEquals(clave.almacen(), movimiento.parametros().get(6));
        assertEquals(carga, movimiento.parametros().get(13));
        for (var sentencia : sentencias) {
            assertFalse(sentencia.sql().contains(carga));
            assertFalse(sentencia.sql().contains(clave.sku()));
        }
    }
}
