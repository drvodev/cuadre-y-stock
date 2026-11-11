package com.surquillo.inventariocaja.modelo.caja;

import java.math.*;
import com.surquillo.inventariocaja.excepcion.ValidacionException;
public record ItemOperacion(String sku,String almacen,String descripcion,String unidad,
                            BigDecimal cantidad,BigDecimal precio,String indicaciones) {
    public ItemOperacion {
        sku=sku==null?"":sku.strip().toUpperCase(java.util.Locale.ROOT);
        almacen=almacen==null?"":almacen.strip(); descripcion=descripcion==null?"":descripcion.strip();
        unidad=unidad==null||unidad.isBlank()?"unidad":unidad.strip(); indicaciones=indicaciones==null?"":indicaciones.strip();
        if(descripcion.isEmpty()||cantidad==null||cantidad.signum()<=0||cantidad.stripTrailingZeros().scale()>3||precio==null||precio.signum()<=0||precio.stripTrailingZeros().scale()>2)
            throw new ValidacionException("Revisa la descripción, cantidad y precio del ítem");
        cantidad=cantidad.stripTrailingZeros(); precio=precio.setScale(2);
        if(!sku.isEmpty()&&almacen.isEmpty()) throw new ValidacionException("Indica el almacén del ítem catalogado");
    }
    public BigDecimal total() { return cantidad.multiply(precio).setScale(2,RoundingMode.HALF_UP); }
}
