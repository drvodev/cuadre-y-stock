package com.surquillo.inventariocaja.configuracion;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/** Configuración explícita: no guarda ni muestra la contraseña. */
public final class ConexionPostgres implements DataSource {
    private final String url, usuario, clave;
    public ConexionPostgres(String url, String usuario, String clave) {
        if (url == null || !url.startsWith("jdbc:postgresql://") || usuario == null || usuario.isBlank() || clave == null || clave.isBlank())
            throw new IllegalArgumentException("Configura la conexión PostgreSQL, el usuario y la contraseña");
        this.url=url; this.usuario=usuario; this.clave=clave;
    }
    public static ConexionPostgres desdeEntorno(Map<String,String> entorno) {
        return new ConexionPostgres(entorno.get("APP_DB_URL"),entorno.get("APP_DB_USER"),entorno.get("APP_DB_PASSWORD"));
    }
    @Override public Connection getConnection() throws SQLException { return getConnection(usuario,clave); }
    @Override public Connection getConnection(String usuario,String clave) throws SQLException {
        var p=new Properties(); p.setProperty("user",usuario); p.setProperty("password",clave);
        p.setProperty("connectTimeout","10"); p.setProperty("socketTimeout","30");
        return DriverManager.getConnection(url,p);
    }
    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter escritor) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setLoginTimeout(int segundos) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public int getLoginTimeout() { return 10; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T unwrap(Class<T> tipo) throws SQLException { if(tipo.isInstance(this)) return tipo.cast(this); throw new SQLException("Tipo no compatible"); }
    @Override public boolean isWrapperFor(Class<?> tipo) { return tipo.isInstance(this); }
}
