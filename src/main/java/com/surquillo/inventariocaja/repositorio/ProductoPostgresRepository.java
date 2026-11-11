package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.PersistenciaException;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Consultas parametrizadas y páginas limitadas; requiere catalogo-v1.sql. */
public class ProductoPostgresRepository implements ProductoRepository {
    @Override public List<String> opciones(Lista lista,int limite) {
        String campo=switch(lista) { case CATEGORIA->"categoria"; case MARCA->"marca"; case UNIDAD->"unidad"; case PRESENTACION->"presentacion"; };
        String sql="SELECT DISTINCT "+campo+" FROM catalogo_variante WHERE "+campo+" <> '' ORDER BY "+campo+" LIMIT ?";
        try(Connection c=dataSource.getConnection(); var p=c.prepareStatement(sql)) {
            p.setInt(1,limite); try(var r=p.executeQuery()) { List<String> valores=new ArrayList<>(); while(r.next()) valores.add(r.getString(1)); return List.copyOf(valores); }
        } catch(SQLException e) { throw fallo(e); }
    }
    @Override public List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> historial(String sku,long despues,int limite) {
        String columnas=Arrays.stream(COLUMNAS.split(",")).map(s->"v."+s).collect(java.util.stream.Collectors.joining(","));
        String sql="SELECT "+columnas+",h.secuencia,h.fecha FROM catalogo_historial h CROSS JOIN LATERAL jsonb_populate_record(NULL::catalogo_variante,h.posterior) v WHERE h.sku=? AND h.secuencia>? ORDER BY h.secuencia LIMIT ?";
        try(Connection c=dataSource.getConnection(); var p=c.prepareStatement(sql)) {
            p.setString(1,sku); p.setLong(2,despues); p.setInt(3,limite);
            try(var r=p.executeQuery()) { List<com.surquillo.inventariocaja.modelo.stock.CambioCatalogo> valores=new ArrayList<>();
                while(r.next()) valores.add(new com.surquillo.inventariocaja.modelo.stock.CambioCatalogo(r.getLong(16),r.getTimestamp(17).toInstant(),leer(r))); return List.copyOf(valores); }
        } catch(SQLException e) { throw fallo(e); }
    }
    private static final String COLUMNAS = "sku,descripcion,categoria,marca,presentacion,tipo,unidad,permite_fraccion,origen,contenido,unidad_contenido,precio_venta,costo,activo,version";
    private final DataSource dataSource;
    public ProductoPostgresRepository(DataSource dataSource) { this.dataSource=Objects.requireNonNull(dataSource); }

    @Override public Variante crear(Variante v) {
        String sql = "INSERT INTO catalogo_variante(" + COLUMNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,v.sku()); campos(p,v,2); p.setLong(15,v.version()); p.executeUpdate(); return v;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) throw new ValidacionException("El SKU ya existe");
            throw fallo(e);
        }
    }
    @Override public Variante actualizar(Variante v) {
        String sql="UPDATE catalogo_variante SET descripcion=?,categoria=?,marca=?,presentacion=?,tipo=?,unidad=?,permite_fraccion=?,origen=?,contenido=?,unidad_contenido=?,precio_venta=?,costo=?,activo=?,version=version+1 WHERE sku=? AND version=? AND permite_fraccion=? AND unidad=?";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            campos(p,v,1); p.setString(14,v.sku()); p.setLong(15,v.version()); p.setBoolean(16,v.permiteFraccion()); p.setString(17,v.unidad());
            if (p.executeUpdate()!=1) throw new ValidacionException("El artículo cambió, no existe o su unidad no puede modificarse automáticamente");
            return v.conVersion(v.version()+1);
        } catch (SQLException e) { throw fallo(e); }
    }
    @Override public Optional<Variante> buscarSku(String sku) {
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement("SELECT "+COLUMNAS+" FROM catalogo_variante WHERE sku=?")) {
            p.setString(1,sku); try (ResultSet r=p.executeQuery()) { return r.next()?Optional.of(leer(r)):Optional.empty(); }
        } catch (SQLException e) { throw fallo(e); }
    }
    @Override public List<Variante> buscar(String texto, String despues, int limite, boolean incluirInactivos) {
        String filtro="lower(concat_ws(' ',sku,descripcion,categoria,marca,presentacion,tipo,unidad)) LIKE lower(?) ESCAPE '!'";
        String sql="SELECT "+COLUMNAS+" FROM catalogo_variante WHERE sku > ? AND (? OR activo) AND "+filtro+" ORDER BY sku LIMIT ?";
        try (Connection c=dataSource.getConnection(); PreparedStatement p=c.prepareStatement(sql)) {
            p.setString(1,despues); p.setBoolean(2,incluirInactivos);
            p.setString(3,"%"+texto.replace("!","!!").replace("%","!%").replace("_","!_")+"%"); p.setInt(4,limite);
            try (ResultSet r=p.executeQuery()) { List<Variante> lista=new ArrayList<>(); while (r.next()) lista.add(leer(r)); return List.copyOf(lista); }
        } catch (SQLException e) { throw fallo(e); }
    }
    private void campos(PreparedStatement p,Variante v,int inicio) throws SQLException {
        int i=inicio; p.setString(i++,v.descripcion()); p.setString(i++,v.categoria()); p.setString(i++,v.marca());
        p.setString(i++,v.presentacion()); p.setString(i++,v.tipo()); p.setString(i++,v.unidad()); p.setBoolean(i++,v.permiteFraccion());
        p.setString(i++,v.origen().name()); p.setBigDecimal(i++,v.contenido()); p.setString(i++,v.unidadContenido());
        p.setBigDecimal(i++,v.precioVenta()); p.setBigDecimal(i++,v.costo()); p.setBoolean(i,v.activo());
    }
    private Variante leer(ResultSet r) throws SQLException {
        return new Variante(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),
                r.getBoolean(8),Variante.Origen.valueOf(r.getString(9)),r.getBigDecimal(10),r.getString(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBoolean(14),r.getLong(15));
    }
    private PersistenciaException fallo(SQLException e) { return new PersistenciaException("No se pudo acceder al catálogo",e); }
}
