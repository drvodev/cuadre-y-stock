package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.modelo.caja.*;
import com.surquillo.inventariocaja.excepcion.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.util.function.Function;

/** Persistencia normalizada de turnos y resultados de solicitudes idempotentes. */
public class TurnoPostgresRepository implements TurnoRepository {
    private final DataSource ds;
    public TurnoPostgresRepository(DataSource ds) { this.ds=Objects.requireNonNull(ds); }
    @Override public Optional<Estado> consultar(UUID turno) {
        try(Connection c=ds.getConnection()) { return leer(c,turno,null,null); }
        catch(SQLException e) { throw fallo(e); }
    }
    @Override public Estado aplicar(UUID id,String huella,String caja,UUID turno,Function<Optional<Estado>,Estado> cambio) {
        Objects.requireNonNull(id); Objects.requireNonNull(huella); Objects.requireNonNull(caja); Objects.requireNonNull(turno);
        try(Connection c=ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(739102815)")) { p.execute(); }
                try(var p=c.prepareStatement("SELECT huella,caja,turno,version,cerrado FROM caja_solicitud WHERE id=?")) {
                    p.setObject(1,id);
                    try(var r=p.executeQuery()) {
                        if(r.next()) {
                            if(!huella.equals(r.getString(1))||!caja.equals(r.getString(2))||!turno.equals(r.getObject(3,UUID.class)))
                                throw new ValidacionException("La solicitud ya se utilizó con otros datos");
                            Estado previo=leer(c,turno,r.getLong(4),r.getBoolean(5)).orElseThrow(); c.commit(); return previo;
                        }
                    }
                }
                Optional<Estado> anterior=leer(c,turno,null,null);
                if(anterior.isEmpty()) {
                    try(var p=c.prepareStatement("SELECT 1 FROM caja_turno WHERE caja=? AND NOT cerrado")) {
                        p.setString(1,caja); try(var r=p.executeQuery()) { if(r.next()) throw new ValidacionException("La caja ya tiene un turno abierto"); }
                    }
                }
                Estado nuevo=Objects.requireNonNull(cambio.apply(anterior));
                if(!nuevo.turno().id().equals(turno)||!nuevo.turno().caja().equals(caja)) throw new IllegalStateException("Turno inconsistente");
                guardar(c,nuevo,anterior);
                try(var p=c.prepareStatement("INSERT INTO caja_solicitud(id,huella,caja,turno,version,cerrado) VALUES(?,?,?,?,?,?)")) {
                    p.setObject(1,id); p.setString(2,huella); p.setString(3,caja); p.setObject(4,turno); p.setLong(5,nuevo.turno().version()); p.setBoolean(6,nuevo.turno().cerrado()); p.executeUpdate();
                }
                c.commit(); return nuevo;
            } catch(SQLException|RuntimeException e) { try { c.rollback(); } catch(SQLException x) { e.addSuppressed(x); } throw e; }
        } catch(SQLException e) { throw fallo(e); }
    }
    private Optional<Estado> leer(Connection c,UUID id,Long version,Boolean cerrado) throws SQLException {
        try(var p=c.prepareStatement("SELECT caja,fecha_operativa,encargado,fondo,version,cerrado,cierre_fecha,cierre_firma,esperado,contado,diferencia,observacion FROM caja_turno WHERE id=?")) {
            p.setObject(1,id);
            try(var r=p.executeQuery()) {
                if(!r.next()) return Optional.empty();
                long corte=version==null?r.getLong(5):version;
                boolean finalizado=cerrado==null?r.getBoolean(6):cerrado;
                Turno turno=new Turno(id,r.getString(1),r.getDate(2).toLocalDate(),r.getString(3),r.getBigDecimal(4),corte,finalizado);
                List<MovimientoCaja> movimientos=movimientos(c,id,corte);
                Cierre cierre=finalizado?new Cierre(r.getTimestamp(7).toInstant(),r.getString(8),r.getBigDecimal(9),r.getBigDecimal(10),r.getBigDecimal(11),r.getString(12),flujo(movimientos),leerDigitales(c,id)):null;
                return Optional.of(new Estado(turno,movimientos,cierre));
            }
        }
    }
    private List<MovimientoCaja> movimientos(Connection c,UUID turno,long version) throws SQLException {
        List<MovimientoCaja> lista=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT id,fecha,tipo,fuente,referencia FROM caja_movimiento WHERE turno=? AND version<=? ORDER BY version")) {
            p.setObject(1,turno); p.setLong(2,version);
            try(var r=p.executeQuery()) { while(r.next()) {
                UUID id=r.getObject(1,UUID.class); Map<MedioPago,BigDecimal> lineas=new EnumMap<>(MedioPago.class);
                try(var q=c.prepareStatement("SELECT medio,importe FROM caja_linea WHERE movimiento=?")) {
                    q.setObject(1,id); try(var s=q.executeQuery()) { while(s.next()) lineas.put(MedioPago.valueOf(s.getString(1)),s.getBigDecimal(2)); }
                }
                lista.add(new MovimientoCaja(id,r.getTimestamp(2).toInstant(),MovimientoCaja.Tipo.valueOf(r.getString(3)),MovimientoCaja.Fuente.valueOf(r.getString(4)),lineas,r.getString(5)));
            } }
        }
        return List.copyOf(lista);
    }
    private Map<MedioPago,BigDecimal> flujo(List<MovimientoCaja> movimientos) {
        Map<MedioPago,BigDecimal> resultado=new EnumMap<>(MedioPago.class);
        for(var medio:MedioPago.values()) resultado.put(medio,new BigDecimal("0.00"));
        for(var m:movimientos) if(m.fuente()==MovimientoCaja.Fuente.CAJA) m.importes().forEach((medio,monto)->resultado.merge(medio,m.ingreso()?monto:monto.negate(),BigDecimal::add));
        return resultado;
    }
    private Map<MedioPago,DetalleCierre> leerDigitales(Connection c,UUID turno) throws SQLException {
        Map<MedioPago,DetalleCierre> resultado=new EnumMap<>(MedioPago.class);
        try(var p=c.prepareStatement("SELECT medio,cobros,devoluciones,egresos,otros,ver_cobros,ver_devoluciones,ver_egresos,ver_otros FROM caja_conciliacion WHERE turno=?")) {
            p.setObject(1,turno); try(var r=p.executeQuery()) { while(r.next()) resultado.put(MedioPago.valueOf(r.getString(1)),new DetalleCierre(new VerificacionMedio(r.getBigDecimal(2),r.getBigDecimal(3),r.getBigDecimal(4),r.getBigDecimal(5)),new VerificacionMedio(r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getBigDecimal(9)))); }
        }
        return Map.copyOf(resultado);
    }
    private void guardar(Connection c,Estado estado,Optional<Estado> anterior) throws SQLException {
        Turno t=estado.turno(); Cierre cierre=estado.cierre();
        if(anterior.isEmpty()) {
            try(var p=c.prepareStatement("INSERT INTO caja_turno(id,caja,fecha_operativa,encargado,fondo,version,cerrado) VALUES(?,?,?,?,?,?,false)")) {
                p.setObject(1,t.id()); p.setString(2,t.caja()); p.setDate(3,java.sql.Date.valueOf(t.fechaOperativa())); p.setString(4,t.encargado()); p.setBigDecimal(5,t.fondoInicial()); p.setLong(6,t.version()); p.executeUpdate();
            }
        } else {
            try(var p=c.prepareStatement("UPDATE caja_turno SET version=?,cerrado=?,cierre_fecha=?,cierre_firma=?,esperado=?,contado=?,diferencia=?,observacion=? WHERE id=?")) {
                p.setLong(1,t.version()); p.setBoolean(2,t.cerrado()); p.setTimestamp(3,cierre==null?null:Timestamp.from(cierre.fecha()));
                p.setString(4,cierre==null?null:cierre.firma()); p.setBigDecimal(5,cierre==null?null:cierre.efectivoEsperado()); p.setBigDecimal(6,cierre==null?null:cierre.efectivoContado());
                p.setBigDecimal(7,cierre==null?null:cierre.diferencia()); p.setString(8,cierre==null?null:cierre.observacion()); p.setObject(9,t.id()); p.executeUpdate();
            }
        }
        int anteriores=anterior.map(s->s.movimientos().size()).orElse(0);
        if(cierre!=null) for(var entrada:cierre.digitales().entrySet()) {
            try(var p=c.prepareStatement("INSERT INTO caja_conciliacion(turno,medio,cobros,devoluciones,egresos,otros,ver_cobros,ver_devoluciones,ver_egresos,ver_otros) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                p.setObject(1,t.id()); p.setString(2,entrada.getKey().name());
                var esperado=entrada.getValue().esperado(); var ver=entrada.getValue().verificado();
                p.setBigDecimal(3,esperado.cobros()); p.setBigDecimal(4,esperado.devoluciones()); p.setBigDecimal(5,esperado.egresos()); p.setBigDecimal(6,esperado.otrosIngresos());
                p.setBigDecimal(7,ver.cobros()); p.setBigDecimal(8,ver.devoluciones()); p.setBigDecimal(9,ver.egresos()); p.setBigDecimal(10,ver.otrosIngresos()); p.executeUpdate();
            }
        }
        for(var m:estado.movimientos().subList(anteriores,estado.movimientos().size())) {
            try(var p=c.prepareStatement("INSERT INTO caja_movimiento(id,turno,version,fecha,tipo,fuente,referencia) VALUES(?,?,?,?,?,?,?)")) {
                p.setObject(1,m.id()); p.setObject(2,t.id()); p.setLong(3,t.version()); p.setTimestamp(4,Timestamp.from(m.fecha())); p.setString(5,m.tipo().name()); p.setString(6,m.fuente().name()); p.setString(7,m.referencia()); p.executeUpdate();
            }
            for(var linea:m.importes().entrySet()) try(var p=c.prepareStatement("INSERT INTO caja_linea(movimiento,medio,importe) VALUES(?,?,?)")) {
                p.setObject(1,m.id()); p.setString(2,linea.getKey().name()); p.setBigDecimal(3,linea.getValue()); p.executeUpdate();
            }
        }
    }
    private PersistenciaException fallo(SQLException e) { return new PersistenciaException("No se pudo guardar o consultar la caja",e); }
}
