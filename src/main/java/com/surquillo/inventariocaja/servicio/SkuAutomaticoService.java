package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.configuracion.UnidadTrabajoPostgres;
import com.surquillo.inventariocaja.excepcion.PersistenciaException;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import com.surquillo.inventariocaja.repositorio.ProductoPostgresRepository;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;

/** Asigna y guarda el SKU y la solicitud en una sola transacción. */
public final class SkuAutomaticoService {
    private final DataSource ds;
    public SkuAutomaticoService(DataSource ds,CatalogoService catalogo){this.ds=Objects.requireNonNull(ds);Objects.requireNonNull(catalogo);}
    public Variante crear(Variante plantilla) { return crear(UUID.randomUUID(),plantilla); }
    public Variante crear(UUID solicitud,Variante plantilla) {
        Objects.requireNonNull(solicitud);Objects.requireNonNull(plantilla);
        if(plantilla.version()!=0)throw new ValidacionException("La versión inicial debe ser cero");
        String huella=huella(plantilla);
        var unidad=new UnidadTrabajoPostgres(ds);var repositorio=new ProductoPostgresRepository(unidad);
        try {
            return unidad.ejecutar(()->{
                try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT pg_advisory_xact_lock(739102817)")){p.execute();}
                try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT huella,sku FROM catalogo_creacion_solicitud WHERE id=?")) {
                    p.setObject(1,solicitud);try(var r=p.executeQuery()){if(r.next()) {
                        if(!huella.equals(r.getString(1)))throw new ValidacionException("La solicitud ya se utilizó con otros datos");
                        return repositorio.buscarSku(r.getString(2)).orElseThrow(()->new ValidacionException("El artículo de esta solicitud ya no está disponible"));
                    }}
                }
                for(int intento=0;intento<1000;intento++) {
                    long n;try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT nextval('catalogo_sku_secuencia')");var r=p.executeQuery()){r.next();n=r.getLong(1);}
                    String codigo=Long.toString(n);if(codigo.length()<3)codigo="0".repeat(3-codigo.length())+codigo;
                    try(var c=unidad.getConnection();var p=c.prepareStatement("SELECT 1 FROM catalogo_sku_utilizado WHERE sku=?")) {
                        p.setString(1,codigo);try(var r=p.executeQuery()){if(r.next())continue;}
                    }
                    var v=new Variante(codigo,plantilla.descripcion(),plantilla.categoria(),plantilla.marca(),plantilla.presentacion(),plantilla.tipo(),plantilla.unidad(),plantilla.permiteFraccion(),plantilla.origen(),plantilla.contenido(),plantilla.unidadContenido(),plantilla.precioVenta(),plantilla.costo(),plantilla.activo(),plantilla.version(),plantilla.atributos());
                    repositorio.crear(v);
                    try(var c=unidad.getConnection();var p=c.prepareStatement("INSERT INTO catalogo_creacion_solicitud(id,huella,sku) VALUES(?,?,?)")) {
                        p.setObject(1,solicitud);p.setString(2,huella);p.setString(3,codigo);p.executeUpdate();
                    }
                    return v;
                }
                throw new ValidacionException("No se encontró un SKU disponible; revisa la numeración.");
            });
        } catch(ValidacionException e){throw e;}
        catch(Exception e){throw new PersistenciaException("No se pudo confirmar el artículo; reintenta con los mismos datos",e);}
    }
    private static String huella(Variante v) {
        var b=new StringBuilder();
        for(Object x:List.of(v.descripcion(),v.categoria(),v.marca(),v.presentacion(),v.tipo(),v.unidad(),v.permiteFraccion(),v.origen(),
                decimal(v.contenido()),v.unidadContenido(),decimal(v.precioVenta()),decimal(v.costo()),v.activo(),v.version())) agregar(b,x.toString());
        new TreeMap<>(v.atributos()).forEach((k,valor)->{agregar(b,k);agregar(b,valor);});return b.toString();
    }
    private static String decimal(BigDecimal n){return n==null?"":n.stripTrailingZeros().toPlainString();}
    private static void agregar(StringBuilder b,String s){b.append(s.length()).append(':').append(s);}
}
