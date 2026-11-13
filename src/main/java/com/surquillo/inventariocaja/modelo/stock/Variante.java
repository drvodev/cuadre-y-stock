package com.surquillo.inventariocaja.modelo.stock;

import com.surquillo.inventariocaja.excepcion.ValidacionException;
import java.math.BigDecimal;
import java.util.Locale;

/** Artículo catalogado con precio y existencias propios. */
public record Variante(String sku, String descripcion, String categoria, String marca,
                       String presentacion, String tipo, String unidad, boolean permiteFraccion,
                       Origen origen, BigDecimal contenido, String unidadContenido,
                       BigDecimal precioVenta, BigDecimal costo, boolean activo, long version,
                       java.util.Map<String,String> atributos) {
    public enum Origen { COMPRADO, FABRICADO }
    public Variante(String sku,String descripcion,String categoria,String marca,String presentacion,String tipo,
                    String unidad,boolean permiteFraccion,Origen origen,BigDecimal contenido,String unidadContenido,
                    BigDecimal precioVenta,BigDecimal costo,boolean activo,long version) {
        this(sku,descripcion,categoria,marca,presentacion,tipo,unidad,permiteFraccion,origen,contenido,
             unidadContenido,precioVenta,costo,activo,version,java.util.Map.of());
    }
    public Variante {
        atributos=AtributosCatalogo.normalizar(atributos);
        sku = obligatorio(sku,"SKU").toUpperCase(Locale.ROOT);
        descripcion = obligatorio(descripcion,"Descripción");
        categoria = opcional(categoria); marca = opcional(marca);
        presentacion = opcional(presentacion); tipo = opcional(tipo);
        unidad = obligatorio(unidad,"Unidad"); unidadContenido = opcional(unidadContenido);
        if (origen == null) throw new ValidacionException("El origen es obligatorio");
        if ((contenido == null) != unidadContenido.isEmpty()) throw new ValidacionException("El contenido y su unidad deben indicarse juntos");
        if (contenido != null && (contenido.signum() <= 0 || contenido.stripTrailingZeros().scale() > 3))
            throw new ValidacionException("El contenido debe ser positivo y tener hasta tres decimales");
        if (precioVenta != null && (precioVenta.signum() <= 0 || precioVenta.stripTrailingZeros().scale() > 2))
            throw new ValidacionException("El precio debe ser positivo y tener hasta dos decimales");
        if (costo != null && (costo.signum() < 0 || costo.stripTrailingZeros().scale() > 2))
            throw new ValidacionException("El costo no puede ser negativo y admite hasta dos decimales");
        if (version < 0) throw new ValidacionException("Versión inválida");
    }
    private static String obligatorio(String valor, String campo) {
        String texto = opcional(valor);
        if (texto.isEmpty()) throw new ValidacionException(campo + " es obligatorio");
        return texto;
    }
    private static String opcional(String valor) { return valor == null ? "" : valor.strip(); }
    public Variante conVersion(long valor) {
        return new Variante(sku,descripcion,categoria,marca,presentacion,tipo,unidad,permiteFraccion,origen,
                contenido,unidadContenido,precioVenta,costo,activo,valor,atributos);
    }
}
