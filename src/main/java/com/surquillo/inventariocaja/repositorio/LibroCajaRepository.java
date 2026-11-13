package com.surquillo.inventariocaja.repositorio;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

/** Datos históricos de los documentos vinculados a los movimientos del turno. */
public final class LibroCajaRepository {
    public record Documento(UUID id, String tipo, String numero, String items, boolean separacion, BigDecimal ganancia) {}
    private final DataSource ds;

    public LibroCajaRepository(DataSource ds) { this.ds = ds; }

    public Map<UUID, Documento> documentos(UUID turno) {
        String sql = """
                SELECT m.id, o.id, o.tipo, o.numero, o.modalidad, g.importe,
                       CASE WHEN x.tipo='DEVOLUCION' THEN
                           (SELECT string_agg(trim_scale(d.cantidad)::text || ' ' || vi.unidad || ' · ' || vi.descripcion,chr(10) ORDER BY d.posicion)
                            FROM venta_correccion_item d JOIN venta_item vi ON vi.operacion=x.original AND vi.posicion=d.posicion WHERE d.correccion=x.id)
                       ELSE string_agg(trim_scale(i.cantidad)::text || ' ' || i.unidad || ' · ' || i.descripcion,
                                  chr(10) ORDER BY i.posicion) END AS items
                FROM caja_movimiento m
                JOIN venta_solicitud s ON s.id = m.id
                JOIN venta_operacion o ON o.id = s.operacion
                JOIN venta_item i ON i.operacion = o.id
                LEFT JOIN venta_ganancia g ON g.operacion = o.id
                LEFT JOIN venta_correccion x ON x.solicitud=m.id
                WHERE m.turno = ?
                GROUP BY m.id, o.id, g.importe, x.id
                """;
        try (var c = ds.getConnection(); var p = c.prepareStatement(sql)) {
            p.setObject(1, turno);
            var documentos = new HashMap<UUID, Documento>();
            try (var r = p.executeQuery()) {
                while (r.next()) documentos.put(r.getObject(1, UUID.class),
                        new Documento(r.getObject(2, UUID.class), r.getString(3), r.getString(4),
                                r.getString(7), "SEPARACION".equals(r.getString(5)), r.getBigDecimal(6)));
            }
            return documentos;
        } catch (SQLException e) { throw new IllegalStateException("No se pudieron consultar los documentos del turno.", e); }
    }

    public void guardarGanancia(UUID operacion, BigDecimal importe, BigDecimal anterior) {
        if (importe.stripTrailingZeros().scale() > 2 || importe.abs().compareTo(new BigDecimal("100000000000000")) >= 0)
            throw new IllegalArgumentException("Revisa el importe. Puedes ingresar hasta dos decimales.");
        String sql = anterior == null
                ? "INSERT INTO venta_ganancia(importe,operacion) VALUES (?,?) ON CONFLICT DO NOTHING"
                : "UPDATE venta_ganancia SET importe=? WHERE operacion=? AND importe=?";
        try (var c = ds.getConnection(); var p = c.prepareStatement(sql)) {
            p.setBigDecimal(1, importe);
            p.setObject(2, operacion);
            if (anterior != null) p.setBigDecimal(3, anterior);
            if (p.executeUpdate() == 0) throw new IllegalStateException("La ganancia cambió. Actualiza el turno y revisa el importe.");
        } catch (SQLException e) { throw new IllegalStateException("No se pudo guardar la ganancia del documento.", e); }
    }
}
