package com.surquillo.inventariocaja.repositorio;
import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.excepcion.*;
import com.surquillo.inventariocaja.modelo.caja.*;
import java.sql.*;
import java.util.*;

/** Cabecera, ítems, pagos y solicitud se guardan en la unidad de trabajo comercial. */
public final class CompraPostgresRepository implements CompraRepository {
    private final UnidadTrabajoPostgres unidad;
    public CompraPostgresRepository(UnidadTrabajoPostgres unidad){this.unidad=Objects.requireNonNull(unidad);}
    @Override public Optional<Solicitud> buscarSolicitud(UUID id) {
        try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT huella,id FROM compra_operacion WHERE solicitud=?")){
            p.setObject(1,id);try(var r=p.executeQuery()){return r.next()?Optional.of(new Solicitud(r.getString(1),r.getObject(2,UUID.class))):Optional.empty();}
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public Compra guardar(Compra compra,UUID solicitud,String huella) {
        try{return unidad.ejecutar(()->guardarDentro(compra,solicitud,huella));}
        catch(ValidacionException e){throw e;}
        catch(SQLException e){if("23505".equals(e.getSQLState()))throw new ValidacionException("Ya existe la compra, su solicitud o el documento de este proveedor");throw fallo(e);}
        catch(Exception e){throw new PersistenciaException("No se pudo guardar la compra",e);}
    }
    private Compra guardarDentro(Compra v,UUID solicitud,String huella)throws SQLException {
        long secuencia;
        String sql="INSERT INTO compra_operacion(id,solicitud,huella,turno,fecha,proveedor,tipo,numero,fuente,total) VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING secuencia";
        try(var c=unidad.getConnection();var p=c.prepareStatement(sql)) {
            p.setObject(1,v.id());p.setObject(2,solicitud);p.setString(3,huella);p.setObject(4,v.turno());p.setTimestamp(5,Timestamp.from(v.fecha()));
            p.setString(6,v.proveedor());p.setString(7,v.documento().tipo().name());p.setString(8,v.documento().numero());p.setString(9,v.fuente().name());p.setBigDecimal(10,v.total());
            try(var r=p.executeQuery()){r.next();secuencia=r.getLong(1);}
            try(var q=c.prepareStatement("INSERT INTO compra_item(compra,posicion,sku,almacen,descripcion,unidad,cantidad,costo) VALUES(?,?,?,?,?,?,?,?)")){
                int i=0;for(var item:v.items()){q.setObject(1,v.id());q.setInt(2,i++);q.setString(3,item.sku());q.setString(4,item.almacen());q.setString(5,item.descripcion());q.setString(6,item.unidad());q.setBigDecimal(7,item.cantidad());q.setBigDecimal(8,item.costoUnitario());q.addBatch();}q.executeBatch();
            }
            try(var q=c.prepareStatement("INSERT INTO compra_pago(compra,medio,importe) VALUES(?,?,?)")){
                for(var e:v.pagos().entrySet()){q.setObject(1,v.id());q.setString(2,e.getKey().name());q.setBigDecimal(3,e.getValue());q.addBatch();}q.executeBatch();
            }
        }
        return new Compra(v.id(),secuencia,v.turno(),v.fecha(),v.proveedor(),v.documento(),v.fuente(),v.total(),v.items(),v.pagos());
    }
    @Override public Optional<Compra> buscar(UUID id) {
        String sql="SELECT secuencia,turno,fecha,proveedor,tipo,numero,fuente,total FROM compra_operacion WHERE id=?";
        try(var c=unidad.getConnection();var p=c.prepareStatement(sql)){
            p.setObject(1,id);try(var r=p.executeQuery()){
                if(!r.next())return Optional.empty();
                var items=new ArrayList<DetalleCompra>();
                try(var q=c.prepareStatement("SELECT sku,almacen,descripcion,unidad,cantidad,costo FROM compra_item WHERE compra=? ORDER BY posicion")){
                    q.setObject(1,id);try(var s=q.executeQuery()){while(s.next())items.add(new DetalleCompra(s.getString(1),s.getString(2),s.getString(3),s.getString(4),s.getBigDecimal(5),s.getBigDecimal(6)));}
                }
                var pagos=new EnumMap<MedioPago,java.math.BigDecimal>(MedioPago.class);
                try(var q=c.prepareStatement("SELECT medio,importe FROM compra_pago WHERE compra=?")){
                    q.setObject(1,id);try(var s=q.executeQuery()){while(s.next())pagos.put(MedioPago.valueOf(s.getString(1)),s.getBigDecimal(2));}
                }
                return Optional.of(new Compra(id,r.getLong(1),r.getObject(2,UUID.class),r.getTimestamp(3).toInstant(),r.getString(4),
                    new Documento(Documento.Tipo.valueOf(r.getString(5)),r.getString(6),true),MovimientoCaja.Fuente.valueOf(r.getString(7)),r.getBigDecimal(8),items,pagos));
            }
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public List<Resumen> listar(long despues,int limite) {
        String sql="SELECT secuencia,id,fecha,proveedor,tipo,numero,total,fuente FROM compra_operacion WHERE secuencia>? ORDER BY secuencia LIMIT ?";
        try(var c=unidad.getConnection();var p=c.prepareStatement(sql)){
            p.setLong(1,despues);p.setInt(2,limite);try(var r=p.executeQuery()){
                var lista=new ArrayList<Resumen>();while(r.next())lista.add(new Resumen(r.getLong(1),r.getObject(2,UUID.class),r.getTimestamp(3).toInstant(),r.getString(4),
                    new Documento(Documento.Tipo.valueOf(r.getString(5)),r.getString(6),true),r.getBigDecimal(7),MovimientoCaja.Fuente.valueOf(r.getString(8))));return List.copyOf(lista);
            }
        }catch(SQLException e){throw fallo(e);}
    }
    private PersistenciaException fallo(SQLException e){return new PersistenciaException("No se pudo acceder a las compras",e);}
}
