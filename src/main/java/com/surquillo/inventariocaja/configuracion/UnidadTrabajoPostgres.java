package com.surquillo.inventariocaja.configuracion;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/** Comparte una conexión por operación y confirma todos los repositorios juntos. */
public final class UnidadTrabajoPostgres implements DataSource {
    private static final class Sesion { final Connection conexion; boolean revertir; Sesion(Connection c) { conexion=c; } }
    private final DataSource base;
    private final ThreadLocal<Sesion> actual=new ThreadLocal<>();
    public UnidadTrabajoPostgres(DataSource base) { this.base=java.util.Objects.requireNonNull(base); }
    public <T> T ejecutar(Callable<T> accion) throws Exception {
        if(actual.get()!=null) {
            try { return accion.call(); } catch(Exception e) { actual.get().revertir=true; throw e; }
        }
        try(Connection c=base.getConnection()) {
            c.setAutoCommit(false); var sesion=new Sesion(c); actual.set(sesion);
            try {
                try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(739102816)")) { p.execute(); }
                T resultado=accion.call();
                if(sesion.revertir) throw new SQLException("Una parte de la operación solicitó revertir la transacción");
                c.commit(); return resultado;
            } catch(Exception e) { try { c.rollback(); } catch(SQLException x) { e.addSuppressed(x); } throw e; }
            finally { actual.remove(); }
        }
    }
    @Override public Connection getConnection() throws SQLException {
        Sesion sesion=actual.get(); if(sesion==null) return base.getConnection();
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,metodo,args)-> {
            switch(metodo.getName()) {
                case "close", "commit" -> { return null; }
                case "rollback" -> { sesion.revertir=true; return null; }
                case "setAutoCommit" -> { if(Boolean.TRUE.equals(args[0])) throw new SQLException("La confirmación pertenece a la unidad de trabajo"); return null; }
                case "unwrap" -> { throw new SQLFeatureNotSupportedException(); }
                case "isWrapperFor" -> { return false; }
                default -> { try { return metodo.invoke(sesion.conexion,args); } catch(InvocationTargetException e) { throw e.getCause(); } }
            }
        });
    }
    @Override public Connection getConnection(String usuario,String clave) throws SQLException {
        if(actual.get()!=null) throw new SQLException("No se puede cambiar de usuario durante una transacción");
        return base.getConnection(usuario,clave);
    }
    @Override public PrintWriter getLogWriter() throws SQLException { return base.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter w) throws SQLException { base.setLogWriter(w); }
    @Override public int getLoginTimeout() throws SQLException { return base.getLoginTimeout(); }
    @Override public void setLoginTimeout(int s) throws SQLException { base.setLoginTimeout(s); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return base.getParentLogger(); }
    @Override public boolean isWrapperFor(Class<?> tipo) { return tipo.isInstance(this); }
    @Override public <T> T unwrap(Class<T> tipo) throws SQLException { if(tipo.isInstance(this)) return tipo.cast(this); throw new SQLException("Tipo no compatible"); }
}
