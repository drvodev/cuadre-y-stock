package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.PersistenciaException;
import com.surquillo.inventariocaja.modelo.stock.ClaveStock;
import com.surquillo.inventariocaja.servicio.InventarioService;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StockPostgresFallosTest {
    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> tipo, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler);
    }

    @Test void errorAlInsertarMovimientoRevierteLaTransaccionSinConfirmarla() {
        var rollbacks = new AtomicInteger();
        var commits = new AtomicInteger();
        var actualizaciones = new AtomicInteger();
        DataSource ds = mock(DataSource.class, (d, dm, da) -> {
            if (!dm.getName().equals("getConnection")) throw new UnsupportedOperationException(dm.getName());
            return mock(Connection.class, (c, m, a) -> switch (m.getName()) {
                case "setAutoCommit", "close" -> null;
                case "commit" -> { commits.incrementAndGet(); yield null; }
                case "rollback" -> { rollbacks.incrementAndGet(); yield null; }
                case "prepareStatement" -> {
                    String sql = (String) a[0];
                    yield mock(PreparedStatement.class, (p, pm, pa) -> {
                        if (pm.getName().startsWith("set")) return null;
                        return switch (pm.getName()) {
                            case "execute" -> true;
                            case "executeQuery" -> mock(ResultSet.class, (r, rm, ra) -> switch (rm.getName()) {
                                case "next" -> false;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            });
                            case "executeUpdate" -> {
                                actualizaciones.incrementAndGet();
                                if (sql.startsWith("INSERT INTO stock_movimiento")) throw new SQLException("fallo simulado");
                                yield 1;
                            }
                            case "close" -> null;
                            default -> throw new UnsupportedOperationException(pm.getName());
                        };
                    });
                }
                default -> throw new UnsupportedOperationException(m.getName());
            });
        });
        var servicio = new InventarioService(new StockPostgresRepository(ds), Clock.systemUTC());
        var error = assertThrows(PersistenciaException.class, () -> servicio.iniciar(
                UUID.randomUUID(), new ClaveStock("SKU", "A"), false, BigDecimal.ONE, ""));
        assertInstanceOf(SQLException.class, error.getCause());
        assertEquals(2, actualizaciones.get());
        assertEquals(1, rollbacks.get());
        assertEquals(0, commits.get());
    }
}
