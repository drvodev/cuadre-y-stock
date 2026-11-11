package com.surquillo.inventariocaja.configuracion;

import javax.sql.DataSource;
public final class PreparacionBeta {
    private PreparacionBeta() {}
    public static DataSource preparar(String url,String usuario,String clave,String esquema) throws Exception {
        if(esquema==null||!esquema.matches("[a-z][a-z0-9_]{0,62}")||esquema.equals("public")||esquema.startsWith("pg_"))
            throw new IllegalArgumentException("Elige un esquema de pruebas: letras minúsculas, números y guion bajo; por ejemplo cuadre_pruebas");
        if(url.contains("currentSchema="))throw new IllegalArgumentException("Configura el esquema por separado");
        var base=new ConexionPostgres(url,usuario,clave);
        try(var c=base.getConnection();var s=c.createStatement()) {s.execute("CREATE SCHEMA IF NOT EXISTS "+esquema);}
        var ds=new ConexionPostgres(url+(url.contains("?")?"&":"?")+"currentSchema="+esquema,usuario,clave);
        MigracionesPostgres.aplicar(ds);return ds;
    }
}
