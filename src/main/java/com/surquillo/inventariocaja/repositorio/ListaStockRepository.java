package com.surquillo.inventariocaja.repositorio;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ListaStockRepository {
    public enum Estado {
        TODOS("Todos"), BAJOS("Bajos y agotados"), AGOTADOS("Agotados"), SIN_INICIAR("Sin stock inicial");
        private final String nombre;Estado(String nombre){this.nombre=nombre;}
        @Override public String toString(){return nombre;}
    }
    public record Articulo(String sku, String producto, String unidad, BigDecimal disponible, BigDecimal costo, BigDecimal sugerido, long version) {}
    private final DataSource ds;
    public ListaStockRepository(DataSource ds) { this.ds = ds; }

    public List<Articulo> buscar(String almacen, String texto, String cursor) {
        return buscar(almacen,texto,cursor,Estado.TODOS);
    }
    public List<Articulo> buscar(String almacen,String texto,String cursor,Estado estado) {
        if(almacen==null || almacen.isBlank() || estado==null)throw new IllegalArgumentException("Indica almacén y estado");
        texto=texto==null?"":texto.strip();cursor=cursor==null?"":cursor.strip().toUpperCase(java.util.Locale.ROOT);
        String sql = """
                SELECT v.sku, v.descripcion, v.unidad, s.fisico - s.reservado, v.costo, v.precio_venta, v.version
                FROM catalogo_variante v LEFT JOIN stock_saldo s ON s.sku = v.sku AND s.almacen = ?
                LEFT JOIN stock_minimo m ON m.sku=s.sku AND m.almacen=s.almacen
                WHERE v.activo AND v.sku > ?
                  AND (strpos(lower(v.descripcion), lower(?)) > 0 OR strpos(lower(v.sku), lower(?)) > 0)
                  AND (?='TODOS' OR (?='BAJOS' AND s.fisico-s.reservado<=coalesce(m.cantidad,0))
                       OR (?='AGOTADOS' AND s.fisico-s.reservado=0) OR (?='SIN_INICIAR' AND s.sku IS NULL))
                ORDER BY v.sku LIMIT 101
                """;
        try (var c = ds.getConnection(); var p = c.prepareStatement(sql)) {
            p.setString(1, almacen); p.setString(2, cursor); p.setString(3, texto); p.setString(4, texto);
            for(int i=5;i<=8;i++)p.setString(i,estado.name());
            var filas = new ArrayList<Articulo>();
            try (var r = p.executeQuery()) {
                while (r.next()) filas.add(new Articulo(r.getString(1), r.getString(2), r.getString(3),
                        r.getBigDecimal(4), r.getBigDecimal(5), r.getBigDecimal(6), r.getLong(7)));
            }
            return filas;
        } catch (SQLException e) { throw new IllegalStateException("No se pudo consultar el stock.", e); }
    }
}
