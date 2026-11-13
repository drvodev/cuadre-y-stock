package com.surquillo.inventariocaja.configuracion;

import javax.sql.DataSource;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Aplicación explícita y transaccional de versiones. No adopta esquemas antiguos automáticamente. */
public final class MigracionesPostgres {
    private static final String[] ARCHIVOS={"/db/catalogo-v1.sql","/db/stock-v1.sql","/db/stock-catalogo-v3.sql","/db/caja-v4.sql","/db/caja-conciliacion-v5.sql","/db/operaciones-v6.sql","/db/anulaciones-v7.sql","/db/catalogo-listas-v8.sql","/db/caja-ganancia-v9.sql","/db/stock-consultas-v10.sql","/db/compras-v11.sql"};
    private MigracionesPostgres() {}
    public static void aplicar(DataSource ds) throws Exception {
        try(Connection c=ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()) {
                    s.execute("SELECT pg_advisory_xact_lock(739102814)");
                    s.execute("CREATE TABLE IF NOT EXISTS app_migracion(version integer PRIMARY KEY, hash text NOT NULL, aplicada timestamptz NOT NULL DEFAULT now())");
                    try(var r=s.executeQuery("SELECT max(version) FROM app_migracion")) {
                        r.next(); if(r.getInt(1)>ARCHIVOS.length) throw new SQLException("La base requiere una versión más reciente de la aplicación");
                    }
                }
                for(int i=0;i<ARCHIVOS.length;i++) {
                    byte[] contenido;
                    try(var recurso=MigracionesPostgres.class.getResourceAsStream(ARCHIVOS[i])) {
                        if(recurso==null) throw new IllegalStateException("Migración no disponible");
                        contenido=recurso.readAllBytes();
                    }
                    String texto=new String(contenido,StandardCharsets.UTF_8).replace("\r\n","\n");
                    String hash=hash(texto.getBytes(StandardCharsets.UTF_8));
                    String hashWindows=hash(texto.replace("\n","\r\n").getBytes(StandardCharsets.UTF_8));
                    boolean existe;
                    try(var p=c.prepareStatement("SELECT hash FROM app_migracion WHERE version=?")) {
                        p.setInt(1,i+1);
                        try(var r=p.executeQuery()) { existe=r.next(); if(existe && !hash.equals(r.getString(1)) && !hashWindows.equals(r.getString(1))) throw new SQLException("Se modificó una migración ya aplicada"); }
                    }
                    if(!existe) {
                        try(var s=c.createStatement()) { s.execute(new String(contenido,StandardCharsets.UTF_8)); }
                        try(var p=c.prepareStatement("INSERT INTO app_migracion(version,hash) VALUES(?,?)")) { p.setInt(1,i+1); p.setString(2,hash); p.executeUpdate(); }
                    }
                }
                c.commit();
            } catch(Exception e) { try { c.rollback(); } catch(SQLException fallo) { e.addSuppressed(fallo); } throw e; }
        }
    }
    private static String hash(byte[] contenido) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
    }
}
