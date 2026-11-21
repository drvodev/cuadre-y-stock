package com.surquillo.inventariocaja.servicio;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import com.surquillo.inventariocaja.modelo.stock.Variante;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;

/** Crea artículos con códigos monotónicos asignados por PostgreSQL. */
public final class SkuAutomaticoService {
    private final DataSource ds;private final CatalogoService catalogo;
    public SkuAutomaticoService(DataSource ds,CatalogoService catalogo){this.ds=Objects.requireNonNull(ds);this.catalogo=Objects.requireNonNull(catalogo);}
    public Variante crear(Variante plantilla) {
        for(int intento=0;intento<1000;intento++) {
            long n;
            try(var c=ds.getConnection();var p=c.prepareStatement("SELECT nextval('catalogo_sku_secuencia')");var r=p.executeQuery()){r.next();n=r.getLong(1);}
            catch(SQLException e){throw new IllegalStateException("No se pudo generar el SKU.",e);}
            String codigo=Long.toString(n);if(codigo.length()<3)codigo="0".repeat(3-codigo.length())+codigo;
            var v=new Variante(codigo,plantilla.descripcion(),plantilla.categoria(),plantilla.marca(),plantilla.presentacion(),plantilla.tipo(),plantilla.unidad(),plantilla.permiteFraccion(),plantilla.origen(),plantilla.contenido(),plantilla.unidadContenido(),plantilla.precioVenta(),plantilla.costo(),plantilla.activo(),plantilla.version(),plantilla.atributos());
            try{return catalogo.crear(v);}catch(ValidacionException duplicado){if(!"El SKU ya existe".equals(duplicado.getMessage()))throw duplicado;}
        }
        throw new ValidacionException("No se encontró un SKU disponible; revisa la numeración.");
    }
}
