package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.ConexionPostgres;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Copias manuales PostgreSQL; recuperación únicamente en una base diferente y vacía. */
public final class RespaldoService {
    private final String host,puerto,base,usuario,clave,esquema;
    private final Path binarios;
    public RespaldoService(String host,String puerto,String base,String usuario,String clave,String esquema,Path binarios) {
        if(host==null||!(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1"))||puerto==null||!puerto.matches("[0-9]{1,5}")
            ||base==null||!base.matches("[a-zA-Z0-9_-]+")||esquema==null||!esquema.matches("[a-z][a-z0-9_]{0,62}")||esquema.equals("public")||esquema.startsWith("pg_")
            ||usuario==null||usuario.isBlank()||clave==null||clave.isBlank()||binarios==null)
            throw new IllegalArgumentException("Para esta versión usa PostgreSQL local y revisa la carpeta de herramientas");
        this.host=host;this.puerto=puerto;this.base=base;this.usuario=usuario;this.clave=clave;this.esquema=esquema;
        this.binarios=binarios.toAbsolutePath().normalize();
    }
    public Path crear(Path carpeta) throws IOException,InterruptedException {
        if(carpeta==null||!Files.isDirectory(carpeta))throw new IllegalArgumentException("Elige una carpeta existente");
        Path parcial=Files.createTempFile(carpeta,".cuadre-respaldo-",".parcial");
        try {
            var nombre="cuadre-"+esquema+"-"+DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("America/Lima")).format(Instant.now())+"-"+UUID.randomUUID().toString().substring(0,8)+".backup";
            ejecutar("pg_dump",List.of("--format=custom","--no-owner","--no-acl","--schema="+esquema,"--file="+parcial,"--dbname="+base));
            validarArchivo(parcial);
            Path destino=carpeta.resolve(nombre);
            return Files.move(parcial,destino);
        } finally { Files.deleteIfExists(parcial); }
    }
    /** El destino debe existir, ser diferente y no contener objetos ni esquemas de usuario. */
    public void restaurar(Path archivo,String destino) throws IOException,InterruptedException,SQLException {
        if(destino==null||!destino.matches("[a-zA-Z0-9_-]+")||destino.equalsIgnoreCase(base))
            throw new IllegalArgumentException("Indica una base nueva distinta de la base actual");
        validarArchivo(archivo);
        var ds=new ConexionPostgres("jdbc:postgresql://"+host+":"+puerto+"/"+destino,usuario,clave);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery(
            "SELECT (SELECT datistemplate FROM pg_database WHERE datname=current_database()) OR EXISTS (SELECT 1 FROM pg_namespace WHERE nspname !~ '^pg_' AND nspname NOT IN ('information_schema','public')) OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public') OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public') OR EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public' AND t.typtype IN ('e','d','c','r'))")){
            r.next();if(r.getBoolean(1))throw new IllegalArgumentException("La base de destino debe estar vacía");
        }
        ejecutar("pg_restore",List.of("--exit-on-error","--single-transaction","--no-owner","--no-acl","--dbname="+destino,archivo.toAbsolutePath().toString()));
    }
    private void validarArchivo(Path archivo)throws IOException,InterruptedException {
        if(archivo==null||!Files.isRegularFile(archivo)||Files.size(archivo)==0)throw new IllegalArgumentException("El archivo de respaldo no existe o está vacío");
        ejecutar("pg_restore",List.of("--list",archivo.toAbsolutePath().toString()));
    }
    private void ejecutar(String programa,List<String> argumentos)throws IOException,InterruptedException {
        Path ejecutable=binarios.resolve(programa+(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":""));
        if(!Files.isRegularFile(ejecutable))throw new IOException("No se encontró "+programa+" en la carpeta de PostgreSQL");
        var comando=new ArrayList<String>();comando.add(ejecutable.toString());comando.add("--host="+host);comando.add("--port="+puerto);
        comando.add("--username="+usuario);comando.add("--no-password");comando.addAll(argumentos);
        var proceso=new ProcessBuilder(comando).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        proceso.environment().put("PGPASSWORD",clave);proceso.environment().put("PGCONNECT_TIMEOUT","10");
        var iniciado=proceso.start();
        try {
            if(!iniciado.waitFor(120,TimeUnit.SECONDS)){iniciado.destroyForcibly();throw new IOException("La operación de PostgreSQL excedió el tiempo de espera");}
            if(iniciado.exitValue()!=0)throw new IOException("PostgreSQL no pudo completar "+programa+". Revisa permisos, versión y conexión");
        }catch(InterruptedException e){iniciado.destroyForcibly();Thread.currentThread().interrupt();throw e;}
    }
}
