package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.stock.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Persistencia de listas independientes y auditoría; todas las entradas van como parámetros. */
final class ListasCatalogoPostgres {
    private final DataSource ds;
    ListasCatalogoPostgres(DataSource ds) { this.ds=ds; }
    OpcionCatalogo crear(OpcionCatalogo o) {
        if(o.version()!=0)throw new ValidacionException("La versión inicial debe ser cero");
        try(var c=ds.getConnection();var p=c.prepareStatement("INSERT INTO catalogo_opcion(id,lista,nombre,activo,version) VALUES(?,?,?,?,0)")) {
            p.setObject(1,o.id());p.setString(2,o.lista().name());p.setString(3,o.nombre());p.setBoolean(4,o.activo());p.executeUpdate();return o;
        }catch(SQLException e){throw fallo(e);}
    }
    OpcionCatalogo actualizar(OpcionCatalogo o) {
        try(var c=ds.getConnection();var p=c.prepareStatement("UPDATE catalogo_opcion SET nombre=?,activo=?,version=version+1 WHERE id=? AND lista=? AND version=?")) {
            p.setString(1,o.nombre());p.setBoolean(2,o.activo());p.setObject(3,o.id());p.setString(4,o.lista().name());p.setLong(5,o.version());
            if(p.executeUpdate()!=1)throw new ValidacionException("La opción cambió, no existe o pertenece a otra lista");
            return o.conVersion(o.version()+1);
        }catch(SQLException e){throw fallo(e);}
    }
    Optional<OpcionCatalogo> buscar(UUID id) {
        try(var c=ds.getConnection();var p=c.prepareStatement("SELECT id,lista,nombre,activo,version FROM catalogo_opcion WHERE id=?")) {
            p.setObject(1,id);try(var r=p.executeQuery()){return r.next()?Optional.of(leer(r)):Optional.empty();}
        }catch(SQLException e){throw fallo(e);}
    }
    List<OpcionCatalogo> listar(ListaCatalogo lista,String texto,String despues,int limite,boolean inactivos) {
        String sql="SELECT id,lista,nombre,activo,version FROM catalogo_opcion WHERE lista=? AND clave>? AND (? OR activo) AND lower(nombre) LIKE lower(?) ESCAPE '!' ORDER BY clave LIMIT ?";
        try(var c=ds.getConnection();var p=c.prepareStatement(sql)) {
            p.setString(1,lista.name());p.setString(2,despues);p.setBoolean(3,inactivos);p.setString(4,patron(texto));p.setInt(5,limite);
            try(var r=p.executeQuery()){var valores=new ArrayList<OpcionCatalogo>();while(r.next())valores.add(leer(r));return List.copyOf(valores);}
        }catch(SQLException e){throw fallo(e);}
    }
    List<CambioOpcionCatalogo> historial(UUID id,long despues,int limite) {
        String sql="SELECT v.id,v.lista,v.nombre,v.activo,v.version,h.secuencia,h.fecha FROM catalogo_opcion_historial h CROSS JOIN LATERAL jsonb_populate_record(NULL::catalogo_opcion,h.posterior) v WHERE h.id=? AND h.secuencia>? ORDER BY h.secuencia LIMIT ?";
        try(var c=ds.getConnection();var p=c.prepareStatement(sql)) {
            p.setObject(1,id);p.setLong(2,despues);p.setInt(3,limite);
            try(var r=p.executeQuery()){var valores=new ArrayList<CambioOpcionCatalogo>();while(r.next())valores.add(new CambioOpcionCatalogo(r.getLong(6),r.getTimestamp(7).toInstant(),leer(r)));return List.copyOf(valores);}
        }catch(SQLException e){throw fallo(e);}
    }
    private OpcionCatalogo leer(ResultSet r)throws SQLException {
        return new OpcionCatalogo(r.getObject(1,UUID.class),ListaCatalogo.valueOf(r.getString(2)),r.getString(3),r.getBoolean(4),r.getLong(5));
    }
    static String patron(String texto) { return "%"+texto.replace("!","!!").replace("%","!%").replace("_","!_")+"%"; }
    private RuntimeException fallo(SQLException e) {
        return "23505".equals(e.getSQLState())?new ValidacionException("El nombre ya existe en esta lista, incluso si está inactivo"):
            new PersistenciaException("No se pudo acceder a las listas del catálogo",e);
    }
}
