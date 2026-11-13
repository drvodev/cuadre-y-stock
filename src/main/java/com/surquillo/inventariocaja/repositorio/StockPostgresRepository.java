package com.surquillo.inventariocaja.repositorio;

import com.surquillo.inventariocaja.excepcion.PersistenciaException;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.*;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import javax.sql.DataSource;

/** Almacén PostgreSQL. Requiere instalar previamente db/stock-v1.sql. */
public class StockPostgresRepository implements StockRepository {
    @Override public void configurarMinimo(ClaveStock clave,java.math.BigDecimal cantidad) {
        var saldo=buscar(clave).orElseThrow(()->new ValidacionException("Stock no registrado"));
        SaldoStock.validarCantidad(cantidad,saldo.permiteFraccion());
        try(Connection c=dataSource.getConnection(); var p=c.prepareStatement("INSERT INTO stock_minimo(sku,almacen,cantidad) VALUES(?,?,?) ON CONFLICT(sku,almacen) DO UPDATE SET cantidad=EXCLUDED.cantidad")) {
            p.setString(1,clave.sku()); p.setString(2,clave.almacen()); p.setBigDecimal(3,cantidad); p.executeUpdate();
        } catch(SQLException e) { throw fallo(e); }
    }
    @Override public java.math.BigDecimal minimo(ClaveStock clave) {
        try(Connection c=dataSource.getConnection(); var p=c.prepareStatement("SELECT cantidad FROM stock_minimo WHERE sku=? AND almacen=?")) {
            p.setString(1,clave.sku()); p.setString(2,clave.almacen());
            try(var r=p.executeQuery()) { return r.next()?r.getBigDecimal(1):java.math.BigDecimal.ZERO; }
        } catch(SQLException e) { throw fallo(e); }
    }
    private final DataSource dataSource;
    public StockPostgresRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override public Optional<SaldoStock> buscar(ClaveStock clave) {
        try (Connection c = dataSource.getConnection()) { return buscar(c, clave); }
        catch (SQLException e) { throw fallo(e); }
    }
    private Optional<SaldoStock> buscar(Connection c, ClaveStock clave) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT permite_fraccion,fisico,reservado FROM stock_saldo WHERE sku=? AND almacen=?")) {
            p.setString(1, clave.sku()); p.setString(2, clave.almacen());
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? Optional.of(new SaldoStock(clave.sku(), clave.almacen(), r.getBoolean(1), r.getBigDecimal(2), r.getBigDecimal(3))) : Optional.empty();
            }
        }
    }
    @Override public List<MovimientoStock> historial(ClaveStock clave) {
        String sql = "SELECT id,fecha,tipo,sku,almacen,cantidad_solicitada,cambio_fisico,fisico_anterior,fisico_posterior,referencia,motivo FROM stock_movimiento WHERE sku=? AND almacen=? ORDER BY secuencia";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, clave.sku()); p.setString(2, clave.almacen());
            try (ResultSet r = p.executeQuery()) {
                List<MovimientoStock> lista = new ArrayList<>();
                while (r.next()) lista.add(movimiento(r));
                return List.copyOf(lista);
            }
        } catch (SQLException e) { throw fallo(e); }
    }
    @Override public List<MovimientoPaginado> historial(ClaveStock clave,long despues,int limite) {
        String sql="SELECT id,fecha,tipo,sku,almacen,cantidad_solicitada,cambio_fisico,fisico_anterior,fisico_posterior,referencia,motivo,secuencia FROM stock_movimiento WHERE sku=? AND almacen=? AND secuencia>? ORDER BY secuencia LIMIT ?";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql)) {
            p.setString(1,clave.sku());p.setString(2,clave.almacen());p.setLong(3,despues);p.setInt(4,limite);
            try(var r=p.executeQuery()){var filas=new ArrayList<MovimientoPaginado>();while(r.next())filas.add(new MovimientoPaginado(r.getLong(12),movimiento(r)));return List.copyOf(filas);}
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public Optional<SaldoStock> buscarAlCorte(ClaveStock clave,java.time.Instant corteExclusivo) {
        String sql="SELECT permite_fraccion,fisico_posterior,reservado_posterior FROM stock_movimiento WHERE sku=? AND almacen=? AND fecha<? ORDER BY secuencia DESC LIMIT 1";
        try(var c=dataSource.getConnection();var p=c.prepareStatement(sql)) {
            p.setString(1,clave.sku());p.setString(2,clave.almacen());p.setTimestamp(3,Timestamp.from(corteExclusivo));
            try(var r=p.executeQuery()){return r.next()?Optional.of(new SaldoStock(clave.sku(),clave.almacen(),r.getBoolean(1),r.getBigDecimal(2),r.getBigDecimal(3))):Optional.empty();}
        }catch(SQLException e){throw fallo(e);}
    }
    @Override public Estado registrar(UUID id, String huella, ClaveStock clave, Function<Optional<SaldoStock>, Estado> operacion) {
        Objects.requireNonNull(id); Objects.requireNonNull(huella); Objects.requireNonNull(clave); Objects.requireNonNull(operacion);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Serializa las escrituras de este módulo entre procesos, incluyendo el primer saldo.
                try (PreparedStatement p = c.prepareStatement("SELECT pg_advisory_xact_lock(739102813)")) { p.execute(); }
                Estado previo = recuperar(c, id, huella, clave);
                if (previo != null) { c.commit(); return previo; }
                Estado nuevo = Objects.requireNonNull(operacion.apply(buscar(c, clave)));
                if (!id.equals(nuevo.movimiento().id()) || !clave.equals(new ClaveStock(nuevo.saldo().sku(), nuevo.saldo().almacen())))
                    throw new IllegalStateException("Resultado inconsistente");
                guardarSaldo(c, nuevo.saldo());
                guardarMovimiento(c, nuevo, huella);
                c.commit(); return nuevo;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw fallo(e); }
    }
    private Estado recuperar(Connection c, UUID id, String huella, ClaveStock clave) throws SQLException {
        String sql = "SELECT id,fecha,tipo,sku,almacen,cantidad_solicitada,cambio_fisico,fisico_anterior,fisico_posterior,referencia,motivo,huella,reservado_posterior,permite_fraccion,reserva_id FROM stock_movimiento WHERE id=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                var m = movimiento(r);
                if (r.getObject(15) != null || !huella.equals(r.getString(12)) || !clave.equals(new ClaveStock(m.sku(), m.almacen())))
                    throw new ValidacionException("El identificador de solicitud ya se usó con otros datos");
                return new Estado(new SaldoStock(m.sku(), m.almacen(), r.getBoolean(14), m.fisicoPosterior(), r.getBigDecimal(13)), m);
            }
        }
    }
    private void guardarSaldo(Connection c, SaldoStock s) throws SQLException {
        String sql = "INSERT INTO stock_saldo(sku,almacen,permite_fraccion,fisico,reservado) VALUES(?,?,?,?,?) ON CONFLICT(sku,almacen) DO UPDATE SET permite_fraccion=EXCLUDED.permite_fraccion,fisico=EXCLUDED.fisico,reservado=EXCLUDED.reservado";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1,s.sku()); p.setString(2,s.almacen()); p.setBoolean(3,s.permiteFraccion()); p.setBigDecimal(4,s.fisico()); p.setBigDecimal(5,s.reservado()); p.executeUpdate();
        }
    }
    private void guardarMovimiento(Connection c, Estado estado, String huella) throws SQLException {
        var m = estado.movimiento(); var s = estado.saldo();
        String sql = "INSERT INTO stock_movimiento(id,huella,fecha,tipo,sku,almacen,cantidad_solicitada,cambio_fisico,fisico_anterior,fisico_posterior,reservado_posterior,permite_fraccion,referencia,motivo) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1,m.id()); p.setString(2,huella); p.setTimestamp(3,Timestamp.from(m.fecha())); p.setString(4,m.tipo().name());
            p.setString(5,m.sku()); p.setString(6,m.almacen()); p.setBigDecimal(7,m.cantidadSolicitada()); p.setBigDecimal(8,m.cambioFisico());
            p.setBigDecimal(9,m.fisicoAnterior()); p.setBigDecimal(10,m.fisicoPosterior()); p.setBigDecimal(11,s.reservado()); p.setBoolean(12,s.permiteFraccion());
            p.setString(13,m.referencia()); p.setString(14,m.motivo()); p.executeUpdate();
        }
    }
    private MovimientoStock movimiento(ResultSet r) throws SQLException {
        return new MovimientoStock(r.getObject(1,UUID.class), r.getTimestamp(2).toInstant(), MovimientoStock.Tipo.valueOf(r.getString(3)),
                r.getString(4),r.getString(5),r.getBigDecimal(6),r.getBigDecimal(7),r.getBigDecimal(8),r.getBigDecimal(9),r.getString(10),r.getString(11));
    }
    @Override public Optional<ReservaOperacion> buscarReserva(UUID id) {
        try (Connection c = dataSource.getConnection()) { return buscarReserva(c, id); }
        catch (SQLException e) { throw fallo(e); }
    }
    private Optional<ReservaOperacion> buscarReserva(Connection c, UUID id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT separacion_id,sku,almacen,solicitada,reservada,estado FROM stock_reserva WHERE id=?")) {
            p.setObject(1,id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new ReservaOperacion(id,r.getObject(1,UUID.class),new ClaveStock(r.getString(2),r.getString(3)),r.getBigDecimal(4),r.getBigDecimal(5),ReservaOperacion.Estado.valueOf(r.getString(6))));
            }
        }
    }
    @Override public ResultadoReserva registrarReserva(UUID id, String huella, ClaveStock clave, UUID reservaId,
                                                       Function<ContextoReserva, ResultadoReserva> operacion) {
        Objects.requireNonNull(id); Objects.requireNonNull(huella); Objects.requireNonNull(clave); Objects.requireNonNull(reservaId); Objects.requireNonNull(operacion);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("SELECT pg_advisory_xact_lock(739102813)")) { p.execute(); }
                ResultadoReserva previo = recuperarReserva(c,id,huella,clave,reservaId);
                if (previo != null) { c.commit(); return previo; }
                SaldoStock saldo = buscar(c,clave).orElseThrow(() -> new ValidacionException("Stock no registrado"));
                var resultado = Objects.requireNonNull(operacion.apply(new ContextoReserva(saldo,buscarReserva(c,reservaId))));
                if (!resultado.reserva().id().equals(reservaId) || !resultado.reserva().clave().equals(clave)
                        || !resultado.movimiento().id().equals(id) || !new ClaveStock(resultado.saldo().sku(),resultado.saldo().almacen()).equals(clave))
                    throw new IllegalStateException("Resultado inconsistente");
                guardarSaldo(c,resultado.saldo());
                guardarMovimiento(c,new Estado(resultado.saldo(),resultado.movimiento()),huella);
                guardarReserva(c,resultado.reserva());
                try (PreparedStatement p = c.prepareStatement("UPDATE stock_movimiento SET reserva_id=?,separacion_id=?,reserva_solicitada=?,reserva_cantidad=?,reserva_estado=? WHERE id=?")) {
                    var r = resultado.reserva(); p.setObject(1,r.id()); p.setObject(2,r.separacionId()); p.setBigDecimal(3,r.solicitada()); p.setBigDecimal(4,r.reservada()); p.setString(5,r.estado().name()); p.setObject(6,id); p.executeUpdate();
                }
                c.commit(); return resultado;
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw fallo(e); }
    }
    private ResultadoReserva recuperarReserva(Connection c, UUID id, String huella, ClaveStock clave, UUID reservaId) throws SQLException {
        String sql = "SELECT id,fecha,tipo,sku,almacen,cantidad_solicitada,cambio_fisico,fisico_anterior,fisico_posterior,referencia,motivo,huella,reservado_posterior,permite_fraccion,reserva_id,separacion_id,reserva_solicitada,reserva_cantidad,reserva_estado FROM stock_movimiento WHERE id=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1,id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                MovimientoStock m = movimiento(r);
                if (!huella.equals(r.getString(12)) || !clave.equals(new ClaveStock(m.sku(),m.almacen())) || !reservaId.equals(r.getObject(15,UUID.class)))
                    throw new ValidacionException("El identificador de solicitud ya se usó con otros datos");
                var saldo = new SaldoStock(m.sku(),m.almacen(),r.getBoolean(14),m.fisicoPosterior(),r.getBigDecimal(13));
                var reserva = new ReservaOperacion(reservaId,r.getObject(16,UUID.class),clave,r.getBigDecimal(17),r.getBigDecimal(18),ReservaOperacion.Estado.valueOf(r.getString(19)));
                return new ResultadoReserva(saldo,m,reserva);
            }
        }
    }
    private void guardarReserva(Connection c, ReservaOperacion r) throws SQLException {
        String sql = "INSERT INTO stock_reserva(id,separacion_id,sku,almacen,solicitada,reservada,estado) VALUES(?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET reservada=EXCLUDED.reservada,estado=EXCLUDED.estado";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setObject(1,r.id()); p.setObject(2,r.separacionId()); p.setString(3,r.clave().sku()); p.setString(4,r.clave().almacen()); p.setBigDecimal(5,r.solicitada()); p.setBigDecimal(6,r.reservada()); p.setString(7,r.estado().name()); p.executeUpdate();
        }
    }
    private PersistenciaException fallo(SQLException e) { return new PersistenciaException("No se pudo acceder al inventario", e); }
}
